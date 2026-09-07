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
    fun sensorWinsWhenEnabledAndActive() {
        // Sensor dark, system light -> dark.
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = true, sensorEnabled = true)
        )
        // Sensor light, system dark -> light.
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = false, sensorEnabled = true)
        )
    }

    @Test
    fun sensorIgnoredWhenOptionDisabled() {
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = true, sensorEnabled = false)
        )
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = false, sensorEnabled = false)
        )
    }

    @Test
    fun sensorUnavailableFallsBackToSystem() {
        // Option enabled but no classification (null) -> system signal.
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true, sensorDark = null, sensorEnabled = true)
        )
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false, sensorDark = null, sensorEnabled = true)
        )
    }

    @Test
    fun manualPreferenceOverridesSensor() {
        assertEquals(
            true,
            resolveDarkPresentation(DarkModePreference.ON, environmentDark = false, sensorDark = false, sensorEnabled = true)
        )
        assertEquals(
            false,
            resolveDarkPresentation(DarkModePreference.OFF, environmentDark = true, sensorDark = true, sensorEnabled = true)
        )
    }
}
