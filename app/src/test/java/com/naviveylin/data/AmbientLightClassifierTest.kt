package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the ambient light hysteresis classifier and debounce — the pure
 * logic behind the phone's light-sensor dark mode (spec: dark-mode "Ambient
 * light sensor option" — no flapping near the threshold).
 */
class AmbientLightClassifierTest {

    // --- classifyLux hysteresis ---

    @Test
    fun belowDarkLuxClassifiesDark() {
        assertTrue(classifyLux(previousDark = false, lux = 5f))
        assertTrue(classifyLux(previousDark = true, lux = 5f))
    }

    @Test
    fun aboveLightLuxClassifiesLight() {
        assertFalse(classifyLux(previousDark = false, lux = 200f))
        assertFalse(classifyLux(previousDark = true, lux = 200f))
    }

    @Test
    fun betweenThresholdsHoldsPreviousState() {
        // Rising from dark: still dark at 30 lux (below LIGHT_LUX).
        assertTrue(classifyLux(previousDark = true, lux = 30f))
        // Falling from light: still light at 30 lux (above DARK_LUX).
        assertFalse(classifyLux(previousDark = false, lux = 30f))
    }

    @Test
    fun boundaryValuesFollowHysteresis() {
        // Exactly DARK_LUX is not below it -> holds previous.
        assertFalse(classifyLux(previousDark = false, lux = DARK_LUX))
        // Exactly LIGHT_LUX is not above it -> holds previous.
        assertTrue(classifyLux(previousDark = true, lux = LIGHT_LUX))
    }

    // --- DebouncedSignal ---

    @Test
    fun flipHeldForDebounceWindowIsApplied() {
        val signal = DebouncedSignal(debounceMs = 10_000, initial = false)
        assertEquals(false, signal.update(true, nowMs = 0))
        assertEquals(false, signal.update(true, nowMs = 9_999))
        assertEquals(true, signal.update(true, nowMs = 10_000))
    }

    @Test
    fun flipRevertedBeforeWindowIsDiscarded() {
        val signal = DebouncedSignal(debounceMs = 10_000, initial = false)
        signal.update(true, nowMs = 0)
        // Raw value returns to current before the window elapses -> no flip.
        assertEquals(false, signal.update(false, nowMs = 5_000))
        assertEquals(false, signal.update(false, nowMs = 20_000))
    }

    @Test
    fun stableValueIsNotDebounced() {
        val signal = DebouncedSignal(debounceMs = 10_000, initial = false)
        assertEquals(false, signal.update(false, nowMs = 0))
        assertEquals(false, signal.update(false, nowMs = 100_000))
    }

    @Test
    fun flipBackAfterAppliedFlipIsDebouncedAgain() {
        val signal = DebouncedSignal(debounceMs = 10_000, initial = false)
        signal.update(true, nowMs = 0)
        assertEquals(true, signal.update(true, nowMs = 10_000))
        // Now flip back: must hold the window again.
        assertEquals(true, signal.update(false, nowMs = 10_001))
        assertEquals(false, signal.update(false, nowMs = 20_001))
    }
}
