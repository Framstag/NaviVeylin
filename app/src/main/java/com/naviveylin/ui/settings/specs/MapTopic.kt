package com.naviveylin.ui.settings.specs

import com.naviveylin.ui.settings.SettingSpec

/**
 * The rows of the "Karte" topic (spec: `settings-taxonomy` — "The settings root holds six topics and
 * no deeper level").
 *
 * Empty in the foundations group: the map rows (map style, the POI-symbol switch, the map-source
 * link) move here with their renderer in tasks.md group 3, one row per setting. The table itself is
 * declared now so the root order and the registry's invariants ([com.naviveylin.ui.settings.SettingsRegistry],
 * `SettingsRegistryTest`) exist before the first row does.
 */
internal val mapTopicRows: List<SettingSpec> = emptyList()
