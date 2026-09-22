package com.naviveylin.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SearchTemplate.SearchCallback
import androidx.core.content.ContextCompat
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.search.FavoriteSearchMerger
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen for location search using [SearchTemplate].
 * Backed by [AutoSearchProvider] via [AutoEntryPoint].
 *
 * An empty query shows suggestions (spec: auto-search-suggestions): mode rows
 * ("Search POIs near me", permission-gated "Search contacts") plus recent
 * searches from the shared history store. Typing replaces the suggestions
 * with places results; a query with no results keeps the mode rows below the
 * "No results found" row.
 *
 * [mainDispatcher] / [ioDispatcher] are injectable for tests (same pattern as
 * the app's `defaultDispatcher`); production uses [Dispatchers.Main] /
 * [Dispatchers.Default].
 */
class SearchScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val initialQuery: String? = null,
    /**
     * Current car map viewport center, used as the search distance reference
     * when no GPS fix exists (spec: search-result-ranking — distance
     * reference). Null when this screen was opened from a surface that shows no
     * map (then the results are ordered by tier and quality without distances).
     */
    private val viewportCenter: () -> Pair<Double, Double>? = { null },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : Screen(carContext) {

    private val scope = carScreenScope("SearchScreen", mainDispatcher)
    private var searchJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val searchProvider = entryPoint.autoSearchProvider()
    private val locationProvider = entryPoint.autoLocationProvider()
    private val historyProvider = entryPoint.autoSearchHistoryProvider()
    private val favoritesProvider = entryPoint.autoFavoritesProvider()

    /** Merges favorite hits with native search results (spec: favorite-search). */
    private val favoriteSearchMerger = FavoriteSearchMerger()

    /** Recent searches; null until the async load completes (design D4). */
    private var history: List<String>? = null

    /** Reference point the current result list was ranked against (row distances). */
    private var lastReference: SearchReference? = null

    init {
        enableBackNavigation()
        initialQuery?.let { runSearch(it) }
        scope.launch {
            history = withContext(ioDispatcher) { historyProvider.load() }
            invalidate()
        }
    }

    override fun onGetTemplate(): SearchTemplate = carSearchTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carSearchTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): SearchTemplate {
        val builder = SearchTemplate.Builder(SearchCallbackImpl())
            .setShowKeyboardByDefault(true)
            // Explicit back — emulated hosts may not render their own.
            .setHeaderAction(Action.BACK)

        if (initialQuery != null && lastQuery.isBlank()) {
            builder.setInitialSearchText(initialQuery)
        }

        // Show the loading state while a search is in flight; once results
        // are ready, render them via setItemList (SearchTemplate results are
        // carried by the template itself, not a separate screen).
        if (searchJob?.isActive == true) {
            builder.setLoading(true)
        } else if (lastQuery.isBlank()) {
            // Empty query: suggestions (mode rows + recent searches).
            builder.setItemList(buildSuggestionsList())
        } else if (lastResults.isEmpty()) {
            // No results: keep the mode rows so the driver can pivot without
            // clearing the field (spec: auto-search-suggestions).
            builder.setItemList(buildNoResultsList())
        } else {
            builder.setItemList(buildResultsList())
        }
        return builder.build()
    }

    /**
     * Reference point for ordering and for the row distances: the last known GPS
     * fix, else the car map viewport center, else none (spec:
     * search-result-ranking). Read inside the search coroutine, and used for both
     * the ranking (passed to the provider) and the displayed distance, so the
     * numbers cannot contradict the order.
     */
    private fun searchReference(): SearchReference? {
        val fix = locationProvider.position().value
        if (fix != null && fix.lat.isFinite() && fix.lon.isFinite()) {
            return SearchReference(fix.lat, fix.lon)
        }
        val center = viewportCenter()
        return if (center != null && center.first.isFinite() && center.second.isFinite()) {
            SearchReference(center.first, center.second)
        } else {
            null
        }
    }

    private fun runSearch(searchText: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
            val (results, reference) = withContext(ioDispatcher) {
                val reference = searchReference()
                val native = searchProvider.searchLocations(
                    searchText,
                    SearchScreenMapper.MAX_RESULTS,
                    reference
                )
                val favorites = favoritesProvider.favoriteLocations().value.values.flatten()
                val criteria = SearchQueryParser.criteriaOf(searchText)
                val merged = favoriteSearchMerger.merge(searchText, favorites, native)
                    .map { result ->
                        result.copy(
                            isPerfectMatch = SearchResultRanker.isPerfectMatch(result.entry, criteria)
                        )
                    }
                    .take(SearchScreenMapper.MAX_RESULTS)
                // Returned, not assigned here: the reference is published on the
                // screen's dispatcher and read while building the template.
                merged to reference
            }
            Log.d(TAG, "Search results: ${results.size} for '$searchText'")
            lastReference = reference
            lastResults = results
            lastQuery = searchText
            invalidate()
        }
    }

    private fun buildSuggestionsList(): ItemList {
        val builder = ItemList.Builder()
        SearchScreenMapper.buildModeRows(
            carContext,
            showContactsRow = hasContactsPermission(),
            onPoiSearch = { onPoiSearch() },
            onContactsSearch = { onContactsSearch() }
        ).forEach { builder.addItem(it) }
        // Mode rows are shown until the history load completes (design D4).
        history?.let { entries ->
            SearchScreenMapper.buildHistoryRows(
                carContext, entries, onHistorySelected = { onHistorySelected(it) }
            ).forEach { builder.addItem(it) }
        }
        return builder.build()
    }

    private fun buildNoResultsList(): ItemList {
        val builder = ItemList.Builder()
        SearchScreenMapper.buildNoResultsRows(
            carContext,
            showContactsRow = hasContactsPermission(),
            onPoiSearch = { onPoiSearch() },
            onContactsSearch = { onContactsSearch() }
        ).forEach { builder.addItem(it) }
        return builder.build()
    }

    private fun buildResultsList(): ItemList {
        val builder = ItemList.Builder()

        for (result in lastResults) {
            builder.addItem(
                SearchScreenMapper.buildResultRow(
                    carContext,
                    result,
                    reference = lastReference,
                    onClick = {
                        val entry = result.entry
                        Log.d(TAG, "Details for: ${entry.label} (${entry.lat}, ${entry.lon})")
                        armScreenPush(carContext, scope, "DetailsScreen (search result)") {
                            DetailsScreen(
                                carContext, navigationViewModel, entry.lat, entry.lon,
                                nameHint = entry.label
                            )
                        }
                    }
                )
            )
        }

        return builder.build()
    }

    private fun onPoiSearch() {
        Log.d(TAG, "Opening POI search from suggestions")
        armScreenPush(carContext, scope, "PoiSearchScreen") {
            PoiSearchScreen(carContext, navigationViewModel)
        }
    }

    private fun onContactsSearch() {
        Log.d(TAG, "Opening address book from suggestions")
        armScreenPush(carContext, scope, "AddressBookScreen") {
            AddressBookScreen(carContext, navigationViewModel)
        }
    }

    /**
     * History tap pushes a fresh [SearchScreen] prefilled with the query
     * (design D2): the host owns the field text after construction, so the
     * query cannot be set inline — the pushed screen builds a new template
     * with the field prefilled and the search running.
     */
    private fun onHistorySelected(query: String) {
        Log.d(TAG, "History entry selected: $query")
        armScreenPush(carContext, scope, "SearchScreen (history entry)") {
            SearchScreen(
                carContext, navigationViewModel, initialQuery = query,
                mainDispatcher = mainDispatcher, ioDispatcher = ioDispatcher
            )
        }
    }

    /** Contacts row visibility follows the READ_CONTACTS permission (same
     *  check as [RootScreen]). */
    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    internal inner class SearchCallbackImpl : SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            if (searchText.isBlank()) {
                searchJob?.cancel()
                lastQuery = ""
                lastResults = emptyList()
                invalidate()
                return
            }
            runSearch(searchText)
        }
    }

    private var lastQuery: String = ""
    private var lastResults: List<MergedSearchResult> = emptyList()

    companion object {
        private const val TAG = "SearchScreen"
    }
}
