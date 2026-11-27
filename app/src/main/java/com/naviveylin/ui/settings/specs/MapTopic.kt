package com.naviveylin.ui.settings.specs

import com.naviveylin.R
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.AppSettings
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.RenderMode
import com.naviveylin.ui.settings.SettingChoice
import com.naviveylin.ui.settings.SettingControl
import com.naviveylin.ui.settings.SettingControlPayload
import com.naviveylin.ui.settings.SettingScope
import com.naviveylin.ui.settings.SettingSpec
import com.naviveylin.ui.settings.SettingsSection
import com.naviveylin.ui.settings.SettingsTopic

/** The rows that decide what the map draws (spec: `map-styles`). */
internal val mapPresentationSection =
    SettingsSection(SettingsTopic.MAP, R.string.settings_section_map_presentation)

/** The rows that decide how the map answers the user's hand (spec: `map-long-press-timeout`). */
internal val mapInteractionSection =
    SettingsSection(SettingsTopic.MAP, R.string.settings_section_interaction)

/**
 * The options of a value with two to four stored values, keyed by what the settings file holds so a
 * row never renames a persisted value (spec: `settings-taxonomy` — "A setting appears once").
 */
private val darkModeChoices = listOf(
    SettingChoice(key = DarkModePreference.ON.name, labelRes = R.string.on),
    SettingChoice(key = DarkModePreference.OFF.name, labelRes = R.string.off),
    SettingChoice(key = DarkModePreference.AUTOMATIC.name, labelRes = R.string.automatic)
)

private val ambientSensitivityChoices = listOf(
    SettingChoice(key = AmbientLightSensitivity.OFF.name, labelRes = R.string.off),
    SettingChoice(key = AmbientLightSensitivity.HIGH.name, labelRes = R.string.sensitivity_high),
    SettingChoice(key = AmbientLightSensitivity.MEDIUM.name, labelRes = R.string.sensitivity_medium),
    SettingChoice(key = AmbientLightSensitivity.LOW.name, labelRes = R.string.sensitivity_low)
)

private val renderModeChoices = listOf(
    SettingChoice(key = RenderMode.TILES.name, labelRes = R.string.tile_cache),
    SettingChoice(key = RenderMode.DIRECT.name, labelRes = R.string.direct)
)

/**
 * The rows of the "Karte" topic (spec: `settings-taxonomy`, spec: `map-styles`).
 *
 * Map style is more than four values, so it is a `SELECT` row: the surface opens its options on a
 * surface of their own and marks the current one (design D5). The options come from
 * [BundledMapStyles.USER_SELECTABLE], a literal list kept in sync with the packaged top-level
 * `*.oss` files by `AssetCopierTest.bundlesEveryTopLevelStyleSheet` — the basemap's internal stylesheet left out, the same exclusion
 * the quick sheet's style list applies (task 3.1; spec: `map-styles` — "Basemap stylesheet not
 * offered") — and each option's label is its own key, because a style's display name is its file
 * name without the postfix (spec: `map-styles` — "Style display name is file name without
 * postfix"). A new row joins this table rather than a composable of its own, so the surface renders
 * it from data (design D1; tasks.md 3.1).
 */
internal val mapTopicRows: List<SettingSpec> = listOf(
    SettingSpec(
        key = "map-style",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.map_style,
        helpShortRes = R.string.settings_help_map_style,
        control = SettingControl.SELECT,
        payload = SettingControlPayload.Select(
            choices = BundledMapStyles.USER_SELECTABLE.map { style -> SettingChoice(key = style) }
        ),
        read = { settings -> settings.styleSheet },
        write = { settings, value -> settings.copy(styleSheet = value) }
    ),
    SettingSpec(
        key = "poi-symbols",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.prefer_symbol_icons,
        helpShortRes = R.string.settings_help_poi_symbols,
        control = SettingControl.SWITCH,
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        read = { settings -> if (settings.preferSymbolPoiIcons) "on" else "off" },
        write = { settings, value -> settings.copy(preferSymbolPoiIcons = value == "on") }
    ),
    SettingSpec(
        key = "dark-mode",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.dark_mode,
        helpShortRes = R.string.settings_help_dark_mode,
        control = SettingControl.CHOICE,
        payload = SettingControlPayload.Choice(choices = darkModeChoices),
        read = { settings -> settings.darkMode.name },
        write = { settings, value ->
            settings.copy(darkMode = darkModeOf(value))
        }
    ),
    SettingSpec(
        key = "ambient-sensitivity",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.ambient_light_dark_mode,
        helpShortRes = R.string.settings_help_ambient_sensitivity,
        control = SettingControl.CHOICE,
        payload = SettingControlPayload.Choice(choices = ambientSensitivityChoices),
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        // The sensitivity is what dark mode falls back to, so it applies only while dark mode is
        // Automatic: under Off or On the row is dropped rather than greyed, and a section it leaves
        // empty goes with it (spec: `settings-surface` — "An inapplicable section is omitted").
        enabled = { settings -> settings.darkMode == DarkModePreference.AUTOMATIC },
        read = { settings -> settings.ambientLightSensitivity.name },
        write = { settings, value ->
            settings.copy(ambientLightSensitivity = sensitivityOf(value))
        }
    ),
    SettingSpec(
        key = "render-mode",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.rendering,
        helpShortRes = R.string.settings_help_render_mode,
        control = SettingControl.CHOICE,
        payload = SettingControlPayload.Choice(choices = renderModeChoices),
        read = { settings -> settings.renderMode.name },
        write = { settings, value -> settings.copy(renderMode = renderModeOf(value)) }
    ),
    SettingSpec(
        key = "keep-screen-on",
        topic = SettingsTopic.MAP,
        section = mapInteractionSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.keep_screen_on,
        helpShortRes = R.string.settings_help_keep_screen_on,
        control = SettingControl.SWITCH,
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        read = { settings -> if (settings.keepScreenOn) "on" else "off" },
        write = { settings, value -> settings.copy(keepScreenOn = value == "on") }
    ),
    SettingSpec(
        key = "long-press-timeout",
        topic = SettingsTopic.MAP,
        section = mapInteractionSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.long_press_timeout,
        helpShortRes = R.string.settings_help_long_press,
        control = SettingControl.SLIDER,
        // 250-1000 ms in 50 ms steps, the range and default the setting carries (spec:
        // `map-long-press-timeout` — "The long-press timeout is a persisted setting").
        payload = SettingControlPayload.Slider(
            min = AppSettings.LONG_PRESS_TIMEOUT_MIN_MS,
            max = AppSettings.LONG_PRESS_TIMEOUT_MAX_MS,
            step = AppSettings.LONG_PRESS_TIMEOUT_STEP_MS
        ),
        valueFormatRes = R.string.long_press_timeout_value,
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        // A hand-edited file can hold a value outside the range; the reader clamps it, so the row
        // shows the duration the gesture really uses.
        read = { settings -> settings.effectiveLongPressTimeoutMs.toString() },
        write = { settings, value ->
            settings.copy(
                longPressTimeoutMs = value.toIntOrNull()
                    ?.coerceIn(AppSettings.LONG_PRESS_TIMEOUT_MIN_MS, AppSettings.LONG_PRESS_TIMEOUT_MAX_MS)
                    ?: settings.longPressTimeoutMs
            )
        }
    )
)

/**
 * An unknown stored value falls back to the default rather than failing the write: a hand-edited or
 * restored settings file must not stop the row from saving the value the user just picked.
 */
private fun darkModeOf(value: String): DarkModePreference =
    DarkModePreference.entries.firstOrNull { it.name == value } ?: DarkModePreference.AUTOMATIC

private fun sensitivityOf(value: String): AmbientLightSensitivity =
    AmbientLightSensitivity.entries.firstOrNull { it.name == value } ?: AmbientLightSensitivity.OFF

private fun renderModeOf(value: String): RenderMode =
    RenderMode.entries.firstOrNull { it.name == value } ?: RenderMode.TILES
