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
import com.naviveylin.auto.AutoMapRenderer.Companion.TAG
import com.naviveylin.auto.AutoMapRenderer.Companion.surfaceLock
import com.naviveylin.auto.AutoMapRenderer.Companion.MIN_ZOOM
import com.naviveylin.auto.AutoMapRenderer.Companion.MAX_ZOOM
import com.naviveylin.auto.AutoMapRenderer.Companion.MOVEMENT_SPEED_MS
import com.naviveylin.auto.AutoMapRenderer.Companion.ZOOM_BLIT_LIMIT
import com.naviveylin.auto.AutoMapRenderer.Companion.ZOOM_EASE_TAU_SEC
import com.naviveylin.auto.AutoMapRenderer.Companion.ZOOM_WALK_SETTLE


// The motion between fixes and between magnifications: the extrapolation tick that
// eases the displayed position, the displayed magnification, the zoom walk that carries
// a magnification change across landed frames, and the zoom steps the gestures use.

/**
 * Current continuous (fractional) zoom, used for pinch accumulation.
 */
fun AutoMapRenderer.fractionalZoom(): Double = viewportZoomFraction

/**
 * Compute the zoom step for a pinch scale factor.
 *
 * Uses continuous zoom accumulation so small pinch deltas never jump a
 * whole level (old behavior: any `scaleFactor > 1f` snapped +1 level).
 * Returns the new (fractional zoom, integer zoom) without mutating state;
 * call [setViewport] with the results.
 */
fun AutoMapRenderer.zoomStep(scaleFactor: Float): Pair<Double, Int> {
    val newFraction = (
        viewportZoomFraction + kotlin.math.ln(scaleFactor.toDouble()) / kotlin.math.ln(2.0)
    ).coerceIn(MIN_ZOOM.toDouble(), MAX_ZOOM.toDouble())
    return newFraction to newFraction.roundToInt()
}

/**
 * One step of a pending zoom transition (spec: auto-speed-zoom — Auto-zoom entry
 * transition; design D2). Render-synchronous: a step is committed only once the
 * previous step's frame has LANDED ([overrunMag] caught up with the committed
 * magnification), so the committed value never leads the frame by more than one blit
 * window and the display never has to snap. Steps are bounded by [ZOOM_BLIT_LIMIT],
 * and the last step lands EXACTLY on the requested magnification. Returns the
 * committed magnification when a step was committed, else null. Exposed for tests.
 */
internal fun AutoMapRenderer.advanceZoomWalk(): Double? {
    val target = zoomWalkTarget
    if (target.isNaN()) return null
    if (overrunMag <= 0.0) {
        // No frame on screen yet (cold start): nothing to transition from, so the
        // request lands directly — the spec's "jumps directly to the target instead of
        // smoothing from the default map zoom".
        zoomWalkTarget = Double.NaN
        commitZoom(target)
        return target
    }
    val committed = viewportZoomFraction
    // Render-synchronous: wait for the previous step's frame.
    if (abs(overrunMag - committed) > ZOOM_WALK_SETTLE) return null
    val delta = target - committed
    if (abs(delta) < ZOOM_WALK_SETTLE) {
        zoomWalkTarget = Double.NaN
        return null
    }
    // Landing step: reach the requested value exactly, not through an arithmetic sum.
    val next = if (abs(delta) <= ZOOM_BLIT_LIMIT) {
        target
    } else {
        committed + delta.coerceIn(-ZOOM_BLIT_LIMIT, ZOOM_BLIT_LIMIT)
    }
    if (next == target) zoomWalkTarget = Double.NaN
    commitZoom(next)
    return next
}

/** Commit a walked magnification: full render at [fraction] (a zoom change cannot be
 *  served by a blit of the previous frame) + viewport state for the screens. */
internal fun AutoMapRenderer.commitZoom(fraction: Double) {
    viewportZoomFraction = fraction
    viewportZoom = fraction.roundToInt()
    blitEligible = false
    emitViewportState()
    requestRender()
}

/**
 * Gate for the extrapolation loop (design D6): resumed + follow mode +
 * moving + valid surface. A stale fix does NOT gate the loop off —
 * [FollowPrediction] holds the position beyond its extrapolation window,
 * so the display eases back to the last fix instead of freezing ahead of
 * it (same behavior as the phone's per-frame loop). Exposed for
 * deterministic tests.
 */
internal fun AutoMapRenderer.extrapolationGateActive(nowMs: Long): Boolean {
    if (paused || surfaceFailed || !followMode || surface == null) return false
    return !lastFixSpeedMs.isNaN() && lastFixSpeedMs > MOVEMENT_SPEED_MS
}

/**
 * One extrapolation tick: ease the displayed position toward the predicted
 * position and blit the overrun buffer by the delta. When the display hits
 * the overrun margin, request a full render at the display position.
 * Exposed for deterministic tests.
 */
internal fun AutoMapRenderer.extrapolationTick(nowMs: Long, dtSec: Double) {
    // The follow diagnostic (throttled, 1 line / 30 ticks) carries the
    // display step, frame target, offset and counters; the per-tick traces
    // were removed when the on-device investigation closed (task 10.3).
    // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
    // "Vehicle stops" / "Vehicle resumes after a stop"): while the gate
    // is closed (stopped / paused / follow disengaged / no surface) the
    // displayed position is FROZEN — no easing toward the fix, no display
    // mutation at all. The resume continues from the frozen position.
    if (!extrapolationGateActive(nowMs)) return
    val surf = surface ?: return
    val w = surfaceWidth
    val h = surfaceHeight
    if (w <= 0 || h <= 0) return
    val predicted = followPrediction.predictedPosition(nowMs)
    if (predicted.first.isNaN() || predicted.second.isNaN()) return
    // Re-sync after a frozen gap (stop / gate-off / surface change): if the
    // volatile display was re-seeded or cleared while the loop did not
    // tick, reset the state so the next advance continues from the current
    // displayed position instead of a stale one.
    if (displayLat.isNaN() || displayLon.isNaN() ||
        abs(displayLat - followDisplayState.lat) > 1e-12 ||
        abs(displayLon - followDisplayState.lon) > 1e-12
    ) {
        followDisplayState.reset()
    }
    // Bounded-rate displayed position (spec: auto-smooth-follow — Correction easing, Shared
    // follow bounds on the car surface): the display advances at the committed effective speed
    // along the direction of travel plus a rate-clamped correction toward the fix, so a fix that
    // lands behind the display is a rate dip, never a halt and never a reversal.
    // `gpsMarkerBearing` is the effective bearing (NaN drops the feed-forward term, matching the
    // phone). The result is copied back into the @Volatile fields, the single cross-thread
    // source for the marker and the margin render target.
    val vEff = followPrediction.effectiveSpeedMs(nowMs)
    // The reference is the extrapolated target ([predicted], the prediction's own output) — a
    // CONTINUOUS reference. The raw fix is a step function between arrivals, and correcting onto
    // it would run the map a quarter slow; the prediction supplies the motion, the fix has
    // already been folded into it.
    val (displayedLat, displayedLon) = followDisplayState.advance(
        predicted.first, predicted.second, dtSec, gpsMarkerBearing, vEff,
        followPrediction.feedAgeSec(nowMs)
    )
    displayLat = displayedLat
    displayLon = displayedLon
    // P3: move the DISPLAYED magnification toward the committed target across frames.
    advanceDisplayedMagnification(dtSec)
    synchronized(surfaceLock) {
        // Read + blit under the shared lock: a concurrent full render
        // recycles the overrun bitmap when it swaps in a new one — drawing
        // a recycled bitmap crashes. Holding the lock from the read
        // through the draw serializes against fullRender's swap.
        val current = overrunBitmap ?: return
        val resolved = resolvedFollowAnchor()
        val offset = FollowPrediction.displayOffsetPx(
            displayLat, displayLon,
            overrunLat, overrunLon, overrunMag, overrunAngle,
            current.width, current.height, w, h, projectionDpi,
            // Single resolved anchor (spec: auto-smooth-follow — Single
            // resolved anchor in the AA follow blit): the SAME fraction
            // anchorCenterFor commits the frame on. The raw preset differs
            // from it for pane-band presets (clampAnchorOutOfPane against
            // the host's 40% leading band) and a mismatch keeps the offset
            // permanently outside the overrun margin — every tick becomes a
            // full native render instead of a sub-region blit.
            resolved.fx, resolved.fy
        )
        // Diagnostic (mirrors the phone's MapCanvasScreen follow log): fix
        // vs predicted vs displayed vs offset, so an AA logcat shows
        // exactly where the overshoot comes from. The line's format (locale-independent
        // numbers, field order) is a tested contract — see followDiagnosticLine.
        if (followLogCount++ % 30 == 0) {
            android.util.Log.d(TAG, followDiagnosticLine(nowMs, offset))
        }
        if (offset.clamped) {
            // Display hit the overrun margin → full render at the display
            // position (throttled so a fast vehicle cannot queue renders).
            if (shouldRequestFullRender(nowMs, lastRenderRequestMs)) {
                lastRenderRequestMs = nowMs
                blitEligible = false
                val (aLat, aLon) = anchorCenterFor(displayLat, displayLon)
                viewportLat = aLat
                viewportLon = aLon
                emitViewportState()
                requestRender()
            }
            return
        }
        blitToSurface(surf, current, offset.clampedX, offset.clampedY, w, h, resolved.fx, resolved.fy)
    }
}

/**
 * P3: ease [displayMag] toward the pending magnification at the display rate. Outside
 * the blit-able window the frame has no pixels to scale (a zoom-out past the rendered
 * area), so the displayed magnification snaps to the frame's own and the commit lands
 * the target with a full render instead.
 */
internal fun AutoMapRenderer.advanceDisplayedMagnification(dtSec: Double) {
    val frame = overrunMag
    if (frame <= 0.0) return
    if (displayMag <= 0.0) {
        displayMag = frame
        return
    }
    val target = viewportZoomFraction
    if (abs(target - frame) > ZOOM_BLIT_LIMIT || abs(displayMag - frame) > ZOOM_BLIT_LIMIT) {
        displayMag = frame
        return
    }
    val step = target - displayMag
    if (abs(step) < 1e-4) {
        displayMag = target
        return
    }
    displayMag += step * FollowPrediction.easeAlpha(dtSec, ZOOM_EASE_TAU_SEC)
}

/**
 * Nearest int of a draw offset. `Double.roundToInt()` THROWS on NaN (unlike Java's
 * `Math.round`), and a NaN offset means "no displayed position yet" — no shift.
 */
internal fun AutoMapRenderer.roundOffset(v: Double): Int = if (v.isNaN()) 0 else v.roundToInt()

/**
 * Scale applied when drawing the displayed frame for P3: the difference between the
 * displayed magnification and the frame's own, clamped to the blit-able window (the
 * buffer must keep covering the surface). 1.0 when nothing is pending or in browse mode.
 */
internal fun AutoMapRenderer.displayedScale(): Float {
    if (!followMode || displayMag <= 0.0 || overrunMag <= 0.0) return 1f
    val delta = (displayMag - overrunMag).coerceIn(-ZOOM_BLIT_LIMIT, ZOOM_BLIT_LIMIT)
    if (abs(delta) < 1e-4) return 1f
    return 2.0.pow(delta).toFloat()
}

/** Current displayed magnification (P3) — exposed for tests. */
internal fun AutoMapRenderer.displayedMagnification(): Double = displayMag
