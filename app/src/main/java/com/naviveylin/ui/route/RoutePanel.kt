package com.naviveylin.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.util.formatDistanceKm
import com.naviveylin.util.haversineDistanceMeters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePanel(
    viewModel: RoutePanelViewModel,
    onOpenFavoritePicker: (ActiveField) -> Unit,
    onDismiss: () -> Unit,
    onStartNavigation: () -> Unit = {},
    onStopNavigation: () -> Unit = {},
    isNavigating: Boolean = false,
    centerLat: Double,
    centerLon: Double
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- Title ----
            Text(
                text = "Route",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Fields with the swap button to the right, vertically
            // centered between start and destination ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // ---- Start field ----
                    val startFieldValue = if (state.activeField == ActiveField.START) {
                        state.searchQuery
                    } else {
                        state.startLocation?.label ?: ""
                    }
                    RouteSearchField(
                        value = startFieldValue,
                        placeholder = "Start location",
                        isActive = state.activeField == ActiveField.START,
                        onFocus = { viewModel.setActiveField(ActiveField.START) },
                        onBlur = {
                            if (viewModel.uiState.value.activeField == ActiveField.START) {
                                viewModel.setActiveField(ActiveField.NONE)
                            }
                        },
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onClear = {
                            viewModel.setStartLocation(
                                LocationEntry().apply { label = "" }
                            )
                            viewModel.setActiveField(ActiveField.START)
                        }
                    )

                    // Start search results
                    if (state.activeField == ActiveField.START) {
                        RouteSearchResults(
                            query = state.searchQuery,
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            gpsAvailable = state.gpsAvailable,
                            centerLat = centerLat,
                            centerLon = centerLon,
                            onSelectCurrentLocation = { viewModel.selectCurrentLocation() },
                            onSelectFavorite = { onOpenFavoritePicker(ActiveField.START) },
                            onSelectResult = { viewModel.selectSearchResult(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ---- Destination field ----
                    val destFieldValue = if (state.activeField == ActiveField.DEST) {
                        state.searchQuery
                    } else {
                        state.destLocation?.label ?: ""
                    }
                    RouteSearchField(
                        value = destFieldValue,
                        placeholder = "Destination",
                        isActive = state.activeField == ActiveField.DEST,
                        onFocus = { viewModel.setActiveField(ActiveField.DEST) },
                        onBlur = {
                            if (viewModel.uiState.value.activeField == ActiveField.DEST) {
                                viewModel.setActiveField(ActiveField.NONE)
                            }
                        },
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onClear = {
                            viewModel.setDestLocation(
                                LocationEntry().apply { label = "" }
                            )
                            viewModel.setActiveField(ActiveField.DEST)
                        }
                    )

                    // Dest search results
                    if (state.activeField == ActiveField.DEST) {
                        RouteSearchResults(
                            query = state.searchQuery,
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            gpsAvailable = state.gpsAvailable,
                            centerLat = centerLat,
                            centerLon = centerLon,
                            onSelectCurrentLocation = { viewModel.selectCurrentLocation() },
                            onSelectFavorite = { onOpenFavoritePicker(ActiveField.DEST) },
                            onSelectResult = { viewModel.selectSearchResult(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ---- Swap button ----
                FilledTonalIconButton(
                    onClick = { viewModel.swapStartDest() },
                    enabled = state.startLocation != null && state.destLocation != null
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Swap start and destination"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Vehicle selector ----
            Text(
                text = "Vehicle",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleButton(
                    label = "Car",
                    selected = state.vehicle == Vehicle.CAR,
                    onClick = { viewModel.setVehicle(Vehicle.CAR) }
                )
                VehicleButton(
                    label = "Bicycle",
                    selected = state.vehicle == Vehicle.BICYCLE,
                    onClick = { viewModel.setVehicle(Vehicle.BICYCLE) }
                )
                VehicleButton(
                    label = "Pedestrian",
                    selected = state.vehicle == Vehicle.PEDESTRIAN,
                    onClick = { viewModel.setVehicle(Vehicle.PEDESTRIAN) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Action buttons ----
            when (state.routeState) {
                is RouteState.Idle -> {
                    Button(
                        onClick = { viewModel.calculateRoute() },
                        enabled = state.startLocation != null && state.destLocation != null &&
                                state.startLocation!!.label.isNotEmpty() &&
                                state.destLocation!!.label.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calculate")
                    }

                    if (state.routeEntry != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearRoute() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Route")
                        }
                    }
                }

                is RouteState.Calculating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Calculating route...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelRoute() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }

                is RouteState.Done -> {
                    if (isNavigating) {
                        Button(
                            onClick = onStopNavigation,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Navigation")
                        }
                    } else {
                        Button(
                            onClick = onStartNavigation,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Navigation")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.showSummaryDialog() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Route")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearRoute() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Route")
                    }
                }

                is RouteState.Error -> {
                    Text(
                        text = state.error ?: "Route calculation failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.calculateRoute() },
                        enabled = state.startLocation != null && state.destLocation != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
            }

            // ---- Turn-by-turn instructions removed — shown in RouteSummaryDialog instead ----
        }
    }
}

@Composable
private fun RouteSearchField(
    value: String,
    placeholder: String,
    isActive: Boolean,
    onFocus: () -> Unit,
    onBlur: () -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit
) {
    val isReadOnly = value.isNotEmpty() && !isActive

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (isActive) {
                onQueryChanged(newValue)
            } else if (!isReadOnly) {
                onFocus()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                // Tap on an empty field must open the results popup (with the
                // convenience entries) immediately — not only after the first
                // keystroke. Losing focus closes the popup again.
                if (focusState.isFocused) {
                    onFocus()
                } else {
                    onBlur()
                }
            },
        placeholder = { Text(placeholder) },
        readOnly = isReadOnly,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear"
                    )
                }
            }
        },
        singleLine = true,
        enabled = true
    )
}

@Composable
private fun RouteSearchResults(
    query: String,
    results: List<LocationEntry>,
    isSearching: Boolean,
    gpsAvailable: Boolean,
    centerLat: Double,
    centerLon: Double,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: () -> Unit,
    onSelectResult: (LocationEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
    ) {
        // Convenience entries (Current Location, Select Favorite) are shown
        // only while the query is empty. Typing a query lists search results
        // only; clearing the field restores both entries immediately.
        if (query.isEmpty()) {
            // First entry: Current Location (if GPS available)
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

            // Second entry: Select Favorite
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

        // Search results
        when {
            isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                        .size(24.dp)
                )
            }

            query.length >= 2 && results.isEmpty() -> {
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            results.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(results) { entry ->
                        // Distance from the current map center, right-aligned in
                        // a smaller font (see Result distance display spec).
                        val meters = haversineDistanceMeters(centerLat, centerLon, entry.lat, entry.lon)
                        val distanceText = if (meters.isFinite()) formatDistanceKm(meters) else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onSelectResult(entry) })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color
        ),
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
