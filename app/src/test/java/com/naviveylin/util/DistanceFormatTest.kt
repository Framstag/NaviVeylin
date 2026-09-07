package com.naviveylin.util

import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Plain-JUnit tests for the distance formatting helpers. No native
 * library access, so no Robolectric sandbox involvement (see AGENTS.md
 * classloader rule).
 */
class DistanceFormatTest {

    // ---- haversineDistanceMeters ----

    @Test
    fun zeroDistanceBetweenIdenticalCoordinates() {
        assertEquals(0.0, haversineDistanceMeters(51.5136, 7.4653, 51.5136, 7.4653), 0.001)
    }

    @Test
    fun berlinToHamburgApproximately255Km() {
        // Berlin (52.5200, 13.4050) -> Hamburg (53.5511, 9.9937) is ~255 km.
        val meters = haversineDistanceMeters(52.5200, 13.4050, 53.5511, 9.9937)
        assertEquals(255000.0, meters, 255000.0 * 0.02)
    }

    @Test
    fun nanCoordinatesYieldInfinity() {
        assertEquals(
            Double.POSITIVE_INFINITY,
            haversineDistanceMeters(Double.NaN, 7.4653, 52.52, 13.405),
            0.0
        )
    }

    @Test
    fun distanceIsSymmetric() {
        val a = haversineDistanceMeters(51.5136, 7.4653, 52.52, 13.405)
        val b = haversineDistanceMeters(52.52, 13.405, 51.5136, 7.4653)
        assertEquals(a, b, 0.001)
    }

    // ---- formatDistanceKm (numeric part only; unit comes from resources) ----

    @Test
    fun subKilometerShowsOneDecimal() {
        assertEquals("0.5", formatDistanceKm(500.0, Locale.US))
    }

    @Test
    fun justBelowTenKmKeepsOneDecimal() {
        assertEquals("9.9", formatDistanceKm(9900.0, Locale.US))
    }

    @Test
    fun tenKmAndAboveRoundsToWholeKilometers() {
        assertEquals("10", formatDistanceKm(10000.0, Locale.US))
        assertEquals("12", formatDistanceKm(12345.0, Locale.US))
    }

    @Test
    fun formattingIsLocaleAware() {
        // German locale uses comma decimals (spec: locale-aware number formatting).
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,2", formatDistanceKm(1234.5))
            assertEquals("1,2", formatDistanceNumber(1234.5))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun englishUsesDotDecimals() {
        assertEquals("1.2", formatDistanceKm(1234.5, Locale.ENGLISH))
        assertEquals("1.2", formatDistanceNumber(1234.5, Locale.ENGLISH))
    }

    // ---- formatDistanceNumber / distanceUsesKilometers ----

    @Test
    fun subKilometerShowsMeters() {
        assertEquals("350", formatDistanceNumber(350.0))
        assertFalse(distanceUsesKilometers(350.0))
    }

    @Test
    fun kilometerShowsOneDecimal() {
        assertEquals("1.2", formatDistanceNumber(1234.5, Locale.ENGLISH))
        assertTrue(distanceUsesKilometers(1234.5))
    }

    @Test
    fun exactlyOneKilometerUsesKilometers() {
        assertTrue(distanceUsesKilometers(1000.0))
        assertEquals("1.0", formatDistanceNumber(1000.0, Locale.ENGLISH))
    }
}
