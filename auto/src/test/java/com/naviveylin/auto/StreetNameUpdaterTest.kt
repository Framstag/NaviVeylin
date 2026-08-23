package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [StreetNameUpdater] (spec: auto/free-driving — "Current street
 * name shown"): the reverse geocode throttle (move threshold + interval) and
 * the blank-tolerant street extraction.
 */
@RunWith(RobolectricTestRunner::class)
class StreetNameUpdaterTest {

    /** ~111 m per degree of latitude; 0.1° ≈ 11 km — far beyond the 25 m threshold. */
    private val lat1 = 51.5
    private val lon1 = 7.46

    @Test
    fun firstFixAlwaysGeocodes() {
        val updater = StreetNameUpdater()
        assertTrue(updater.shouldGeocode(lat1, lon1))
    }

    @Test
    fun stationaryWithinIntervalSkipsGeocode() {
        var t = 0L
        val updater = StreetNameUpdater(now = { t })
        assertTrue(updater.shouldGeocode(lat1, lon1))
        updater.markGeocoded(lat1, lon1)

        // Same position, 1 s later — below the 2 s interval and no movement.
        t = 1_000L
        assertFalse(updater.shouldGeocode(lat1, lon1))
    }

    @Test
    fun movementBeyondThresholdTriggersGeocode() {
        var t = 0L
        val updater = StreetNameUpdater(now = { t })
        assertTrue(updater.shouldGeocode(lat1, lon1))
        updater.markGeocoded(lat1, lon1)
        t = 1_000L

        // ~11 km south — well beyond the 25 m threshold; geocodes despite
        // the interval not having elapsed.
        assertTrue(updater.shouldGeocode(lat1 - 0.1, lon1))
    }

    @Test
    fun intervalElapsedTriggersGeocodeEvenStationary() {
        var t = 0L
        val updater = StreetNameUpdater(now = { t })
        updater.markGeocoded(lat1, lon1)
        t = 3_000L
        assertTrue(updater.shouldGeocode(lat1, lon1))
    }

    @Test
    fun tinyMovementWithinIntervalSkipsGeocode() {
        var t = 0L
        val updater = StreetNameUpdater(now = { t })
        updater.markGeocoded(lat1, lon1)
        t = 1_000L
        // 1e-5 degrees latitude ≈ 1.1 m — below the 25 m threshold.
        assertFalse(updater.shouldGeocode(lat1 + 1e-5, lon1))
    }

    @Test
    fun streetNameExtractedFromAddressIndexZero() {
        val updater = StreetNameUpdater()
        assertEquals("Hauptstraße", updater.streetFromAddress(arrayOf("Hauptstraße", "12")))
        assertEquals("Main St", updater.streetFromAddress(arrayOf("  Main St  ", "5")))
    }

    @Test
    fun blankOrMissingStreetReturnsNull() {
        val updater = StreetNameUpdater()
        assertNull(updater.streetFromAddress(arrayOf("", "12")))
        assertNull(updater.streetFromAddress(arrayOf("   ", "")))
        assertNull(updater.streetFromAddress(null))
        assertNull(updater.streetFromAddress(emptyArray()))
    }
}
