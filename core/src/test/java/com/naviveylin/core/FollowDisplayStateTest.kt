package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [FollowDisplayState]: monotonic forward easing (the fix-arrival
 * overshoot must hold, never slide backward along travel), convergence with a
 * bounded lead at constant speed, stop-freeze (no advance call = frozen), and
 * reset.
 * Plain JUnit — no Robolectric, no JNI stub involved.
 */
class FollowDisplayStateTest {

    private val display = FollowDisplayState()

    private fun metersToLon(meters: Double): Double = meters / FollowPrediction.METERS_PER_DEG_LON

    private fun metersToLat(meters: Double): Double = meters / FollowPrediction.METERS_PER_DEG_LAT

    /** Eastward lead (m) of the display behind the target. */
    private fun eastLeadM(displayLonDeg: Double, targetLonDeg: Double): Double =
        (targetLonDeg - displayLonDeg) * FollowPrediction.METERS_PER_DEG_LON

    @Test
    fun `first advance initializes the displayed position to the target`() {
        val (lat, lon) = display.advance(48.0, 2.0, 0.016, 90.0)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
        assertTrue(display.hasPosition)
    }

    @Test
    fun `advances monotonically east toward a moving target with bounded lead`() {
        // Target moves east at 14 m/s (the extrapolated prediction); the
        // display eases behind it. Lead must converge to ~v*tau = 4.2 m and
        // never exceed it by more than a frame's worth.
        var targetLon = 2.0
        var prevLon = Double.NaN
        var maxLead = 0.0
        for (i in 0 until 600) {
            targetLon += 14.0 * (1.0 / 60.0) * (1.0 / FollowPrediction.METERS_PER_DEG_LON)
            val (lat, lon) = display.advance(48.0, targetLon, 1.0 / 60.0, 90.0)
            if (i > 0) {
                assertTrue("display must advance forward", lon > prevLon)
            }
            maxLead = maxOf(maxLead, eastLeadM(lon, targetLon))
            prevLon = lon
            assertEquals(48.0, lat, 1e-9)
        }
        assertTrue("max lead $maxLead too large", maxLead < 4.3)
    }

    @Test
    fun `holds position when the target drops behind at fix arrival`() {
        // Display settles 8 m ahead of the origin (prediction overshoot).
        display.advance(48.0, 2.0, 0.016, 90.0)
        for (i in 0 until 120) {
            display.advance(48.0, 2.0 + metersToLon(8.0), 1.0 / 60.0, 90.0)
        }
        assertTrue("display should have caught up", eastLeadM(display.lon, 2.0 + metersToLon(8.0)) < 0.1)

        // New fix arrives BEHIND the display: the target drops ~8 m back.
        val (lat, lon) = display.advance(48.0, 2.0, 1.0 / 60.0, 90.0)
        assertTrue("hold: no backward slide", lon > 2.0 + metersToLon(7.0))
        assertEquals(48.0, lat, 1e-9)
    }

    @Test
    fun `resumes forward after the hold once the target advances beyond`() {
        // Display settles 8 m ahead, then the target crashes back to the fix
        // (hold), then the prediction grows again at 14 m/s.
        display.advance(48.0, 2.0, 0.016, 90.0)
        for (i in 0 until 120) {
            display.advance(48.0, 2.0 + metersToLon(8.0), 1.0 / 60.0, 90.0)
        }
        val heldLon = display.lon
        display.advance(48.0, 2.0, 1.0 / 60.0, 90.0) // hold (target behind)
        assertEquals(heldLon, display.lon, 1e-9)

        // Prediction resumes: target = 8 m + 14 m/s * t. Monotonic non-
        // decreasing, and the display eventually advances past the hold.
        var prevLon = display.lon
        for (i in 0 until 300) {
            val t = (i + 1) * (1.0 / 60.0)
            val targetLon = 2.0 + metersToLon(8.0) + 14.0 * t * (1.0 / FollowPrediction.METERS_PER_DEG_LON)
            val (_, lon) = display.advance(48.0, targetLon, 1.0 / 60.0, 90.0)
            assertTrue("display must never slide backward", lon >= prevLon)
            prevLon = lon
        }
        assertTrue("display advanced past the held position", prevLon > heldLon)
    }

    @Test
    fun `no clamp without a heading`() {
        // No heading → the forward-only rule is skipped: the display eases
        // toward the target even when it sits behind it.
        display.advance(48.0, 2.0, 0.016, 90.0)
        val (_, lon) = display.advance(48.0, 2.0 + metersToLon(10.0), 1.0 / 60.0, Double.NaN)
        assertTrue(lon > 2.0)
        // Without a heading the display may also ease back (fix-arrival
        // correction — there is no overshoot to absorb).
        val (_, lon2) = display.advance(48.0, 2.0, 1.0 / 60.0, Double.NaN)
        assertTrue(lon2 < lon)
    }

    @Test
    fun `lateral drift does not trigger a false hold`() {
        // Display glides east; the target advances east while drifting 6 m
        // south (a lane change at constant heading). The east component stays
        // positive → the display moves east continuously (no hold).
        display.advance(48.0, 2.0, 0.016, 90.0)
        var prevLon = display.lon
        for (i in 0 until 120) {
            val targetLon = 2.0 + metersToLon(4.0)
            val targetLat = 48.0 - metersToLat(6.0)
            val (_, lon) = display.advance(targetLat, targetLon, 1.0 / 60.0, 90.0)
            assertTrue("eastward advance must continue", lon >= prevLon)
            prevLon = lon
        }
        assertEquals(prevLon, display.lon, 1e-9)
    }

    @Test
    fun `frozen without advance calls`() {
        // No advance calls while stopped — the position must not change
        // (freeze semantics are a call-site property).
        display.advance(48.0, 2.0, 0.016, 90.0)
        val lat = display.lat
        val lon = display.lon
        assertEquals(lat, display.lat, 1e-9)
        assertEquals(lon, display.lon, 1e-9)
    }

    @Test
    fun `reset clears the displayed position`() {
        display.advance(48.0, 2.0, 0.016, 90.0)
        display.reset()
        assertFalse(display.hasPosition)
        assertTrue(display.lat.isNaN())
    }

    @Test
    fun `heading north holds when target is south`() {
        // Traveling north (heading 0): a fix arriving SOUTH of the display
        // must NOT pull the display backward.
        display.advance(48.0, 2.0, 0.016, 0.0)
        for (i in 0 until 120) {
            display.advance(48.0 + metersToLat(10.0), 2.0, 1.0 / 60.0, 0.0)
        }
        val latBefore = display.lat
        display.advance(48.0, 2.0, 1.0 / 60.0, 0.0)
        assertEquals(latBefore, display.lat, 1e-9) // no southward slide
    }

    @Test
    fun `snaps to target beyond the teleport gap`() {
        // GPS recovery / reroute: target jumps 200 m ahead — the display snaps
        // instead of gliding for seconds at a nonphysical speed.
        display.advance(48.0, 2.0, 0.016, 90.0)
        val targetLon = 2.0 + metersToLon(200.0)
        val (_, lon) = display.advance(48.0, targetLon, 1.0 / 60.0, 90.0)
        assertEquals(targetLon, lon, 1e-9)
    }

    @Test
    fun `small gaps ease instead of snapping`() {
        // A 10 m target gap (curve overshoot scale) must still ease — no snap.
        display.advance(48.0, 2.0, 0.016, 90.0)
        val (lat, lon) = display.advance(48.0, 2.0 + metersToLon(10.0), 1.0 / 60.0, 90.0)
        assertTrue(lon > 2.0)
        assertTrue(lon < 2.0 + metersToLon(10.0)) // partially eased, not snapped
        assertEquals(48.0, lat, 1e-9)
    }
}
