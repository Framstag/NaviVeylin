package com.naviveylin.ui.mapmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.UpdateState
import com.naviveylin.data.MapCheckReport
import com.naviveylin.data.MapUpdatesState
import android.text.format.DateUtils

/**
 * The updates section of the map manager screen: when the last check ran and what it found, one row per
 * installation an update is available for, and the two actions that act on them.
 *
 * The section belongs to a repository source and is shown for it whether or not anything is updatable —
 * that is what keeps the check control reachable when the answer is "nothing to update", and it is why the
 * section's presence is a rule of its own rather than "omit when empty" (spec `map-updates` — "The updates
 * section belongs to the repository source").
 *
 * The transfer itself is shown in the active-downloads section with its progress, as the basemap's is: a row
 * here states its own state and offers the actions, so one transfer is not presented twice (spec
 * `map-updates` — "Updating every listed item is a queue"; `basemap-ui` states the same for the basemap).
 */
@Composable
internal fun MapUpdatesSection(
    state: MapUpdatesState,
    transfers: Map<String, UpdateTransferState>,
    isUpdating: Boolean,
    onCheck: () -> Unit,
    onUpdate: (MapUpdateItem) -> Unit,
    onUpdateAll: () -> Unit,
    onCancel: (String) -> Unit,
    onDismissError: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("map-updates-section")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.updates),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.updates.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.updates_available_count,
                        state.updates.size,
                        state.updates.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("map-updates-count")
                )
            }
            if (state.updates.isNotEmpty()) {
                TextButton(
                    onClick = onUpdateAll,
                    enabled = !state.isChecking && !isUpdating,
                    modifier = Modifier.testTag("update-all")
                ) {
                    Text(stringResource(R.string.update_all))
                }
            }
            TextButton(
                onClick = onCheck,
                enabled = !state.isChecking && !isUpdating,
                modifier = Modifier.testTag("check-for-updates")
            ) {
                Text(
                    stringResource(
                        if (state.isChecking) R.string.checking_for_updates else R.string.check_for_updates
                    )
                )
            }
        }

        Text(
            text = lastCheckText(state),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("map-updates-status")
        )

        state.lastAttemptFailure?.let {
            Text(
                text = stringResource(
                    R.string.updates_check_failed,
                    it.javaClass.simpleName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp).testTag("map-updates-check-failed")
            )
        }

        state.updates.forEach { item ->
            UpdateRow(
                item = item,
                transfer = transfers[item.directoryName],
                actionsEnabled = !isUpdating,
                onUpdate = { onUpdate(item) },
                onCancel = { onCancel(item.directoryName) },
                onDismissError = { onDismissError(item.directoryName) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun UpdateRow(
    item: MapUpdateItem,
    transfer: UpdateTransferState?,
    actionsEnabled: Boolean,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("update-row-${item.directoryName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName ?: item.directoryName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.subtitle(),
                style = MaterialTheme.typography.bodySmall
            )
        }
        when (transfer) {
            is UpdateTransferState.Transferring -> {
                Text(
                    text = stringResource(R.string.updating_item),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-${item.directoryName}")) {
                    Text(stringResource(R.string.cancel))
                }
            }
            is UpdateTransferState.Failed -> {
                Text(
                    text = transfer.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onDismissError, modifier = Modifier.testTag("ok-${item.directoryName}")) {
                    Text(stringResource(R.string.ok))
                }
            }
            null -> TextButton(
                onClick = onUpdate,
                enabled = actionsEnabled,
                modifier = Modifier.testTag("update-${item.directoryName}")
            ) {
                Text(stringResource(R.string.update))
            }
        }
    }
}

/** The row's size line: a basemap says what it is, a region database names the source's kind of entry. */
@Composable
private fun MapUpdateItem.subtitle(): String {
    val label = if (kind == MapUpdateKind.BASEMAP) {
        stringResource(R.string.world_basemap)
    } else {
        directoryName
    }
    return "$label · %.1f MB".format(downloadBytes() / (1024.0 * 1024.0))
}

/** The bytes the comparison measured for this item, or 0 when it is not an update. */
private fun MapUpdateItem.downloadBytes(): Long =
    (state as? UpdateState.UpdateAvailable)?.downloadBytes ?: 0L

/** When the last completed check ran, or that none has, or what the last attempt hit. */
@Composable
private fun lastCheckText(state: MapUpdatesState): String = when (val report = state.report) {
    MapCheckReport.NeverChecked -> stringResource(R.string.updates_never_checked)
    is MapCheckReport.Completed ->
        if (report.updates.isEmpty()) {
            stringResource(R.string.updates_none_available) + " · " + stringResource(
                R.string.updates_last_checked,
                DateUtils.getRelativeTimeSpanString(report.checkedAtEpochMs).toString()
            )
        } else {
            stringResource(
                R.string.updates_last_checked,
                DateUtils.getRelativeTimeSpanString(report.checkedAtEpochMs).toString()
            )
        }
}

@Preview(showBackground = true)
@Composable
private fun MapUpdatesSectionPreview() {
    val item = MapUpdateItem(
        kind = MapUpdateKind.REGION,
        directoryName = "europe-germany-berlin",
        displayName = "Berlin",
        idPath = listOf("europe", "germany", "berlin"),
        state = UpdateState.UpdateAvailable(48L * 1024L * 1024L)
    )
    MaterialTheme {
        Column {
            MapUpdatesSection(
                state = MapUpdatesState(
                    report = MapCheckReport.Completed(checkedAtEpochMs = 0L, updates = listOf(item))
                ),
                transfers = emptyMap(),
                isUpdating = false,
                onCheck = {},
                onUpdate = {},
                onUpdateAll = {},
                onCancel = {},
                onDismissError = {}
            )
            MapUpdatesSection(
                state = MapUpdatesState(report = MapCheckReport.NeverChecked),
                transfers = mapOf(item.directoryName to UpdateTransferState.Transferring(42)),
                isUpdating = true,
                onCheck = {},
                onUpdate = {},
                onUpdateAll = {},
                onCancel = {},
                onDismissError = {}
            )
        }
    }
}
