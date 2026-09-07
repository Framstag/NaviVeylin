package com.naviveylin.auto

import androidx.car.app.navigation.model.PanModeListener
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.ProjectionUtils
import kotlin.math.abs

/**
 * Shared pan-mode handling for the `NavigationTemplate` map surfaces (free
 * driving + turn-by-turn navigation, spec: auto/map-pan).
 *
 * The host only forwards pan gestures (`onScroll`/`onScale`) to the surface
 * while pan mode is active; the PAN action in the map action strip toggles
 * it. On pan-mode entry the map must stop following the vehicle: follow mode
 * is disengaged (`setViewport` flips `followMode` off), speed-driven
 * auto-zoom is suspended, and the screens freeze heading-up rotation by
 * gating their GPS viewport commits on [panning]. On exit follow re-engages
 * smoothly (`reengageFollow` — the extrapolation loop eases the display back
 * to the fix, design D4).
 */
class MapPanHandler(
    private val mapRenderer: AutoMapRenderer,
    private val autoZoomController: AutoZoomController,
    private val surfaceSize: () -> Pair<Int, Int>
) : PanModeListener {

    /** True while pan mode is active; screens gate GPS viewport commits on it. */
    var panning: Boolean = false
        private set

    override fun onPanModeChanged(panMode: Boolean) {
        panning = panMode
        if (panMode) {
            // Disengage follow so the map does not snap back to GPS, and
            // suspend speed-driven auto-zoom (same pattern as the zoom
            // buttons). setViewport with the current viewport flips
            // followMode off without moving the map.
            autoZoomController.suspend()
            val vp = mapRenderer.viewportState.value
            mapRenderer.setViewport(vp.lat, vp.lon, vp.zoom, vp.angle, vp.zoom.toDouble())
        } else {
            // Re-engage follow WITHOUT snapping: the extrapolation loop eases
            // the display back to the fix (design D4).
            mapRenderer.reengageFollow()
        }
    }

    /** Convert a pan gesture to a viewport move (host forwards only in pan mode). */
    fun onScroll(distanceX: Float, distanceY: Float) {
        if (!panning) return
        val vp = mapRenderer.viewportState.value
        val (w, h) = surfaceSize()
        val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
            distanceX.toDouble(), distanceY.toDouble(),
            vp.angle,
            vp.zoom.toDouble(),
            w.toDouble(), h.toDouble(),
            vp.lat, vp.lon,
            mapRenderer.projectionDpi
        )
        // Mirror to the file-backed diagnostics log (throttled) so pan
        // behavior is visible even when logcat capture misses the app.
        val now = System.currentTimeMillis()
        if (now - lastGestureLogMs > GESTURE_LOG_INTERVAL_MS) {
            lastGestureLogMs = now
            DiagnosticsLog.log(
                "PAN",
                "onScroll dx=$distanceX dy=$distanceY center=${vp.lat},${vp.lon} mag=${vp.zoom} -> $newLat,$newLon"
            )
        }
        mapRenderer.setViewport(newLat, newLon, vp.zoom, vp.angle)
    }

    /** Pinch zoom around the gesture focus (host forwards only in pan mode). */
    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (!panning) return
        // Ignore jitter so tiny finger spread during a pan never triggers a zoom step.
        if (abs(scaleFactor - 1f) < SCALE_JITTER_THRESHOLD) return

        val vp = mapRenderer.viewportState.value
        val (newFraction, newZoom) = mapRenderer.zoomStep(scaleFactor)
        if (newZoom == vp.zoom && newFraction == mapRenderer.fractionalZoom()) return

        val (w, h) = surfaceSize()
        // Host may report an unavailable focal point (negative coords, e.g.
        // rotary-knob zoom) — zoom around the screen center in that case.
        val fx = if (focusX >= 0f) focusX.toDouble() else w / 2.0
        val fy = if (focusY >= 0f) focusY.toDouble() else h / 2.0
        val (newLat, newLon) = ProjectionUtils.zoomAtCursor(
            fx, fy,
            vp.zoom.toDouble(), newFraction,
            w.toDouble(), h.toDouble(),
            vp.lat, vp.lon,
            mapRenderer.projectionDpi
        )
        mapRenderer.setViewport(newLat, newLon, newZoom, vp.angle, newFraction)
    }

    private var lastGestureLogMs = 0L

    companion object {
        /** Ignore pinch deltas below this (tiny finger spread during a pan). */
        private const val SCALE_JITTER_THRESHOLD = 0.02f

        /** Throttle for file-backed gesture diagnostics. */
        private const val GESTURE_LOG_INTERVAL_MS = 500L
    }
}

/**
 * Whether a GPS fix should commit a viewport change: never while panned
 * (spec: auto/map-pan — follow suspended while panned), otherwise when the
 * heading or the zoom changed. Shared by [NavigationScreen] and
 * [FreeDrivingScreen] so the two screens can never drift apart (design D1).
 *
 * The panning gate is the critical one: while panned, a speed-band crossing
 * can make the auto-zoom controller return a zoom (it re-engages on band
 * change by design — manual-zoom semantics), and without the gate that zoom
 * would reach the commit block and re-engage follow mid-pan.
 */
fun shouldCommitViewport(panning: Boolean, angle: Double?, newZoom: Int?): Boolean =
    !panning && (angle != null || newZoom != null)
