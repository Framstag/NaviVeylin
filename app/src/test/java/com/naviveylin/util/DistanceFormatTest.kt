package com.naviveylin.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for the search-result distance helper. No native
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

    // ---- formatDistanceKm ----

    @Test
    fun subKilometerShowsOneDecimal() {
        assertEquals("0.5 km", formatDistanceKm(500.0))
    }

    @Test
    fun justBelowTenKmKeepsOneDecimal() {
        assertEquals("9.9 km", formatDistanceKm(9900.0))
    }

    @Test
    fun tenKmAndAboveRoundsToWholeKilometers() {
        assertEquals("10 km", formatDistanceKm(10000.0))
        assertEquals("12 km", formatDistanceKm(12345.0))
    }

    @Test
    fun unitSuffixAlwaysIncluded() {
        assertTrue(formatDistanceKm(1.0).endsWith(" km"))
        assertTrue(formatDistanceKm(100000.0).endsWith(" km"))
    }

    @Test
    fun formattingIsLocaleIndependent() {
        // Locale.ROOT forces '.' regardless of default locale; run twice with a
        // comma-decimal locale to prove the formatted value does not change.
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.2 km", formatDistanceKm(1234.5))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
