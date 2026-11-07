package com.naviveylin.ui.map

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.R
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.formatCoordinatePair
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.RegionFixReference
import com.naviveylin.core.search.RegionScopeDecision
import com.naviveylin.core.search.SearchGateState
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchRegionScope
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.core.search.SearchSlots
import com.naviveylin.core.search.resolveRegionScope
import com.naviveylin.data.StructuredAddressSearch
import com.naviveylin.location.GpsFix
import com.naviveylin.share.SharedLocationOutcome
import com.naviveylin.share.SharedLocationRequest
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Returns the admin region handle to scope the current search with,
 * resolving or re-resolving it from the current GPS fix as needed.
 * Returns 0 when no usable fix exists or resolution fails — the search
 * then runs unconstrained.
 */
internal fun MapCanvasViewModel.currentSearchAdminRegionHandle(): Long =
    searchAdminRegionHandleForFix(locationService.location.value)

/**
 * Pure scoping decision, delegated to the shared rule ([resolveRegionScope])
 * so the phone search panel and the car search scope identically (spec:
 * `location-search` — Search scoped by current admin region; spec:
 * `auto-search` — Car and phone region scoping parity). Exposed internal for
 * unit testing.
 */
internal fun MapCanvasViewModel.searchAdminRegionHandleForFix(fix: GpsFix?): Long {
    val outcome = resolveRegionScope(
        fix = fix?.let {
            RegionFixReference(lat = it.lat, lon = it.lon, accuracyMeters = it.accuracy)
        },
        previous = SearchRegionScope(
            handle = searchAdminRegionHandle,
            lat = searchAdminRegionLat,
            lon = searchAdminRegionLon,
            name = searchAdminRegionName
        ),
        releaseRegion = { handle ->
            try {
                client.releaseAdminRegion(handle)
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "releaseAdminRegion failed", e)
            }
        },
        resolveRegion = { lat, lon ->
            try {
                val h = client.resolveAdminRegion(lat, lon)
                Log.d(MapCanvasViewModel.TAG, "resolveAdminRegion -> handle=$h")
                h
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "resolveAdminRegion failed", e)
                0L
            }
        },
        scopeNameOf = { handle ->
            // Show the search scope region (the parent when sibling
            // expansion applies), not just the resolved region. Falls back
            // to the resolved region name when the scope lookup fails
            // (e.g. stale native library).
            try {
                val scopeName = client.getAdminRegionScopeName(handle)
                Log.d(MapCanvasViewModel.TAG, "getAdminRegionScopeName(handle=$handle) -> '$scopeName'")
                scopeName
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getAdminRegionScopeName failed", e)
                null
            }
        },
        regionNameOf = { handle ->
            try {
                client.getAdminRegionName(handle)
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getAdminRegionName failed", e)
                null
            }
        }
    )

    if (outcome.decision == RegionScopeDecision.FIX_UNUSABLE) {
        Log.d(
            MapCanvasViewModel.TAG,
            "searchAdminRegionHandleForFix: fix unusable (null=${fix == null}, " +
                "accuracy=${fix?.accuracy})"
        )
    }
    if (outcome.decision != RegionScopeDecision.REUSED) {
        searchAdminRegionHandle = outcome.scope.handle
        searchAdminRegionLat = outcome.scope.lat
        searchAdminRegionLon = outcome.scope.lon
        searchAdminRegionName = outcome.scope.name
        pushSearchAdminRegionState()
    }
    return searchAdminRegionHandle
}

internal fun MapCanvasViewModel.releaseSearchAdminRegion() {
    if (searchAdminRegionHandle != 0L) {
        try {
            client.releaseAdminRegion(searchAdminRegionHandle)
        } catch (e: Exception) {
            Log.e(MapCanvasViewModel.TAG, "releaseAdminRegion failed", e)
        }
        searchAdminRegionHandle = 0L
    }
    searchAdminRegionLat = Double.NaN
    searchAdminRegionLon = Double.NaN
    searchAdminRegionName = null
    pushSearchAdminRegionState()
}

/** Mirror the resolved region name into the UI state. */
internal fun MapCanvasViewModel.pushSearchAdminRegionState() {
    _uiState.value = _uiState.value.copy(searchAdminRegionName = searchAdminRegionName)
}

/**
 * A standalone location search, for callers that own no request of their own (the focused
 * suites and one-off lookups): it begins and ends a request for the place slot, so it
 * supersedes nothing a gate is running and leaves no request behind.
 */
internal suspend fun MapCanvasViewModel.searchLocations(query: String): List<LocationEntry> {
    val generation = client.beginSearch(SearchSlots.PLACES)

    try {
        return searchLocations(query, generation)
    } finally {
        client.cancelSearch(generation)
    }
}

/**
 * The location search of [generation]'s request. Every abort check inside the native walk
 * asks that generation, so a superseded or abandoned search stops instead of running to
 * completion (spec: `search-request-lifecycle`).
 */
internal suspend fun MapCanvasViewModel.searchLocations(query: String, generation: Long): List<LocationEntry> = withContext(defaultDispatcher) {
    val handle = currentSearchAdminRegionHandle()
    try {
        // A candidate set larger than the displayed list: the ranker needs
        // the candidates the backend's own order would have truncated away
        // (spec: search-result-ranking — "Candidate set larger than
        // displayed list").
        val startedNs = System.nanoTime()
        val entries = client.searchLocations(query, SearchResultRanker.CANDIDATE_LIMIT, handle, generation)
        val results = entries?.toList() ?: emptyList()
        // Candidate count and elapsed native time: the device check for the
        // larger candidate set (design D3) reads this line.
        Log.d(
            MapCanvasViewModel.TAG,
            "searchLocations: query='$query', adminRegionHandle=$handle, " +
                "candidates=${results.size}, limit=${SearchResultRanker.CANDIDATE_LIMIT}, " +
                "nativeMs=${(System.nanoTime() - startedNs) / 1_000_000}"
        )
        results
    } catch (e: Exception) {
        Log.e(MapCanvasViewModel.TAG, "searchLocations failed", e)
        emptyList()
    }
}

/**
 * Reference point for search distances and ranking: the last known GPS fix
 * (however old — the region scope uses it the same way), else the current map
 * center; null when neither is usable, in which case the list is ordered
 * without distances and shows none (spec: search-result-ranking).
 */
internal fun MapCanvasViewModel.searchDistanceReference(): SearchReference? {
    val fix = _uiState.value.gpsLocation
    if (fix != null && fix.lat.isFinite() && fix.lon.isFinite()) {
        return SearchReference(fix.lat, fix.lon)
    }
    val viewport = _uiState.value.viewport
    return if (viewport.centerLat.isFinite() && viewport.centerLon.isFinite()) {
        SearchReference(viewport.centerLat, viewport.centerLon)
    } else {
        null
    }
}

/**
 * Native location search merged with favorites matching the query
 * (spec: favorite-search): favorite hits first, native results marked
 * when they match a favorite, identical objects deduplicated.
 *
 * Native results are ranked by the match tier rule before merging (spec:
 * search-result-ranking), the perfect-match fact is attached to each row so
 * the marking and the order come from one decision, and the list is cut to
 * the displayed maximum as the best-ranked prefix of the candidate set.
 */
internal suspend fun MapCanvasViewModel.mergeSearchResults(query: String): List<MergedSearchResult> {
    val generation = client.beginSearch(SearchSlots.PLACES)

    try {
        return mergeSearchResults(query, generation)
    } finally {
        client.cancelSearch(generation)
    }
}

/** The merged list of [generation]'s request (see [searchLocations]). */
internal suspend fun MapCanvasViewModel.mergeSearchResults(query: String, generation: Long): List<MergedSearchResult> {
    val nativeResults = searchLocations(query, generation)
    // Everything after the native string search is CPU work over up to
    // CANDIDATE_LIMIT entries plus a second native query (the structured form
    // search) — kept off the main thread together with the ranking
    // (guidelines/Design.md §4, design D12). The state write below stays on
    // the caller's context.
    val (merged, reference) = withContext(defaultDispatcher) {
        // Full formatted addresses (street + house + PLZ + city) resolve via
        // the structured form search first; its house-level results rank
        // above the raw string results, which drop such queries when a postal
        // code sits inside the query (spec: location-search "Full formatted
        // address resolution").
        val structured = StructuredAddressSearch.resolve(query, client, generation)
        val combined = StructuredAddressSearch.merge(structured, nativeResults)
        val favorites = favoriteRepository.favorites.value.values.flatten()
        val criteria = SearchQueryParser.criteriaOf(query)
        val reference = searchDistanceReference()
        val ranked = SearchResultRanker.rank(combined, criteria, reference)
        val results = favoriteSearchMerger.merge(query, favorites, ranked)
            .map { result ->
                result.copy(
                    isPerfectMatch = SearchResultRanker.isPerfectMatch(result.entry, criteria)
                )
            }
            .take(SearchResultRanker.DISPLAY_LIMIT)
        results to reference
    }
    // Publish the reference the ranking used: the rows measure their
    // distance against the same point instead of recomputing it (one value,
    // so the numbers cannot contradict the order).
    _uiState.value = _uiState.value.copy(searchReference = reference)
    return merged
}

/** Called when user types in the search field. */
fun MapCanvasViewModel.onSearchQueryChanged(query: String) {
    _uiState.value = _uiState.value.copy(searchQuery = query)
    searchGate.request(query)
}

/** Called when user picks an entry from the search history: fills the search box and replays the search. */
fun MapCanvasViewModel.onHistoryEntrySelected(text: String) {
    onSearchQueryChanged(text)
}

/** Called when user selects a favorite from the favorites sheet. */
fun MapCanvasViewModel.onFavoriteSelected(fav: com.framstag.libosmscout.client.FavoriteLocation) {
    Log.d(MapCanvasViewModel.TAG, "onFavoriteSelected: name='${fav.name}' source=favourites")
    viewModelScope.launch {
        // Deactivate follow mode so the picked destination stays visible and a
        // deliberate route calculation always produces the overview (spec:
        // route-map-overview — favorite while free-driving). Mirrors the
        // search / POI / contacts selection paths.
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
        }
        val entry = LocationEntry().apply {
            label = fav.name
            lat = fav.lat
            lon = fav.lon
            matchQuality = "favorite"
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = entry,
            isLongPress = false,
            showFavoritesSheet = false,
            showDetailsSheet = true,
            isLoading = true
        )
        updateCenter(fav.lat, fav.lon)

        // Fetch object description and bounding box in parallel
        val descDeferred = async {
            withContext(defaultDispatcher) {
                try {
                    client.getDescription(fav.lat, fav.lon, _uiState.value.viewport.magnification.roundToInt())
                } catch (e: Exception) {
                    Log.e(MapCanvasViewModel.TAG, "getDescription failed for favorite", e)
                    null
                }
            }
        }
        val bboxDeferred = async {
            withContext(defaultDispatcher) {
                try {
                    client.getObjectBoundingBox(fav.lat, fav.lon, _uiState.value.viewport.magnification.roundToInt())
                } catch (e: Exception) {
                    Log.e(MapCanvasViewModel.TAG, "getObjectBoundingBox failed for favorite", e)
                    null
                }
            }
        }

        val desc = descDeferred.await()
        val bbox = bboxDeferred.await()

        // Determine target zoom: area bounding box → compute, else fixed node zoom.
        // The camera is the favorite coordinate (see updateCenter above), which is
        // generally not the object bbox midpoint, so the fit is verified against
        // that camera and keeps the documented area-favorites floor
        // (spec: fav-auto-zoom — Bounding box zoom calculation; FitVerification.kt).
        val targetMag = if (bbox != null && bbox.size == 4) {
            verifiedAreaFit(
                bbox, fav.lat, fav.lon, screenWidth, screenHeight, projectionDpi,
                minZoom = MapCanvasViewModel.MIN_AREA_ZOOM, angleRad = _uiState.value.viewport.angle
            )
        } else {
            MapCanvasViewModel.NODE_ZOOM
        }
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(magnification = targetMag)
        )

        if (desc != null && desc.entries.isNotEmpty()) {
            val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else fav.lat
            val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else fav.lon
            val objEntry = LocationEntry().apply {
                this.label = fav.name
                this.lat = objLat
                this.lon = objLon
                this.matchQuality = "favorite"
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = objEntry,
                objectDescription = desc,
                isLoading = false
            )
            updateCenter(objLat, objLon)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
        renderMap()
    }
}

/** Called when user selects a search result. */
fun MapCanvasViewModel.onSearchResultSelected(entry: LocationEntry) {
    Log.d(MapCanvasViewModel.TAG, "onSearchResultSelected: label='${entry.label}' source=search")
    // Capture the query before the state copy below clears it. Only real
    // search selections (non-blank query) are recorded; convenience entries
    // like "Current Location" are selected from an empty query.
    val query = _uiState.value.searchQuery
    viewModelScope.launch {
        if (query.isNotBlank()) {
            searchHistoryRepository.record(query)
        }
        // Deactivate follow mode so the selected result stays visible and
        // subsequent GPS updates do not re-center the viewport.
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = entry,
            objectDescription = null,
            isLongPress = false,
            searchQuery = "",
            searchResults = emptyList(),
            showDetailsSheet = true,
            detailsFromPoiSearch = false,
            isLoading = true
        )
        // The displayed query is empty again, so the request follows it: no committed
        // search text may be left behind for a later replay to collide with
        // (spec: `location-search` — A repeated query text still runs the search).
        searchGate.abandon()
        updateCenter(entry.lat, entry.lon)
        renderMap()

        // Fetch full object description at search result location
        val desc = withContext(defaultDispatcher) {
            try {
                client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getDescription failed for search result", e)
                null
            }
        }
        if (desc != null && desc.entries.isNotEmpty()) {
            val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else entry.lat
            val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else entry.lon
            val objEntry = LocationEntry().apply {
                this.label = entry.label
                this.lat = objLat
                this.lon = objLon
                this.matchQuality = entry.matchQuality
                this.adminRegionHierarchy = entry.adminRegionHierarchy
                this.name = entry.name
                this.objectType = entry.objectType
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = objEntry,
                objectDescription = desc,
                isLoading = false
            )
            updateCenter(objLat, objLon)
            renderMap()
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

/**
 * Open the unified search dialog (spec: search-dialog — entry points).
 * Resets to Places mode; if the dialog was last in POIs mode, the POI
 * viewport snapshot is restored first. Eagerly resolves the admin region
 * when a usable GPS fix exists so the region name shows immediately.
 */
fun MapCanvasViewModel.openSearch() {
    if (_uiState.value.searchMode == SearchMode.POIS) {
        restorePoiViewport()
    }
    _uiState.value = _uiState.value.copy(
        searchOpen = true,
        searchMode = SearchMode.PLACES
    )
    onSearchPanelOpened()
}

/**
 * Close the unified search dialog. If the dialog is in POIs mode, the
 * in-flight search is cancelled and the map center/zoom restored to the
 * values at POI-mode entry (spec: viewport restored when POI search closes).
 */
fun MapCanvasViewModel.closeSearch() {
    if (_uiState.value.searchMode == SearchMode.POIS) {
        restorePoiViewport()
    }
    searchPanelOpen = false
    releaseSearchAdminRegion()
    _uiState.value = _uiState.value.copy(searchOpen = false)
}

/**
 * Switch the search dialog mode. Entering POIs mode snapshots the viewport
 * and resets POI state (no category preselected, no preloaded results);
 * leaving it restores the snapshot and clears the POI marker. Per-mode
 * state (query, results) is preserved for the session (spec: search-dialog
 * — mode switch preserves state).
 */
fun MapCanvasViewModel.setSearchMode(mode: SearchMode) {
    val s = _uiState.value
    if (mode == s.searchMode) return
    when {
        mode == SearchMode.POIS -> {
            // Entering POI mode drops any POI search from before it.
            poiSearchGate.abandon()
            // Leaving Places mode abandons its search rather than letting it publish into the
            // POI view (spec: `search-dialog` — leaving Places mode abandons its search).
            searchGate.abandon()
            poiViewportSnapshot = s.viewport
            _uiState.value = s.copy(
                searchMode = mode,
                poiCategory = null,
                poiResults = emptyList(),
                isPoiSearching = false,
                poiSearchError = null
            )
        }
        s.searchMode == SearchMode.POIS -> {
            restorePoiViewport()
            _uiState.value = _uiState.value.copy(searchMode = mode)
        }
        else -> {
            _uiState.value = s.copy(searchMode = mode)
        }
    }
}

/**
 * Restore the POI-mode viewport snapshot (taken at POI-mode entry) and
 * clear the POI marker. Cancels any in-flight search.
 */
internal fun MapCanvasViewModel.restorePoiViewport() {
    // The POI view is being left: its search is abandoned outright (spec: `poi-search` — a
    // POI search keeps its own slot and is abandoned when its view is left).
    poiSearchGate.abandon()
    val snapshot = poiViewportSnapshot
    poiViewportSnapshot = null
    mapRenderer?.clearSearchSelected()
    if (snapshot != null) {
        _uiState.value = _uiState.value.copy(
            isPoiSearching = false,
            poiSelectedLat = Double.NaN,
            poiSelectedLon = Double.NaN,
            viewport = snapshot
        )
    } else {
        _uiState.value = _uiState.value.copy(
            isPoiSearching = false,
            poiSelectedLat = Double.NaN,
            poiSelectedLon = Double.NaN
        )
    }
    renderMap()
}

/** Select a POI category. Never triggers a search (spec: no preloaded results). */
fun MapCanvasViewModel.onPoiCategorySelected(category: String?) {
    _uiState.value = _uiState.value.copy(poiCategory = category)
}

/** Change the POI search radius. Never triggers a search. */
fun MapCanvasViewModel.onPoiRadiusChanged(radiusMeters: Double) {
    _uiState.value = _uiState.value.copy(poiRadiusMeters = radiusMeters)
}

/**
 * Run the POI search for the selected category around the current map
 * center. Blocking JNI call runs off the main thread; a previous in-flight
 * search is cancelled.
 */
fun MapCanvasViewModel.performPoiSearch() {
    val s = _uiState.value
    val category = s.poiCategory ?: return
    _uiState.value = s.copy(
        isPoiSearching = true,
        poiSearchError = null,
        poiSearchCenterLat = s.viewport.centerLat,
        poiSearchCenterLon = s.viewport.centerLon,
        poiSelectedLat = Double.NaN,
        poiSelectedLon = Double.NaN
    )
    // The gate supersedes this slot's previous request and owns its generation, so a second POI
    // search replaces the first and leaving the view can abandon it.
    poiSearchGate.request(category)
}

/**
 * The one POI search of [category] under [generation]'s request; `null` reports a failure, which
 * the collector words as [MapCanvasUiState.poiSearchError].
 */
internal suspend fun MapCanvasViewModel.searchPoisOnce(category: String, generation: Long): List<PoiEntry>? {
    val s = _uiState.value
    val lat = s.poiSearchCenterLat
    val lon = s.poiSearchCenterLon
    val radiusMeters = s.poiRadiusMeters

    return withContext(defaultDispatcher) {
        try {
            val typeNames = PoiCategories.getTypeNames(category)

            if (typeNames == null || typeNames.isEmpty()) {
                emptyList()
            } else {
                client.searchPOIsByTypes(
                    typeNames, lat, lon, radiusMeters, MapCanvasViewModel.MAX_POI_RESULTS, generation
                )?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(MapCanvasViewModel.TAG, "POI search failed", e)
            null
        }
    }
}

/** Maps the POI gate's state onto the POI sheet's state. */
internal fun MapCanvasViewModel.onPoiSearchStateChanged(state: SearchGateState<List<PoiEntry>?>) {
    when (state) {
        is SearchGateState.Idle -> Unit
        is SearchGateState.Searching ->
            _uiState.value = _uiState.value.copy(isPoiSearching = true, poiSearchError = null)
        is SearchGateState.Cleared ->
            _uiState.value = _uiState.value.copy(isPoiSearching = false, poiResults = emptyList())
        is SearchGateState.Completed -> {
            val results = state.results
            _uiState.value = if (results != null) {
                _uiState.value.copy(
                    isPoiSearching = false,
                    poiResults = results,
                    poiSearchError = null
                )
            } else {
                _uiState.value.copy(
                    isPoiSearching = false,
                    poiSearchError = context.getString(R.string.poi_search_failed)
                )
            }
        }
    }
}

/**
 * Select a POI result: close the POI sheet, open the details sheet with
 * the object description fetched off-main, center the map on the POI, and
 * zoom to fit both the current location and the POI (spec: details via
 * single click). The POI is marked with the search-selection marker.
 */
fun MapCanvasViewModel.onPoiEntryClick(entry: PoiEntry) {
    Log.d(MapCanvasViewModel.TAG, "onPoiEntryClick: label='${entry.label}' source=poi")
    viewModelScope.launch {
        // Disengage follow mode so a GPS fix does not yank the map away
        // while the user is looking at the details sheet.
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
        }
        val locEntry = LocationEntry().apply {
            this.label = entry.label.ifEmpty { "(unnamed)" }
            this.lat = entry.lat
            this.lon = entry.lon
            this.matchQuality = "poi"
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = locEntry,
            objectDescription = null,
            isLongPress = false,
            searchOpen = false,
            searchMode = SearchMode.POIS,
            poiSelectedLat = entry.lat,
            poiSelectedLon = entry.lon,
            detailsFromPoiSearch = true,
            showDetailsSheet = true,
            isLoading = true
        )

        // Mark the POI, center the map on it, and fit current location + POI
        mapRenderer?.setSearchSelected(entry.lat, entry.lon)
        updateCenter(entry.lat, entry.lon)
        val fitMag = poiEntryFitMagnification(entry)
        if (fitMag != _uiState.value.viewport.magnification) {
            // A camera fit lands directly - no walked zoom animation.
            updateMagnification(fitMag, walk = false)
        }
        renderMap()

        val desc = withContext(defaultDispatcher) {
            try {
                client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getDescription failed for POI", e)
                null
            }
        }
        if (desc != null && desc.entries.isNotEmpty()) {
            val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else entry.lat
            val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else entry.lon
            val objEntry = LocationEntry().apply {
                this.label = locEntry.label
                this.lat = objLat
                this.lon = objLon
                this.matchQuality = "object"
                this.name = locEntry.label
                this.adminRegionHierarchy = resolveRegionName(objLat, objLon)
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = objEntry,
                objectDescription = desc,
                isLoading = false
            )
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

/**
 * Magnification that fits the current location and the POI with ~30%
 * margin, or the current magnification when no GPS fix exists.
 *
 * The camera stays on the POI ([onPoiEntryClick] centers there), which is one
 * end of the fitted bbox rather than its midpoint, so the fit is verified
 * around that camera. Its floor is the render-stability minimum
 * ([MIN_MAG]), not the area-favorites floor: a POI whose distance does not fit
 * at 14 must still be shown together with the current location
 * (spec: poi-search — Distant POI zooms out below the area-favorites floor).
 */
internal fun MapCanvasViewModel.poiEntryFitMagnification(entry: PoiEntry): Double {
    val currentMag = _uiState.value.viewport.magnification
    val loc = locationService.location.value ?: return currentMag
    val lat1 = loc.lat
    val lon1 = loc.lon
    val dLat = kotlin.math.abs(entry.lat - lat1)
    val dLon = kotlin.math.abs(entry.lon - lon1)
    if (dLat < 1e-9 && dLon < 1e-9) return currentMag
    val marginLat = dLat * 0.3
    val marginLon = dLon * 0.3
    val bbox = doubleArrayOf(
        kotlin.math.min(lat1, entry.lat) - marginLat,
        kotlin.math.max(lat1, entry.lat) + marginLat,
        kotlin.math.min(lon1, entry.lon) - marginLon,
        kotlin.math.max(lon1, entry.lon) + marginLon
    )
    return verifiedAreaFit(
        bbox, entry.lat, entry.lon, screenWidth, screenHeight, projectionDpi,
        minZoom = MapCanvasViewModel.MIN_MAG, angleRad = _uiState.value.viewport.angle
    )
}

/**
 * Resolve the admin region name for a location (one-shot; handle released).
 * Used to fill the details dialog's area/address for results that carry no
 * admin region hierarchy (POI search, long-press).
 */
internal fun MapCanvasViewModel.resolveRegionName(lat: Double, lon: Double): String? {
    return try {
        val handle = client.resolveAdminRegion(lat, lon)
        if (handle == 0L) return null
        val name = client.getAdminRegionName(handle)
        client.releaseAdminRegion(handle)
        name
    } catch (e: Exception) {
        Log.e(MapCanvasViewModel.TAG, "resolveRegionName failed", e)
        null
    }
}

/** Called when user long-presses on the map. */
/**
 * Long press on the map.
 *
 * @param screenX press point in canvas pixels, or null when the caller has no screen
 *   point (tests). Diagnostics identity only: the file-backed entry records the press
 *   point and the magnification instead of a coordinate (spec: auto-diagnostics —
 *   Diagnostics carry no coordinates), which is what the screen→geo mapping check
 *   compares.
 */
fun MapCanvasViewModel.onLongPress(lat: Double, lon: Double, screenX: Int? = null, screenY: Int? = null) {
    val mapName = currentMapKey ?: "?"
    Log.d(MapCanvasViewModel.TAG, "onLongPress x=${screenX ?: -1} y=${screenY ?: -1} map=$mapName")
    // File-backed diagnostics: verify the resolved point against the
    // clicked object (screen→geo mapping check).
    com.naviveylin.core.DiagnosticsLog.log(
        "LONGPRESS",
        "x=${screenX ?: -1} y=${screenY ?: -1} mag=${_uiState.value.viewport.magnification} map=$mapName"
    )
    showCandidatesFor(
        lat = lat,
        lon = lon,
        zoom = _uiState.value.viewport.magnification.roundToInt(),
        // Locale-stable pair (spec: i18n-l10n — Coordinate string is
        // locale-stable): the pair layout comes from the resource, the
        // locale does not follow the device.
        label = formatCoordinatePair(lat, lon, context.getString(R.string.coordinates_format))
    )
}

/**
 * Shared candidate flow for a coordinate: query the map database for
 * objects near the point and show the picker, or fall back to the raw
 * coordinate entry. Used by long-press and shared-location processing.
 *
 * @param nameHint a place name the shared input carried (the share subject, else the place
 *   name the link itself carries): candidates whose name contains it are listed first. It is
 *   a ranking, never a filter — nothing is dropped.
 * @param openDetailsOnNoCandidates when no objects are found, open the
 *   details sheet on the raw coordinate instead of showing only the marker
 *   (share flow; long-press keeps the marker-only behavior).
 * @param openDetailsOnSingleCandidate when exactly one object is found, open the details
 *   sheet for it instead of a one-row picker (share flow; long-press keeps the picker as
 *   confirmation of the point the user chose).
 */
internal fun MapCanvasViewModel.showCandidatesFor(
    lat: Double,
    lon: Double,
    zoom: Int,
    label: String,
    nameHint: String? = null,
    openDetailsOnNoCandidates: Boolean = false,
    openDetailsOnSingleCandidate: Boolean = false
) {
    viewModelScope.launch {
        val entry = LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            this.matchQuality = "coordinate"
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = entry,
            isLongPress = true,
            isLoading = true
        )
        val candidates = withContext(defaultDispatcher) {
            try {
                client.getDescriptionCandidates(lat, lon, zoom)
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getDescriptionCandidates failed", e)
                emptyList<ObjectDescription>()
            }
        }
        if (candidates.isNotEmpty()) {
            val ranked = rankCandidatesByName(candidates, nameHint)
            if (openDetailsOnSingleCandidate && ranked.size == 1) {
                // Exactly one object at the point: the picker would ask a question with one
                // answer, so the details flow opens for it (spec: share/location-receiving —
                // Exactly one candidate opens the details sheet).
                _uiState.value = _uiState.value.copy(isLoading = false)
                onCandidateSelected(ranked.single())
            } else {
                _uiState.value = _uiState.value.copy(
                    candidateDescriptions = ranked,
                    showCandidatePicker = true,
                    isLoading = false,
                    showDetailsSheet = false,
                    detailsFromPoiSearch = false
                )
            }
        } else {
            // No objects at the point: keep the coordinate entry, no picker.
            _uiState.value = _uiState.value.copy(
                candidateDescriptions = emptyList(),
                showCandidatePicker = false,
                objectDescription = null,
                isLoading = false,
                showDetailsSheet = openDetailsOnNoCandidates,
                detailsFromPoiSearch = false
            )
        }
        renderMap()
    }
}

/**
 * Process a share outcome: wait for the map to be ready, then dispatch.
 * Coordinates go through the candidate flow (fixed high zoom, the shared name hint
 * ranking the candidates, details directly on a single hit and on the raw coordinate
 * when nothing is found); address text opens the search panel with the query filled; an
 * unreadable map link is reported and never searched (spec: share/location-receiving).
 */
internal suspend fun MapCanvasViewModel.processSharedLocation(outcome: SharedLocationOutcome) {
    // Candidate lookup and search need the map database open.
    mapReady.first { it }
    sharedLocationHandler.consume()

    when (outcome) {
        SharedLocationOutcome.UnusableLink -> {
            // Shape only: the link text is exactly what the search must not receive.
            Log.d(MapCanvasViewModel.TAG, "Shared location: shape=unusable map=${currentMapKey ?: "?"}")
            showSnackbar(context.getString(R.string.shared_link_unreadable))
        }
        is SharedLocationOutcome.Request -> processSharedRequest(outcome.request)
    }
}

internal fun MapCanvasViewModel.processSharedRequest(request: SharedLocationRequest) {
    if (request.hasCoordinates) {
        val lat = request.lat!!
        val lon = request.lon!!
        Log.d(
            MapCanvasViewModel.TAG,
            "Shared location: shape=coordinates " +
                "labelHint=${if (request.label != null) "present" else "none"} " +
                "mag=${_uiState.value.viewport.magnification} map=${currentMapKey ?: "?"}"
        )
        updateCenter(lat, lon)
        showCandidatesFor(
            lat = lat,
            lon = lon,
            zoom = MapCanvasViewModel.SHARE_CANDIDATE_ZOOM,
            label = request.label ?: String.format(Locale.US, "%.5f, %.5f", lat, lon),
            nameHint = request.label,
            openDetailsOnNoCandidates = true,
            openDetailsOnSingleCandidate = true
        )
    } else {
        val query = request.query
        if (!query.isNullOrBlank()) {
            // Shape, not text: a shared text can itself be a coordinate pair.
            Log.d(MapCanvasViewModel.TAG, "Shared location: shape=query chars=${query.length}")
            onSearchQueryChanged(query)
            openSearch()
        }
    }
}

/**
 * Called when the user selects one candidate from the picker: open the
 * details sheet for that object and place the marker on the object center
 * (falling back to the press point when the object has no coordinates).
 */
fun MapCanvasViewModel.onCandidateSelected(desc: ObjectDescription) {
    val s = _uiState.value
    val pressLat = s.selectedLocation?.lat ?: Double.NaN
    val pressLon = s.selectedLocation?.lon ?: Double.NaN
    val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else pressLat
    val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else pressLon
    val objEntry = LocationEntry().apply {
        this.label = s.selectedLocation?.label ?: formatCoordinatePair(objLat, objLon)
        this.lat = objLat
        this.lon = objLon
        this.matchQuality = "object"
        this.adminRegionHierarchy = resolveRegionName(objLat, objLon)
    }
    _uiState.value = s.copy(
        selectedLocation = objEntry,
        objectDescription = desc,
        showDetailsSheet = true,
        isLongPress = true,
        showCandidatePicker = false,
        candidateDescriptions = emptyList(),
        detailsFromPoiSearch = false
    )
    updateCenter(objLat, objLon)
    renderMap()
}

/** Dismiss the candidate picker without opening details. */
fun MapCanvasViewModel.dismissCandidatePicker() {
    _uiState.value = _uiState.value.copy(
        candidateDescriptions = emptyList(),
        showCandidatePicker = false,
        showDetailsSheet = false,
        objectDescription = null,
        isLongPress = false,
        detailsFromPoiSearch = false
    )
}

/** Toggle favorites sheet visibility. */
fun MapCanvasViewModel.toggleFavoritesSheet() {
    _uiState.value = _uiState.value.copy(
        showFavoritesSheet = !_uiState.value.showFavoritesSheet
    )
}

/**
 * Re-check READ_CONTACTS and update [MapCanvasUiState.addressBookAvailable]
 * (spec: address-book-permission — permission state drives feature
 * visibility). Called on init, on permission result, and on resume so
 * later grants/revocations in system settings apply without a restart.
 */
fun MapCanvasViewModel.refreshAddressBookAvailability() {
    val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        )
    if (_uiState.value.addressBookAvailable != granted) {
        _uiState.value = _uiState.value.copy(addressBookAvailable = granted)
    }
}

/**
 * Show the resolved address-book object in the existing details view
 * (spec: address-book-search — details view for the resolved object).
 * Mirrors search-result selection but without search history recording.
 */
fun MapCanvasViewModel.onAddressBookResultSelected(entry: LocationEntry) {
    Log.d(MapCanvasViewModel.TAG, "onAddressBookResultSelected: label='${entry.label}' source=address-book")
    viewModelScope.launch {
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
            settingsStorage.update { it.copy(followMode = false) }
        }
        _uiState.value = _uiState.value.copy(
            searchOpen = false,
            searchMode = SearchMode.CONTACTS,
            selectedLocation = entry,
            objectDescription = null,
            isLongPress = false,
            showDetailsSheet = true,
            detailsFromPoiSearch = false,
            detailsFromAddressBook = true,
            isLoading = true
        )
        updateCenter(entry.lat, entry.lon)
        renderMap()

        // Fetch full object description at the resolved location
        val desc = withContext(defaultDispatcher) {
            try {
                client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
            } catch (e: Exception) {
                Log.e(MapCanvasViewModel.TAG, "getDescription failed for address-book result", e)
                null
            }
        }
        if (desc != null && desc.entries.isNotEmpty()) {
            val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else entry.lat
            val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else entry.lon
            val objEntry = LocationEntry().apply {
                this.label = entry.label
                this.lat = objLat
                this.lon = objLon
                this.matchQuality = entry.matchQuality
                this.adminRegionHierarchy = entry.adminRegionHierarchy
                this.name = entry.name
                this.objectType = entry.objectType
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = objEntry,
                objectDescription = desc,
                isLoading = false
            )
            updateCenter(objLat, objLon)
            renderMap()
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

/** Select the current GPS position as a search result (centers map + details sheet). */
fun MapCanvasViewModel.selectCurrentLocation() {
    val loc = locationService.location.value ?: return
    val entry = LocationEntry().apply {
        label = context.getString(R.string.current_location)
        lat = loc.lat
        lon = loc.lon
        matchQuality = "coordinate"
    }
    onSearchResultSelected(entry)
}

/** Dismiss snackbar. */
fun MapCanvasViewModel.clearSnackbar() {
    _uiState.value = _uiState.value.copy(snackbarMessage = null)
}

/** Show a snackbar message. */
fun MapCanvasViewModel.showSnackbar(message: String) {
    _uiState.value = _uiState.value.copy(snackbarMessage = message)
}

/**
 * Search panel opened: eagerly resolve the admin region (when a usable GPS
 * fix exists) so the region name shows above the search field immediately.
 */
fun MapCanvasViewModel.onSearchPanelOpened() {
    searchPanelOpen = true
    viewModelScope.launch {
        withContext(defaultDispatcher) { currentSearchAdminRegionHandle() }
    }
}

/** Clear search state. */
fun MapCanvasViewModel.clearSearch() {
    searchPanelOpen = false
    releaseSearchAdminRegion()
    // The dialog is going away: the running search is abandoned outright, not left to run to
    // completion (spec: `search-request-lifecycle` — abandon without a successor).
    searchGate.abandon()
    _uiState.value = _uiState.value.copy(
        searchQuery = "",
        searchResults = emptyList(),
        isSearching = false
    )
}

/**
 * The collectors started before anything else: the search-history read and the address-book availability probe.
 */
internal fun MapCanvasViewModel.wireStartup() {
    viewModelScope.launch { searchHistoryRepository.load() }
    refreshAddressBookAvailability()
}

/**
 * The share-sheet and deep-link outcomes, processed while the map screen is up.
 */
internal fun MapCanvasViewModel.wireSharedLocation() {
    // Share sheet / deep links: process when the map screen is up. Coordinates go
    // through the candidate flow, address text opens the search panel with the query
    // filled, and a map link that cannot be read is reported, never searched.
    viewModelScope.launch {
        sharedLocationHandler.outcome.collect { outcome ->
            if (outcome != null) processSharedLocation(outcome)
        }
    }
}

/**
 * The places search's request lifecycle, mirrored into the UI state.
 */
internal fun MapCanvasViewModel.wirePlaceSearch() {
    // The gate owns the phone places search's request lifecycle — debounce, the minimum query
    // length, supersede and abandon — the same seam the other surfaces use
    // (spec: `search-request-lifecycle`). The search itself is [mergeSearchResults], which needs
    // the request's generation to stop being aborted halfway.
    viewModelScope.launch {
        searchGate.state.collect { state ->
            when (state) {
                is SearchGateState.Idle -> Unit
                is SearchGateState.Searching ->
                    _uiState.value = _uiState.value.copy(isSearching = true)
                is SearchGateState.Cleared ->
                    _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
                is SearchGateState.Completed ->
                    _uiState.value = _uiState.value.copy(searchResults = state.results, isSearching = false)
            }
        }
    }
}

/**
 * The POI sheet's own request slot, mirrored into the sheet's state.
 */
internal fun MapCanvasViewModel.wirePoiSearch() {
    // The POI sheet's own slot: one collector so its state follows its gate.
    viewModelScope.launch {
        poiSearchGate.state.collect { state -> onPoiSearchStateChanged(state) }
    }
}

/**
 * Re-renders when the favourites change.
 */
internal fun MapCanvasViewModel.wireFavoriteChanges() {
    // Re-render when favorites change
    viewModelScope.launch {
        favoriteRepository.favorites.collect {
            val renderer = mapRenderer ?: return@collect
            val favs = favoriteRepository.favorites.value
            val allFavs = mutableListOf<com.framstag.libosmscout.client.FavoriteLocation>()
            for ((_, list) in favs) {
                allFavs.addAll(list)
            }
            renderer.setFavoriteLocations(allFavs.toTypedArray())
        }
    }
}
