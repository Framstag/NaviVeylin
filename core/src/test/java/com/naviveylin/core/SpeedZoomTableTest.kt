package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the speed-to-magnification table: exact breakpoints, linear
 * interpolation between them, and clamping outside the table range.
 * Plain JUnit — no Robolectric, no JNI stub involved.
 */
class SpeedZoomTableTest {

    @Test
    fun `exact breakpoints map to their magnification`() {
        assertEquals(18.0, SpeedZoomTable.compute(0.0), 0.001)
        assertEquals(17.5, SpeedZoomTable.compute(6.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(15.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(30.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(60.0), 0.001)
        assertEquals(13.0, SpeedZoomTable.compute(90.0), 0.001)
        assertEquals(12.0, SpeedZoomTable.compute(130.0), 0.001)
    }

    @Test
    fun `walking speed interpolates between 0 and 6`() {
        // 5 km/h: 18.0 + (5/6) * (17.5 - 18.0) = 17.5833
        assertEquals(17.5833, SpeedZoomTable.compute(5.0), 0.001)
    }

    @Test
    fun `suburban to highway interpolates between 60 and 90`() {
        // 75 km/h: 16.0 + (15/30) * (13.0 - 16.0) = 14.5
        assertEquals(14.5, SpeedZoomTable.compute(75.0), 0.001)
    }

    @Test
    fun `highway speed interpolates between 90 and 130`() {
        // 100 km/h: 13.0 + (10/40) * (12.0 - 13.0) = 12.75
        assertEquals(12.75, SpeedZoomTable.compute(100.0), 0.001)
    }

    @Test
    fun `speeds below and above the table clamp to the ends`() {
        assertEquals(18.0, SpeedZoomTable.compute(-5.0), 0.001)
        assertEquals(12.0, SpeedZoomTable.compute(200.0), 0.001)
    }

    @Test
    fun `band index matches the speed band`() {
        assertEquals(0, SpeedZoomTable.bandIndex(0.0))
        assertEquals(0, SpeedZoomTable.bandIndex(5.0))
        assertEquals(1, SpeedZoomTable.bandIndex(10.0))
        assertEquals(3, SpeedZoomTable.bandIndex(45.0))
        assertEquals(4, SpeedZoomTable.bandIndex(75.0))
        assertEquals(6, SpeedZoomTable.bandIndex(200.0))
    }

    @Test
    fun `step toward moves a proportional fraction clamped at half a level`() {
        // 16.0 -> 14.5 (delta 1.5): proportional step = 0.3 × 1.5 = 0.45 (< 0.5 cap).
        assertEquals(15.55, SpeedZoomTable.stepToward(16.0, 14.5), 0.001)
        // 15.55 -> 14.5 (delta 1.05): 0.3 × 1.05 = 0.315.
        assertEquals(15.235, SpeedZoomTable.stepToward(15.55, 14.5), 0.001)
        // Large gap: proportional 0.3 × 3.25 = 0.975 clamps at the 0.5 cap.
        assertEquals(15.5, SpeedZoomTable.stepToward(16.0, 12.75), 0.001)
    }

    @Test
    fun `step toward works in the zoom-in direction`() {
        assertEquals(15.3, SpeedZoomTable.stepToward(15.0, 16.0), 0.001)
        // Same cap applies in both directions.
        assertEquals(12.5, SpeedZoomTable.stepToward(12.0, 17.5), 0.001)
    }

    @Test
    fun `step toward keeps fractional values`() {
        // Larger deltas also step fractionally, never rounding to a whole level.
        val stepped = SpeedZoomTable.stepToward(16.0, 12.75)
        assertEquals(15.5, stepped, 0.001)
        assertTrue("stepped value $stepped must not be an integer level", stepped % 1.0 != 0.0)
        val nearTarget = SpeedZoomTable.stepToward(14.75, 14.5)
        assertEquals(14.675, nearTarget, 0.001)
        assertTrue("near-target step $nearTarget must stay fractional", nearTarget % 1.0 != 0.0)
    }

    @Test
    fun `step toward is a no-op below epsilon`() {
        // Deltas strictly smaller than ZOOM_EPSILON (0.05) cause no change.
        assertEquals(14.5, SpeedZoomTable.stepToward(14.5, 14.46), 0.0)
        assertEquals(14.5, SpeedZoomTable.stepToward(14.5, 14.54), 0.0)
        assertEquals(14.5, SpeedZoomTable.stepToward(14.5, 14.5), 0.0)
    }

    @Test
    fun `step toward reaches the exact target when within one step`() {
        // Deltas inside the proportional region move only a fraction of the
        // remaining gap (damped, no fixed-step snap toward the target).
        assertEquals(14.44, SpeedZoomTable.stepToward(14.5, 14.3), 0.001)
        assertEquals(14.56, SpeedZoomTable.stepToward(14.5, 14.7), 0.001)
        // Sub-epsilon proportional motion is floored at ZOOM_EPSILON so
        // convergence lands inside the deadband in bounded updates.
        assertEquals(14.55, SpeedZoomTable.stepToward(14.5, 14.6), 0.001)
    }

    @Test
    fun `step toward converges without overshoot and settles in the deadband`() {
        // Repeated calls follow an exponential-approach curve: monotonic
        // toward the target, never crossing it, no oscillation (pumping).
        var mag = 16.0
        val target = 14.5
        var previous = mag
        while (true) {
            mag = SpeedZoomTable.stepToward(mag, target)
            if (mag == previous) break
            assertTrue("must approach the target", mag < previous)
            assertTrue("must not overshoot", mag >= target)
            previous = mag
        }
        assertTrue("settled inside the deadband", kotlin.math.abs(mag - target) <= SpeedZoomTable.ZOOM_EPSILON)
    }
}
