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
import com.naviveylin.core.search.SearchGate
import com.naviveylin.core.search.SearchGateState
import com.naviveylin.core.search.SearchRequestClient
import com.naviveylin.core.search.SearchSlots
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
 */
class PoiResultsScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val category: String,
    private val categoryLabel: String,
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
     * the client; `guidelines/UI.md` §3a).
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

    init {
        enableBackNavigation()
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
        // The category is the whole request here: the gate searches it once, immediately.
        searchGate.request(category)
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
            return@withContext PoiOutcome(null, "GPS signal required. Please wait for a fix.")
        }

        try {
            val client = entryPoint.autoClientProvider().client()
            val typeNames = PoiCategories.getTypeNames(category)

            val rows = if (typeNames.isEmpty()) {
                emptyList()
            } else {
                client.searchPOIsByTypes(
                    typeNames, pos.lat, pos.lon, SEARCH_RADIUS_M, MAX_RESULTS, generation
                )?.toList() ?: emptyList()
            }

            PoiOutcome(rows, null)
        } catch (e: Exception) {
            Log.w(TAG, "POI search failed for $category", e)
            PoiOutcome(null, "POI search failed: ${e.message ?: "unknown error"}")
        }
    }

    override fun onGetTemplate(): Template = carScreenTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carScreenTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): Template {
        val listBuilder = ItemList.Builder()

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
                for (poi in results!!) {
                    // Row tap selects the POI (rows with a click listener must
                    // not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    val title = (poi.label ?: "").ifBlank { poi.objectType ?: "POI" }
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
            }
        }

        return ListTemplate.Builder()
            .setHeader(Header.Builder().setTitle(categoryLabel).setStartHeaderAction(Action.BACK).build())
            .setSingleList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "PoiResultsScreen"
        private const val SEARCH_RADIUS_M = 5000.0
        private const val MAX_RESULTS = 20
    }
}
