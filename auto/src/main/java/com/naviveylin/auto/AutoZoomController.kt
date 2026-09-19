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
 * - an unknown or invalid speed (negative, NaN, or a > 150 km/h spike) resolves to
 *   [DEFAULT_SPEED_KMH] = 20 km/h, the same seed the phone's auto-zoom filter carries
 *   (`MapCanvasViewModel.lastValidSpeedKmH`), so the first position estimate already yields a
 *   "reasonable initial zoom" instead of no target at all (spec: auto-speed-zoom — Speed unknown);
 * - manual zoom suspends auto-zoom; a speed-band change re-engages it.
 */
class AutoZoomController(
    private val minMag: Double = AutoMapRenderer.MIN_ZOOM.toDouble(),
    private val maxMag: Double = AutoMapRenderer.MAX_ZOOM.toDouble()
) {

    /** Last good speed (km/h): SEEDED with [DEFAULT_SPEED_KMH], so an invalid speed before the
     *  first valid one resolves to the spec's default rather than to "no target". */
    private var lastValidSpeedKmH = DEFAULT_SPEED_KMH
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
     * null when nothing should change (suspended, or the target differs from
     * the committed magnification by less than the epsilon — constant speed
     * never re-commits). An INVALID speed (negative / NaN / spike) is not a
     * no-op: it resolves to [DEFAULT_SPEED_KMH] until a valid speed arrives
     * (spec: auto-speed-zoom — Speed unknown).
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

    /**
     * Reject speed spikes (> 150 km/h) and NaN; keep the last good speed
     * (which starts at the seeded [DEFAULT_SPEED_KMH], so `filterSpeed` never
     * yields an unusable value).
     */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0.0 && rawSpeedKmH <= MAX_SPEED_KMH) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    companion object {
        const val MAX_SPEED_KMH = 150.0

        /**
         * Speed (km/h) used until a VALID speed has been reported (spec:
         * auto-speed-zoom — Speed unknown: "a default speed of 20 km/h to compute a reasonable
         * initial zoom"). Mirrors the phone's seed (`MapCanvasViewModel.lastValidSpeedKmH`).
         */
        const val DEFAULT_SPEED_KMH = 20.0
    }
}
