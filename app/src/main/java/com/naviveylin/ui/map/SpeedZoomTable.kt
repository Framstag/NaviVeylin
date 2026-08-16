package com.naviveylin.ui.map

/**
 * Speed-to-magnification lookup table for auto-zoom during navigation.
 *
 * Maps the navigation engine's reported speed to a target map magnification
 * with linear interpolation between breakpoints. Walking speeds (≤6 km/h)
 * target 18–17.5; speeds up to 60 km/h target at least 16 so building names
 * and numbers are rendered (the stylesheet draws building labels at
 * magnification ≥ 16); highway speeds zoom out to 13–12.
 */
internal object SpeedZoomTable {

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
