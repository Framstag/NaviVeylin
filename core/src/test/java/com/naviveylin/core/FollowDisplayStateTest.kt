package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Verifies [FollowDisplayState]: the bounded-rate advance (spec: `smooth-follow` — Correction
 * easing, design D1) — the display advances at the committed effective speed plus a correction
 * clamped to a bounded fraction of it, so it never halts and never closes a gap faster than the
 * bound; the teleport snap, the no-heading path, and reset.
 *
 * Replaces the former forward-only-hold cases: the hold is gone (the spec's "Correction easing"
 * delta), and the case names here follow the behaviour that replaced it.
 *
 * Plain JUnit — no Robolectric, no JNI stub involved.
 */
class FollowDisplayStateTest {

    private val display = FollowDisplayState()

    private fun metersToLon(meters: Double): Double =
        meters / (FollowPrediction.METERS_PER_DEG_LON * cos(Math.toRadians(latDeg)))

    private fun metersToLat(meters: Double): Double = meters / FollowPrediction.METERS_PER_DEG_LAT

    /** Eastward lead (m) of the display behind the target (the primitive's local-metre convention). */
    private fun eastLeadM(displayLonDeg: Double, targetLonDeg: Double): Double =
        (targetLonDeg - displayLonDeg) * FollowPrediction.METERS_PER_DEG_LON * cos(Math.toRadians(latDeg))

    private val vEff = 14.0
    private val dt = 1.0 / 60.0
    private val maxCorr = FollowDisplayState.CORR_FRACTION * vEff

    /** Latitude of the test track, for the local-metre conversion the primitive uses. */
    private val latDeg = 48.0

    private fun eastMeters(fromLon: Double, toLon: Double): Double =
        (toLon - fromLon) * FollowPrediction.METERS_PER_DEG_LON * cos(Math.toRadians(latDeg))

    @Test
    fun `first advance initializes the displayed position to the target`() {
        val (lat, lon) = display.advance(48.0, 2.0, dt, 90.0, vEff)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
        assertTrue(display.hasPosition)
    }

    @Test
    fun `advances at the effective speed toward a moving target with a bounded lead`() {
        // Target moves east at 14 m/s (the extrapolated prediction); the display advances at the
        // same speed plus the clamped correction, so the lead converges to the clamp boundary
        // (~0.25 * v * tau = 1.05 m) instead of growing.
        var targetLon = 2.0
        var prevLon = Double.NaN
        var maxLead = 0.0
        for (i in 0 until 600) {
            targetLon += vEff * dt * (1.0 / FollowPrediction.METERS_PER_DEG_LON)
            val (lat, lon) = display.advance(48.0, targetLon, dt, 90.0, vEff)
            if (i > 0) {
                assertTrue("display must advance forward", lon > prevLon)
            }
            maxLead = maxOf(maxLead, eastLeadM(lon, targetLon))
            prevLon = lon
            assertEquals(48.0, lat, 1e-9)
        }
        assertTrue("max lead $maxLead too large", maxLead < 1.5)
    }

    @Test
    fun `a target drop behind the display corrects at the bounded rate`() {
        // A fix landing 8 m BEHIND the display (the overshoot the forward-only hold used to freeze):
        // with the feed-forward running, the display still advances — only slower, and never at less
        // than (1 - CORR_FRACTION) of the effective speed.
        display.advance(48.0, 2.0 + metersToLon(8.0), dt, 90.0, vEff)
        val before = display.lon
        display.advance(48.0, 2.0, dt, 90.0, vEff)
        val stepM = eastMeters(before, display.lon)
        assertTrue("correction must slow the advance", stepM < vEff * dt)
        assertTrue("advance $stepM m/frame below the bound", stepM >= (vEff - maxCorr) * dt - 1e-6)
    }

    @Test
    fun `without a heading and with no speed the display settles back onto the fix`() {
        // Correction alone, at the floor bound (1 m/s): the display may move backward, slowly.
        display.advance(48.0, 2.0 + metersToLon(8.0), dt, Double.NaN, 0.0)
        val before = display.lon
        display.advance(48.0, 2.0, dt, Double.NaN, 0.0)
        val stepM = eastMeters(before, display.lon)
        assertTrue("must move back toward the fix", stepM < 0.0)
        assertTrue(
            "backward rate $stepM m/frame exceeds the floor",
            stepM >= -(FollowDisplayState.CORR_FLOOR_M_S + 1e-9) * dt
        )
    }

    @Test
    fun `keeps advancing forward when the target lies behind`() {
        // The whole point of the feed-forward term: with the fix 8 m behind along the heading the
        // MAP still advances forward at no less than (1 - CORR_FRACTION) of the effective speed.
        display.advance(48.0, 2.0 + metersToLon(8.0), dt, 90.0, vEff)
        var prevLon = display.lon
        for (i in 0 until 60) {
            val (_, lon) = display.advance(48.0, 2.0, dt, 90.0, vEff)
            val stepM = eastMeters(prevLon, lon)
            assertTrue("display must advance forward, not hold", stepM > 0.0)
            assertTrue("advance $stepM m/frame below the bound", stepM >= (vEff - maxCorr) * dt - 1e-6)
            prevLon = lon
        }
    }

    @Test
    fun `without a heading the correction alone applies`() {
        // No heading (no direction of travel): no feed-forward term — the display settles on the
        // fix from either side, at the bounded rate.
        display.advance(48.0, 2.0, dt, Double.NaN, vEff)
        val (_, aheadFwd) = display.advance(48.0, 2.0 + metersToLon(10.0), dt, Double.NaN, vEff)
        assertTrue("must move toward a target ahead", aheadFwd > 2.0)
        val (_, aheadBack) = display.advance(48.0, 2.0, dt, Double.NaN, vEff)
        assertTrue("must move toward a target behind", aheadBack < aheadFwd)
    }

    @Test
    fun `heading north advances when the target lies south`() {
        // Traveling north with the fix south of the display: the forward-only hold froze here; now
        // the display keeps advancing north at a reduced rate.
        display.advance(48.0 + metersToLat(10.0), 2.0, dt, 0.0, vEff)
        val latBefore = display.lat
        display.advance(48.0, 2.0, dt, 0.0, vEff)
        val stepM = (display.lat - latBefore) * FollowPrediction.METERS_PER_DEG_LAT
        assertTrue("must keep advancing north", stepM > 0.0)
        assertTrue("advance $stepM m/frame below the bound", stepM >= (vEff - maxCorr) * dt - 1e-6)
    }

    @Test
    fun `snaps to target beyond the teleport gap`() {
        // GPS recovery / reroute: target jumps 200 m ahead — the display snaps instead of gliding
        // for seconds at a nonphysical rate.
        display.advance(48.0, 2.0, dt, 90.0, vEff)
        val targetLon = 2.0 + metersToLon(200.0)
        val (_, lon) = display.advance(48.0, targetLon, dt, 90.0, vEff)
        assertEquals(targetLon, lon, 1e-9)
    }

    @Test
    fun `small gaps are corrected instead of snapping`() {
        display.advance(48.0, 2.0, dt, 90.0, vEff)
        val (lat, lon) = display.advance(48.0, 2.0 + metersToLon(10.0), dt, 90.0, vEff)
        assertTrue(lon > 2.0)
        assertTrue(lon < 2.0 + metersToLon(10.0)) // partially corrected, not snapped
        assertEquals(48.0, lat, 1e-9)
    }

    @Test
    fun `frozen without advance calls`() {
        // No advance calls while stopped — the position must not change
        // (freeze semantics are a call-site property).
        display.advance(48.0, 2.0, dt, 90.0, vEff)
        val lat = display.lat
        val lon = display.lon
        assertEquals(lat, display.lat, 1e-9)
        assertEquals(lon, display.lon, 1e-9)
    }

    @Test
    fun `reset clears the displayed position`() {
        display.advance(48.0, 2.0, dt, 90.0, vEff)
        display.reset()
        assertFalse(display.hasPosition)
        assertTrue(display.lat.isNaN())
    }
}
