package com.naviveylin.auto

import com.naviveylin.core.AutoSettings
import com.naviveylin.core.VehicleAnchorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesScreenMapperTest {

    private val styles = listOf("cycle", "motorways", "standard", "winter-sports")

    @Test
    fun rows_containsAllCarRelevantSettings() {
        val rows = PreferencesScreenMapper.rows(AutoSettings())

        assertEquals(13, rows.size)
        assertEquals(
            setOf(
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
            rows.map { it.key }.toSet()
        )
    }

    // --- Grouped sub-screens (spec: auto/preferences — The car's preferences are grouped into sub-screens) ---

    /**
     * Spec case: The root lists topics and no setting row.
     */
    @Test
    fun theRootListsTopicsAndNoSettingRow() {
        val topics = PreferencesScreenMapper.topics(AutoSettings())

        // The three topics whose rows the car carries, in declaration order — the phone's topic
        // names and the phone's order (spec: auto/preferences — "The root lists topics").
        assertEquals(
            listOf(
                PreferenceTopic.MAP,
                PreferenceTopic.NAVIGATION_AND_ROUTE,
                PreferenceTopic.HINTS_AND_LANGUAGE
            ),
            topics
        )
        // Every row the mapper declares belongs to one of those topics, so the root is the topic
        // list alone: a driver reaches a setting row only through a topic screen.
        assertEquals(
            13,
            PreferencesScreenMapper.rows(AutoSettings()).count { it.topic in topics }
        )
    }

    /**
     * Spec case: A topic opens its rows.
     */
    @Test
    fun aTopicListsItsCarUsableRows() {
        val map = PreferencesScreenMapper.rowsFor(PreferenceTopic.MAP, AutoSettings())
        val navigation = PreferencesScreenMapper.rowsFor(PreferenceTopic.NAVIGATION_AND_ROUTE, AutoSettings())
        val hints = PreferencesScreenMapper.rowsFor(PreferenceTopic.HINTS_AND_LANGUAGE, AutoSettings())

        assertEquals(
            listOf("darkMode", "renderMode", "styleSheet", "freeFormNorthUp"),
            map.map { it.key }
        )
        assertEquals(
            listOf(
                "followMode", "autoZoomEnabled", "navNorthUp",
                "overspeedWarningDeltaKmh", "routingAnchor", "freeDrivingAnchor"
            ),
            navigation.map { it.key }
        )
        assertEquals(
            listOf("laneHintsEnabled", "voiceGuidanceEnabled", "announceGpsState"),
            hints.map { it.key }
        )
        // The three lists are disjoint and together hold every row: no row is shown twice and none
        // is dropped between the levels.
        assertEquals(13, (map + navigation + hints).size)
        // Every topic keeps one row list under the ten rows a driver should scroll (design D6).
        assertTrue(map.size <= 10 && navigation.size <= 10 && hints.size <= 10)
    }

    @Test
    fun everyListedTopicHasRows() {
        PreferencesScreenMapper.topics(AutoSettings()).forEach { topic ->
            assertTrue(
                "topic $topic is listed only with rows",
                PreferencesScreenMapper.rowsFor(topic, AutoSettings()).isNotEmpty()
            )
        }
    }

    @Test
    fun everyRowCarriesItsOwnTopic() {
        val rows = PreferencesScreenMapper.rows(AutoSettings())
        PreferencesScreenMapper.topics(AutoSettings()).forEach { topic ->
            assertTrue(
                PreferencesScreenMapper.rowsFor(topic, AutoSettings()).all { it.topic == topic }
            )
        }
        assertEquals(13, rows.count { it.topic in PreferenceTopic.entries })
    }

    // --- A finite value set is presented as options (spec: auto/preferences — A value is chosen, never only cycled) ---

    /**
     * Spec case: A value is chosen, never only cycled.
     */
    @Test
    fun aChoiceRowOpensItsOptions() {
        val rows = PreferencesScreenMapper.rows(AutoSettings(darkMode = "OFF"), styles).associateBy { it.key }

        val darkMode = rows.getValue(PreferencesScreenMapper.KEY_DARK_MODE)
        assertEquals(PreferenceControl.CHOICE, darkMode.control)
        assertEquals(listOf("ON", "AUTOMATIC", "OFF"), darkMode.choices.map { it.value })
        assertEquals(listOf("On", "Automatic", "Off"), darkMode.choices.map { it.label })
        assertEquals("the value in force is marked", "OFF", darkMode.currentValue)

        val renderMode = rows.getValue("renderMode")
        assertEquals(PreferenceControl.CHOICE, renderMode.control)
        assertEquals(listOf("TILES", "DIRECT"), renderMode.choices.map { it.value })

        val style = rows.getValue("styleSheet")
        assertEquals(PreferenceControl.CHOICE, style.control)
        assertEquals(styles, style.choices.map { it.value })
        // A style carries its own name: its label is its key (spec: map-styles).
        assertEquals(styles, style.choices.map { it.label })
    }

    @Test
    fun aBooleanRowStaysASwitch() {
        val rows = PreferencesScreenMapper.rows(AutoSettings()).associateBy { it.key }
        listOf("followMode", "autoZoomEnabled", "laneHintsEnabled", "voiceGuidanceEnabled", "announceGpsState")
            .forEach { key ->
                assertEquals(key, PreferenceControl.SWITCH, rows.getValue(key).control)
                assertTrue(key, rows.getValue(key).choices.isEmpty())
            }
    }

    @Test
    fun theDedicatedPickersKeepTheirOwnScreens() {
        val rows = PreferencesScreenMapper.rows(AutoSettings()).associateBy { it.key }
        listOf(
            PreferencesScreenMapper.KEY_OVERSPEED_DELTA,
            PreferencesScreenMapper.KEY_ROUTING_ANCHOR,
            PreferencesScreenMapper.KEY_FREE_DRIVING_ANCHOR
        ).forEach { key ->
            assertEquals(key, PreferenceControl.PICKER, rows.getValue(key).control)
        }
    }

    @Test
    fun aStyleRowCarriesTheCurrentStyleEvenWhenTheLiveListDropsIt() {
        val row = PreferencesScreenMapper
            .rows(AutoSettings(styleSheet = "stale"), listOf("cycle", "standard"))
            .first { it.key == "styleSheet" }

        assertTrue("the value in force is always an option", row.choices.any { it.value == "stale" })
        assertEquals("stale", row.currentValue)
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
    fun toggle_leavesAChoiceRowUnchanged() {
        val before = AutoSettings(darkMode = "ON", renderMode = "TILES", styleSheet = "standard")

        assertEquals(before, PreferencesScreenMapper.toggle(before, PreferencesScreenMapper.KEY_DARK_MODE))
        assertEquals(before, PreferencesScreenMapper.toggle(before, "renderMode"))
        assertEquals(before, PreferencesScreenMapper.toggle(before, "styleSheet", styles))
    }

    @Test
    fun toggle_unknownKeyReturnsUnchanged() {
        val settings = AutoSettings()

        assertEquals(settings, PreferencesScreenMapper.toggle(settings, "nope"))
    }

    // --- Choosing a value (spec: auto/preferences — "A value is chosen, never only cycled") ---

    @Test
    fun choose_setsTheExactValue() {
        assertEquals("OFF", PreferencesScreenMapper.choose(AutoSettings(darkMode = "ON"), "darkMode", "OFF").darkMode)
        assertEquals("ON", PreferencesScreenMapper.choose(AutoSettings(darkMode = "OFF"), "darkMode", "ON").darkMode)
        assertEquals(
            "DIRECT",
            PreferencesScreenMapper.choose(AutoSettings(renderMode = "TILES"), "renderMode", "DIRECT").renderMode
        )
        assertEquals(
            "winter-sports",
            PreferencesScreenMapper.choose(AutoSettings(styleSheet = "standard"), "styleSheet", "winter-sports", styles)
                .styleSheet
        )
        assertEquals(
            "cycle",
            PreferencesScreenMapper.choose(AutoSettings(styleSheet = "standard"), "styleSheet", "cycle", styles)
                .styleSheet
        )
    }

    @Test
    fun choose_refusesAValueTheRowDoesNotOffer() {
        val before = AutoSettings(darkMode = "ON", styleSheet = "standard", renderMode = "TILES")

        assertEquals("an unknown dark mode", before, PreferencesScreenMapper.choose(before, "darkMode", "DIM"))
        assertEquals("a style the device does not offer", before,
            PreferencesScreenMapper.choose(before, "styleSheet", "gone", styles))
        assertEquals("a switch's key", before, PreferencesScreenMapper.choose(before, "followMode", "on"))
        assertEquals("an unknown key", before, PreferencesScreenMapper.choose(before, "nope", "ON"))
    }
}