package com.naviveylin.ui.settings

import androidx.annotation.StringRes
import com.naviveylin.R

/**
 * The six settings topics, in the order the root page shows them (spec: `settings-taxonomy` — "The
 * settings root holds six topics and no deeper level"). Declaration order *is* the root order, so a
 * topic is added by declaring it here and giving it a table in [SettingsRegistry], never by sorting
 * names at render time.
 */
enum class SettingsTopic(@param:StringRes val titleRes: Int) {
    MAP(R.string.settings_topic_map),
    NAVIGATION_AND_ROUTE(R.string.settings_topic_navigation),
    HINTS_AND_LANGUAGE(R.string.settings_topic_hints),
    MAPS_AND_DATA(R.string.settings_topic_maps_data),
    ADVANCED(R.string.settings_topic_advanced),
    ABOUT(R.string.settings_topic_about)
}

/**
 * One titled group of rows on a topic page (spec: `settings-taxonomy` — "A new setting joins an
 * existing topic"): a topic whose rows outgrow one screen grows sections, never a page below the
 * root.
 */
data class SettingsSection(
    val topic: SettingsTopic,
    @param:StringRes val titleRes: Int
)
