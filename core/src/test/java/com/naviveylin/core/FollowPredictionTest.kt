package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [FollowPrediction]: linear extrapolation along speed + heading,
 * clamping when the fix is too old, holding when speed/heading are unknown,
 * and the exponential ease factor.
 * Plain JUnit — no Robolectric, no JNI stub involved.
 */
class FollowPredictionTest {

    private val prediction = FollowPrediction()

    @Test
    fun `extrapolates linearly along heading`() {
        // Fix at (0,0), 14 m/s heading 90° (east). After 500 ms → ~7 m east.
        prediction.update(0.0, 0.0, 14.0, 90.0, 1_000L)
        val (lat, lon) = prediction.predictedPosition(1_500L)
        assertEquals(0.0, lat, 1e-9)
        assertEquals(7.0 / FollowPrediction.METERS_PER_DEG_LON, lon, 1e-9)
    }

    @Test
    fun `extrapolates north along heading 0`() {
        prediction.update(0.0, 0.0, 10.0, 0.0, 1_000L)
        val (lat, lon) = prediction.predictedPosition(2_000L)
        assertEquals(10.0 / FollowPrediction.METERS_PER_DEG_LAT, lat, 1e-9)
        assertEquals(0.0, lon, 1e-9)
    }

    @Test
    fun `holds position when fix is older than max extrapolation window`() {
        prediction.maxExtrapolationSec = 1.5
        prediction.update(48.0, 2.0, 14.0, 90.0, 1_000L)
        // 2 s later > 1.5 s window → hold at the fix
        val (lat, lon) = prediction.predictedPosition(3_000L)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
        prediction.maxExtrapolationSec = 3.0
    }

    @Test
    fun `holds position when speed unknown`() {
        prediction.update(48.0, 2.0, Double.NaN, 90.0, 1_000L)
        val (lat, lon) = prediction.predictedPosition(1_500L)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
    }

    @Test
    fun `holds position when bearing unknown`() {
        prediction.update(48.0, 2.0, 14.0, Double.NaN, 1_000L)
        val (lat, lon) = prediction.predictedPosition(1_500L)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
    }

    @Test
    fun `holds position when speed is zero`() {
        prediction.update(48.0, 2.0, 0.0, 90.0, 1_000L)
        val (lat, lon) = prediction.predictedPosition(1_500L)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
    }

    @Test
    fun `holds position before any fix`() {
        val (lat, lon) = prediction.predictedPosition(1_000L)
        assertTrue(lat.isNaN())
        assertTrue(lon.isNaN())
    }

    @Test
    fun `does not extrapolate into the past`() {
        prediction.update(48.0, 2.0, 14.0, 90.0, 2_000L)
        val (lat, lon) = prediction.predictedPosition(1_000L)
        assertEquals(48.0, lat, 1e-9)
        assertEquals(2.0, lon, 1e-9)
    }

    @Test
    fun `caps the speed at 70 percent of the average while decelerating`() {
        // Fix A at (0,0) t=1000 with a noisy GPS speed of 20 m/s.
        prediction.update(0.0, 0.0, 20.0, 90.0, 1_000L)
        // Fix B 10 m east at t=2000, GPS speed dropped to 10 m/s (decelerating)
        // → average speed = 10 m/s.
        val dLon = 10.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLon, 10.0, 90.0, 2_000L)
        // Prediction from B at t=2500 must use min(10, 0.7*10) = 7 m/s.
        val (lat, lon) = prediction.predictedPosition(2_500L)
        val expectedLon = dLon + 3.5 / FollowPrediction.METERS_PER_DEG_LON
        assertEquals(0.0, lat, 1e-9)
        assertEquals(expectedLon, lon, 1e-9)
    }

    @Test
    fun `uses GPS speed in steady state without the cap`() {
        // Fix A at (0,0) t=1000, GPS 10 m/s.
        prediction.update(0.0, 0.0, 10.0, 90.0, 1_000L)
        // Fix B 10 m east at t=2000, GPS still 10 m/s (steady state) → the
        // prediction must use the full GPS speed, NOT 0.7*avg — the cap only
        // applies while decelerating, otherwise the display lags 30% behind
        // (the "stop and go" artifact).
        val dLon = 10.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLon, 10.0, 90.0, 2_000L)
        val (lat, lon) = prediction.predictedPosition(2_500L)
        val expectedLon = dLon + 5.0 / FollowPrediction.METERS_PER_DEG_LON
        assertEquals(0.0, lat, 1e-9)
        assertEquals(expectedLon, lon, 1e-9)
    }

    @Test
    fun `holds position when the fix has not moved`() {
        // Fix A at (0,0) t=1000, GPS 10 m/s.
        prediction.update(0.0, 0.0, 10.0, 90.0, 1_000L)
        // Fix B 10 m east at t=2000, GPS 10 m/s.
        val dLonB = 10.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLonB, 10.0, 90.0, 2_000L)
        // Fix C at the SAME position as B at t=3000 — the emulator reports a
        // non-zero speed (6.3 km/h) while stationary, so the fix-movement
        // check must win: the fix has not moved for 1 s → hold.
        prediction.update(0.0, dLonB, 1.75, 90.0, 3_000L)
        val (lat, lon) = prediction.predictedPosition(3_500L)
        assertEquals(0.0, lat, 1e-9)
        assertEquals(dLonB, lon, 1e-9)
    }

    @Test
    fun `uses GPS speed when it is below the cap`() {
        // Fix A at (0,0) t=1000 with GPS speed 6 m/s.
        prediction.update(0.0, 0.0, 6.0, 90.0, 1_000L)
        // Fix B 10 m east at t=2000 → average speed = 10 m/s.
        val dLon = 10.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLon, 6.0, 90.0, 2_000L)
        // Prediction from B at t=2500 must use min(6, 0.7*10) = 6 m/s.
        val (lat, lon) = prediction.predictedPosition(2_500L)
        val expectedLon = dLon + 3.0 / FollowPrediction.METERS_PER_DEG_LON
        assertEquals(0.0, lat, 1e-9)
        assertEquals(expectedLon, lon, 1e-9)
    }

    @Test
    fun `holds position when GPS speed is zero even with a stale average`() {
        // Fix A at (0,0) t=1000, GPS 10 m/s.
        prediction.update(0.0, 0.0, 10.0, 90.0, 1_000L)
        // Fix B 10 m east at t=2000 → average = 10 m/s.
        val dLonB = 10.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLonB, 10.0, 90.0, 2_000L)
        // Fix C 15 m east at t=3000, GPS 0 (stopped) → average = 5 m/s (B→C).
        val dLonC = 15.0 / FollowPrediction.METERS_PER_DEG_LON
        prediction.update(0.0, dLonC, 0.0, 90.0, 3_000L)
        // Prediction from C at t=3500 must HOLD (GPS says stopped), not
        // extrapolate at the stale 5 m/s average past the stop.
        val (lat, lon) = prediction.predictedPosition(3_500L)
        assertEquals(0.0, lat, 1e-9)
        assertEquals(dLonC, lon, 1e-9)
    }

    @Test
    fun `falls back to GPS speed before a second fix is available`() {
        prediction.update(0.0, 0.0, 14.0, 90.0, 1_000L)
        val (lat, lon) = prediction.predictedPosition(1_500L)
        assertEquals(0.0, lat, 1e-9)
        assertEquals(7.0 / FollowPrediction.METERS_PER_DEG_LON, lon, 1e-9)
    }

    @Test
    fun `ease alpha converges to one`() {
        // After ~3 tau (1.5 s at the default tau=0.5) the eased value is
        // within 5% of the target.
        val alpha = FollowPrediction.easeAlpha(1.5)
        assertTrue(alpha > 0.95)
        assertTrue(alpha < 1.0)
    }

    @Test
    fun `ease alpha default tau is 0_3 seconds`() {
        // 1 - exp(-0.5/0.3) = 1 - exp(-1.667) ≈ 0.811
        val alpha = FollowPrediction.easeAlpha(0.5)
        assertEquals(1.0 - Math.exp(-0.5 / 0.3), alpha, 1e-9)
    }

    @Test
    fun `ease alpha is zero for zero delta`() {
        assertEquals(0.0, FollowPrediction.easeAlpha(0.0), 1e-9)
    }

    @Test
    fun `display offset is zero when displayed position equals frame center`() {
        // 1080x1920 canvas, 1296x2304 overrun bitmap (1.2x), mag 14, dpi 320.
        val off = FollowPrediction.displayOffsetPx(
            48.0, 2.0, 48.0, 2.0, 14, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(0.0, off.clampedX, 1e-6)
        assertEquals(0.0, off.clampedY, 1e-6)
        assertFalse(off.clamped)
    }

    @Test
    fun `display offset equals the geo delta within the margin`() {
        // Move ~50 m east of the frame center at mag 14 (≈1.2 m/px at 320 dpi).
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = FollowPrediction.displayOffsetPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertTrue(off.clampedX > 0.0)
        assertTrue(off.clampedX < 108.0) // within the 0.1 * 1080 margin
        assertEquals(0.0, off.clampedY, 1e-6)
        assertFalse(off.clamped)
    }

    @Test
    fun `display offset clamps to the overrun margin`() {
        // Move far east — the offset must clamp to the margin (108 px at 1080 wide).
        val dLon = 500.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = FollowPrediction.displayOffsetPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14, 0.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(108.0, off.clampedX, 1e-6)
        assertEquals(0.0, off.clampedY, 1e-6)
        assertTrue(off.clamped)
    }

    @Test
    fun `display offset rotates with the viewport angle`() {
        // Eastward delta at a -90° (heading-up) viewport must become an upward
        // (negative-y) offset — the bitmap has the rotation baked in.
        val dLon = 50.0 / FollowPrediction.METERS_PER_DEG_LON
        val off = FollowPrediction.displayOffsetPx(
            48.0, 2.0 + dLon, 48.0, 2.0, 14, -Math.PI / 2.0, 1296, 2304, 1080, 1920, 320.0
        )
        assertEquals(0.0, off.clampedX, 1e-6)
        assertTrue(off.clampedY < 0.0) // east on the map points up on screen
        assertFalse(off.clamped)
    }
}
