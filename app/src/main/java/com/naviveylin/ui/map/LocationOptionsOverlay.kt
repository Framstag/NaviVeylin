package com.naviveylin.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AmbientLightSensitivity
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
    overspeedWarningDeltaKmh: Int = 5,
    onSetOverspeedWarningDelta: (Int) -> Unit = {},
    routingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT,
    onSetRoutingAnchor: (VehicleAnchorPosition) -> Unit = {},
    freeDrivingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT,
    onSetFreeDrivingAnchor: (VehicleAnchorPosition) -> Unit = {},
    renderMode: RenderMode = RenderMode.TILES,
    onSetRenderMode: (RenderMode) -> Unit = {},
    availableStyles: List<String> = emptyList(),
    styleSheet: String = "standard",
    onSetStyleSheet: (String) -> Unit = {},
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
                overspeedWarningDeltaKmh = overspeedWarningDeltaKmh,
                onSetOverspeedWarningDelta = onSetOverspeedWarningDelta,
                routingAnchor = routingAnchor,
                onSetRoutingAnchor = onSetRoutingAnchor,
                freeDrivingAnchor = freeDrivingAnchor,
                onSetFreeDrivingAnchor = onSetFreeDrivingAnchor,
                renderMode = renderMode,
                onSetRenderMode = onSetRenderMode,
                availableStyles = availableStyles,
                styleSheet = styleSheet,
                onSetStyleSheet = onSetStyleSheet
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
    overspeedWarningDeltaKmh: Int,
    onSetOverspeedWarningDelta: (Int) -> Unit,
    routingAnchor: VehicleAnchorPosition,
    onSetRoutingAnchor: (VehicleAnchorPosition) -> Unit,
    freeDrivingAnchor: VehicleAnchorPosition,
    onSetFreeDrivingAnchor: (VehicleAnchorPosition) -> Unit,
    renderMode: RenderMode,
    onSetRenderMode: (RenderMode) -> Unit,
    availableStyles: List<String>,
    styleSheet: String,
    onSetStyleSheet: (String) -> Unit
) {
    // Vehicle anchor grid dialog target (spec: location-options-ui — Vehicle
    // anchor position controls): null = no picker open.
    var pickerFor by remember { mutableStateOf<AnchorPickerFor?>(null) }

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

        // Vehicle anchor presets (spec: location-options-ui — Vehicle anchor
        // position controls): one row per mode, each opening a 5×3 grid
        // picker; the value labels come from the shared enum so the phone and
        // Android Auto pickers stay in label parity (guidelines/UI.md).
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        AnchorPreferenceRow(
            title = stringResource(R.string.vehicle_position_routing),
            value = routingAnchor.label,
            onClick = { pickerFor = AnchorPickerFor.ROUTING }
        )
        AnchorPreferenceRow(
            title = stringResource(R.string.vehicle_position_free_driving),
            value = freeDrivingAnchor.label,
            onClick = { pickerFor = AnchorPickerFor.FREE_DRIVING }
        )

        pickerFor?.let { target ->
            val current = when (target) {
                AnchorPickerFor.ROUTING -> routingAnchor
                AnchorPickerFor.FREE_DRIVING -> freeDrivingAnchor
            }
            AnchorGridPickerDialog(
                current = current,
                onSelect = { anchor ->
                    when (target) {
                        AnchorPickerFor.ROUTING -> onSetRoutingAnchor(anchor)
                        AnchorPickerFor.FREE_DRIVING -> onSetFreeDrivingAnchor(anchor)
                    }
                    pickerFor = null
                },
                onDismiss = { pickerFor = null },
                closeLabel = stringResource(R.string.close)
            )
        }

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

/** Which per-mode anchor the grid picker is editing. */
private enum class AnchorPickerFor { ROUTING, FREE_DRIVING }

/**
 * One vehicle-position preference row: title + the current preset label;
 * tapping opens the grid picker (spec: location-options-ui — Anchor rows
 * visible in the sheet).
 */
@Composable
private fun AnchorPreferenceRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 5×3 grid picker for one vehicle anchor (spec: location-options-ui —
 * Picker offers the 5×3 grid): all 15 presets, the current one marked,
 * labels shared with the Android Auto picker.
 */
@Composable
private fun AnchorGridPickerDialog(
    current: VehicleAnchorPosition,
    onSelect: (VehicleAnchorPosition) -> Unit,
    onDismiss: () -> Unit,
    closeLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_position)) },
        text = {
            Column {
                VehicleAnchorPosition.entries.chunked(5).forEach { rowAnchors ->
                    Row {
                        rowAnchors.forEach { anchor ->
                            AnchorGridCell(
                                anchor = anchor,
                                selected = anchor == current,
                                onClick = { onSelect(anchor) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(closeLabel) }
        }
    )
}

/** One 5×3 grid cell: compact preset label, selected preset highlighted. */
@Composable
private fun AnchorGridCell(
    anchor: VehicleAnchorPosition,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(border)
            .clickable(onClick = onClick)
            .padding(2.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = anchor.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
