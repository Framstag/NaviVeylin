package com.naviveylin.auto

import com.naviveylin.core.AutoSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesScreenMapperTest {

    @Test
    fun rows_containsAllCarRelevantSettings() {
        val rows = PreferencesScreenMapper.rows(AutoSettings())

        assertEquals(9, rows.size)
        assertEquals(
            listOf(
                "followMode",
                "autoZoomEnabled",
                "freeFormNorthUp",
                "navNorthUp",
                "darkMode",
                "laneHintsEnabled",
                "renderMode",
                "overspeedWarningDeltaKmh",
                "styleSheet"
            ),
            rows.map { it.key }
        )
    }

    @Test
    fun rows_showsCurrentValues() {
        val settings = AutoSettings(
            followMode = true,
            autoZoomEnabled = false,
            darkMode = "ON",
            renderMode = "DIRECT",
            overspeedWarningDeltaKmh = 3,
            styleSheet = "cycle"
        )

        val rows = PreferencesScreenMapper.rows(settings).associateBy { it.key }

        assertEquals("On", rows["followMode"]?.valueText)
        assertEquals("Off", rows["autoZoomEnabled"]?.valueText)
        assertEquals("On", rows["darkMode"]?.valueText)
        assertEquals("Direct", rows["renderMode"]?.valueText)
        assertEquals("3 km/h", rows["overspeedWarningDeltaKmh"]?.valueText)
        assertEquals("Overspeed warning", rows["overspeedWarningDeltaKmh"]?.title)
        assertEquals("cycle", rows["styleSheet"]?.valueText)
        assertEquals("Map style", rows["styleSheet"]?.title)
    }

    @Test
    fun rows_doesNotIncludeKeepScreenOn() {
        val keys = PreferencesScreenMapper.rows(AutoSettings()).map { it.key }

        assertEquals(false, keys.contains("keepScreenOn"))
    }

    @Test
    fun toggle_flipsBooleanPreference() {
        val toggled = PreferencesScreenMapper.toggle(AutoSettings(followMode = false), "followMode")

        assertEquals(true, toggled.followMode)
    }

    @Test
    fun toggle_cyclesDarkMode() {
        assertEquals("OFF", PreferencesScreenMapper.toggle(AutoSettings(darkMode = "ON"), "darkMode").darkMode)
        assertEquals("AUTOMATIC", PreferencesScreenMapper.toggle(AutoSettings(darkMode = "OFF"), "darkMode").darkMode)
        assertEquals("ON", PreferencesScreenMapper.toggle(AutoSettings(darkMode = "AUTOMATIC"), "darkMode").darkMode)
    }

    @Test
    fun toggle_cyclesRenderMode() {
        assertEquals("DIRECT", PreferencesScreenMapper.toggle(AutoSettings(renderMode = "TILES"), "renderMode").renderMode)
        assertEquals("TILES", PreferencesScreenMapper.toggle(AutoSettings(renderMode = "DIRECT"), "renderMode").renderMode)
    }

    @Test
    fun toggle_cyclesMapStyle() {
        val styles = listOf("cycle", "motorways", "standard", "winter-sports")
        assertEquals("winter-sports", PreferencesScreenMapper.toggle(AutoSettings(styleSheet = "standard"), "styleSheet", styles).styleSheet)
        assertEquals("cycle", PreferencesScreenMapper.toggle(AutoSettings(styleSheet = "winter-sports"), "styleSheet", styles).styleSheet)
    }

    @Test
    fun toggle_mapStyleWrapsToFirstForUnknownCurrent() {
        val styles = listOf("cycle", "standard")
        assertEquals("cycle", PreferencesScreenMapper.toggle(AutoSettings(styleSheet = "stale"), "styleSheet", styles).styleSheet)
    }

    @Test
    fun toggle_mapStyleKeepsCurrentWhenListEmpty() {
        assertEquals(
            "standard",
            PreferencesScreenMapper.toggle(AutoSettings(styleSheet = "standard"), "styleSheet", emptyList()).styleSheet
        )
    }

    @Test
    fun toggle_unknownKeyReturnsUnchanged() {
        val settings = AutoSettings()

        assertEquals(settings, PreferencesScreenMapper.toggle(settings, "nope"))
    }
}
