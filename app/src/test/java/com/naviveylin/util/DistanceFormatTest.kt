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

    // The haversine cases moved to `:core` with the single implementation
    // (GeoDistanceTest); this class covers the app-facing formatting helpers.

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
