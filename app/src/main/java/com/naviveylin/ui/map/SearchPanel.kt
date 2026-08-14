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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.util.formatDistanceKm
import com.naviveylin.util.haversineDistanceMeters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    query: String,
    results: List<LocationEntry>,
    isSearching: Boolean,
    gpsAvailable: Boolean,
    adminRegionName: String?,
    centerLat: Double,
    centerLon: Double,
    onQueryChanged: (String) -> Unit,
    onResultSelected: (LocationEntry) -> Unit,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the input once it is attached. Requesting focus from a
    // LaunchedEffect races sheet content attachment (crashes in tests and can
    // miss the frame); onGloballyPositioned fires when the field is in the tree.
    var inputFocused by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 280.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Resolved admin region indicator (shown when search is scoped to a region)
            if (adminRegionName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Searching in $adminRegionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search input with clear button
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        if (!inputFocused) {
                            inputFocused = true
                            focusRequester.requestFocus()
                        }
                    },
                placeholder = { Text("Search location...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Convenience entries (Current Location, Select Favorite) are shown
            // only while the query is empty. Typing a query lists search results
            // only; clearing the field restores both entries immediately.
            if (query.isEmpty()) {
                if (gpsAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSelectCurrentLocation)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    HorizontalDivider()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectFavorite)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Select Favorite",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }

            // Results or loading/no-results
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                }

                results.isEmpty() && query.length >= 2 -> {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                results.isNotEmpty() -> {
                    val duplicateLabels = remember(results) {
                        results.groupBy { it.label }
                            .filter { it.value.size > 1 }
                            .keys
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(results) { entry ->
                            val isDuplicate = entry.label in duplicateLabels
                            val disambiguationDetail = if (isDuplicate) {
                                val detail = buildDisambiguationDetail(entry)
                                detail.ifEmpty { null }
                            } else null
                            // Distance from the current map center, right-aligned
                            // in a smaller font (see Result distance display spec).
                            val distanceText = distanceFromCenter(entry, centerLat, centerLon)
                            SearchResultItem(
                                entry = entry,
                                isDuplicate = isDuplicate,
                                disambiguationDetail = disambiguationDetail,
                                distanceText = distanceText,
                                onClick = { onResultSelected(entry) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    entry: LocationEntry,
    isDuplicate: Boolean = false,
    disambiguationDetail: String? = null,
    distanceText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isDuplicate && disambiguationDetail != null) {
                Text(
                    text = disambiguationDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.adminRegionHierarchy != null && entry.adminRegionHierarchy.isNotEmpty()) {
                Text(
                    text = entry.adminRegionHierarchy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (distanceText != null) {
            Text(
                text = distanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Distance from the map center to a result as a km string, or null when not computable. */
private fun distanceFromCenter(entry: LocationEntry, centerLat: Double, centerLon: Double): String? {
    val meters = haversineDistanceMeters(centerLat, centerLon, entry.lat, entry.lon)
    return if (meters.isFinite()) formatDistanceKm(meters) else null
}

internal fun buildDisambiguationDetail(entry: LocationEntry): String {
    val parts = mutableListOf<String>()
    if (entry.name != null && entry.name.isNotEmpty()) {
        parts.add(entry.name)
    }
    if (entry.objectTypeName != null && entry.objectTypeName.isNotEmpty()) {
        parts.add(entry.objectTypeName)
    }
    if (entry.postalArea != null && entry.postalArea.isNotEmpty()) {
        parts.add(entry.postalArea)
    }
    if (entry.region != null && entry.region.isNotEmpty()) {
        parts.add(entry.region[0])
    }
    return parts.joinToString(" · ")
}
