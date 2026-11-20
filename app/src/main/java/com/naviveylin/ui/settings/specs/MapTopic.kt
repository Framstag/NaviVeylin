package com.naviveylin.ui.settings.specs

import com.naviveylin.R
import com.naviveylin.core.BundledMapStyles
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
    )
)
