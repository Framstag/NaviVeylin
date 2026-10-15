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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.SEARCH_ROW_ICON_DP
import com.naviveylin.core.SearchRowIcons
import com.naviveylin.core.TypeIconSource
import com.naviveylin.core.search.FavoriteSearchMerger
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.SearchGate
import com.naviveylin.core.search.SearchGateState
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.core.search.SearchSlots
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
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

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val searchProvider = entryPoint.autoSearchProvider()
    private val locationProvider = entryPoint.autoLocationProvider()
    private val historyProvider = entryPoint.autoSearchHistoryProvider()
    private val favoritesProvider = entryPoint.autoFavoritesProvider()
    private val clientProvider = entryPoint.autoClientProvider()

    /** One car search's outcome: the rows and the reference they were ranked and measured against. */
    private data class CarSearchResult(
        val results: List<MergedSearchResult>,
        val reference: SearchReference?
    )

    /**
     * The screen's search request lifecycle: the debounce, the minimum query length (2 characters,
     * the same as the phone), supersede and abandon (spec: `search-request-lifecycle`). The provider
     * is the request-lifecycle seam, so a request this gate abandons stops the native walk instead
     * of running to completion.
     */
    private val searchGate = SearchGate(
        scope = scope,
        requests = searchProvider,
        slot = SearchSlots.CAR,
        debounceMs = SearchScreenMapper.SEARCH_DEBOUNCE_MS,
        requestDispatcher = { ioDispatcher },
        search = { query, generation -> searchOnce(query, generation) }
    )

    /** Merges favorite hits with native search results (spec: favorite-search). */
    private val favoriteSearchMerger = FavoriteSearchMerger()

    /** Recent searches; null until the async load completes (design D4). */
    private var history: List<String>? = null

    /** Reference point the current result list was ranked against (row distances). */
    private var lastReference: SearchReference? = null

    init {
        enableBackNavigation()
        // The screen's own scope ends with the screen: a search still running when the driver
        // leaves is abandoned (so its native walk stops) and cannot publish into a template
        // (spec: `auto-search` — the car search screen stops its own search when it leaves its
        // started period; spec: `auto/screen-observation` — a stopped screen performs no host work).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                searchGate.abandon()
                scope.cancel()
            }
        })
        // The gate's state becomes this screen's result state, and one publication is one template
        // refresh: the template is the host-facing half of a search here.
        scope.launch {
            searchGate.state.collect { state -> onSearchStateChanged(state) }
        }
        initialQuery?.let { runSearch(it) }
        scope.launch {
            history = withContext(ioDispatcher) { historyProvider.load() }
            invalidate()
        }
    }

    /** Maps the gate's state onto the fields the template build reads, and refreshes the host. */
    private suspend fun onSearchStateChanged(state: SearchGateState<CarSearchResult>) {
        when (state) {
            is SearchGateState.Idle -> Unit
            is SearchGateState.Searching -> invalidate()
            is SearchGateState.Cleared -> {
                lastQuery = ""
                lastResults = emptyList()
                invalidate()
            }
            is SearchGateState.Completed -> {
                lastReference = state.results.reference
                lastResults = state.results.results
                lastQuery = state.query
                prefetchTypeIcons(state.results.results)
                invalidate()
            }
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
        if (searchGate.state.value is SearchGateState.Searching) {
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
        searchGate.request(searchText)
    }

    /**
     * The one search of [generation]'s request, off the screen's dispatcher: the provider's ranked
     * rows plus the reference they were ranked against (the same one the rows measure their distance
     * from, so the numbers cannot contradict the order).
     */
    private suspend fun searchOnce(searchText: String, generation: Long): CarSearchResult =
        withContext(ioDispatcher) {
            val reference = searchReference()
            val native = searchProvider.searchLocations(
                searchText,
                SearchScreenMapper.MAX_RESULTS,
                reference,
                generation
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
            Log.d(TAG, "Search results: ${merged.size} for '$searchText'")
            CarSearchResult(merged, reference)
        }

    /**
     * Resolve the rows' type artwork before the template is built. The build runs on the host
     * thread and must not touch native code, so it reads the cache ([SearchRowIcons.peek]) and this
     * pass fills it — off the main thread, once per distinct type, which is what the single-flight
     * cache makes cheap (spec: `search-result-row-icon` — No native artwork request on a
     * host-facing build). A type without artwork resolves to nothing and is not retried.
     */
    private suspend fun prefetchTypeIcons(results: List<MergedSearchResult>) {
        val client = runCatching { clientProvider.client() }.getOrNull() ?: return
        val sizePx = (SEARCH_ROW_ICON_DP * carContext.resources.displayMetrics.density).toInt()
        if (sizePx <= 0) return
        val source = TypeIconSource { objectType, px -> client.renderTypeIcon(objectType, px) }
        withContext(ioDispatcher) {
            results.map { it.entry.objectTypeName }
                .filterNotNull()
                .distinct()
                .forEach { objectType -> SearchRowIcons.resolve(source, objectType, sizePx) }
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
        val sizePx = (SEARCH_ROW_ICON_DP * carContext.resources.displayMetrics.density).toInt()

        for (result in lastResults) {
            builder.addItem(
                SearchScreenMapper.buildResultRow(
                    carContext,
                    result,
                    reference = lastReference,
                    // Read-only: the artwork is already resolved (see prefetchTypeIcons), so this
                    // host-thread build never asks the bridge for it.
                    typeIcon = result.entry.objectTypeName?.let { SearchRowIcons.peek(it, sizePx) },
                    onClick = {
                        val entry = result.entry
                        Log.d(TAG, "Details for: ${entry.label}")
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
            // Below the minimum length (and for an empty query) the gate runs nothing and clears the
            // result area — the same rule as the phone (spec: `search-request-lifecycle`).
            if (searchText.isBlank()) {
                searchGate.abandon()
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
