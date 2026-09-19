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
    /** Resolves the current renderer (may be null before readiness — no-op then). */
    private val rendererProvider: () -> AutoMapRenderer?,
    private val autoZoomController: AutoZoomController,
    private val surfaceSize: () -> Pair<Int, Int>
) : PanModeListener {

    /** True while pan mode is active; screens gate GPS viewport commits on it. */
    var panning: Boolean = false
        private set

    override fun onPanModeChanged(panMode: Boolean) {
        val mapRenderer = rendererProvider() ?: return
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
        val mapRenderer = rendererProvider() ?: return
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

        val mapRenderer = rendererProvider() ?: return
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
 * Heading commit deadband (radians, design D2; spec: auto-smooth-follow —
 * Heading commit deadband): the follow-mode rotation is re-committed only when
 * the smoothed heading moved more than this from the COMMITTED rotation.
 * Measured against the committed value, so the heading-up lag can never
 * accumulate beyond the deadband. ~1.5°: the measured heading moves ~0.9°/s
 * mean (max ~2.7°/fix), so this removes most rotation commits — and with them
 * the forced full native render per fix (a rotation cannot be blitted) —
 * while a real turn still re-commits promptly and the map keeps following the
 * direction of travel. Tuning constant, verified on device (task 7.2).
 */
val HEADING_DEADBAND_RAD: Double = Math.toRadians(1.5)

/**
 * The rotation a fix should commit, applying the heading deadband: returns
 * [heading] when it differs from the committed rotation by more than
 * [HEADING_DEADBAND_RAD] (or when there is no committed rotation yet — first
 * rotation commits as today), null when the change falls below the deadband,
 * so the caller keeps the committed rotation and the fix commits a
 * centre/zoom-only change that the overrun blit can serve. Never while
 * panned (spec: auto/map-pan — follow suspended while panned). Shared by
 * [NavigationScreen] and [FreeDrivingScreen] so the two screens can never
 * drift apart (design D2).
 */
fun resolveCommittedAngle(panning: Boolean, heading: Double?, committedAngle: Double?): Double? {
    if (panning || heading == null) return null
    if (committedAngle == null) return heading
    return if (Math.abs(angleDeltaRadians(heading, committedAngle)) > HEADING_DEADBAND_RAD) {
        heading
    } else {
        null
    }
}

/**
 * Normalized signed difference `a - b` into (-π, π], so heading deltas cross the
 * ±180° wrap correctly (bearings are 0..360°, committed rotations are negative
 * radians of the same convention).
 */
internal fun angleDeltaRadians(a: Double, b: Double): Double {
    var d = a - b
    while (d > Math.PI) d -= 2.0 * Math.PI
    while (d < -Math.PI) d += 2.0 * Math.PI
    return d
}

/**
 * Whether a GPS fix should commit a viewport change: never while panned
 * (spec: auto/map-pan — follow suspended while panned), otherwise when the
 * heading moved beyond the deadband (see [resolveCommittedAngle] — [angle]
 * here is already the resolved rotation) or the zoom changed. Shared by
 * [NavigationScreen] and [FreeDrivingScreen] so the two screens can never
 * drift apart (design D1).
 *
 * The panning gate is the critical one: while panned, a speed-band crossing
 * can make the auto-zoom controller return a zoom (it re-engages on band
 * change by design — manual-zoom semantics), and without the gate that zoom
 * would reach the commit block and re-engage follow mid-pan.
 */
fun shouldCommitViewport(panning: Boolean, angle: Double?, committedAngle: Double?, newZoom: Double?): Boolean =
    !panning && (newZoom != null || resolveCommittedAngle(panning, angle, committedAngle) != null)
