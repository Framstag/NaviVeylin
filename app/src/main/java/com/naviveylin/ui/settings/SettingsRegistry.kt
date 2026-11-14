package com.naviveylin.ui.settings

import com.naviveylin.ui.settings.specs.aboutTopicRows
import com.naviveylin.ui.settings.specs.advancedTopicRows
import com.naviveylin.ui.settings.specs.hintsTopicRows
import com.naviveylin.ui.settings.specs.mapTopicRows
import com.naviveylin.ui.settings.specs.mapsDataTopicRows
import com.naviveylin.ui.settings.specs.navigationTopicRows

/** One topic page: the topic, and the rows it shows under its section titles. */
data class SettingsTopicTable(
    val topic: SettingsTopic,
    val rows: List<SettingSpec>
)

/**
 * The declared settings, such as they are (design D1; spec: `settings-taxonomy`).
 *
 * The tables live one file per topic under [com.naviveylin.ui.settings.specs] and this object only
 * fixes the root order and reads them. Both surfaces render from here, so a row that is missing, a
 * key that appears twice or a car-only row in a phone topic is a test failure rather than a
 * divergence between two composables.
 */
object SettingsRegistry {
    /**
     * The root page: exactly the six topics, in the order the spec names them.
     *
     * The list is written out rather than derived from a set, so re-ordering the root is an edit a
     * reviewer sees (spec: `settings-taxonomy` — "The settings root holds six topics and no deeper
     * level").
     */
    val root: List<SettingsTopicTable> = listOf(
        SettingsTopicTable(SettingsTopic.MAP, mapTopicRows),
        SettingsTopicTable(SettingsTopic.NAVIGATION_AND_ROUTE, navigationTopicRows),
        SettingsTopicTable(SettingsTopic.HINTS_AND_LANGUAGE, hintsTopicRows),
        SettingsTopicTable(SettingsTopic.MAPS_AND_DATA, mapsDataTopicRows),
        SettingsTopicTable(SettingsTopic.ADVANCED, advancedTopicRows),
        SettingsTopicTable(SettingsTopic.ABOUT, aboutTopicRows)
    )

    /** Every declared row, in root order, so a key's uniqueness is checked over the whole surface. */
    val all: List<SettingSpec> = root.flatMap { it.rows }

    /** The rows of one topic, in declaration order; empty for a topic whose rows have not landed. */
    fun rowsOf(topic: SettingsTopic): List<SettingSpec> =
        root.firstOrNull { it.topic == topic }?.rows.orEmpty()

    /**
     * The rows a surface offers: its own and the shared ones, never the other surface's.
     *
     * Both renderers read this projection rather than filtering the registry themselves, so
     * "the car shows no phone-only row" is one function's rule instead of two screens' habit
     * (spec: `settings-taxonomy` — "A phone-only row is absent from the car").
     */
    fun rowsFor(scope: SettingScope, rows: List<SettingSpec> = all): List<SettingSpec> =
        rows.filter { it.scope == scope || it.scope == SettingScope.SHARED }
}
