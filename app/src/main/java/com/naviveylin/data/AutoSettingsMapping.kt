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
    renderMode = renderMode.name,
    styleSheet = styleSheet,
    overspeedWarningDeltaKmh = overspeedWarningDeltaKmh,
    // The car sees its OWN voice switch (spec: `voice-guidance` — The voice switch is
    // per surface), while the GPS announcement switch is one value both surfaces
    // share (spec: `voice-guidance` — GPS announcements are one shared switch).
    voiceGuidanceEnabled = autoVoiceGuidanceEnabled,
    announceGpsState = announceGpsState,
    // Per-surface anchors: the car sees its OWN value, falling back to the phone's
    // until an anchor is chosen on the car (spec: auto-map-layout — "Car falls back
    // to the phone value until configured on the car").
    routingAnchorId = autoRoutingAnchorId ?: routingAnchorId,
    freeDrivingAnchorId = autoFreeDrivingAnchorId ?: freeDrivingAnchorId
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
    renderMode = RenderMode.valueOf(renderMode),
    styleSheet = styleSheet,
    overspeedWarningDeltaKmh = overspeedWarningDeltaKmh,
    // Only the car's own voice field is written back; the phone's voice switch stays
    // untouched (spec: settings-persistence — The announcement fields declare
    // per-surface and shared ownership). The GPS field is shared, so the car's write
    // is the value the phone reads.
    autoVoiceGuidanceEnabled = voiceGuidanceEnabled,
    announceGpsState = announceGpsState,
    // The car's own anchor fields are deliberately NOT written here: they are set
    // only by an explicit anchor selection on the car
    // (AutoSettingsProvider.saveCarAnchor). A generic settings write (dark mode,
    // zoom, ...) must not freeze a value the car is merely inheriting from the
    // phone (spec: auto-map-layout — "Car falls back to the phone value until
    // configured on the car").
)
