package com.naviveylin.ui.settings.specs

import com.naviveylin.R
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.DarkModePreference
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
 * postfix").
 *
 * Dark mode is a `CHOICE`, not a `SELECT`: three values fit inline, listed on the row with the
 * current one marked (design D5; spec: `settings-surface` — "Dark mode lists its three values"). The
 * stored preference is one both surfaces read and either may edit — the car maps it through
 * `AutoSettingsMapping` — so the row is `SHARED` and carries no surface icon (spec:
 * `settings-taxonomy` — "A surface-specific row is marked, a shared row is not"). Its
 * ambient-light sensitivity is phone-only: the value decides how the phone's own sensor enters dark
 * mode, so nothing on the car reads it (spec: `settings-taxonomy` — "Every row declares the surface
 * it belongs to"). That row only decides the presentation while dark mode is Automatic, so it
 * declares the documents it applies to instead of being drawn greyed (design D1; spec:
 * `settings-surface` — "An inapplicable section is omitted").
 *
 * A new row joins this table rather than a composable of its own, so the surface renders
 * it from data (design D1; tasks.md 3.1, 3.2).
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
        payload = SettingControlPayload.Choice(
            choices = listOf(
                SettingChoice(key = DarkModePreference.OFF.name, labelRes = R.string.off),
                SettingChoice(key = DarkModePreference.ON.name, labelRes = R.string.on),
                SettingChoice(key = DarkModePreference.AUTOMATIC.name, labelRes = R.string.automatic)
            )
        ),
        read = { settings -> settings.darkMode.name },
        write = { settings, value -> settings.copy(darkMode = DarkModePreference.valueOf(value)) }
    ),
    SettingSpec(
        key = "ambient-light-sensitivity",
        topic = SettingsTopic.MAP,
        section = mapPresentationSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.ambient_light_dark_mode,
        helpShortRes = R.string.settings_help_ambient_light,
        control = SettingControl.CHOICE,
        payload = SettingControlPayload.Choice(
            choices = listOf(
                SettingChoice(key = AmbientLightSensitivity.OFF.name, labelRes = R.string.off),
                SettingChoice(
                    key = AmbientLightSensitivity.HIGH.name,
                    labelRes = R.string.sensitivity_high
                ),
                SettingChoice(
                    key = AmbientLightSensitivity.MEDIUM.name,
                    labelRes = R.string.sensitivity_medium
                ),
                SettingChoice(
                    key = AmbientLightSensitivity.LOW.name,
                    labelRes = R.string.sensitivity_low
                )
            )
        ),
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        // The sensor may only choose the presentation while the preference is Automatic: with
        // On or Off forced there is nothing for it to decide, so the row is omitted as
        // inapplicable rather than shown greyed (design D1).
        enabled = { settings -> settings.darkMode == DarkModePreference.AUTOMATIC },
        read = { settings -> settings.ambientLightSensitivity.name },
        write = { settings, value ->
            settings.copy(ambientLightSensitivity = AmbientLightSensitivity.valueOf(value))
        }
    )
)
