package com.naviveylin.auto

import com.naviveylin.core.AutoSettings
import com.naviveylin.core.BundledMapStyles

/**
 * A single preference row on the [PreferencesScreen]: a stable [key] used to
 * toggle the value, a display [title], and the current value as text.
 */
data class PreferenceRow(
    val key: String,
    val title: String,
    val valueText: String
)

/**
 * Pure functions for building [PreferencesScreen] content.
 * Extracted for testability.
 */
object PreferencesScreenMapper {

    private const val KEY_FOLLOW_MODE = "followMode"
    private const val KEY_AUTO_ZOOM = "autoZoomEnabled"
    private const val KEY_FREE_FORM_NORTH_UP = "freeFormNorthUp"
    private const val KEY_NAV_NORTH_UP = "navNorthUp"
    private const val KEY_DARK_MODE = "darkMode"
    private const val KEY_LANE_HINTS = "laneHintsEnabled"
    private const val KEY_RENDER_MODE = "renderMode"
    private const val KEY_STYLE_SHEET = "styleSheet"

    /**
     * Fallback style list (the bundled libosmscout top-level `*.oss` set,
     * sorted); the production preferences screen passes the live device list.
     */
    val DEFAULT_STYLES: List<String> = BundledMapStyles.ALL

    /**
     * Build the preference rows for [settings]. Phone-only settings
     * (e.g. keep-screen-on) are intentionally absent.
     */
    fun rows(settings: AutoSettings): List<PreferenceRow> = listOf(
        PreferenceRow(KEY_FOLLOW_MODE, "Follow mode", onOff(settings.followMode)),
        PreferenceRow(KEY_AUTO_ZOOM, "Auto-zoom", onOff(settings.autoZoomEnabled)),
        PreferenceRow(KEY_FREE_FORM_NORTH_UP, "North-up (browse)", onOff(settings.freeFormNorthUp)),
        PreferenceRow(KEY_NAV_NORTH_UP, "North-up (navigation)", onOff(settings.navNorthUp)),
        PreferenceRow(KEY_DARK_MODE, "Dark mode", darkModeText(settings.darkMode)),
        PreferenceRow(KEY_LANE_HINTS, "Lane hints", onOff(settings.laneHintsEnabled)),
        PreferenceRow(KEY_RENDER_MODE, "Render mode", renderModeText(settings.renderMode)),
        PreferenceRow(KEY_STYLE_SHEET, "Map style", settings.styleSheet)
    )

    /**
     * Return [settings] with the preference identified by [key] toggled to its
     * next value. Boolean preferences flip; [darkMode] cycles
     * ON → OFF → AUTOMATIC, [renderMode] cycles TILES → DIRECT and the map
     * style cycles through [styles] (defaults to the bundled set, wrapping).
     * Unknown keys return the settings unchanged.
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
        KEY_DARK_MODE -> settings.copy(darkMode = nextDarkMode(settings.darkMode))
        KEY_LANE_HINTS -> settings.copy(laneHintsEnabled = !settings.laneHintsEnabled)
        KEY_RENDER_MODE -> settings.copy(renderMode = nextRenderMode(settings.renderMode))
        KEY_STYLE_SHEET -> settings.copy(styleSheet = nextStyle(settings.styleSheet, styles))
        else -> settings
    }

    private fun nextStyle(current: String, styles: List<String>): String {
        if (styles.isEmpty()) return current
        val index = styles.indexOf(current)
        if (index < 0) return styles.first()
        return styles[(index + 1) % styles.size]
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

    private fun nextDarkMode(value: String): String = when (value) {
        "ON" -> "OFF"
        "OFF" -> "AUTOMATIC"
        else -> "ON"
    }

    private fun nextRenderMode(value: String): String = when (value) {
        "TILES" -> "DIRECT"
        else -> "TILES"
    }
}
