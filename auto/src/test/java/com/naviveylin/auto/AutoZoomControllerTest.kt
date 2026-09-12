package com.naviveylin.auto

import com.naviveylin.core.AutoPositionUtil
import com.naviveylin.core.SpeedZoomTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AutoZoomController] (spec: auto/free-driving auto-zoom; spec:
 * auto-speed-zoom mapping + "Smooth zoom transitions" delta): fractional
 * commits without integer rounding, ≤ 0.5 levels per update convergence,
 * epsilon no-op at constant speed, first-commit direct jump, manual-zoom
 * suspension and band-change re-engage.
 */
@RunWith(RobolectricTestRunner::class)
class AutoZoomControllerTest {

    @Test
    fun firstSpeedCommitJumpsDirectlyToTarget() {
        val c = AutoZoomController()
        // 130 km/h → 12.0; the very first fix commits straight to the target
        // (spec's "Speed unknown" scenario — no easing from the default zoom).
        assertEquals(12.0, c.onSpeed(130.0)!!, 0.001)
        // Constant speed afterwards: epsilon no-op, nothing to commit.
        assertNull(c.onSpeed(130.0))
    }

    @Test
    fun fractionalConvergenceIsDistanceProportional() {
        val c = AutoZoomController()
        assertEquals(16.0, c.onSpeed(40.0)!!, 0.001)
        // 75 km/h → interpolated target 14.5. The step is proportional to the
        // remaining gap (gain 0.3, capped at 0.5 levels per update): 0.45 on
        // the first update, then shrinking — fast when far, gentle near the
        // target so speed-noise jitter around the target never gets chased
        // back and forth (no zoom "pumping").
        assertEquals(15.55, c.onSpeed(75.0)!!, 0.001)
        assertEquals(15.235, c.onSpeed(75.0)!!, 0.001)
        assertEquals(15.0145, c.onSpeed(75.0)!!, 0.001)
        // Converges monotonically toward 14.5 and settles inside the epsilon
        // deadband (never rounded, never churned).
        var mag = 15.0145
        while (true) {
            val next = c.onSpeed(75.0)
                ?: break // null → settled within the deadband
            assertTrue("must approach the target", next < mag)
            assertTrue("must not overshoot", next >= 14.5)
            mag = next
        }
        assertTrue("settled inside the deadband",
            kotlin.math.abs(mag - 14.5) <= SpeedZoomTable.ZOOM_EPSILON)
        // Constant speed afterwards: epsilon no-op, nothing to commit.
        assertNull(c.onSpeed(75.0))
    }

    @Test
    fun zoomInDirectionAlsoConvergesFractionally() {
        val c = AutoZoomController()
        assertEquals(12.0, c.onSpeed(130.0)!!, 0.001)
        // Slow down: 6 km/h → 17.5; step up half a level per update.
        assertEquals(12.5, c.onSpeed(6.0)!!, 0.001)
        assertEquals(13.0, c.onSpeed(6.0)!!, 0.001)
    }

    @Test
    fun invalidSpeedReturnsNullAndKeepsLastGoodSpeed() {
        val c = AutoZoomController()
        assertNull(c.onSpeed(Double.NaN))
        // Speed spike > 150 km/h is rejected; last good speed is kept.
        assertNull(c.onSpeed(500.0))
        // A valid speed still commits directly afterwards.
        assertEquals(16.0, c.onSpeed(30.0)!!, 0.001)
    }

    @Test
    fun manualZoomSuspendsUntilBandChange() {
        val c = AutoZoomController()
        assertEquals(16.0, c.onSpeed(30.0)!!, 0.001)

        c.suspend()
        assertTrue(c.isSuspended())
        // Same band (28 km/h city) → stays suspended.
        assertNull(c.onSpeed(28.0))
        assertTrue(c.isSuspended())
        // Highway band (100 km/h) crosses a boundary → re-engage; the zoom
        // converges fractionally from the committed 16.0 toward 12.75.
        assertEquals(15.5, c.onSpeed(100.0)!!, 0.001)
        assertTrue(!c.isSuspended())
        assertEquals(15.0, c.onSpeed(100.0)!!, 0.001)
    }

    // ── movementBearing (GPX replay without a GPS bearing) ──

    @Test
    fun movementBearingNorthIsZero() {
        val b = AutoPositionUtil.movementBearing(51.0, 7.0, 51.1, 7.0)!!
        assertEquals(0.0, b, 0.5)
    }

    @Test
    fun movementBearingEastIsNinety() {
        val b = AutoPositionUtil.movementBearing(51.0, 7.0, 51.0, 7.1)!!
        assertEquals(90.0, b, 0.5)
    }

    @Test
    fun movementBearingSouthIsHundredEighty() {
        val b = AutoPositionUtil.movementBearing(51.1, 7.0, 51.0, 7.0)!!
        assertEquals(180.0, b, 0.5)
    }

    @Test
    fun tinyMovementReturnsNull() {
        // ~1.1 m north — below the 3 m trust threshold.
        assertNull(AutoPositionUtil.movementBearing(51.0, 7.0, 51.00001, 7.0))
    }
}
