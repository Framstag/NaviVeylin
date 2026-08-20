package com.naviveylin.data

import com.naviveylin.core.AutoSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSettingsMappingTest {

    @Test
    fun appSettingsToAutoSettings_mapsAllCarRelevantFields() {
        val app = AppSettings(
            followMode = true,
            autoZoomEnabled = false,
            freeFormNorthUp = false,
            navNorthUp = true,
            keepScreenOn = false,
            darkMode = DarkModePreference.ON,
            laneHintsEnabled = false,
            renderMode = RenderMode.DIRECT
        )

        val auto = app.toAutoSettings()

        assertEquals(true, auto.followMode)
        assertEquals(false, auto.autoZoomEnabled)
        assertEquals(false, auto.freeFormNorthUp)
        assertEquals(true, auto.navNorthUp)
        assertEquals("ON", auto.darkMode)
        assertEquals(false, auto.laneHintsEnabled)
        assertEquals("DIRECT", auto.renderMode)
    }

    @Test
    fun appSettingsToAutoSettings_defaultsMatch() {
        assertEquals(AutoSettings(), AppSettings().toAutoSettings())
    }

    @Test
    fun autoSettingsToAppSettings_preservesPhoneOnlyFields() {
        val current = AppSettings(
            keepScreenOn = false,
            darkMode = DarkModePreference.AUTOMATIC,
            renderMode = RenderMode.TILES
        )

        val updated = AutoSettings(
            followMode = true,
            darkMode = "OFF",
            renderMode = "DIRECT"
        ).toAppSettings(current)

        assertEquals(false, updated.keepScreenOn)
        assertEquals(true, updated.followMode)
        assertEquals(DarkModePreference.OFF, updated.darkMode)
        assertEquals(RenderMode.DIRECT, updated.renderMode)
    }

    @Test
    fun autoSettingsToAppSettings_roundTripPreservesEverything() {
        val app = AppSettings(
            followMode = true,
            autoZoomEnabled = false,
            freeFormNorthUp = false,
            navNorthUp = true,
            keepScreenOn = true,
            darkMode = DarkModePreference.ON,
            laneHintsEnabled = false,
            renderMode = RenderMode.DIRECT
        )

        val roundTripped = app.toAutoSettings().toAppSettings(app)

        assertEquals(app, roundTripped)
    }
}
