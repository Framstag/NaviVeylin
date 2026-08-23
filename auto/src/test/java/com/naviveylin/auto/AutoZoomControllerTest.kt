package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AutoZoomController] (spec: auto/free-driving auto-zoom; spec:
 * auto-speed-zoom mapping): speed → magnification with cooldown, stability
 * sampling, hysteresis, manual-zoom suspension and band-change re-engage.
 */
@RunWith(RobolectricTestRunner::class)
class AutoZoomControllerTest {

    // Large start so the first-commit cooldown is already elapsed.
    private var clock = 100_000L
    private fun controller(): AutoZoomController = AutoZoomController(now = { clock })

    @Test
    fun fastSpeedCommitsZoomedOut() {
        val c = controller()
        // 130 km/h → table mag 12. Three stable samples are required, so the
        // commit lands on the 4th call.
        repeat(3) { assertNull(c.onSpeed(130.0)) }
        assertEquals(12, c.onSpeed(130.0))
    }

    @Test
    fun slowSpeedCommitsZoomedIn() {
        val c = controller()
        repeat(3) { assertNull(c.onSpeed(4.0)) }
        assertEquals(18, c.onSpeed(4.0))
    }

    @Test
    fun cooldownGatesRepeatedCommits() {
        val c = controller()
        repeat(3) { c.onSpeed(130.0) }
        assertEquals(12, c.onSpeed(130.0))

        // Same speed after the cooldown: target unchanged → hysteresis blocks
        // a new commit.
        clock += AutoZoomController.ZOOM_COOLDOWN_MS + 1
        repeat(4) { assertNull(c.onSpeed(130.0)) }
        assertNull(c.onSpeed(130.0))
    }

    @Test
    fun speedChangeCommitsAfterCooldown() {
        val c = controller()
        repeat(3) { c.onSpeed(130.0) }
        assertEquals(12, c.onSpeed(130.0))

        clock += AutoZoomController.ZOOM_COOLDOWN_MS + 1
        repeat(3) { assertNull(c.onSpeed(30.0)) }
        assertEquals(16, c.onSpeed(30.0))
    }

    @Test
    fun invalidSpeedReturnsNull() {
        val c = controller()
        assertNull(c.onSpeed(Double.NaN))
        // Speed spike > 150 km/h is rejected; last good speed is kept.
        assertNull(c.onSpeed(500.0))
        // A valid speed still works afterwards.
        repeat(3) { assertNull(c.onSpeed(30.0)) }
        assertEquals(16, c.onSpeed(30.0))
    }

    @Test
    fun manualZoomSuspendsUntilBandChange() {
        val c = controller()
        // Establish the city band first.
        repeat(3) { c.onSpeed(40.0) }
        assertEquals(16, c.onSpeed(40.0))

        c.suspend()
        assertTrue(c.isSuspended())
        // Same band (45 km/h city) → stays suspended.
        repeat(4) { assertNull(c.onSpeed(45.0)) }
        assertTrue(c.isSuspended())
        // Highway band (100 km/h) crosses a boundary → re-engage.
        clock += AutoZoomController.ZOOM_COOLDOWN_MS + 1
        repeat(3) { assertNull(c.onSpeed(100.0)) }
        assertTrue(!c.isSuspended())
        assertEquals(13, c.onSpeed(100.0))
    }

    // ── movementBearing (GPX replay without a GPS bearing) ──

    @Test
    fun movementBearingNorthIsZero() {
        val b = FreeDrivingScreen.movementBearing(51.0, 7.0, 51.1, 7.0)!!
        assertEquals(0.0, b, 0.5)
    }

    @Test
    fun movementBearingEastIsNinety() {
        val b = FreeDrivingScreen.movementBearing(51.0, 7.0, 51.0, 7.1)!!
        assertEquals(90.0, b, 0.5)
    }

    @Test
    fun movementBearingSouthIsHundredEighty() {
        val b = FreeDrivingScreen.movementBearing(51.1, 7.0, 51.0, 7.0)!!
        assertEquals(180.0, b, 0.5)
    }

    @Test
    fun tinyMovementReturnsNull() {
        // ~1.1 m north — below the 3 m trust threshold.
        assertNull(FreeDrivingScreen.movementBearing(51.0, 7.0, 51.00001, 7.0))
    }
}
