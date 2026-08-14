package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.naviveylin.data.DarkModePreference

/**
 * Location options button that opens a full-width Material 3 bottom sheet
 * for toggling map behaviour: follow mode, orientation, and auto-zoom.
 *
 * Orientation controls show the current mode's setting (free-form or navigation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationOptionsOverlay(
    followMode: Boolean,
    onToggleFollowMode: (Boolean) -> Unit,
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
    laneHintsEnabled: Boolean = true,
    onToggleLaneHints: (Boolean) -> Unit = {},
    isNavigating: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                contentDescription = "Location options",
                tint = if (followMode) {
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
                followMode = followMode,
                onToggleFollowMode = { enabled ->
                    onToggleFollowMode(enabled)
                },
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
                laneHintsEnabled = laneHintsEnabled,
                onToggleLaneHints = onToggleLaneHints,
                isNavigating = isNavigating
            )
        }
    }
}

@Composable
private fun LocationOptionsSheetContent(
    followMode: Boolean,
    onToggleFollowMode: (Boolean) -> Unit,
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
    laneHintsEnabled: Boolean,
    onToggleLaneHints: (Boolean) -> Unit,
    isNavigating: Boolean
) {
    val currentNorthUp = if (isNavigating) navNorthUp else freeFormNorthUp
    val onSetOrientation = if (isNavigating) onSetNavOrientation else onSetFreeFormOrientation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Section: Map follows position
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Map follows position",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = followMode,
                onCheckedChange = { enabled ->
                    onToggleFollowMode(enabled)
                }
            )
        }

        HorizontalDivider()

        // Section: Orientation
        Text(
            text = "Orientation",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        Column(modifier = Modifier.selectableGroup()) {
            OrientationOption(
                label = "North up",
                selected = currentNorthUp,
                onClick = { onSetOrientation(true) }
            )
            OrientationOption(
                label = "Follow direction",
                selected = !currentNorthUp,
                onClick = { onSetOrientation(false) }
            )
        }

        // Auto-zoom toggle — only visible during active navigation
        if (isNavigating) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Auto zoom",
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
                text = "Keep screen on",
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
                text = "Lane instructions",
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

        // Dark mode section — always visible
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Text(
            text = "Dark mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        Column(modifier = Modifier.selectableGroup()) {
            OrientationOption(
                label = "On",
                selected = darkModePreference == DarkModePreference.ON,
                onClick = { onSetDarkModePreference(DarkModePreference.ON) }
            )
            OrientationOption(
                label = "Off",
                selected = darkModePreference == DarkModePreference.OFF,
                onClick = { onSetDarkModePreference(DarkModePreference.OFF) }
            )
            OrientationOption(
                label = "Automatic",
                selected = darkModePreference == DarkModePreference.AUTOMATIC,
                onClick = { onSetDarkModePreference(DarkModePreference.AUTOMATIC) }
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
