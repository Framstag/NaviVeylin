package com.naviveylin.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.R
import com.naviveylin.util.formatDistanceKm
import kotlin.math.roundToInt

/**
 * POI search sheet: choose a category (never preselected) and radius, run the
 * search around the current map center, and browse results. Result rows follow
 * the existing result-list style; selecting a result opens the details sheet
 * (spec: poi-search, map-for-poi-search).
 *
 * When a [client] is provided and results exist, an interactive mini map is
 * embedded next to the result list: above the list in portrait, to the left of
 * the list in landscape. The map shows a marker for every result, the current
 * position when available, and highlights the selected result. Panning/zooming
 * it never affects the main map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiSearchPanel(
    category: String?,
    radiusMeters: Double,
    results: List<PoiEntry>,
    isSearching: Boolean,
    error: String?,
    client: OSMScoutClient? = null,
    centerLat: Double = Double.NaN,
    centerLon: Double = Double.NaN,
    currentPosition: Pair<Double, Double>? = null,
    selectedPoi: Pair<Double, Double>? = null,
    onCategorySelected: (String?) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onSearch: () -> Unit,
    onEntryClick: (PoiEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories = remember { PoiCategories.getCategoryTypes().keys.toList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Full width so the embedded map gets real space in landscape
        // (default sheet max width 640 dp would starve it on tablets).
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title bar with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.poi_search_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.poi_search_close)
                    )
                }
            }

            // Category dropdown — searchable, scales to any number of
            // categories; no preselection; selecting the chosen category
            // (or the clear button) deselects
            var query by remember { mutableStateOf("") }
            var expanded by remember { mutableStateOf(false) }
            val categoryLabels = categories.associateWith { id ->
                stringResource(categoryLabelRes(id))
            }
            // Keep the field in sync when the selection changes externally
            // (e.g. state restore); typing below clears the selection, so
            // this only fires on pick/restore.
            LaunchedEffect(category) {
                if (category != null) query = categoryLabels[category]!!
            }
            val filteredCategories = if (query.isBlank()) {
                categories
            } else {
                categories.filter { categoryLabels[it]!!.contains(query, ignoreCase = true) }
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { text ->
                        query = text
                        // The field text is the source of truth: editing it
                        // invalidates any previously selected category.
                        if (category != null && text != categoryLabels[category]) {
                            onCategorySelected(null)
                        }
                        expanded = true
                    },
                    label = { Text(stringResource(R.string.poi_search_category)) },
                    placeholder = { Text(stringResource(R.string.poi_search_category_placeholder)) },
                    trailingIcon = {
                        if (category != null || query.isNotEmpty()) {
                            IconButton(onClick = {
                                onCategorySelected(null)
                                query = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.poi_search_clear_category)
                                )
                            }
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.testTag("poi_category_menu")
                ) {
                    filteredCategories.forEach { id ->
                        DropdownMenuItem(
                            text = { Text(categoryLabels[id]!!) },
                            onClick = {
                                onCategorySelected(if (category == id) null else id)
                                query = if (category == id) "" else categoryLabels[id]!!
                                expanded = false
                            }
                        )
                    }
                    if (filteredCategories.isEmpty()) {
                        Text(
                            text = stringResource(R.string.poi_search_no_category_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Radius slider over the shared radius steps
            Text(
                text = stringResource(R.string.poi_search_radius, formatDistanceKm(radiusMeters)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val steps = MapCanvasViewModel.POI_RADIUS_STEPS_M
            var sliderIndex by remember(radiusMeters) {
                mutableFloatStateOf(radiusToIndex(radiusMeters, steps).toFloat())
            }
            Slider(
                value = sliderIndex,
                onValueChange = { idx ->
                    sliderIndex = idx
                    val step = idx.roundToInt().coerceIn(0, steps.size - 1)
                    onRadiusChanged(steps[step])
                },
                valueRange = 0f..(steps.size - 1).toFloat(),
                steps = (steps.size - 2).coerceAtLeast(0)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Search trigger — disabled until a category is selected
            Button(
                onClick = onSearch,
                enabled = category != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.poi_search_button))
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                }

                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                results.isEmpty() && category != null -> {
                    Text(
                        text = stringResource(R.string.poi_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                results.isNotEmpty() && client != null -> {
                    PoiResultsWithMap(
                        client = client,
                        results = results,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        radiusMeters = radiusMeters,
                        currentPosition = currentPosition,
                        selectedPoi = selectedPoi,
                        onEntryClick = onEntryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                results.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(results) { entry ->
                            PoiResultItem(
                                entry = entry,
                                onClick = { onEntryClick(entry) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Results region: embedded map + result list. Portrait puts the map above the
 * list, landscape puts it to the left (spec: poi-search POI results map
 * embedded in the search sheet). The map is centered on the search center at a
 * magnification fitting the results, current position, and search center.
 */
@Composable
internal fun PoiResultsWithMap(
    client: OSMScoutClient,
    results: List<PoiEntry>,
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    currentPosition: Pair<Double, Double>?,
    selectedPoi: Pair<Double, Double>?,
    onEntryClick: (PoiEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** Test hook: force the landscape branch; null uses the real maxWidth > maxHeight check. */
    landscapeOverride: Boolean? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val landscape = landscapeOverride ?: (maxWidth > maxHeight)
        val density = LocalDensity.current
        val mapWpx = with(density) { (if (landscape) maxWidth / 2 else maxWidth).toPx() }.toInt()
        val mapHpx = with(density) {
            (if (landscape) maxHeight else minOf(maxHeight * 0.5f, 240.dp)).toPx()
        }.toInt()
        val fitMag = remember(results, centerLat, centerLon, radiusMeters, currentPosition, mapWpx, mapHpx) {
            poiFitMagnification(results, centerLat, centerLon, currentPosition, radiusMeters, mapWpx, mapHpx)
        }

        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                MiniMap(
                    client = client,
                    lat = centerLat,
                    lon = centerLon,
                    initialMag = fitMag,
                    additionalMarkers = results.map { it.lat to it.lon },
                    selectedMarker = selectedPoi,
                    currentPosition = currentPosition,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                ResultList(
                    results = results,
                    onEntryClick = onEntryClick,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MiniMap(
                    client = client,
                    lat = centerLat,
                    lon = centerLon,
                    initialMag = fitMag,
                    additionalMarkers = results.map { it.lat to it.lon },
                    selectedMarker = selectedPoi,
                    currentPosition = currentPosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(bottom = 8.dp)
                )
                ResultList(
                    results = results,
                    onEntryClick = onEntryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResultList(
    results: List<PoiEntry>,
    onEntryClick: (PoiEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(results) { entry ->
            PoiResultItem(
                entry = entry,
                onClick = { onEntryClick(entry) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PoiResultItem(
    entry: PoiEntry,
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
                text = entry.label.ifEmpty { "(unnamed)" },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${entry.objectType} · ${formatDistanceKm(entry.distance)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatDistanceKm(entry.distance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Magnification that fits the search center, all results, and the current
 * position (30% margin) into a [mapW]x[mapH] pixel viewport. Falls back to a
 * radius-derived bounding box when there are no results or everything is at
 * the same point.
 */
private fun poiFitMagnification(
    results: List<PoiEntry>,
    centerLat: Double,
    centerLon: Double,
    currentPosition: Pair<Double, Double>?,
    radiusMeters: Double,
    mapW: Int,
    mapH: Int
): Int {
    fun radiusBbox(): DoubleArray {
        val latRad = Math.toRadians(centerLat)
        val dLat = radiusMeters / 111320.0
        val dLon = radiusMeters / (111320.0 * Math.cos(latRad))
        return doubleArrayOf(
            centerLat - dLat, centerLat + dLat,
            centerLon - dLon, centerLon + dLon
        )
    }

    if (mapW <= 0 || mapH <= 0) return MapCanvasViewModel.MIN_MAG
    if (centerLat.isNaN() || centerLon.isNaN()) return MapCanvasViewModel.MIN_MAG

    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    fun extend(lat: Double, lon: Double) {
        if (lat.isNaN() || lon.isNaN()) return
        if (lat < minLat) minLat = lat
        if (lat > maxLat) maxLat = lat
        if (lon < minLon) minLon = lon
        if (lon > maxLon) maxLon = lon
    }

    extend(centerLat, centerLon)
    currentPosition?.let { extend(it.first, it.second) }
    results.forEach { extend(it.lat, it.lon) }

    val dLat = maxLat - minLat
    val dLon = maxLon - minLon
    if (dLat <= 1e-9 && dLon <= 1e-9) {
        // Everything sits at one point (or only the center is known):
        // fall back to fitting the search radius.
        return MapCanvasViewModel.computeAreaZoom(radiusBbox(), mapW, mapH)
    }
    val marginLat = dLat * 0.3
    val marginLon = dLon * 0.3
    val bbox = doubleArrayOf(
        minLat - marginLat, maxLat + marginLat,
        minLon - marginLon, maxLon + marginLon
    )
    return MapCanvasViewModel.computeAreaZoom(bbox, mapW, mapH)
}

/** Map a radius in meters to the nearest slider index in [steps]. */
private fun radiusToIndex(radiusMeters: Double, steps: DoubleArray): Int {
    var best = 0
    var bestDelta = Double.MAX_VALUE
    for (i in steps.indices) {
        val delta = kotlin.math.abs(steps[i] - radiusMeters)
        if (delta < bestDelta) {
            bestDelta = delta
            best = i
        }
    }
    return best
}

/** Display label for a PoiCategories id. */
private fun categoryLabelRes(id: String): Int = when (id) {
    PoiCategories.HOTELS -> R.string.poi_category_hotels
    PoiCategories.RESTAURANTS -> R.string.poi_category_restaurants
    PoiCategories.GROCERY -> R.string.poi_category_grocery
    PoiCategories.VIEWPOINT -> R.string.poi_category_viewpoint
    PoiCategories.MUSEUM -> R.string.poi_category_museum
    PoiCategories.FUEL -> R.string.poi_category_fuel
    PoiCategories.CHARGING_STATION -> R.string.poi_category_charging_station
    PoiCategories.ATM -> R.string.poi_category_atm
    PoiCategories.TOURISM -> R.string.poi_category_tourism
    PoiCategories.PARKING -> R.string.poi_category_parking
    PoiCategories.POLICE -> R.string.poi_category_police
    PoiCategories.HOSPITAL -> R.string.poi_category_hospital
    PoiCategories.DOCTORS -> R.string.poi_category_doctors
    PoiCategories.PUBLIC_TRANSPORT -> R.string.poi_category_public_transport
    else -> R.string.poi_category_hotels
}
