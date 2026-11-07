package com.naviveylin.auto

import com.naviveylin.core.AutoSettings
import com.naviveylin.core.VehicleAnchorPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesScreenMapperTest {

    @Test
    fun rows_containsAllCarRelevantSettings() {
        val rows = PreferencesScreenMapper.rows(AutoSettings())

        assertEquals(13, rows.size)
        assertEquals(
            listOf(
                "followMode",
                "autoZoomEnabled",
                "freeFormNorthUp",
                "navNorthUp",
                "darkMode",
                "laneHintsEnabled",
                "voiceGuidanceEnabled",
                "announceGpsState",
                "renderMode",
                "overspeedWarningDeltaKmh",
                "routingAnchor",
                "freeDrivingAnchor",
                "styleSheet"
            ),
            rows.map { it.key }
        )
    }

    // --- The announcement switches (spec: auto/preferences — The car offers the announcement switches) ---

    /**
     * Spec case: Both rows are shown with their values.
     */
    @Test
    fun announcementRowsCarryTheirValues() {
        val rows = PreferencesScreenMapper.rows(
            AutoSettings(voiceGuidanceEnabled = true, announceGpsState = false)
        ).associateBy { it.key }

        assertEquals("Voice guidance", rows.getValue(PreferencesScreenMapper.KEY_VOICE_GUIDANCE).title)
        assertEquals("On", rows.getValue(PreferencesScreenMapper.KEY_VOICE_GUIDANCE).valueText)
        assertEquals("GPS announcements", rows.getValue(PreferencesScreenMapper.KEY_GPS_ANNOUNCEMENTS).title)
        assertEquals("Off", rows.getValue(PreferencesScreenMapper.KEY_GPS_ANNOUNCEMENTS).valueText)

        val flipped = PreferencesScreenMapper.rows(
            AutoSettings(voiceGuidanceEnabled = false, announceGpsState = true)
        ).associateBy { it.key }
        assertEquals("Off", flipped.getValue(PreferencesScreenMapper.KEY_VOICE_GUIDANCE).valueText)
        assertEquals("On", flipped.getValue(PreferencesScreenMapper.KEY_GPS_ANNOUNCEMENTS).valueText)
    }

    /**
     * Spec case: The car voice row flips only the car's value.
     */
    @Test
    fun theVoiceRowFlipsOnlyTheCarValue() {
        val before = AutoSettings(voiceGuidanceEnabled = true, announceGpsState = false)

        val after = PreferencesScreenMapper.toggle(before, PreferencesScreenMapper.KEY_VOICE_GUIDANCE)

        assertEquals(false, after.voiceGuidanceEnabled)
        assertEquals("the shared GPS value is untouched", false, after.announceGpsState)
        assertEquals("and so is every other preference", before.followMode, after.followMode)
    }

    /**
     * Spec case: The GPS row flips the shared value.
     */
    @Test
    fun theGpsRowFlipsTheSharedValue() {
        val before = AutoSettings(voiceGuidanceEnabled = true, announceGpsState = false)

        val after = PreferencesScreenMapper.toggle(before, PreferencesScreenMapper.KEY_GPS_ANNOUNCEMENTS)

        assertEquals(true, after.announceGpsState)
        assertEquals("the car's voice value is untouched", true, after.voiceGuidanceEnabled)
    }

    /**
     * Spec case: The phone's own switch is not editable from the car.
     */
    @Test
    fun thePhoneVoiceValueHasNoCarRow() {
        // Every row either edits the car's own view or a shared value; the phone's own switch is not
        // this surface's to edit, and the app-side write-back is proven where the mapping lives
        // (`SettingsStorageVoiceSwitchTest#aCarWriteLeavesThePhoneVoiceValueAlone`).
        val keys = PreferencesScreenMapper.rows(AutoSettings()).map { it.key }
        assertEquals(true, keys.contains(PreferencesScreenMapper.KEY_VOICE_GUIDANCE))
        assertEquals(false, keys.contains("autoVoiceGuidanceEnabled"))
        assertEquals(
            "no row names the phone's voice field",
            false,
            keys.any { it == "voiceGuidanceEnabled" && it != PreferencesScreenMapper.KEY_VOICE_GUIDANCE }
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
        // Vehicle anchors: current preset labels from the shared enum.
        assertEquals("Center", rows["routingAnchor"]?.valueText)
        assertEquals("Center", rows["freeDrivingAnchor"]?.valueText)
        assertEquals("Vehicle position (navigation)", rows["routingAnchor"]?.title)
        assertEquals("Vehicle position (free driving)", rows["freeDrivingAnchor"]?.title)
    }

    @Test
    fun rows_showsConfiguredAnchorLabels() {
        val settings = AutoSettings(
            routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id,
            freeDrivingAnchorId = VehicleAnchorPosition.TOP_CENTER.id
        )
        val rows = PreferencesScreenMapper.rows(settings).associateBy { it.key }
        assertEquals("Bottom right", rows["routingAnchor"]?.valueText)
        assertEquals("Top center", rows["freeDrivingAnchor"]?.valueText)
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
