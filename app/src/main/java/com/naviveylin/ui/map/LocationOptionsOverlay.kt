package com.naviveylin.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.AppSettings
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.RenderMode
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Location options button that opens a full-width Material 3 bottom sheet for
 * toggling map behaviour. The sheet shows a header naming the current map
 * state (Browse / Free drive / Navigation) and that state's options only —
 * mode switching is performed exclusively through the right-column mode
 * toggle button, never from config (spec: location-options-ui).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationOptionsOverlay(
    mode: MapMode,
    freeFormNorthUp: Boolean = true,
    onSetFreeFormOrientation: (Boolean) -> Unit = {},
    navNorthUp: Boolean = false,
    onSetNavOrientation: (Boolean) -> Unit = {},
    autoZoomEnabled: Boolean = true,
    onToggleAutoZoom: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = true,
    onToggleKeepScreenOn: (Boolean) -> Unit = {},
    darkModePreference: DarkModePreference = DarkModePreference.AUTOMATIC,
    onSetDarkModePreference: (DarkModePreference) -> Unit = {},
    ambientLightSensitivity: AmbientLightSensitivity = AmbientLightSensitivity.OFF,
    onSetAmbientLightSensitivity: (AmbientLightSensitivity) -> Unit = {},
    laneHintsEnabled: Boolean = true,
    onToggleLaneHints: (Boolean) -> Unit = {},
    // The phone's spoken-guidance switch and the shared GPS-announcement switch
    // (spec: `voice-guidance` — The phone offers both switches in its settings sheet).
    voiceGuidanceEnabled: Boolean = true,
    onToggleVoiceGuidance: (Boolean) -> Unit = {},
    announceGpsState: Boolean = false,
    onToggleAnnounceGpsState: (Boolean) -> Unit = {},
    overspeedWarningDeltaKmh: Int = 5,
    onSetOverspeedWarningDelta: (Int) -> Unit = {},
    // Long-press timeout: REQUIRED (no defaults), for the same reason as the max-speed and anchor
    // rows below. The row renders in EVERY mode, so an optional callback would let a call site
    // compile into a slider whose selection is silently dropped
    // (spec: location-options-ui — Long-press timeout control).
    longPressTimeoutMs: Int,
    onSetLongPressTimeoutMs: (Int) -> Unit,
    renderMode: RenderMode = RenderMode.TILES,
    onSetRenderMode: (RenderMode) -> Unit = {},
    availableStyles: List<String> = emptyList(),
    styleSheet: String = "standard",
    onSetStyleSheet: (String) -> Unit = {},
    /**
     * Symbol/icon preference: draw the symbol of a style entry that carries both renderings. Sits
     * with the style picker, because it selects how the active style's icon entries resolve
     * (spec: `map-styles` — Phone control for the preference).
     */
    preferSymbolPoiIcons: Boolean = false,
    onSetPreferSymbolPoiIcons: (Boolean) -> Unit = {},
    /**
     * The quick sheet's last row: close this sheet and open the settings surface at its root
     * (spec: `settings-surface` — "The sheet's entry row opens the surface"). Both steps are one
     * callback, so a host cannot open the surface with the sheet still under it.
     */
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = modifier) {
        FilledTonalIconButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .size(48.dp)
                .shadow(3.dp, RoundedCornerShape(16.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.location_options),
                tint = if (mode != MapMode.BROWSE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            LocationOptionsSheetContent(
                mode = mode,
                freeFormNorthUp = freeFormNorthUp,
                onSetFreeFormOrientation = onSetFreeFormOrientation,
                navNorthUp = navNorthUp,
                onSetNavOrientation = onSetNavOrientation,
                autoZoomEnabled = autoZoomEnabled,
                onToggleAutoZoom = onToggleAutoZoom,
                keepScreenOn = keepScreenOn,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                darkModePreference = darkModePreference,
                onSetDarkModePreference = onSetDarkModePreference,
                ambientLightSensitivity = ambientLightSensitivity,
                onSetAmbientLightSensitivity = onSetAmbientLightSensitivity,
                laneHintsEnabled = laneHintsEnabled,
                onToggleLaneHints = onToggleLaneHints,
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                onToggleVoiceGuidance = onToggleVoiceGuidance,
                announceGpsState = announceGpsState,
                onToggleAnnounceGpsState = onToggleAnnounceGpsState,
                overspeedWarningDeltaKmh = overspeedWarningDeltaKmh,
                onSetOverspeedWarningDelta = onSetOverspeedWarningDelta,
                longPressTimeoutMs = longPressTimeoutMs,
                onSetLongPressTimeoutMs = onSetLongPressTimeoutMs,
                renderMode = renderMode,
                onSetRenderMode = onSetRenderMode,
                availableStyles = availableStyles,
                styleSheet = styleSheet,
                onSetStyleSheet = onSetStyleSheet,
                preferSymbolPoiIcons = preferSymbolPoiIcons,
                onSetPreferSymbolPoiIcons = onSetPreferSymbolPoiIcons,
                // The sheet closes before the surface opens: the surface is a full-height overlay
                // and the two are never on screen at once (spec: `settings-surface` — "Three entry
                // points, one surface at a time").
                onOpenSettings = {
                    showSheet = false
                    onOpenSettings()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationOptionsSheetContent(
    mode: MapMode,
    freeFormNorthUp: Boolean,
    onSetFreeFormOrientation: (Boolean) -> Unit,
    navNorthUp: Boolean,
    onSetNavOrientation: (Boolean) -> Unit,
    autoZoomEnabled: Boolean,
    onToggleAutoZoom: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    darkModePreference: DarkModePreference,
    onSetDarkModePreference: (DarkModePreference) -> Unit,
    ambientLightSensitivity: AmbientLightSensitivity,
    onSetAmbientLightSensitivity: (AmbientLightSensitivity) -> Unit,
    laneHintsEnabled: Boolean,
    onToggleLaneHints: (Boolean) -> Unit,
    voiceGuidanceEnabled: Boolean,
    onToggleVoiceGuidance: (Boolean) -> Unit,
    announceGpsState: Boolean,
    onToggleAnnounceGpsState: (Boolean) -> Unit,
    overspeedWarningDeltaKmh: Int,
    onSetOverspeedWarningDelta: (Int) -> Unit,
    longPressTimeoutMs: Int,
    onSetLongPressTimeoutMs: (Int) -> Unit,
    renderMode: RenderMode,
    onSetRenderMode: (RenderMode) -> Unit,
    availableStyles: List<String>,
    styleSheet: String,
    onSetStyleSheet: (String) -> Unit,
    preferSymbolPoiIcons: Boolean,
    onSetPreferSymbolPoiIcons: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    // FREE_DRIVE and NAVIGATION share the driving config (auto-zoom +
    // orientation); BROWSE has its own orientation only.
    val driving = mode == MapMode.FREE_DRIVE || mode == MapMode.NAVIGATION
    val currentNorthUp = if (driving) navNorthUp else freeFormNorthUp
    val onSetOrientation = if (driving) onSetNavOrientation else onSetFreeFormOrientation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Mode header — names the current state; no mode switch here
        // (spec: location-options-ui — mode header without mode switch).
        Text(
            text = when (mode) {
                MapMode.BROWSE -> stringResource(R.string.mode_browse)
                MapMode.FREE_DRIVE -> stringResource(R.string.mode_free_drive)
                MapMode.NAVIGATION -> stringResource(R.string.mode_navigation)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (driving) {
            // Driving section: auto-zoom + orientation (spec:
            // location-options-ui — auto-zoom visible in driving states).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.auto_zoom),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoZoomEnabled,
                    onCheckedChange = { enabled ->
                        onToggleAutoZoom(enabled)
                    }
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.orientation),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                OrientationOption(
                    label = stringResource(R.string.follow_direction),
                    selected = !currentNorthUp,
                    onClick = { onSetOrientation(false) }
                )
                OrientationOption(
                    label = stringResource(R.string.north_up),
                    selected = currentNorthUp,
                    onClick = { onSetOrientation(true) }
                )
            }
        } else {
            // Browse section: orientation only (spec: location-options-ui —
            // browse orientation controls).
            Text(
                text = stringResource(R.string.orientation),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                OrientationOption(
                    label = stringResource(R.string.north_up),
                    selected = currentNorthUp,
                    onClick = { onSetOrientation(true) }
                )
                OrientationOption(
                    label = stringResource(R.string.free_rotation),
                    selected = !currentNorthUp,
                    onClick = { onSetOrientation(false) }
                )
            }
        }

        // Keep screen on toggle — always visible
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.keep_screen_on),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = keepScreenOn,
                onCheckedChange = { enabled ->
                    onToggleKeepScreenOn(enabled)
                }
            )
        }

        // Lane hints toggle — always visible
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.lane_instructions),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = laneHintsEnabled,
                onCheckedChange = { enabled ->
                    onToggleLaneHints(enabled)
                }
            )
        }

        // Spoken guidance: the phone's own voice switch and the GPS-announcement switch both
        // surfaces share (spec: `voice-guidance` — The phone offers both switches in its settings
        // sheet). Always visible, like the lane-hint switch above them.
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        AnnouncementSwitchRow(
            label = stringResource(R.string.voice_guidance_switch),
            checked = voiceGuidanceEnabled,
            testTag = "voiceGuidanceSwitch",
            onToggle = onToggleVoiceGuidance
        )

        AnnouncementSwitchRow(
            label = stringResource(R.string.gps_announcements_switch),
            checked = announceGpsState,
            testTag = "announceGpsSwitch",
            onToggle = onToggleAnnounceGpsState
        )

        // Overspeed warning delta — always visible, 0-30 km/h, 1 km/h
        // precision (spec: location-options-ui — Overspeed warning delta
        // control: slider over the integer range 0-30 with the current value
        // shown; the value is global and shared with Android Auto).
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.overspeed_warning_delta),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.overspeed_delta_value, overspeedWarningDeltaKmh),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = overspeedWarningDeltaKmh.toFloat(),
            onValueChange = { value ->
                onSetOverspeedWarningDelta(value.roundToInt())
            },
            valueRange = 0f..30f,
            steps = 29,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overspeedDeltaSlider")
        )

        // Long-press timeout — always visible, 250-1000 ms in 50 ms steps (spec:
        // location-options-ui — Long-press timeout control; map-long-press-timeout — The
        // long-press timeout is a persisted setting). The map's gesture handler reads this
        // value instead of a literal, and the sheet is its only editor.
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.long_press_timeout),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.long_press_timeout_value, longPressTimeoutMs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = longPressTimeoutMs.toFloat(),
            onValueChange = { value ->
                onSetLongPressTimeoutMs(value.roundToInt())
            },
            valueRange = AppSettings.LONG_PRESS_TIMEOUT_MIN_MS.toFloat()..
                AppSettings.LONG_PRESS_TIMEOUT_MAX_MS.toFloat(),
            steps = (AppSettings.LONG_PRESS_TIMEOUT_MAX_MS - AppSettings.LONG_PRESS_TIMEOUT_MIN_MS) /
                AppSettings.LONG_PRESS_TIMEOUT_STEP_MS - 1,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("longPressTimeoutSlider")
        )

        // Dark mode section — always visible
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Text(
            text = stringResource(R.string.dark_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        Column(modifier = Modifier.selectableGroup()) {
            OrientationOption(
                label = stringResource(R.string.on),
                selected = darkModePreference == DarkModePreference.ON,
                onClick = { onSetDarkModePreference(DarkModePreference.ON) }
            )
            OrientationOption(
                label = stringResource(R.string.off),
                selected = darkModePreference == DarkModePreference.OFF,
                onClick = { onSetDarkModePreference(DarkModePreference.OFF) }
            )
            OrientationOption(
                label = stringResource(R.string.automatic),
                selected = darkModePreference == DarkModePreference.AUTOMATIC,
                onClick = { onSetDarkModePreference(DarkModePreference.AUTOMATIC) }
            )
        }

        // Ambient light sensor sensitivity — only meaningful in Automatic mode.
        Text(
            text = stringResource(R.string.ambient_light_dark_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp)
                .testTag("ambientLightSensitivity")
        )

        Column(modifier = Modifier.selectableGroup()) {
            OrientationOption(
                label = stringResource(R.string.off),
                selected = ambientLightSensitivity == AmbientLightSensitivity.OFF,
                onClick = { onSetAmbientLightSensitivity(AmbientLightSensitivity.OFF) }
            )
            OrientationOption(
                label = stringResource(R.string.sensitivity_high),
                selected = ambientLightSensitivity == AmbientLightSensitivity.HIGH,
                onClick = { onSetAmbientLightSensitivity(AmbientLightSensitivity.HIGH) }
            )
            OrientationOption(
                label = stringResource(R.string.sensitivity_medium),
                selected = ambientLightSensitivity == AmbientLightSensitivity.MEDIUM,
                onClick = { onSetAmbientLightSensitivity(AmbientLightSensitivity.MEDIUM) }
            )
            OrientationOption(
                label = stringResource(R.string.sensitivity_low),
                selected = ambientLightSensitivity == AmbientLightSensitivity.LOW,
                onClick = { onSetAmbientLightSensitivity(AmbientLightSensitivity.LOW) }
            )
        }

        // Rendering mode section — always visible
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Text(
            text = stringResource(R.string.rendering),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        Column(modifier = Modifier.selectableGroup()) {
            OrientationOption(
                label = stringResource(R.string.tile_cache),
                selected = renderMode == RenderMode.TILES,
                onClick = { onSetRenderMode(RenderMode.TILES) }
            )
            OrientationOption(
                label = stringResource(R.string.direct),
                selected = renderMode == RenderMode.DIRECT,
                onClick = { onSetRenderMode(RenderMode.DIRECT) }
            )
        }

        // Map style control — compact exposed dropdown (one row instead of a
        // radio row per style, keeping the sheet short).
        if (availableStyles.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_style),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                var styleMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = styleMenuExpanded,
                    onExpandedChange = { styleMenuExpanded = it }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { styleMenuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = styleSheet,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = styleMenuExpanded,
                        onDismissRequest = { styleMenuExpanded = false }
                    ) {
                        availableStyles.forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style) },
                                trailingIcon = if (style == styleSheet) {
                                    { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.selected)) }
                                } else {
                                    null
                                },
                                onClick = {
                                    styleMenuExpanded = false
                                    onSetStyleSheet(style)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Symbol/icon preference — next to the style picker: both decide what the active style
        // draws (spec: map-styles — Phone control for the preference).
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.prefer_symbol_icons),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = preferSymbolPoiIcons,
                onCheckedChange = { preferSymbols ->
                    onSetPreferSymbolPoiIcons(preferSymbols)
                },
                modifier = Modifier.testTag("preferSymbolIconsToggle")
            )
        }

        // The entry row — the sheet's last row in every mode: the settings the map's own state does
        // not need live in the surface, never here (spec: `settings-surface` — "The quick sheet holds
        // only the current mode's live controls").
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onOpenSettings() }
                .testTag("openSettingsSurfaceRow")
        ) {
            Text(
                text = stringResource(R.string.settings_open_surface),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun OrientationOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        RadioButton(
            selected = selected,
            onClick = null // handled by selectable modifier
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * One spoken-announcement switch row: a label and its switch, 56 dp tall, tagged so a Compose case
 * can find it (spec: `voice-guidance` — The phone offers both switches in its settings sheet).
 */
@Composable
private fun AnnouncementSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                onToggle(enabled)
            }
        )
    }
}

/**
 * The two spoken-announcement rows at their defaults: the phone's voice switch on, the shared GPS
 * switch off (spec: `voice-guidance` — The voice switch is per surface, persisted and on by default;
 * GPS announcements are one shared switch, off by default).
 */
@Preview(showBackground = true)
@Composable
private fun AnnouncementSwitchRowsPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            AnnouncementSwitchRow(
                label = stringResource(R.string.voice_guidance_switch),
                checked = true,
                testTag = "previewVoiceGuidanceSwitch",
                onToggle = {}
            )
            AnnouncementSwitchRow(
                label = stringResource(R.string.gps_announcements_switch),
                checked = false,
                testTag = "previewAnnounceGpsSwitch",
                onToggle = {}
            )
        }
    }
}

