package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * POI search results for one [com.framstag.libosmscout.client.PoiCategories]
 * category, searched around the current GPS position.
 *
 * Each result row offers a "Navigate here" action (route + turn-by-turn via
 * the shared navigation controller). Requires a GPS fix; without one an
 * explanatory row is shown instead.
 */
class PoiResultsScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val category: String,
    private val categoryLabel: String
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )

    private var results: List<PoiEntry>? = null
    private var error: String? = null
    private var loading = true

    init {
        enableBackNavigation()
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                try {
                    val pos = entryPoint.autoLocationProvider().position().value
                    if (pos == null) {
                        error = "GPS signal required. Please wait for a fix."
                        null
                    } else {
                        val client = entryPoint.autoClientProvider().client()
                        client.searchPOIs(category, pos.lat, pos.lon, SEARCH_RADIUS_M, MAX_RESULTS)
                            ?.toList()
                            ?: emptyList()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "POI search failed for $category", e)
                    error = "POI search failed: ${e.message ?: "unknown error"}"
                    null
                }
            }
            results = outcome
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        when {
            loading -> {
                listBuilder.addItem(Row.Builder().setTitle("Searching...").build())
            }
            error != null -> {
                listBuilder.addItem(Row.Builder().setTitle(error!!).build())
            }
            results.isNullOrEmpty() -> {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("No results found")
                        .addText("No $categoryLabel nearby")
                        .build()
                )
            }
            else -> {
                for (poi in results!!) {
                    // Row tap selects the POI (rows with a click listener must
                    // not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    val title = (poi.label ?: "").ifBlank { poi.objectType ?: "POI" }
                    val distance = if (poi.distance > 0) "%.0f m away".format(poi.distance) else ""
                    val text = listOf(poi.objectType ?: "", distance)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")

                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(title)
                            .addText(text)
                            .setOnClickListener {
                                Log.d(TAG, "POI selected: ${poi.label} (${poi.lat}, ${poi.lon})")
                                navigationViewModel.navigateTo(poi.lat, poi.lon)
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
