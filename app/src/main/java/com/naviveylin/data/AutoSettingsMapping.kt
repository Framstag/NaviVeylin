package com.naviveylin.data

import com.naviveylin.core.AutoSettings

/**
 * Mapping between the phone app's persisted [AppSettings] and the shared
 * [AutoSettings] consumed by the Android Auto process.
 *
 * The car only edits a subset of settings; [keepScreenOn] is phone-only and
 * never crosses the boundary.
 */

/** Map the car-relevant subset of [AppSettings] to the shared [AutoSettings]. */
internal fun AppSettings.toAutoSettings(): AutoSettings = AutoSettings(
    followMode = followMode,
    autoZoomEnabled = autoZoomEnabled,
    freeFormNorthUp = freeFormNorthUp,
    navNorthUp = navNorthUp,
    darkMode = darkMode.name,
    laneHintsEnabled = laneHintsEnabled,
    renderMode = renderMode.name
)

/**
 * Apply [AutoSettings] onto [current] [AppSettings], preserving fields the car
 * does not edit (e.g. [keepScreenOn]).
 */
internal fun AutoSettings.toAppSettings(current: AppSettings): AppSettings = current.copy(
    followMode = followMode,
    autoZoomEnabled = autoZoomEnabled,
    freeFormNorthUp = freeFormNorthUp,
    navNorthUp = navNorthUp,
    darkMode = DarkModePreference.valueOf(darkMode),
    laneHintsEnabled = laneHintsEnabled,
    renderMode = RenderMode.valueOf(renderMode)
)
