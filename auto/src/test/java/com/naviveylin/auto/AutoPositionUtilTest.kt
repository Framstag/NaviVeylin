package com.naviveylin.auto

import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoPositionUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the shared speed/bearing derivation helpers (spec:
 * auto-smooth-follow — "Fix feed from follow-mode screens"): GPS values
 * used as-is when valid, movement-derived fallbacks for GPX replay tracks
 * without GPS speed/bearing, last-effective-value retention when the fix
 * moved too little to trust a derivation.
 */
@RunWith(RobolectricTestRunner::class)
class AutoPositionUtilTest {

    // ── movementSpeedKmH (GPX replay without a GPS speed) ──

    @Test
    fun movementSpeedDerivedFromDistanceAndTime() {
        // 0.1° latitude ≈ 11.1 km in 600 s → ≈ 66.7 km/h.
        val s = AutoPositionUtil.movementSpeedKmH(51.0, 7.0, 51.1, 7.0, 600_000L)!!
        assertEquals(66.7, s, 1.0)
    }

    @Test
    fun movementSpeedNullWhenTimeTooShort() {
        assertNull(AutoPositionUtil.movementSpeedKmH(51.0, 7.0, 51.1, 7.0, 100L))
    }

    @Test
    fun movementSpeedNullWhenMovedTooLittle() {
        // ~0.55 m in 10 s — below the 1 m trust threshold.
        assertNull(AutoPositionUtil.movementSpeedKmH(51.0, 7.0, 51.000005, 7.0, 10_000L))
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

    // ── AutoFixDerivation: effective speed/bearing per fix ──

    @Test
    fun gpsSpeedUsedWhenValid() {
        val d = AutoFixDerivation()
        val (speed, bearing) = d.derive(
            AutoPosition(lat = 51.0, lon = 7.0, bearing = 90.0, speedKmH = 60.0),
            1_000L
        )
        assertEquals(60.0, speed, 1e-9)
        assertEquals(90.0, bearing, 1e-9)
    }

    @Test
    fun speedDerivedWhenGpsSpeedMissing() {
        // First fix: no previous position, no GPS speed → nothing to derive;
        // the sentinel -1.0 passes through. The second fix moves 0.1° north
        // over 600 s → ≈ 66.7 km/h derived.
        val d = AutoFixDerivation()
        d.derive(AutoPosition(lat = 51.0, lon = 7.0, speedKmH = Double.NaN), 0L)
        val (speed, _) = d.derive(
            AutoPosition(lat = 51.1, lon = 7.0, speedKmH = Double.NaN),
            600_000L
        )
        assertEquals(66.7, speed, 1.0)
    }

    @Test
    fun bearingDerivedWhenGpsBearingMissing() {
        val d = AutoFixDerivation()
        d.derive(AutoPosition(lat = 51.0, lon = 7.0, bearing = Double.NaN), 0L)
        val (_, bearing) = d.derive(
            AutoPosition(lat = 51.1, lon = 7.0, bearing = Double.NaN),
            600_000L
        )
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun lastEffectiveValuesKeptWhenFixMovedTooLittle() {
        // Seed a valid effective bearing/speed, then a negligible movement
        // with all GPS values missing → previous effective values retained.
        val d = AutoFixDerivation()
        val first = AutoPosition(lat = 51.0, lon = 7.0, bearing = 90.0, speedKmH = 50.0)
        d.derive(first, 1_000L)
        val second = AutoPosition(
            lat = 51.0, lon = 7.0, bearing = Double.NaN, speedKmH = Double.NaN
        )
        val (speed, bearing) = d.derive(second, 2_000L)
        assertEquals(50.0, speed, 1e-9)
        assertEquals(90.0, bearing, 1e-9)
        // Sanity: a real speed is never replaced by a sentinel.
        assertTrue(speed >= 0.0)
    }
}
