package com.naviveylin.ui.settings

import androidx.annotation.StringRes

/**
 * The six settings topics, in the order the root page shows them (spec: `settings-taxonomy` — "The
 * settings root holds six topics and no deeper level"). Declaration order *is* the root order, so a
 * topic is added by declaring it here and giving it a table in [SettingsRegistry], never by sorting
 * names at render time.
 */
enum class SettingsTopic {
    MAP,
    NAVIGATION_AND_ROUTE,
    HINTS_AND_LANGUAGE,
    MAPS_AND_DATA,
    ADVANCED,
    ABOUT
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
