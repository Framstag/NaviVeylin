package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.search.POI_CANDIDATE_CAP
import com.naviveylin.core.search.SearchGate
import com.naviveylin.core.search.SearchGateState
import com.naviveylin.core.search.SearchRequestClient
import com.naviveylin.core.search.SearchSlots
import com.naviveylin.core.search.filterPoiEntries
import com.naviveylin.core.search.poiDisplayLabel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * POI search results for one [com.framstag.libosmscout.client.PoiCategories]
 * category, searched around the current GPS position.
 *
 * Each result row offers a "Navigate to" action (route + turn-by-turn via
 * the shared navigation controller). Requires a GPS fix; without one an
 * explanatory row is shown instead.
 *
 * The list presents at most the row capacity the connected host displays and keeps the rows past it
 * reachable through its final page row (spec: `car-list-row-limit`; design D1/D3) — the host silently
 * ignores the rows over that capacity — and the walk is asked for twice as many candidates as the page
 * shows (spec: `auto-search` — Search result limit), so the page row continues a list the walk already
 * fetched instead of buying a second one.
 *
 * A name filter narrows the list (spec: `auto-search` — A car POI result list can be narrowed by name;
 * change `poi-name-filter`). The "Filter by name" row is the list's first row and opens
 * [PoiFilterScreen], which applies its term to *this* screen and pops back, so the back stack does not
 * grow per refinement (design D5). Under a term the walk is asked for the candidate cap instead of
 * twice the capacity, because a filter applied to a page the display limit already truncated would
 * miss a match beyond it (design D2).
 */
class PoiResultsScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val category: String,
    private val categoryLabel: String,
    /**
     * The rows a page opened by this screen's page row carries: the search already fetched them, so the
     * page slices them instead of running the walk again. Null on the first page, which searches.
     */
    private val prefetched: List<PoiEntry>? = null,
    /** The page of the fetched rows to show; 0 is their first page (spec: `car-list-row-limit`). */
    private val page: Int = 0,
    /** The name filter this screen carries, as [PoiFilterScreen] last applied it (design D5). */
    private var filter: String = "",
    /** Production uses the main dispatcher; the tests inject their own, as the other car screens do. */
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : Screen(carContext) {

    private val scope = carScreenScope("PoiResultsScreen", mainDispatcher)

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )

    /** One POI search's outcome: the rows, or the sentence the screen shows instead of them. */
    private data class PoiOutcome(val results: List<PoiEntry>?, val message: String?)

    /**
     * The request-lifecycle seam. The client is resolved per call and never in the constructor, and the
     * gate runs these calls off this screen's host-facing dispatcher, because the native client must not
     * be built on the car-app main thread (spec: `auto-map-renderer` — Provider resolution does not build
     * the client; `guidelines/UICar.md` §3a).
     */
    private val requests = object : SearchRequestClient {
        override fun beginSearch(slot: String): Long =
            entryPoint.autoClientProvider().client().beginSearch(slot)

        override fun cancelSearch(generation: Long): Boolean =
            entryPoint.autoClientProvider().client().cancelSearch(generation)
    }

    /**
     * This screen's search request. It is button-triggered (a category row was tapped), so it has no
     * debounce and no minimum text, but it keeps its own slot and generation: leaving the screen abandons
     * the walk instead of letting it run to completion (spec: `poi-search` — a POI search keeps its own slot
     * and is abandoned when its view is left; spec: `search-request-lifecycle`).
     */
    private val searchGate = SearchGate(
        scope = scope,
        requests = requests,
        slot = SearchSlots.CAR_POI,
        debounceMs = 0L,
        minQueryLength = 0,
        requestDispatcher = { ioDispatcher },
        search = { _, generation -> searchOnce(generation) }
    )

    private var results: List<PoiEntry>? = null
    private var error: String? = null
    private var loading = true

    /**
     * The row capacity this screen presents (spec: `car-list-row-limit` — A car list never presents more
     * rows than the host displays; `guidelines/UICar.md` §3). Starts at the library default so the first
     * template — which the host may ask for before the capacity query returns — is already bounded, and is
     * replaced by the host's answer on the screen's own scope, off the host thread.
     */
    private var rowCapacity: Int = CarListCapacityReader.LIBRARY_DEFAULT

    /**
     * The candidates the walk is asked for: twice the rows this screen shows, the floor `auto-search`'s
     * "Search result limit" states ("at least twice the displayed maximum"), so the fetched rows always
     * cover the page and the one its page row opens.
     *
     * Under a name filter the walk is asked for the candidate cap instead — and never for less than
     * twice the capacity, which is what a host reporting more than half the cap would otherwise lose
     * (spec: `auto-search` — A filtered walk asks for the candidate cap; design D2).
     */
    private val fetchBudget: Int get() = if (filter.isBlank()) {
        2 * rowCapacity
    } else {
        maxOf(POI_CANDIDATE_CAP, 2 * rowCapacity)
    }

    init {
        enableBackNavigation()
        prefetched?.let { carried ->
            loading = false
            results = carried
        }
        // Leaving the screen abandons its search and ends the screen's own work (spec: `poi-search`;
        // spec: `auto/screen-observation` — a stopped screen performs no renderer or host work).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                searchGate.abandon()
                scope.cancel()
            }
        })
        scope.launch {
            searchGate.state.collect { state -> onSearchStateChanged(state) }
        }
        scope.launch {
            rowCapacity = withContext(ioDispatcher) { CarListCapacityReader.read(carContext) }
            if (prefetched == null) {
                // The category is the whole request here: the gate searches it once, immediately, with a
                // budget twice the capacity the host just reported.
                searchGate.request(category)
            } else {
                invalidate()
            }
        }
    }

    /** Maps the gate's state onto what this screen shows, and refreshes the host once per change. */
    private fun onSearchStateChanged(state: SearchGateState<PoiOutcome>) {
        when (state) {
            is SearchGateState.Idle -> Unit
            is SearchGateState.Searching -> {
                loading = true
                invalidate()
            }
            is SearchGateState.Cleared -> {
                loading = false
                results = emptyList()
                invalidate()
            }
            is SearchGateState.Completed -> {
                val outcome = state.results
                loading = false
                results = outcome.results
                error = outcome.message
                invalidate()
            }
        }
    }

    /**
     * The one POI search of [generation]'s request: the current fix, this category's types, and the
     * generation that lets the native walk stop. Returns the rows, or the sentence to show instead (no
     * fix, or a failure).
     */
    private suspend fun searchOnce(generation: Long): PoiOutcome = withContext(ioDispatcher) {
        val pos = entryPoint.autoLocationProvider().position().value
        if (pos == null) {
            return@withContext PoiOutcome(
                null,
                carContext.getString(
                    com.naviveylin.core.R.string.navigation_failure_gps_signal_missing
                )
            )
        }

        try {
            val client = entryPoint.autoClientProvider().client()
            val typeNames = PoiCategories.getTypeNames(category)

            val rows = if (typeNames.isEmpty()) {
                emptyList()
            } else {
                client.searchPOIsByTypes(
                    typeNames, pos.lat, pos.lon, SEARCH_RADIUS_M, fetchBudget, generation
                )?.toList() ?: emptyList()
            }

            PoiOutcome(rows, null)
        } catch (e: Exception) {
            Log.w(TAG, "POI search failed for $category", e)
            PoiOutcome(
                null,
                carContext.getString(
                    R.string.poi_search_failed,
                    e.message ?: carContext.getString(R.string.unknown_error)
                )
            )
        }
    }

    /**
     * Apply a term the filter screen submitted: re-search this category under it and show the narrowed
     * list on this same screen, which is what keeps the back stack constant (design D5).
     */
    internal fun applyFilter(term: String) {
        filter = term
        loading = true
        error = null
        invalidate()
        searchGate.request(category)
    }

    override fun onGetTemplate(): Template = carScreenTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carScreenTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // The filter row sits above the results — a row that survived a filter on the operator or brand
        // must be reachable, and a filter that matched nothing must still be clearable from here
        // (spec: `auto-search` — The filter row sits above the results; design D5).
        if (!loading) {
            listBuilder.addItem(filterRow())
        }

        when {
            loading -> {
                listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.searching)).build())
            }
            error != null -> {
                listBuilder.addItem(Row.Builder().setTitle(error!!).build())
            }
            results.isNullOrEmpty() -> {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.no_results_found))
                        .addText(carContext.getString(R.string.no_category_nearby, categoryLabel))
                        .build()
                )
            }
            else -> {
                // The list the term in effect leaves of the fetched candidates — the walk is asked for the
                // candidate cap under a filter, so the page continues the narrowed set and not a page the
                // display limit truncated (spec: `auto-search` — A car POI result list can be narrowed by
                // name; design D2/D5).
                val matches = filterPoiEntries(results!!, filter, results!!.size)
                if (matches.isEmpty() && filter.isNotBlank()) {
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(
                                carContext.getString(
                                    com.naviveylin.core.R.string.poi_filter_no_match, filter
                                )
                            )
                            .build()
                    )
                }
                val shown = FavoritesScreenMapper.page(matches, capacity = rowCapacity, page = page)
                for (poi in shown.rows) {
                    // Row tap selects the POI (rows with a click listener must
                    // not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    val title = poiDisplayLabel(poi)
                    val distance = if (poi.distance > 0) {
                        carContext.getString(R.string.distance_away_m, formatDistanceNumber(poi.distance))
                    } else {
                        ""
                    }
                    val text = listOf(poi.objectType ?: "", distance)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")

                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(title)
                            .addText(text)
                            .setOnClickListener {
                                Log.d(TAG, "POI details: ${poi.label}")
                                armScreenPush(carContext, scope, "DetailsScreen (POI result)") {
                                    DetailsScreen(
                                        carContext, navigationViewModel, poi.lat, poi.lon,
                                        nameHint = poi.label
                                    )
                                }
                            }
                            .build()
                    )
                }
                if (shown.hasFurtherPage) {
                    listBuilder.addItem(pageRow(shown.remaining))
                }
            }
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    // The header carries the active term beside the category, so the driver can see why
                    // the list is short (spec: `auto-search` — The header carries the active term).
                    .setTitle(if (filter.isBlank()) categoryLabel else "$categoryLabel · $filter")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()
    }

    /**
     * The list's first row: the filter's own row, carrying the active term when one is set and opening
     * [PoiFilterScreen] on tap. The screen applies its term to this instance and pops back, so a
     * refinement costs no screen (design D5).
     */
    private fun filterRow(): Row {
        val builder = Row.Builder()
            .setTitle(carContext.getString(com.naviveylin.core.R.string.poi_filter_label))
            .setOnClickListener {
                armScreenPush(carContext, scope, "PoiFilterScreen") {
                    PoiFilterScreen(carContext, initialTerm = filter, onApply = { applyFilter(it) })
                }
            }
        if (filter.isNotBlank()) {
            builder.addText(filter)
        }
        return builder.build()
    }

    /**
     * The final row of a page holding more fetched rows than it shows: it names how many are left and
     * opens the next page of the same already-fetched rows (spec: `car-list-row-limit` — The final row
     * opens the next page; Items beyond the capacity stay reachable; design D3).
     *
     * The next page is a pushed screen of the same class carrying the rows this screen holds, so the host's
     * back navigation returns to the page that opened it and no second native walk is paid for.
     */
    private fun pageRow(remaining: Int): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.more_items, remaining))
        .setOnClickListener {
            armScreenPush(carContext, scope, "PoiResultsScreen (page)") {
                PoiResultsScreen(
                    carContext, navigationViewModel, category, categoryLabel,
                    prefetched = results, page = page + 1, filter = filter,
                    mainDispatcher = mainDispatcher, ioDispatcher = ioDispatcher
                )
            }
        }
        .build()

    companion object {
        private const val TAG = "PoiResultsScreen"
        private const val SEARCH_RADIUS_M = 5000.0
    }
}
