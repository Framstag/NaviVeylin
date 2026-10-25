package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.FollowDisplayState
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.VehicleMarkerGeometry
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.naviveylin.auto.AutoMapRenderer.Companion.RENDER_DEBOUNCE_MS
import com.naviveylin.auto.AutoMapRenderer.Companion.EXTRAPOLATION_FRAME_MS
import com.naviveylin.auto.AutoMapRenderer.Companion.ZOOM_WALK_FRAME_MS


// The three loops this renderer starts and the iteration hooks their supervisor
// calls: the render loop, the follow/extrapolation loop and the zoom-transition loop.

internal fun AutoMapRenderer.startRenderLoop() {
    renderJob = scope.launch {
        var lastRender = 0L
        renderSignal.collect { ts ->
            if (!asyncLoopsEnabled) return@collect
            if (isShutdown || paused || surfaceFailed) return@collect
            if (ts == lastRender) return@collect
            delay(RENDER_DEBOUNCE_MS)
            lastRender = renderSignal.value
            // One frame per iteration, confined: without the guard a throwing frame ended
            // this collector, so every later `requestRender()` was dropped and the surface
            // stayed on its last frame until the screen was re-entered (TODO.md §51;
            // spec: car-host-fault-isolation — A fault in one frame keeps the frames coming).
            frameIteration()
        }
    }
}

internal fun AutoMapRenderer.maybeFailForTest(loop: RenderLoop) {
    if (testIterationFault == loop) error("injected ${loop.label} fault")
}

/**
 * One iteration of the render loop: consumes a pending render request and draws the frame,
 * confining a fault to this iteration (spec: car-host-fault-isolation — A fault in one frame
 * keeps the frames coming; design D1). Extracted so the confinement is testable without the
 * loop's debounce.
 */
internal fun AutoMapRenderer.frameIteration() {
    // A stopped (or shut-down, or dead-surface) renderer performs no iteration — and therefore
    // can neither draw nor count a fault, so a screen that is not started cannot drive the
    // re-creation ladder (spec: car-host-fault-isolation — Fresh screen start re-arms the
    // recovery budget). The loop's own gate already returns early; this is the same rule at the
    // iteration, which is what the tests can drive deterministically.
    if (isShutdown || paused || surfaceFailed) return
    if (!pendingRender) return
    pendingRender = false
    loops.runIteration(RenderLoop.FRAME) {
        maybeFailForTest(RenderLoop.FRAME)
        renderFrame()
    }
}

/** Consecutive confined faults of the current streak — exposed for tests. */
internal fun AutoMapRenderer.loopFaultStreak(): Int = loops.currentStreak()

/**
 * One iteration of the displayed-frame loop, confined (spec: car-host-fault-isolation — A
 * fault in a display tick keeps the follow pipeline running; design D1).
 */
internal fun AutoMapRenderer.displayTickIteration(nowMs: Long, dtSec: Double) {
    loops.runIteration(RenderLoop.EXTRAPOLATION) {
        maybeFailForTest(RenderLoop.EXTRAPOLATION)
        extrapolationTick(nowMs, dtSec)
    }
}

/**
 * One iteration of the zoom-transition loop — the eased magnification and the walk are one
 * step, so a fault skips the pair (spec: car-host-fault-isolation — A fault in a display tick
 * keeps the follow pipeline running; design D1).
 */
internal fun AutoMapRenderer.zoomWalkIteration(nowMs: Long, dtSec: Double) {
    loops.runIteration(RenderLoop.ZOOM_WALK) {
        maybeFailForTest(RenderLoop.ZOOM_WALK)
        if (!extrapolationGateActive(nowMs)) advanceDisplayedMagnification(dtSec)
        advanceZoomWalk()
    }
}

/**
 * Gated extrapolation display loop (spec: auto-smooth-follow, design D3/D6).
 *
 * Runs at ~30 fps while the renderer is resumed, follow mode is active, the
 * vehicle is moving (speed > ~1 m/s), the fix is fresh, and the surface is
 * valid. Each tick eases the displayed position toward the prediction and
 * blits the overrun buffer by the delta; when the display hits the overrun
 * margin a full render is requested at the display position.
 */
internal fun AutoMapRenderer.startExtrapolationLoop() {
    extrapolationJob = scope.launch {
        var lastFrameMs = 0L
        while (isActive) {
            if (!asyncLoopsEnabled) {
                lastFrameMs = 0L
                delay(EXTRAPOLATION_FRAME_MS)
                continue
            }
            if (isShutdown) break
            val nowMs = System.currentTimeMillis()
            if (!extrapolationGateActive(nowMs)) {
                lastFrameMs = 0L
                // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-
                // follow — "Vehicle stops"): the displayed position and
                // marker stay FROZEN at the last position while the gate
                // is closed — no NaN reset, no raw-fix fallback. The
                // resume continues from the frozen position (see
                // extrapolationTick's gate guard + state re-sync).
                delay(EXTRAPOLATION_FRAME_MS)
                continue
            }
            val dtSec = if (lastFrameMs > 0) (nowMs - lastFrameMs) / 1000.0 else 0.016
            lastFrameMs = nowMs
            // One displayed-frame step per iteration, confined (spec: car-host-fault-isolation
            // — A fault in a display tick keeps the follow pipeline running).
            displayTickIteration(nowMs, dtSec)
            delay(EXTRAPOLATION_FRAME_MS)
        }
    }
}

/**
 * Zoom-transition loop (spec: auto-speed-zoom — Auto-zoom entry transition; design D2).
 *
 * Deliberately NOT the extrapolation loop: that one is gated on vehicle movement
 * ([MOVEMENT_SPEED_MS], [extrapolationGateActive]), which would stall a transition
 * started while parked — entering free driving at standstill would keep the old
 * magnification until the vehicle moves. This loop only needs a usable surface, and
 * while the extrapolation loop is closed it also owns the displayed-magnification
 * easing; otherwise the frames would land at the new magnification while the displayed
 * scale stayed behind (the overrun blit can only show one blit window of it).
 */
internal fun AutoMapRenderer.startZoomWalkLoop() {
    zoomWalkJob = scope.launch {
        var lastFrameMs = 0L
        while (isActive) {
            zoomWalkLoopTurns += 1
            val usable = asyncLoopsEnabled && !isShutdown && !paused && !surfaceFailed &&
                surface != null
            if (usable) {
                val nowMs = System.currentTimeMillis()
                val dtSec = if (lastFrameMs > 0) {
                    (nowMs - lastFrameMs) / 1000.0
                } else {
                    ZOOM_WALK_FRAME_MS / 1000.0
                }
                lastFrameMs = nowMs
                // One transition step per iteration, confined (spec: car-host-fault-isolation
                // — A fault in a display tick keeps the follow pipeline running).
                zoomWalkIteration(nowMs, dtSec)
            } else {
                lastFrameMs = 0L
            }
            delay(ZOOM_WALK_FRAME_MS)
        }
    }
}
