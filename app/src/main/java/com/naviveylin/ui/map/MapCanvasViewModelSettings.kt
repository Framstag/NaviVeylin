package com.naviveylin.ui.map

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.core.BundledMapStyles.DEFAULT_STYLE_NAME
import com.naviveylin.core.MapStyleLoadReporter
import com.naviveylin.core.stringResolver
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.clampLongPressTimeoutMs
import com.naviveylin.data.RenderMode
import com.naviveylin.core.ResultMarkings
import com.naviveylin.core.SearchRowIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Toggle keep-screen-on during navigation. */
fun MapCanvasViewModel.onToggleKeepScreenOn(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(keepScreenOn = enabled)
    viewModelScope.launch {
        settingsController.update { it.copy(keepScreenOn = enabled) }
    }
}

/** Toggle lane hints on/off. */
fun MapCanvasViewModel.onToggleLaneHints(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(laneHintsEnabled = enabled)
    viewModelScope.launch {
        settingsController.update { it.copy(laneHintsEnabled = enabled) }
    }
}

/**
 * Toggle the phone's spoken guidance (spec: `voice-guidance` — The phone offers both switches in its
 * settings sheet). Persisted per surface, so the car's own switch keeps its value.
 */
fun MapCanvasViewModel.onToggleVoiceGuidance(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(voiceGuidanceEnabled = enabled)
    viewModelScope.launch {
        settingsController.update { it.copy(voiceGuidanceEnabled = enabled) }
    }
}

/**
 * Toggle GPS announcements (spec: `voice-guidance` — GPS announcements are one shared switch, off by
 * default). One value for both surfaces, so the car preferences read what this writes.
 */
fun MapCanvasViewModel.onToggleAnnounceGpsState(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(announceGpsState = enabled)
    viewModelScope.launch {
        settingsController.update { it.copy(announceGpsState = enabled) }
    }
}

/**
 * Set the overspeed warning delta (km/h, 0-30). Applies to the speed
 * widget immediately and persists; the same global value drives the
 * Android Auto badge (spec: map-speed-widget — Overspeed warning color;
 * location-options-ui — Overspeed warning delta control).
 */
fun MapCanvasViewModel.onSetOverspeedWarningDelta(deltaKmh: Int) {
    _uiState.value = _uiState.value.copy(overspeedWarningDeltaKmh = deltaKmh)
    viewModelScope.launch {
        settingsController.update { it.copy(overspeedWarningDeltaKmh = deltaKmh) }
    }
}

/**
 * Set the map's long-press duration (ms, 250-1000 in 50 ms steps). Applies to the next gesture
 * immediately and persists; the map's gesture handler reads it instead of a literal
 * (spec: `map-long-press-timeout` — The long-press timeout is a persisted setting; `location-options-ui`
 * — Long-press timeout control). The value is clamped to the offered range here, so a caller that computes
 * it cannot install a timeout the sheet cannot show.
 */
fun MapCanvasViewModel.onSetLongPressTimeoutMs(timeoutMs: Int) {
    val clamped = clampLongPressTimeoutMs(timeoutMs)
    _uiState.value = _uiState.value.copy(longPressTimeoutMs = clamped)
    viewModelScope.launch {
        settingsController.update { it.copy(longPressTimeoutMs = clamped) }
    }
}

/**
 * Set the car's usual maximum speed (km/h). Persists immediately; the value reaches routing at
 * the next calculation and never restarts one already in flight (spec: `routing-max-speed` —
 * A speed change does not restart a calculation).
 */
fun MapCanvasViewModel.onSetCarMaxSpeed(kmh: Int) {
    _uiState.value = _uiState.value.copy(carMaxSpeedKmh = kmh)
    viewModelScope.launch {
        settingsController.update { it.copy(carMaxSpeedKmh = kmh) }
    }
}

/** Set the bicycle's usual maximum speed (km/h) and persist it (spec: `routing-max-speed`). */
fun MapCanvasViewModel.onSetBicycleMaxSpeed(kmh: Int) {
    _uiState.value = _uiState.value.copy(bicycleMaxSpeedKmh = kmh)
    viewModelScope.launch {
        settingsController.update { it.copy(bicycleMaxSpeedKmh = kmh) }
    }
}

/** Set the pedestrian's usual maximum speed (km/h) and persist it (spec: `routing-max-speed`). */
fun MapCanvasViewModel.onSetFootMaxSpeed(kmh: Int) {
    _uiState.value = _uiState.value.copy(footMaxSpeedKmh = kmh)
    viewModelScope.launch {
        settingsController.update { it.copy(footMaxSpeedKmh = kmh) }
    }
}

/**
 * Set whether routes avoid toll roads and persist it (spec: `routing-avoidance` — The phone edits the
 * choices and the car reads them). The value reaches routing at the next calculation; it never restarts
 * one in flight, for the same reason a speed change does not (spec: `routing-max-speed`).
 */
fun MapCanvasViewModel.onSetAvoidTolls(avoid: Boolean) {
    _uiState.value = _uiState.value.copy(avoidTolls = avoid)
    viewModelScope.launch {
        settingsController.update { it.copy(avoidTolls = avoid) }
    }
}

/** Set whether routes avoid ferry routes and persist it (spec: `routing-avoidance`). */
fun MapCanvasViewModel.onSetAvoidFerries(avoid: Boolean) {
    _uiState.value = _uiState.value.copy(avoidFerries = avoid)
    viewModelScope.launch {
        settingsController.update { it.copy(avoidFerries = avoid) }
    }
}

/** Set whether routes avoid unpaved roads and persist it (spec: `routing-avoidance`). */
fun MapCanvasViewModel.onSetAvoidUnpaved(avoid: Boolean) {
    _uiState.value = _uiState.value.copy(avoidUnpaved = avoid)
    viewModelScope.launch {
        settingsController.update { it.copy(avoidUnpaved = avoid) }
    }
}

/**
 * Set the map rendering mode (tile-cached vs direct). Persists the
 * selection and applies it immediately: the renderer is switched and a
 * forced full re-render clears any tiles/buffers from the other mode.
 */
fun MapCanvasViewModel.onSetRenderMode(mode: RenderMode) {
    _uiState.value = _uiState.value.copy(renderMode = mode)
    viewModelScope.launch {
        settingsController.update { it.copy(renderMode = mode) }
    }
    mapRenderer?.renderMode = mode
    mapRenderer?.invalidateStyle()
}

/** Feed the environment dimming signal (system night mode) from composition. */
fun MapCanvasViewModel.setEnvironmentDark(dark: Boolean) {
    darkModeController.setEnvironmentDark(dark)
}

/** Feed the ambient light sensor classification (null = inactive). */
fun MapCanvasViewModel.setSensorDark(dark: Boolean?) {
    darkModeController.setSensorDark(dark)
}

/** Set the dark mode preference (On / Off / Automatic); persists via controller. */
fun MapCanvasViewModel.onSetDarkModePreference(preference: DarkModePreference) {
    darkModePreferenceSetByUser = true
    darkModeController.setPreference(preference)
    _uiState.value = _uiState.value.copy(darkModePreference = preference)
}

/** Set the ambient light sensor sensitivity; persists via controller. */
fun MapCanvasViewModel.onSetAmbientLightSensitivity(sensitivity: AmbientLightSensitivity) {
    ambientSensitivitySetByUser = true
    darkModeController.setSensorSensitivity(sensitivity)
    _uiState.value = _uiState.value.copy(ambientLightSensitivity = sensitivity)
}

internal data class AppliedMapStyle(val styleName: String?, val daylight: Boolean)

/**
 * What justifies one application of the style/flag pair through [ensureMapStyle]. Only the
 * re-push reasons force native work for a pair that already matches the recorded one.
 */
internal enum class MapStyleApplyReason(
    /** Re-push the flag even when its value equals the recorded one. */
    val forceFlag: Boolean,
    /** Load the stylesheet even when the recorded one already is the requested one. */
    val forceLoad: Boolean = false,
) {
    /** A re-observation of the current state (init settings load, panel rebuild). */
    RE_OBSERVED(false),

    /** The user picked a style in the picker; applied even before a map opens. */
    STYLE_SWITCH(false),

    /**
     * A map database has just been opened: `SetStyleFlag` was a no-op before it existed, and
     * the freshly opened database carries no style config of its own, so both halves are
     * re-applied.
     */
    STARTUP_DB_READY(true, true),

    /** The first rendered frame after [STARTUP_DB_READY]: re-push the flag once. */
    FIRST_RENDERED_FRAME(true),
}

/**
 * Resolved presentation the stylesheet `daylight` flag has to carry — `true` means the **dark**
 * variant is wanted, so `pushDaylight` unsets the flag for it (the name is historical; the value
 * is the dark presentation, as in `AppliedMapStyle.daylight` and `CarDaylightApplier.apply`).
 */
internal fun MapCanvasViewModel.resolvedDaylight(): Boolean = darkModeController.isDarkPresentation.value

/**
 * The one entry point that applies the style/flag pair to the native client (spec: map-styles -
 * One stylesheet load per active style and flag set): a request whose pair equals the recorded
 * one performs no native call, unless [reason] says the native side may have dropped earlier
 * work. Pass `null` for [styleName] on a request that concerns only the flag.
 *
 * A rejected load keeps the previous style and clears the recorded stylesheet, so the next
 * request retries it. [allowDefaultFallback] is used on the startup path only: a persisted style
 * whose stylesheet cannot be loaded falls back to `standard`, so the first map display is never
 * empty (spec: map-styles - "Persisted style fails at startup").
 */
internal suspend fun MapCanvasViewModel.ensureMapStyle(
    styleName: String?,
    daylight: Boolean,
    reason: MapStyleApplyReason,
    allowDefaultFallback: Boolean = false,
): Boolean {
    // The flag's record is the shared applier's, not this view model's: the mini map widget writes
    // the same client, and a per-writer record would reload the stylesheet set for a value the
    // client already carries (guidelines/MapStyling.md §15 — one record per client and value). The
    // *re-render* below stays this surface's own (see pushDaylight).
    if (reason.forceFlag || appliedMapStyle?.daylight != daylight || MapDaylightApplier.needsPush(client, daylight)) {
        pushDaylight(daylight, force = reason.forceFlag)
    }
    if (styleName != null && (reason.forceLoad || appliedMapStyle?.styleName != styleName)) {
        return loadMapStyle(styleName, daylight, allowDefaultFallback)
    }
    return true
}

/**
 * Pushes the `daylight` flag through [MapDaylightApplier]; the native side reloads the active
 * stylesheet with it (`guidelines/MapStyling.md` §15). The applier records the value only after the
 * call returned successfully, so a later request — from this view model or from the mini map
 * widget — re-pushes a value the native side never took, and no writer reloads a value the client
 * already carries.
 */
internal fun MapCanvasViewModel.pushDaylight(daylight: Boolean, force: Boolean = false) {
    // What *this* view model last drew is its own fact: a value another writer (the mini map widget)
    // already applied makes the applier answer UNCHANGED, and the main map's tiles and frame are
    // still its own to invalidate — otherwise the map keeps the other variant until the next pan.
    val changed = appliedMapStyle?.daylight != daylight
    val outcome = MapDaylightApplier.push(client, daylight, force)
    when (outcome) {
        MapDaylightPush.ERROR -> return
        MapDaylightPush.FAILED ->
            // The flag change reloads the active stylesheet; when that load is rejected the previously
            // active style and variant stay in effect and the failure is reported (spec: map-styles —
            // "Style flag change fails"). Nothing was applied, so neither this surface's record nor a
            // re-render follows: the next request retries, and it must see `changed` then.
            rejectedVariantMessage(context, client)?.let { showSnackbar(it) }
        MapDaylightPush.UNCHANGED, MapDaylightPush.APPLIED -> Unit
    }
    if (outcome == MapDaylightPush.FAILED) return
    appliedMapStyle = AppliedMapStyle(appliedMapStyle?.styleName, daylight)
    // Both halves re-render this surface: a value it had not drawn, and a *forced* re-push — the
    // `STARTUP_DB_READY` / `FIRST_RENDERED_FRAME` reasons exist because the native side may have dropped
    // the earlier push, so the map has to render again under it.
    if (changed || force) {
        mapRenderer?.invalidateStyle()
        invalidateRowIcons()
    }
}

/**
 * Load the map style [name] on the native database thread (blocking — runs
 * off the main thread) and re-render on success. On failure the native side
 * keeps the previous style and the failure is reported once through
 * [MapStyleLoadReporter]; the recorded stylesheet is cleared so the next request
 * retries it.
 *
 * [allowDefaultFallback] is used on the startup path only: a persisted style
 * whose stylesheet cannot be loaded falls back to the default `standard`
 * style, so the first map display is never empty (spec: map-styles —
 * "Persisted style fails at startup").
 */
internal suspend fun MapCanvasViewModel.loadMapStyle(
    name: String,
    daylight: Boolean,
    allowDefaultFallback: Boolean,
): Boolean {
    val ok = withContext(defaultDispatcher) {
        try {
            client.loadStyleSheet(name)
        } catch (e: Exception) {
            Log.e(MapCanvasViewModel.TAG, "loadStyleSheet failed for '$name'", e)
            false
        }
    }
    if (ok) {
        appliedMapStyle = AppliedMapStyle(name, daylight)
        // `loadStyleSheet` hands the load the enabled flags, so the variant this load installed is
        // the one the applier must remember (§15).
        MapDaylightApplier.record(client, daylight)
        mapRenderer?.invalidateStyle()
        invalidateRowIcons()
        return true
    }

    appliedMapStyle = AppliedMapStyle(null, daylight)
    Log.e(MapCanvasViewModel.TAG, "loadStyleSheet returned false for '$name' — previous style kept")
    reportStyleLoadFailure(name)

    if (allowDefaultFallback && name != DEFAULT_STYLE_NAME) {
        Log.w(MapCanvasViewModel.TAG, "Persisted style '$name' unavailable — falling back to '$DEFAULT_STYLE_NAME'")
        return loadMapStyle(DEFAULT_STYLE_NAME, daylight, allowDefaultFallback)
    }

    return false
}

/**
 * Reports one failed stylesheet load through the shared seam: a diagnostics
 * entry plus the non-blocking message shown next to the map (spec:
 * map-styles). Called once per load attempt; a report produced while no map
 * surface is visible stays in the UI state and is shown when the map is next
 * composed. The seam itself is [rejectedLoadMessage], shared with the mini map
 * widget's load path (`guidelines/MapStyling.md` §15a — one place).
 */

/** The native side's active stylesheet, or `null` when the client cannot answer. */
internal fun MapCanvasViewModel.activeStyleSheet(): String? = try {
    client.getActiveStyleSheet()
} catch (e: Exception) {
    Log.e(MapCanvasViewModel.TAG, "getActiveStyleSheet failed", e)
    null
}

internal fun MapCanvasViewModel.reportStyleLoadFailure(requestedStyle: String) {
    rejectedLoadMessage(context, client, requestedStyle, activeStyleSheet())?.let { showSnackbar(it) }
}

/**
 * Hand the symbol/icon preference to the native client, once per value. The render path reads it
 * when it builds its parameters, so a change applies to the next frame without a stylesheet
 * reload. With [invalidate] the tile cache is cleared and a full re-render is forced, so no tile
 * or frame from the previous preference is displayed (spec: `map-styles` — Preference is applied
 * at start and on change).
 */
internal fun MapCanvasViewModel.applyPreferSymbolIcons(preferSymbolIcons: Boolean, invalidate: Boolean) {
    if (preferSymbolNative == preferSymbolIcons) {
        return
    }
    preferSymbolNative = preferSymbolIcons
    try {
        client.setPreferSymbolIcons(preferSymbolIcons)
    } catch (e: Exception) {
        Log.e(MapCanvasViewModel.TAG, "setPreferSymbolIcons failed", e)
    }
    // The rows draw the same artwork the map draws, so the preference moves them too
    // (spec: search-result-row-icon — Preference change reaches the row without a restart).
    invalidateRowIcons()
    if (invalidate) {
        mapRenderer?.invalidateStyle()
    }
}

/**
 * Drop the result rows' resolved type-icon artwork — the icon cache and the composites drawn
 * with it. Called wherever the stylesheet that artwork comes from changes: a loaded style, the
 * day/night reload (which reloads the active stylesheet), and the icon-versus-symbol preference.
 * A missed call shows the previous style's icon until the next one (spec:
 * `search-result-row-icon` — Resolved artwork is reused and follows the style; change
 * `search-result-row-content`, design D7).
 */
internal fun MapCanvasViewModel.invalidateRowIcons() {
    SearchRowIcons.clear()
    ResultMarkings.clear()
}

/**
 * Toggle the symbol-first rendering of style entries that carry both a raster icon and a symbol;
 * persists it and applies it to the visible map immediately (spec: `map-styles`).
 */
fun MapCanvasViewModel.onSetPreferSymbolPoiIcons(preferSymbolIcons: Boolean) {
    preferSymbolSetByUser = true
    _uiState.value = _uiState.value.copy(preferSymbolPoiIcons = preferSymbolIcons)
    viewModelScope.launch {
        settingsController.update { it.copy(preferSymbolPoiIcons = preferSymbolIcons) }
    }
    applyPreferSymbolIcons(preferSymbolIcons, invalidate = true)
}

/** Select and persist a map style; applies it to the renderer immediately. */
fun MapCanvasViewModel.onStyleSheetSelected(name: String) {
    _uiState.value = _uiState.value.copy(styleSheet = name)
    viewModelScope.launch {
        settingsController.update { it.copy(styleSheet = name) }
    }
    viewModelScope.launch {
        ensureMapStyle(name, resolvedDaylight(), MapStyleApplyReason.STYLE_SWITCH)
    }
}

/** Toggle auto-zoom on/off. */
fun MapCanvasViewModel.onToggleAutoZoom(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(autoZoomEnabled = enabled)
    viewModelScope.launch {
        settingsController.update { it.copy(autoZoomEnabled = enabled) }
    }
    if (enabled) {
        // Reset suspension state so zoom adjusts immediately
        setAutoZoomSuspended(false)
        lastSpeedBandIndex = -1
    }
}

/**
 * The resolved dark presentation: theme state and the native stylesheet flag.
 */
internal fun MapCanvasViewModel.wireDarkPresentation() {
    // Resolved dark presentation drives theme state + the native style sheet flag. The flag
    // reloads the style variant natively; loading a stylesheet is not part of this path, so the
    // style name is deliberately left out of the request (spec: dark-mode - Unchanged
    // presentation reloads nothing; spec: map-styles - One stylesheet load per active style and
    // flag set).
    viewModelScope.launch {
        darkModeController.isDarkPresentation
            .collect { dark ->
                _uiState.value = _uiState.value.copy(isDarkPresentation = dark)
                ensureMapStyle(null, dark, MapStyleApplyReason.RE_OBSERVED)
            }
    }
}

/**
 * Loads the persisted settings once, off the main thread.
 */
internal fun MapCanvasViewModel.wirePersistedSettings() {
    // Load persisted settings
    viewModelScope.launch {
        val settings = settingsController.load()
        // A user change that landed while this read was in flight wins: the read is a suspension
        // point, so the persisted value it returns may already be the one the user replaced
        // (spec: `dark-mode` — A user change outranks an in-flight settings read).
        if (!darkModePreferenceSetByUser) {
            darkModeController.restorePreference(settings.darkMode)
        }
        if (!ambientSensitivitySetByUser) {
            darkModeController.restoreSensorSensitivity(settings.ambientLightSensitivity)
        }
        val darkModePreference = if (darkModePreferenceSetByUser) {
            _uiState.value.darkModePreference
        } else {
            settings.darkMode
        }
        val ambientLightSensitivity = if (ambientSensitivitySetByUser) {
            _uiState.value.ambientLightSensitivity
        } else {
            settings.ambientLightSensitivity
        }
        val preferSymbolPoiIcons = if (preferSymbolSetByUser) {
            _uiState.value.preferSymbolPoiIcons
        } else {
            settings.preferSymbolPoiIcons
        }
        // The orientations are a suspension-point read like the dark-mode fields above: a compass long
        // press or a location-options change that landed while this read was in flight wins, and a field
        // the user did not change is still restored from disk
        // (spec: `compass-settings` — A user orientation choice outranks an in-flight settings read).
        val navNorthUp = if (navNorthUpSetByUser) {
            _uiState.value.navNorthUp
        } else {
            settings.navNorthUp
        }
        val freeFormNorthUp = if (freeFormNorthUpSetByUser) {
            _uiState.value.freeFormNorthUp
        } else {
            settings.freeFormNorthUp
        }
        _uiState.value = _uiState.value.copy(
            // followMode is deliberately NOT restored: the app always starts
            // in BROWSE (spec: map-modes — always browse on start); free
            // drive is per-session intent entered via the mode toggle.
            autoZoomEnabled = settings.autoZoomEnabled,
            freeFormNorthUp = freeFormNorthUp,
            navNorthUp = navNorthUp,
            keepScreenOn = settings.keepScreenOn,
            darkModePreference = darkModePreference,
            ambientLightSensitivity = ambientLightSensitivity,
            laneHintsEnabled = settings.laneHintsEnabled,
            voiceGuidanceEnabled = settings.voiceGuidanceEnabled,
            announceGpsState = settings.announceGpsState,
            renderMode = settings.renderMode,
            overspeedWarningDeltaKmh = settings.overspeedWarningDeltaKmh,
            longPressTimeoutMs = settings.effectiveLongPressTimeoutMs,
            carMaxSpeedKmh = settings.carMaxSpeedKmh,
            bicycleMaxSpeedKmh = settings.bicycleMaxSpeedKmh,
            footMaxSpeedKmh = settings.footMaxSpeedKmh,
            avoidTolls = settings.avoidTolls,
            avoidFerries = settings.avoidFerries,
            avoidUnpaved = settings.avoidUnpaved,
            styleSheet = settings.styleSheet,
            preferSymbolPoiIcons = preferSymbolPoiIcons
        )
        routingAnchor = VehicleAnchorPosition.fromId(settings.routingAnchorId)
        freeDrivingAnchor = VehicleAnchorPosition.fromId(settings.freeDrivingAnchorId)
        _uiState.value = _uiState.value.copy(
            routingAnchor = routingAnchor,
            freeDrivingAnchor = freeDrivingAnchor,
            activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
        )
        // Resolve the persisted preset into the visible map area.
        publishResolvedAnchor()
        // If initMap already ran before this load finished (fast user flow),
        // re-apply the persisted style — initMap may have used the default.
        if (mapRenderer != null) {
            ensureMapStyle(
                settings.styleSheet,
                resolvedDaylight(),
                MapStyleApplyReason.RE_OBSERVED
            )
        }
        // The preference needs no stylesheet reload — the render path reads it per frame — so it
        // only has to reach the client before the frame that follows. If the first frame already
        // ran with the default, invalidate so it re-renders with the persisted value.
        if (!preferSymbolSetByUser) {
            applyPreferSymbolIcons(preferSymbolPoiIcons,
                                   invalidate = mapRenderer != null)
        }
    }
}

/**
 * Loads the bundled map styles for the picker.
 */
internal fun MapCanvasViewModel.wireMapStyles() {
    // Load the bundled map styles for the picker (off main; a directory
    // scan on the device stylesheet dir). basemap-render is the basemap's
    // internal stylesheet, not a user-selectable map style — exclude it
    // (spec: map-styles).
    viewModelScope.launch {
        val styles = withContext(defaultDispatcher) {
            try {
                client.getAvailableStyleSheets()
                    .filterNot { it == BundledMapStyles.BASEMAP_STYLE_NAME }
            } catch (e: Exception) {
                Log.w(MapCanvasViewModel.TAG, "getAvailableStyleSheets failed", e)
                emptyList()
            }
        }
        _uiState.value = _uiState.value.copy(availableStyleSheets = styles)
    }
}
