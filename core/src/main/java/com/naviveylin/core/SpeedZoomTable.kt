package com.naviveylin.core

/**
 * Speed-to-magnification lookup table for auto-zoom during navigation and
 * free driving (spec: auto-speed-zoom).
 *
 * Maps the reported speed to a target map magnification with linear
 * interpolation between breakpoints. Walking speeds (≤6 km/h) target
 * 18–17.5; speeds up to 60 km/h target at least 16 so building names and
 * numbers are rendered (the stylesheet draws building labels at
 * magnification ≥ 16); highway speeds zoom out to 13–12.
 *
 * Shared between the phone app (`MapCanvasViewModel`) and the Android Auto
 * module (free-driving auto-zoom).
 */
object SpeedZoomTable {

    /**
     * Zoom-change deadband: when the target magnification differs from the
     * current one by less than this, the auto-zoom makes no change and no
     * re-render is triggered (spec: auto-speed-zoom — Smooth zoom
     * transitions, epsilon no-op scenario).
     */
    const val ZOOM_EPSILON = 0.05

    /**
     * Maximum magnification change per position update (spec:
     * auto-speed-zoom — Smooth zoom transitions): the zoom converges toward
     * the speed-derived target at most this many levels per update, so a
     * multi-level transition takes multiple seconds instead of snapping.
     */
    const val MAX_ZOOM_STEP_PER_UPDATE = 0.5

    private data class SpeedZoomLevel(val speedKmH: Double, val magnification: Double)

    private val TABLE = listOf(
        SpeedZoomLevel(0.0, 18.0),   // stationary
        SpeedZoomLevel(6.0, 17.5),   // slow jog
        SpeedZoomLevel(15.0, 16.0),  // cycling / slow city
        SpeedZoomLevel(30.0, 16.0),  // city driving
        SpeedZoomLevel(60.0, 16.0),  // suburban
        SpeedZoomLevel(90.0, 13.0),  // highway
        SpeedZoomLevel(130.0, 12.0), // very fast
    )

    /** Compute target magnification from speed using linear interpolation. */
    fun compute(speedKmH: Double): Double {
        if (TABLE.isEmpty()) return 15.0

        // Clamp below first entry
        if (speedKmH <= TABLE.first().speedKmH) return TABLE.first().magnification
        // Clamp above last entry
        if (speedKmH >= TABLE.last().speedKmH) return TABLE.last().magnification

        // Linear interpolation between breakpoints
        for (i in 0 until TABLE.size - 1) {
            val low = TABLE[i]
            val high = TABLE[i + 1]
            if (speedKmH in low.speedKmH..high.speedKmH) {
                val fraction = (speedKmH - low.speedKmH) / (high.speedKmH - low.speedKmH)
                return low.magnification + fraction * (high.magnification - low.magnification)
            }
        }

        return TABLE.last().magnification
    }

    /**
     * Rate-limited fractional convergence from [current] toward [target].
     * Returns [current] unchanged when the difference is below [ZOOM_EPSILON]
     * (no-op, so callers skip commits and renders); otherwise moves at most
     * [maxStep] magnification levels per update, keeping the fractional value
     * (no integer rounding). Shared by the phone (MapCanvasViewModel) and the
     * Android Auto controller so both converge identically.
     */
    fun stepToward(current: Double, target: Double, maxStep: Double = MAX_ZOOM_STEP_PER_UPDATE): Double {
        val delta = target - current
        if (kotlin.math.abs(delta) < ZOOM_EPSILON) return current
        if (delta > maxStep) return current + maxStep
        if (delta < -maxStep) return current - maxStep
        return target
    }

    /** Find the table index for the current speed band. */
    fun bandIndex(speedKmH: Double): Int {
        if (TABLE.isEmpty()) return 0
        if (speedKmH <= TABLE.first().speedKmH) return 0
        if (speedKmH >= TABLE.last().speedKmH) return TABLE.size - 1
        for (i in 0 until TABLE.size - 1) {
            if (speedKmH in TABLE[i].speedKmH..TABLE[i + 1].speedKmH) {
                return i
            }
        }
        return TABLE.size - 1
    }
}
