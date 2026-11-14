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

    /** One topic's rows; the deepest page the surface has (spec: `settings-taxonomy`). */
    data class Topic(val topic: SettingsTopic) : SettingsSurfaceState
}
