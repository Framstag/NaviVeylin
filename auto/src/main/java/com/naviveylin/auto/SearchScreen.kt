package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SearchTemplate.SearchCallback
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen for location search using [SearchTemplate].
 * Backed by [AutoSearchProvider] via [AutoEntryPoint].
 */
class SearchScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val initialQuery: String? = null
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val searchProvider = entryPoint.autoSearchProvider()

    init {
        enableBackNavigation()
        initialQuery?.let { runSearch(it) }
    }

    override fun onGetTemplate(): SearchTemplate {
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
        } else if (lastQuery.isNotBlank() || lastResults.isNotEmpty()) {
            builder.setItemList(buildResultsList())
        }
        return builder.build()
    }

    private fun runSearch(searchText: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SearchScreenMapper.SEARCH_DEBOUNCE_MS)
            val results = withContext(Dispatchers.Default) {
                searchProvider.searchLocations(searchText, SearchScreenMapper.MAX_RESULTS)
            }
            Log.d(TAG, "Search results: ${results.size} for '$searchText'")
            lastResults = results
            lastQuery = searchText
            invalidate()
        }
    }

    private inner class SearchCallbackImpl : SearchCallback {
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
    private var lastResults: List<com.framstag.libosmscout.client.LocationEntry> = emptyList()

    private fun buildResultsList(): ItemList {
        val builder = ItemList.Builder()

        if (lastQuery.isBlank()) {
            return builder.build()
        }

        if (lastResults.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle("No results found")
                    .build()
            )
            return builder.build()
        }

        for (result in lastResults) {
            val description = SearchScreenMapper.buildDescription(result)

            builder.addItem(
                Row.Builder()
                    .setTitle(result.label ?: "Unknown")
                    .addText(description)
                    // Row tap starts navigation (click listeners and row actions
                    // are mutually exclusive — ROW_CONSTRAINTS_SIMPLE).
                    .setOnClickListener {
                        Log.d(TAG, "Details for: ${result.label} (${result.lat}, ${result.lon})")
                        carContext.getCarService(ScreenManager::class.java).push(
                            DetailsScreen(
                                carContext, navigationViewModel, result.lat, result.lon,
                                nameHint = result.label
                            )
                        )
                    }
                    .build()
            )
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "SearchScreen"
    }
}
