package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Tests for the shared distance display helpers ([formatDistanceNumber],
 * [distanceUsesKilometers]) used by the phone UI and the car screens.
 */
@RunWith(RobolectricTestRunner::class)
class DistanceFormatTest {

    @Test
    fun formatDistanceNumberHandlesMetersAndKilometers() {
        assertEquals("350", formatDistanceNumber(350.0, Locale.US))
        assertEquals("1.2", formatDistanceNumber(1200.0, Locale.US))
        assertEquals("0", formatDistanceNumber(0.0, Locale.US))
    }

    @Test
    fun formatDistanceNumberRoundsForDisplay() {
        assertEquals("45", formatDistanceNumber(45.0, Locale.US))
        assertEquals("150", formatDistanceNumber(137.0, Locale.US))
        assertEquals("100", formatDistanceNumber(124.0, Locale.US))
        assertEquals("1.4", formatDistanceNumber(1350.0, Locale.US))
        assertEquals("1.2", formatDistanceNumber(1234.0, Locale.US))
    }

    @Test
    fun formatDistanceNumberUsesLocaleDecimalSeparator() {
        assertEquals("1,2", formatDistanceNumber(1200.0, Locale.GERMANY))
    }

    @Test
    fun distanceUsesKilometersReflectsUnit() {
        assertFalse(distanceUsesKilometers(350.0))
        assertTrue(distanceUsesKilometers(1200.0))
        assertFalse(distanceUsesKilometers(0.0))
    }
}
