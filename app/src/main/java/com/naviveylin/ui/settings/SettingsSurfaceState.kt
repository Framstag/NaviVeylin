package com.naviveylin.ui.settings

/**
 * Which page of the settings surface is showing (design D2). The surface is one state, not a flag
 * per host: [SettingsController.surfaceState] holds it, so "one settings surface at a time" is
 * structural and a host that opens the root closes whatever topic was showing.
 */
sealed interface SettingsSurfaceState {
    /** Nothing of the surface is on screen. */
    data object Closed : SettingsSurfaceState

    /** The six topics, and no setting row. */
    data object Root : SettingsSurfaceState

    /** One topic's rows (spec: `settings-taxonomy`). */
    data class Topic(val topic: SettingsTopic) : SettingsSurfaceState

    /**
     * One row's options, listed on a page of their own: the values a `SELECT` row offers (design D5;
     * spec: `settings-surface` — "A finite value set is presented as options, never only by
     * cycling"). It always belongs to the [topic] it was opened from, which is the page back returns
     * to, so a selection surface is never a place the surface can be left stranded in.
     */
    data class Options(val topic: SettingsTopic, val rowKey: String) : SettingsSurfaceState
}
