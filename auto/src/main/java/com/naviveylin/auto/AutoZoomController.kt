package com.naviveylin.auto

import com.naviveylin.core.SpeedZoomTable

/**
 * Speed-driven auto-zoom for free driving (spec: auto/free-driving — "Current
 * driving speed shown" driving the magnification; spec: auto-speed-zoom
 * mapping; spec: auto-speed-zoom "Smooth zoom transitions" delta — fractional
 * commits + slow convergence). Pure and deterministic for unit tests.
 *
 * Mirrors the phone's auto-zoom semantics (MapCanvasViewModel) without the
 * route-specific parts (turn boost, curve boost, post-turn hold):
 * - linear-interpolated speed → magnification table ([SpeedZoomTable]);
 * - fractional commits: the interpolated target (e.g. 14.5 at 75 km/h) is
 *   committed without integer rounding;
 * - slow convergence: after the first commit the magnification moves a
 *   distance-proportional fraction of the remaining gap per speed update
 *   (ZOOM_CONVERGENCE_GAIN × |current−target|, capped at
 *   [SpeedZoomTable.MAX_ZOOM_STEP_PER_UPDATE] 0.5 levels) via
 *   [SpeedZoomTable.stepToward] — fast when far from the target, gentle near
 *   it, so speed-noise target jitter is damped instead of chased (no zoom
 *   "pumping"); constant speed → epsilon no-op;
 * - first commit jumps directly to the target (spec's "Speed unknown"
 *   scenario: no easing from the default map zoom);
 * - manual zoom suspends auto-zoom; a speed-band change re-engages it.
 */
class AutoZoomController(
    private val minMag: Double = AutoMapRenderer.MIN_ZOOM.toDouble(),
    private val maxMag: Double = AutoMapRenderer.MAX_ZOOM.toDouble()
) {

    private var lastValidSpeedKmH = Double.NaN
    private var suspended = false
    private var lastBand = -1
    // NaN = never committed; first commit jumps straight to the target.
    private var committedTarget = Double.NaN

    /** Manual zoom: suspend auto-zoom until the speed crosses a band boundary. */
    fun suspend() {
        suspended = true
    }

    /** True after [suspend] until a speed-band change re-engages auto-zoom. */
    fun isSuspended(): Boolean = suspended

    /**
     * Feed a speed fix. Returns the fractional magnification to commit, or
     * null when nothing should change (speed invalid, suspended, or the
     * target differs from the committed magnification by less than the
     * epsilon — constant speed never re-commits).
     */
    fun onSpeed(rawSpeedKmH: Double): Double? {
        val speed = filterSpeed(rawSpeedKmH)
        if (speed.isNaN()) return null

        val target = SpeedZoomTable.compute(speed).coerceIn(minMag, maxMag)
        val band = SpeedZoomTable.bandIndex(speed)

        if (suspended) {
            // Speed crossed a band boundary → re-engage after a manual zoom.
            if (band != lastBand) {
                suspended = false
                lastBand = band
            } else {
                return null
            }
        } else {
            lastBand = band
        }

        // Design D1 (spec: auto-speed-zoom — Smooth zoom transitions): the
        // first commit jumps directly to the target; later commits converge
        // at most 0.5 levels per update, fractionally. The epsilon no-op
        // (stepped == committed) returns null so constant speeds never
        // re-commit/re-render.
        val stepped = if (committedTarget.isNaN()) {
            target
        } else {
            SpeedZoomTable.stepToward(committedTarget, target)
        }
        if (stepped == committedTarget) return null
        committedTarget = stepped
        return stepped
    }

    /** Reject speed spikes (> 150 km/h) and NaN; keep the last good speed. */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0.0 && rawSpeedKmH <= MAX_SPEED_KMH) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    companion object {
        const val MAX_SPEED_KMH = 150.0
    }
}
