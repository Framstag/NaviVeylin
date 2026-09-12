package com.naviveylin.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.R
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.data.SearchHistoryEntry
import com.naviveylin.util.formatDistanceKm
import com.naviveylin.util.haversineDistanceMeters
import kotlin.math.roundToInt

/**
 * Unified Material 3 search dialog (spec: search-dialog). One full-screen
 * search surface with a mode switch (Places / POIs / Contacts), suggestion
 * sources in Places mode, and per-mode content. The search field is shared:
 * it drives the location query in Places mode, filters the category chips in
 * POIs mode, and filters contacts in Contacts mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    searchMode: SearchMode,
    onModeSelected: (SearchMode) -> Unit,
    onDismiss: () -> Unit,
    // Places mode
    query: String,
    results: List<MergedSearchResult>,
    isSearching: Boolean,
    gpsAvailable: Boolean,
    adminRegionName: String?,
    centerLat: Double,
    centerLon: Double,
    historyEntries: List<SearchHistoryEntry>,
    favoriteGroups: Map<String, List<FavoriteLocation>>,
    onQueryChanged: (String) -> Unit,
    onResultSelected: (LocationEntry) -> Unit,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onHistoryEntrySelected: (String) -> Unit,
    // POIs mode
    poiCategory: String?,
    poiRadiusMeters: Double,
    poiResults: List<PoiEntry>,
    isPoiSearching: Boolean,
    poiError: String?,
    client: OSMScoutClient?,
    poiCenterLat: Double,
    poiCenterLon: Double,
    currentPosition: Pair<Double, Double>?,
    selectedPoi: Pair<Double, Double>?,
    onPoiCategorySelected: (String?) -> Unit,
    onPoiRadiusChanged: (Double) -> Unit,
    onPoiSearch: () -> Unit,
    onPoiEntryClick: (PoiEntry) -> Unit,
    // Contacts mode
    addressBookAvailable: Boolean,
    contactsQuery: String,
    onContactsQueryChanged: (String) -> Unit,
    contactsContent: @Composable () -> Unit
) {
    // The category dropdown replaces the shared field in POIs mode, so the
    // shared-field value/placeholder only apply to Places and Contacts.
    val isPlaces = searchMode == SearchMode.PLACES
    val fieldValue = if (isPlaces) query else contactsQuery
    val onFieldValueChange: (String) -> Unit =
        if (isPlaces) onQueryChanged else onContactsQueryChanged
    val placeholderRes =
        if (isPlaces) R.string.search_location_placeholder else R.string.address_book_search_hint

    SearchBar(
        inputField = {
            if (searchMode == SearchMode.POIS) {
                // The category dropdown replaces the shared field in POIs mode:
                // one editable field that filters the category list and shows
                // the selected category (spec: poi-search — category and
                // radius selection).
                PoiCategoryDropdown(
                    category = poiCategory,
                    onCategorySelected = onPoiCategorySelected
                )
            } else {
                SearchBarDefaults.InputField(
                    query = fieldValue,
                    onQueryChange = onFieldValueChange,
                    onSearch = {
                        // IME search action: Places searches as you type,
                        // Contacts filters as you type.
                    },
                    expanded = true,
                    onExpandedChange = { if (!it) onDismiss() },
                    placeholder = { Text(stringResource(placeholderRes)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (fieldValue.isNotEmpty()) {
                            IconButton(onClick = { onFieldValueChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    }
                )
            }
        },
        expanded = true,
        onExpandedChange = { if (!it) onDismiss() },
        // fillMaxWidth (not fillMaxSize): a tight min-height constraint makes
        // the SearchBar measure its input field at the full screen height.
        modifier = Modifier.fillMaxWidth()
    ) {
        SearchModeSwitcher(
            searchMode = searchMode,
            addressBookAvailable = addressBookAvailable,
            onModeSelected = onModeSelected
        )

        when (searchMode) {
            SearchMode.PLACES -> PlacesContent(
                query = query,
                results = results,
                isSearching = isSearching,
                gpsAvailable = gpsAvailable,
                adminRegionName = adminRegionName,
                centerLat = centerLat,
                centerLon = centerLon,
                historyEntries = historyEntries,
                favoriteGroups = favoriteGroups,
                onResultSelected = onResultSelected,
                onSelectCurrentLocation = onSelectCurrentLocation,
                onSelectFavorite = onSelectFavorite,
                onHistoryEntrySelected = onHistoryEntrySelected
            )

            SearchMode.POIS -> PoiContent(
                poiCategory = poiCategory,
                poiRadiusMeters = poiRadiusMeters,
                poiResults = poiResults,
                isPoiSearching = isPoiSearching,
                poiError = poiError,
                client = client,
                poiCenterLat = poiCenterLat,
                poiCenterLon = poiCenterLon,
                currentPosition = currentPosition,
                selectedPoi = selectedPoi,
                onPoiCategorySelected = onPoiCategorySelected,
                onPoiRadiusChanged = onPoiRadiusChanged,
                onPoiSearch = onPoiSearch,
                onPoiEntryClick = onPoiEntryClick
            )

            SearchMode.CONTACTS -> contactsContent()
        }
    }
}

/** Mode switch: Places / POIs / Contacts (Contacts hidden without READ_CONTACTS). */
@Composable
private fun SearchModeSwitcher(
    searchMode: SearchMode,
    addressBookAvailable: Boolean,
    onModeSelected: (SearchMode) -> Unit
) {
    val segmentCount = if (addressBookAvailable) 3 else 2
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        SegmentedButton(
            selected = searchMode == SearchMode.PLACES,
            onClick = { onModeSelected(SearchMode.PLACES) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = segmentCount)
        ) {
            Text(stringResource(R.string.search_mode_places))
        }
        SegmentedButton(
            selected = searchMode == SearchMode.POIS,
            onClick = { onModeSelected(SearchMode.POIS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = segmentCount)
        ) {
            Text(stringResource(R.string.search_mode_pois))
        }
        if (addressBookAvailable) {
            SegmentedButton(
                selected = searchMode == SearchMode.CONTACTS,
                onClick = { onModeSelected(SearchMode.CONTACTS) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = segmentCount)
            ) {
                Text(stringResource(R.string.search_mode_contacts))
            }
        }
    }
}

/** Places mode: region scope line, suggestions on empty query, results. */
@Composable
private fun PlacesContent(
    query: String,
    results: List<MergedSearchResult>,
    isSearching: Boolean,
    gpsAvailable: Boolean,
    adminRegionName: String?,
    centerLat: Double,
    centerLon: Double,
    historyEntries: List<SearchHistoryEntry>,
    favoriteGroups: Map<String, List<FavoriteLocation>>,
    onResultSelected: (LocationEntry) -> Unit,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onHistoryEntrySelected: (String) -> Unit
) {
    val duplicateLabels = remember(results) {
        results.groupBy { it.entry.label }
            .filter { it.value.size > 1 }
            .keys
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (adminRegionName != null) {
            item {
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
                        text = stringResource(R.string.searching_in_region, adminRegionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (query.isEmpty()) {
            item {
                SuggestionsSection(
                    gpsAvailable = gpsAvailable,
                    historyEntries = historyEntries,
                    favoriteGroups = favoriteGroups,
                    onSelectCurrentLocation = onSelectCurrentLocation,
                    onSelectFavorite = onSelectFavorite,
                    onHistoryEntrySelected = onHistoryEntrySelected
                )
            }
        }

        when {
            isSearching -> item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }

            results.isEmpty() && query.length >= 2 -> item {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            results.isNotEmpty() -> {
                items(results) { result ->
                    val entry = result.entry
                    val isDuplicate = entry.label in duplicateLabels
                    val disambiguationDetail = if (isDuplicate) {
                        val detail = buildDisambiguationDetail(entry)
                        detail.ifEmpty { null }
                    } else null
                    val distanceText = distanceFromCenter(entry, centerLat, centerLon)
                    SearchResultItem(
                        entry = entry,
                        isFavorite = result.isFavorite,
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

/**
 * Suggestions shown on an empty Places query: recent searches as chips
 * (youngest first), favorite locations as rows, and the current location.
 */
@Composable
private fun SuggestionsSection(
    gpsAvailable: Boolean,
    historyEntries: List<SearchHistoryEntry>,
    favoriteGroups: Map<String, List<FavoriteLocation>>,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onHistoryEntrySelected: (String) -> Unit
) {
    Column {
        if (historyEntries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyEntries) { entry ->
                    SuggestionChip(
                        onClick = { onHistoryEntrySelected(entry.text) },
                        label = { Text(entry.text) }
                    )
                }
            }
        }

        val allFavorites = favoriteGroups.values.flatten()
        if (allFavorites.isNotEmpty()) {
            Text(
                text = stringResource(R.string.search_favorites_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            allFavorites.forEach { fav ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onSelectFavorite(fav) })
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
                        text = fav.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }
        }

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
                    text = stringResource(R.string.current_location),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * Searchable category dropdown rendered in the SearchBar field slot while in
 * POIs mode (spec: poi-search — category and radius selection). The editable
 * field filters the category list by typing; selecting a category shows its
 * label in the field; editing or the clear button deselects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiCategoryDropdown(
    category: String?,
    onCategorySelected: (String?) -> Unit
) {
    val categories = remember { PoiCategories.getCategoryTypes().keys.toList() }
    val categoryLabels = categories.associateWith { id ->
        stringResource(categoryLabelRes(id))
    }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    // Keep the field in sync when the selection changes externally (e.g.
    // state restore); typing below clears the selection, so this only fires
    // on pick/restore.
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
}

/**
 * POIs mode: radius slider, explicit search button, and results with embedded
 * map. The category dropdown lives in the SearchBar field slot (see
 * [PoiCategoryDropdown]).
 */
@Composable
private fun PoiContent(
    poiCategory: String?,
    poiRadiusMeters: Double,
    poiResults: List<PoiEntry>,
    isPoiSearching: Boolean,
    poiError: String?,
    client: OSMScoutClient?,
    poiCenterLat: Double,
    poiCenterLon: Double,
    currentPosition: Pair<Double, Double>?,
    selectedPoi: Pair<Double, Double>?,
    onPoiCategorySelected: (String?) -> Unit,
    onPoiRadiusChanged: (Double) -> Unit,
    onPoiSearch: () -> Unit,
    onPoiEntryClick: (PoiEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Radius slider over the shared radius steps
        Text(
            text = stringResource(R.string.poi_search_radius, formatDistanceKm(poiRadiusMeters)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val steps = MapCanvasViewModel.POI_RADIUS_STEPS_M
        var sliderIndex by remember(poiRadiusMeters) {
            mutableFloatStateOf(radiusToIndex(poiRadiusMeters, steps).toFloat())
        }
        Slider(
            value = sliderIndex,
            onValueChange = { idx ->
                sliderIndex = idx
                val step = idx.roundToInt().coerceIn(0, steps.size - 1)
                onPoiRadiusChanged(steps[step])
            },
            valueRange = 0f..(steps.size - 1).toFloat(),
            steps = (steps.size - 2).coerceAtLeast(0)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Explicit search trigger — disabled until a category is selected
        Button(
            onClick = onPoiSearch,
            enabled = poiCategory != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.poi_search_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isPoiSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            }

            poiError != null -> {
                Text(
                    text = poiError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            poiResults.isEmpty() && poiCategory != null -> {
                Text(
                    text = stringResource(R.string.poi_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            poiResults.isNotEmpty() && client != null -> {
                PoiResultsWithMap(
                    client = client,
                    results = poiResults,
                    centerLat = poiCenterLat,
                    centerLon = poiCenterLon,
                    radiusMeters = poiRadiusMeters,
                    currentPosition = currentPosition,
                    selectedPoi = selectedPoi,
                    onEntryClick = onPoiEntryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            poiResults.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(poiResults) { entry ->
                        PoiResultItem(
                            entry = entry,
                            onClick = { onPoiEntryClick(entry) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    entry: LocationEntry,
    isFavorite: Boolean = false,
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
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = stringResource(R.string.favorite_marker),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
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
@Composable
private fun distanceFromCenter(entry: LocationEntry, centerLat: Double, centerLon: Double): String? {
    val meters = haversineDistanceMeters(centerLat, centerLon, entry.lat, entry.lon)
    return if (meters.isFinite()) {
        stringResource(R.string.distance_unit_km, formatDistanceKm(meters))
    } else {
        null
    }
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

/**
 * Primary text for a POI result row (spec: poi-search — POI results list):
 * the name with the brand (preferred) or operator in parentheses when it
 * differs; the brand or operator alone when there is no name; "(unnamed)"
 * only when name, operator, and brand are all absent.
 */
private fun poiDisplayLabel(entry: PoiEntry): String {
    val name = entry.label
    val brand = entry.brand
    val operator = entry.operator
    return when {
        name.isNotEmpty() -> {
            val extra = when {
                !brand.isNullOrEmpty() && brand != name -> brand
                !operator.isNullOrEmpty() && operator != name -> operator
                else -> null
            }
            if (extra != null) "$name ($extra)" else name
        }
        !brand.isNullOrEmpty() -> brand
        !operator.isNullOrEmpty() -> operator
        else -> "(unnamed)"
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
                text = poiDisplayLabel(entry),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.poi_result_format,
                    entry.objectType,
                    stringResource(R.string.distance_unit_km, formatDistanceKm(entry.distance))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.distance_unit_km, formatDistanceKm(entry.distance)),
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
): Double {
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
