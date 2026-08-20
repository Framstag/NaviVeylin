package com.naviveylin.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.ObjectDescription

/**
 * Bottom sheet listing all reasonable objects at a long-pressed coordinate,
 * in native ranking order (spec: long-press-candidate-picker).
 *
 * Each row shows the object name (or "(unnamed)"), its OSM type, and a short
 * description snippet in search-result format. Tapping a row invokes
 * [onCandidateSelected] with that candidate; dismissing the sheet (swipe,
 * outside tap, back) invokes [onDismiss] and opens no details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatePickerSheet(
    candidates: List<ObjectDescription>,
    onCandidateSelected: (ObjectDescription) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 480.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "What's here?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn {
                items(candidates, key = { it.objectFileOffset to it.objectRefType }) { desc ->
                    CandidateRow(
                        candidate = desc,
                        onClick = { onCandidateSelected(desc) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ObjectDescription,
    onClick: () -> Unit
) {
    val name = candidate.entries.firstOrNull {
        it.sectionKey == "General" && it.labelKey == "Name"
    }?.value?.takeIf { it.isNotBlank() } ?: "(unnamed)"
    val type = candidate.objectTypeName?.takeIf { it.isNotBlank() }
    val snippet = candidate.entries
        .filterNot { it.sectionKey == "General" && it.labelKey == "Name" }
        .mapNotNull { it.value?.takeIf { v -> v.isNotBlank() } }
        .distinct()
        .take(2)
        .joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge
            )
            if (type != null) {
                Text(
                    text = type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (snippet.isNotEmpty()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
