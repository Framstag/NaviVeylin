package com.naviveylin.auto

import com.naviveylin.core.AutoPositionUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [FreeDrivingScreen.headingAngleRadians] — the heading-up rotation
 * decision (spec: auto/free-driving — "Heading-up orientation"). The screen
 * itself needs a live CarContext + Hilt entry point (same constraint as
 * [MapScreenTest]); the rotation decision is a pure companion function so it
 * can be pinned here.
 */
@RunWith(RobolectricTestRunner::class)
class FreeDrivingScreenTest {

    @Test
    fun bearingRotatesHeadingUp() {
        // 90° east → -π/2 radians: the projections rotate by R(-angle), so a
        // negative angle makes the driving direction point up (same
        // convention as the phone app).
        val angle = FreeDrivingScreen.headingAngleRadians(90.0)
        assertTrue(angle != null)
        assertEquals(-Math.PI / 2, angle!!, 1e-9)
    }

    @Test
    fun zeroBearingKeepsNorthUp() {
        assertEquals(0.0, FreeDrivingScreen.headingAngleRadians(0.0)!!, 1e-9)
    }

    @Test
    fun unknownBearingReturnsNull() {
        // NaN/negative bearing: keep the previous heading (spec: "Heading-up
        // independent of settings" — never snaps to north on a bad fix).
        assertNull(FreeDrivingScreen.headingAngleRadians(Double.NaN))
        assertNull(FreeDrivingScreen.headingAngleRadians(-1.0))
    }

    // ── autoZoomTarget (spec: auto/map-pan — auto-zoom suspended while
    // panned; design D2 — same gate as NavigationScreen) ──

    @Test
    fun panningSuppressesAutoZoomFeed() {
        // A suspended controller re-engages on a speed-band crossing
        // (highway speed fed from a city band) — the panning gate must
        // return null regardless, mirroring NavigationScreen.
        val controller = AutoZoomController()
        controller.onSpeed(45.0) // city band
        controller.suspend()
        assertNull(
            FreeDrivingScreen.autoZoomTarget(
                panning = true, autoZoomEnabled = true, speedKmH = 100.0, controller = controller
            )
        )
    }
}
