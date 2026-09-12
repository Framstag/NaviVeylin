package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkModeResolutionTest {

    @Test
    fun onIsAlwaysDark() {
        assertEquals(true, resolveDarkPresentation(DarkModePreference.ON, environmentDark = true))
        assertEquals(true, resolveDarkPresentation(DarkModePreference.ON, environmentDark = false))
    }

    @Test
    fun offIsAlwaysLight() {
        assertEquals(false, resolveDarkPresentation(DarkModePreference.OFF, environmentDark = true))
        assertEquals(false, resolveDarkPresentation(DarkModePreference.OFF, environmentDark = false))
    }

    @Test
    fun automaticFollowsEnvironment() {
        assertEquals(true, resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true))
        assertEquals(false, resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false))
    }

    @Test
    fun sensorWinsWhenSensitivityActive() {
        // Sensor dark, system light -> dark.
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = true, sensorSensitivity = AmbientLightSensitivity.HIGH)
        )
        // Sensor light, system dark -> light.
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = false, sensorSensitivity = AmbientLightSensitivity.MEDIUM)
        )
    }

    @Test
    fun sensorIgnoredWhenSensitivityOff() {
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = true, sensorSensitivity = AmbientLightSensitivity.OFF)
        )
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = false, sensorSensitivity = AmbientLightSensitivity.OFF)
        )
    }

    @Test
    fun sensorUnavailableFallsBackToSystem() {
        // Sensitivity active but no classification (null) -> system signal.
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = null, sensorSensitivity = AmbientLightSensitivity.HIGH)
        )
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = null, sensorSensitivity = AmbientLightSensitivity.HIGH)
        )
    }

    @Test
    fun manualPreferenceOverridesSensor() {
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.ON, environmentDark = false, sensorDark = false, sensorSensitivity = AmbientLightSensitivity.HIGH)
        )
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.OFF, environmentDark = true, sensorDark = true, sensorSensitivity = AmbientLightSensitivity.HIGH)
        )
    }
}
