package com.naviveylin.auto

import androidx.annotation.StringRes
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.core.VehicleAnchorPosition

/**
 * The settings topics the car's Preferences root lists (spec: `auto/preferences` — "The car's
 * preferences are grouped into sub-screens"). The names are the phone's topic names, resolved from
 * this module's own resources, so the two surfaces group a driver's settings the same way
 * (spec: `settings-taxonomy`).
 */
enum class PreferenceTopic(@param:StringRes val titleRes: Int) {
    MAP(R.string.settings_topic_map),
    NAVIGATION_AND_ROUTE(R.string.settings_topic_navigation),
    HINTS_AND_LANGUAGE(R.string.settings_topic_hints)
}

/**
 * How a row edits its value (design D5/D6): a [SWITCH] flips a boolean in place, a [CHOICE] opens
 * the row's finite value set as an options screen, and a [PICKER] opens a dedicated screen the car
 * already has (the overspeed delta and the two vehicle-anchor pickers).
 */
enum class PreferenceControl {
    SWITCH,
    CHOICE,
    PICKER
}

/** One value a [PreferenceControl.CHOICE] row offers: what is persisted, and how it reads. */
data class PreferenceChoice(
    val value: String,
    val label: String
)

/**
 * A single preference row on a topic screen of the [PreferencesScreen]: a stable [key] that
 * identifies the setting, a display [title], the current value as text, the [topic] it belongs to
 * and the [control] that edits it.
 */
data class PreferenceRow(
    val key: String,
    val title: String,
    val valueText: String,
    val topic: PreferenceTopic,
    val control: PreferenceControl = PreferenceControl.SWITCH,
    val choices: List<PreferenceChoice> = emptyList(),
    /** The value as it is stored, for a [PreferenceControl.CHOICE] row's options screen. */
    val currentValue: String = ""
)

/**
 * Pure functions for building the car's Preferences screens. Extracted for testability: the two
 * screens render these values and own no setting of their own.
 */
object PreferencesScreenMapper {

    private const val KEY_FOLLOW_MODE = "followMode"
    private const val KEY_AUTO_ZOOM = "autoZoomEnabled"
    private const val KEY_FREE_FORM_NORTH_UP = "freeFormNorthUp"
    private const val KEY_NAV_NORTH_UP = "navNorthUp"

    /** Dark mode preference row key (public so the screen can report changes). */
    const val KEY_DARK_MODE = "darkMode"
    private const val KEY_LANE_HINTS = "laneHintsEnabled"
    private const val KEY_RENDER_MODE = "renderMode"
    private const val KEY_STYLE_SHEET = "styleSheet"

    /**
     * Overspeed warning delta row: selecting it opens the value picker
     * (handled by [PreferencesScreen]); the row itself is not tapped to
     * cycle. Public so the screen can route the click.
     */
    const val KEY_OVERSPEED_DELTA = "overspeedWarningDeltaKmh"

    /** Routing vehicle anchor row: opens the position picker (handled by [PreferencesScreen]). */
    const val KEY_ROUTING_ANCHOR = "routingAnchor"

    /** Free-driving vehicle anchor row: opens the position picker (handled by [PreferencesScreen]). */
    const val KEY_FREE_DRIVING_ANCHOR = "freeDrivingAnchor"

    /**
     * The car's own spoken-guidance switch (spec: `auto/preferences` — The car offers the announcement
     * switches; spec: `voice-guidance` — The voice switch is per surface). Flips in place.
     */
    const val KEY_VOICE_GUIDANCE = "voiceGuidanceEnabled"

    /**
     * The shared GPS-announcement switch (spec: `auto/preferences` — The car offers the announcement
     * switches). One value both surfaces edit, off by default. Flips in place.
     */
    const val KEY_GPS_ANNOUNCEMENTS = "announceGpsState"

    /** The dark-mode values, in the order the options screen shows them. */
    private val DARK_MODE_CHOICES = listOf(
        PreferenceChoice("ON", "On"),
        PreferenceChoice("AUTOMATIC", "Automatic"),
        PreferenceChoice("OFF", "Off")
    )

    /** The render-mode values, in the order the options screen shows them. */
    private val RENDER_MODE_CHOICES = listOf(
        PreferenceChoice("TILES", "Tiles"),
        PreferenceChoice("DIRECT", "Direct")
    )

    /**
     * Fallback style list (the bundled libosmscout top-level `*.oss` set minus
     * the basemap's internal stylesheet, sorted); the production preferences
     * screen passes the live device list.
     */
    val DEFAULT_STYLES: List<String> = BundledMapStyles.USER_SELECTABLE

    /**
     * Build every preference row of [settings], each carrying the [PreferenceTopic] it belongs to.
     * Phone-only settings (e.g. keep-screen-on) are intentionally absent.
     *
     * A value with a finite set of more than one option is a [PreferenceControl.CHOICE] row with
     * its [PreferenceChoice]s, never a row whose repeated taps walk the values (design D5; spec:
     * `auto/preferences` — "A value is chosen, never only cycled"). The overspeed delta and the two
     * anchors keep the dedicated pickers the car already has.
     */
    fun rows(
        settings: AutoSettings,
        styles: List<String> = DEFAULT_STYLES
    ): List<PreferenceRow> = listOf(
        // Karte
        PreferenceRow(KEY_DARK_MODE, "Dark mode", darkModeText(settings.darkMode), PreferenceTopic.MAP,
            PreferenceControl.CHOICE, darkModeChoices(), settings.darkMode),
        PreferenceRow(KEY_RENDER_MODE, "Render mode", renderModeText(settings.renderMode), PreferenceTopic.MAP,
            PreferenceControl.CHOICE, renderModeChoices(), settings.renderMode),
        PreferenceRow(KEY_STYLE_SHEET, "Map style", settings.styleSheet, PreferenceTopic.MAP,
            PreferenceControl.CHOICE, styleChoices(styles, settings.styleSheet), settings.styleSheet),
        PreferenceRow(KEY_FREE_FORM_NORTH_UP, "North-up (browse)", onOff(settings.freeFormNorthUp),
            PreferenceTopic.MAP),
        // Navigation & Route
        PreferenceRow(KEY_FOLLOW_MODE, "Follow mode", onOff(settings.followMode),
            PreferenceTopic.NAVIGATION_AND_ROUTE),
        PreferenceRow(KEY_AUTO_ZOOM, "Auto-zoom", onOff(settings.autoZoomEnabled),
            PreferenceTopic.NAVIGATION_AND_ROUTE),
        PreferenceRow(KEY_NAV_NORTH_UP, "North-up (navigation)", onOff(settings.navNorthUp),
            PreferenceTopic.NAVIGATION_AND_ROUTE),
        PreferenceRow(KEY_OVERSPEED_DELTA, "Overspeed warning", "${settings.overspeedWarningDeltaKmh} km/h",
            PreferenceTopic.NAVIGATION_AND_ROUTE, PreferenceControl.PICKER),
        PreferenceRow(KEY_ROUTING_ANCHOR, "Vehicle position (navigation)",
            VehicleAnchorPosition.fromId(settings.routingAnchorId).label,
            PreferenceTopic.NAVIGATION_AND_ROUTE, PreferenceControl.PICKER),
        PreferenceRow(KEY_FREE_DRIVING_ANCHOR, "Vehicle position (free driving)",
            VehicleAnchorPosition.fromId(settings.freeDrivingAnchorId).label,
            PreferenceTopic.NAVIGATION_AND_ROUTE, PreferenceControl.PICKER),
        // Hinweise & Sprache
        PreferenceRow(KEY_LANE_HINTS, "Lane hints", onOff(settings.laneHintsEnabled),
            PreferenceTopic.HINTS_AND_LANGUAGE),
        // The car's own voice switch, and the GPS switch both surfaces share. The phone's own voice
        // value has no row here: this surface does not speak by it
        // (spec: `auto/preferences` — The car offers the announcement switches).
        PreferenceRow(KEY_VOICE_GUIDANCE, "Voice guidance", onOff(settings.voiceGuidanceEnabled),
            PreferenceTopic.HINTS_AND_LANGUAGE),
        PreferenceRow(KEY_GPS_ANNOUNCEMENTS, "GPS announcements", onOff(settings.announceGpsState),
            PreferenceTopic.HINTS_AND_LANGUAGE)
    )

    /**
     * The topics the root lists: those holding at least one car-usable row, in [PreferenceTopic]
     * declaration order (spec: `auto/preferences` — "The root lists the topics"). A topic with no
     * row is absent rather than an empty screen.
     */
    fun topics(
        settings: AutoSettings,
        styles: List<String> = DEFAULT_STYLES
    ): List<PreferenceTopic> {
        val present = rows(settings, styles).map { it.topic }.toSet()
        return PreferenceTopic.entries.filter { it in present }
    }

    /**
     * The rows of one topic, in declaration order (spec: `auto/preferences` — "A topic opens its
     * rows"). One row list per screen, so a driver never scrolls a mixed list.
     */
    fun rowsFor(
        topic: PreferenceTopic,
        settings: AutoSettings,
        styles: List<String> = DEFAULT_STYLES
    ): List<PreferenceRow> = rows(settings, styles).filter { it.topic == topic }

    /**
     * Set the preference identified by [key] to exactly [value] — what a choice row's options
     * screen does with the value the driver picked. A value the row does not offer, or a key that
     * is not a choice row, returns [settings] unchanged rather than storing an unknown value.
     */
    fun choose(
        settings: AutoSettings,
        key: String,
        value: String,
        styles: List<String> = DEFAULT_STYLES
    ): AutoSettings {
        val row = rows(settings, styles).firstOrNull { it.key == key } ?: return settings
        if (row.choices.none { it.value == value }) return settings
        return when (key) {
            KEY_DARK_MODE -> settings.copy(darkMode = value)
            KEY_RENDER_MODE -> settings.copy(renderMode = value)
            KEY_STYLE_SHEET -> if (styles.contains(value)) settings.copy(styleSheet = value) else settings
            else -> settings
        }
    }

    /**
     * Return [settings] with the preference identified by [key] moved on by one — kept for the
     * boolean rows a tap flips. A choice or picker row comes back unchanged: its value is picked
     * from its options, never walked by taps (spec: `auto/preferences` — "A value is chosen, never
     * only cycled"). Unknown keys return the settings unchanged.
     */
    fun toggle(
        settings: AutoSettings,
        key: String,
        styles: List<String> = DEFAULT_STYLES
    ): AutoSettings = when (key) {
        KEY_FOLLOW_MODE -> settings.copy(followMode = !settings.followMode)
        KEY_AUTO_ZOOM -> settings.copy(autoZoomEnabled = !settings.autoZoomEnabled)
        KEY_FREE_FORM_NORTH_UP -> settings.copy(freeFormNorthUp = !settings.freeFormNorthUp)
        KEY_NAV_NORTH_UP -> settings.copy(navNorthUp = !settings.navNorthUp)
        KEY_LANE_HINTS -> settings.copy(laneHintsEnabled = !settings.laneHintsEnabled)
        KEY_VOICE_GUIDANCE -> settings.copy(voiceGuidanceEnabled = !settings.voiceGuidanceEnabled)
        KEY_GPS_ANNOUNCEMENTS -> settings.copy(announceGpsState = !settings.announceGpsState)
        else -> settings
    }

    private fun darkModeChoices(): List<PreferenceChoice> = DARK_MODE_CHOICES

    private fun renderModeChoices(): List<PreferenceChoice> = RENDER_MODE_CHOICES

    /**
     * The map styles the device offers, each labelled by its own key (a style's display name is its
     * file name without the postfix, so it carries its own name). The current style is included even
     * when the live list does not carry it, so the options screen always marks the value in force.
     */
    private fun styleChoices(styles: List<String>, current: String): List<PreferenceChoice> {
        val values = if (styles.isEmpty() || current in styles) styles else listOf(current) + styles
        return values.map { PreferenceChoice(it, it) }
    }

    private fun onOff(value: Boolean): String = if (value) "On" else "Off"

    private fun darkModeText(value: String): String = when (value) {
        "ON" -> "On"
        "OFF" -> "Off"
        else -> "Automatic"
    }

    private fun renderModeText(value: String): String = when (value) {
        "TILES" -> "Tiles"
        else -> "Direct"
    }
}