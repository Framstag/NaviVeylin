package com.naviveylin.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the routing status row's progress ring geometry
 * (spec: navigation-status-details — "Route progress rings on the routing status row": the ring
 * is filled to the completed fraction of its own dimension, and "Progress values clamped to
 * valid range").
 *
 * The arc is a draw-time value with no semantics node, so the sweep it is drawn with is pulled
 * out of the composable as a pure function and asserted here; the drawn pixels are measured
 * separately in `NavigationStatusRingRasterTest`.
 */
class NavProgressRingTest {

    @Test
    fun ringSweepMatchesPercent() {
        assertEquals("nothing done, nothing drawn", 0f, ringSweepDegrees(0), 0.001f)
        assertEquals("42 % of the ring", 151.2f, ringSweepDegrees(42), 0.001f)
        assertEquals("half the ring", 180f, ringSweepDegrees(50), 0.001f)
        assertEquals("arrived: the ring is closed", 360f, ringSweepDegrees(100), 0.001f)
    }

    @Test
    fun ringSweepIsClamped() {
        assertEquals("above 100 % cannot draw past a full ring", 360f, ringSweepDegrees(150), 0.001f)
        assertEquals("below 0 % cannot draw backwards", 0f, ringSweepDegrees(-20), 0.001f)
    }
}
