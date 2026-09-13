package com.naviveylin.core

/**
 * Car-relevant subset of the app's persisted settings, shared with the
 * Android Auto process.
 *
 * Mirrors the fields of the phone app's [AppSettings] (see `:app`
 * `SettingsStorage`); [darkMode] and [renderMode] are carried as `String`
 * values matching the app enums' names so `:core` stays free of `:app` types.
 */
data class AutoSettings(
    val followMode: Boolean = false,
    val autoZoomEnabled: Boolean = true,
    val freeFormNorthUp: Boolean = true,
    val navNorthUp: Boolean = false,
    val darkMode: String = "AUTOMATIC",
    val laneHintsEnabled: Boolean = true,
    val renderMode: String = "TILES",
    val styleSheet: String = "standard",
    /**
     * Overspeed warning delta (km/h, 0-30, default 5): the speed badge warns
     * when `current >= max + delta`. Same global value as the phone app's
     * setting, so both surfaces warn identically.
     */
    val overspeedWarningDeltaKmh: Int = 5,
    /**
     * Vehicle anchor preset for routing (see [com.naviveylin.core.VehicleAnchorPosition]);
     * persisted as the preset's stable id string, default `"center"`. During
     * turn-by-turn navigation the follow-mode map keeps the vehicle at this
     * screen fraction instead of the surface center. Same global value as the
     * phone app's setting.
     */
    val routingAnchorId: String = com.naviveylin.core.VehicleAnchorPosition.DEFAULT.id,
    /**
     * Vehicle anchor preset for free driving (see [com.naviveylin.core.VehicleAnchorPosition]);
     * persisted as the preset's stable id string, default `"center"`. During
     * free driving the follow-mode map keeps the vehicle at this screen
     * fraction. Same global value as the phone app's setting.
     */
    val freeDrivingAnchorId: String = com.naviveylin.core.VehicleAnchorPosition.DEFAULT.id
)
