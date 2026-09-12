package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the ambient light hysteresis classifier and debounce — the pure
 * logic behind the phone's light-sensor dark mode (spec: dark-mode "Ambient
 * light sensor option" — no flapping near the threshold; sensitivity levels
 * enter dark with different reluctance).
 */
class AmbientLightClassifierTest {

    // --- classifyLux hysteresis (HIGH = current behavior) ---

    @Test
    fun highMatchesCurrentConstants() {
        assertTrue(classifyLux(previousDark = false, lux = 5f, AmbientLightSensitivity.HIGH))
        assertTrue(classifyLux(previousDark = true, lux = 5f, AmbientLightSensitivity.HIGH))
        assertFalse(classifyLux(previousDark = false, lux = 200f, AmbientLightSensitivity.HIGH))
        assertFalse(classifyLux(previousDark = true, lux = 200f, AmbientLightSensitivity.HIGH))
    }

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

    // --- sensitivity levels ---

    @Test
    fun offHoldsPreviousStateForAnyLux() {
        assertFalse(classifyLux(previousDark = false, lux = 1f, AmbientLightSensitivity.OFF))
        assertTrue(classifyLux(previousDark = true, lux = 300f, AmbientLightSensitivity.OFF))
    }

    @Test
    fun lowerSensitivityEntriesDarkAtLowerLux() {
        // 6 lux: dark under HIGH (6 < 10), light under MEDIUM (6 > 5) and LOW.
        assertTrue(classifyLux(previousDark = false, lux = 6f, AmbientLightSensitivity.HIGH))
        assertFalse(classifyLux(previousDark = false, lux = 6f, AmbientLightSensitivity.MEDIUM))
        assertFalse(classifyLux(previousDark = false, lux = 6f, AmbientLightSensitivity.LOW))
        // 3 lux: HIGH and MEDIUM dark (3 < 5), LOW still light (3 > 2).
        assertTrue(classifyLux(previousDark = false, lux = 3f, AmbientLightSensitivity.HIGH))
        assertTrue(classifyLux(previousDark = false, lux = 3f, AmbientLightSensitivity.MEDIUM))
        assertFalse(classifyLux(previousDark = false, lux = 3f, AmbientLightSensitivity.LOW))
        // 1 lux: all three dark.
        assertTrue(classifyLux(previousDark = false, lux = 1f, AmbientLightSensitivity.HIGH))
        assertTrue(classifyLux(previousDark = false, lux = 1f, AmbientLightSensitivity.MEDIUM))
        assertTrue(classifyLux(previousDark = false, lux = 1f, AmbientLightSensitivity.LOW))
    }

    @Test
    fun lowerSensitivityLeavesDarkAtHigherLux() {
        // Rising from dark: 60 lux is light under HIGH (60 > 50), still dark
        // under MEDIUM (60 < 100) and LOW (60 < 200).
        assertFalse(classifyLux(previousDark = true, lux = 60f, AmbientLightSensitivity.HIGH))
        assertTrue(classifyLux(previousDark = true, lux = 60f, AmbientLightSensitivity.MEDIUM))
        assertTrue(classifyLux(previousDark = true, lux = 60f, AmbientLightSensitivity.LOW))
        // 150 lux: HIGH and MEDIUM light, LOW still dark.
        assertFalse(classifyLux(previousDark = true, lux = 150f, AmbientLightSensitivity.HIGH))
        assertFalse(classifyLux(previousDark = true, lux = 150f, AmbientLightSensitivity.MEDIUM))
        assertTrue(classifyLux(previousDark = true, lux = 150f, AmbientLightSensitivity.LOW))
        // 250 lux: all light.
        assertFalse(classifyLux(previousDark = true, lux = 250f, AmbientLightSensitivity.HIGH))
        assertFalse(classifyLux(previousDark = true, lux = 250f, AmbientLightSensitivity.MEDIUM))
        assertFalse(classifyLux(previousDark = true, lux = 250f, AmbientLightSensitivity.LOW))
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
