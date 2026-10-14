package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto search history screen (shared with the phone app's search
 * history). Tapping an entry opens the search screen prefilled with the query.
 *
 * The list presents at most the row capacity the connected host displays and keeps the older
 * searches reachable through its final page row (spec: `car-list-row-limit`; design D1/D3) — the host
 * silently ignores the rows over that capacity, so the store's up-to-50 entries would otherwise lose
 * their tail on any host whose list budget is below that.
 */
class SearchHistoryScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    /** The page of the history to show; 0 is its first page (spec: `car-list-row-limit`; design D3). */
    private val page: Int = 0
) : Screen(carContext) {

    private val scope = carScreenScope("SearchHistoryScreen")

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val historyProvider = entryPoint.autoSearchHistoryProvider()

    private var entries: List<String>? = null

    /**
     * The row capacity this screen presents (spec: `car-list-row-limit`; design D2). Starts at the library
     * default so the first template — which the host may ask for before the query returns — is already
     * bounded, and is replaced by the host's answer on the screen's own scope.
     */
    private var rowCapacity: Int = CarListCapacityReader.LIBRARY_DEFAULT

    init {
        enableBackNavigation()
        readRowCapacity()
        scope.launch {
            entries = withContext(Dispatchers.Default) { historyProvider.load() }
            invalidate()
        }
    }

    /**
     * Ask the host for this screen's row capacity, off the host thread, and re-render with the answer
     * (spec: `car-list-row-limit` — A car list never presents more rows than the host displays; design D2).
     * A host below car API 2, a host whose query fails and an unusable answer all take the library's
     * default instead ([CarListCapacityReader]), which the reader records as a coordinate-free
     * diagnostics line. Every page row opens a new instance of this screen, so each page asks for itself.
     */
    private fun readRowCapacity() {
        scope.launch {
            val capacity = withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }
            if (capacity != rowCapacity) {
                rowCapacity = capacity
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        val itemList = ItemList.Builder()
        val list = entries
        if (list == null) {
            itemList.addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
        } else if (list.isEmpty()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.no_search_history))
                    .addText(carContext.getString(R.string.search_history_hint))
                    .build()
            )
        } else {
            // The stored searches, capped at the host's capacity, with the remainder behind the final
            // page row (spec: `car-list-row-limit` — A car list never presents more rows than the host
            // displays; Items beyond the capacity stay reachable). The arithmetic is the mapper's, so the
            // pages partition the history: every entry is reachable exactly once.
            val stored = FavoritesScreenMapper.page(list, capacity = rowCapacity, page = page)
            for (entry in stored.rows) {
                itemList.addItem(
                    Row.Builder()
                        .setTitle(entry)
                        .setOnClickListener { onEntrySelected(entry) }
                        .build()
                )
            }
            if (stored.hasFurtherPage) {
                itemList.addItem(pageRow(stored.remaining))
            }
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.search_history))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    /**
     * The final row of a page holding more searches than it shows: it names how many are left and opens the
     * next page of the same list (spec: `car-list-row-limit` — The final row opens the next page; The
     * search-history page continues the stored history; design D3).
     *
     * The next page is a pushed screen of the same class, so the host's back navigation returns to the page
     * that opened it (spec: "Back navigation returns to the previous page").
     */
    private fun pageRow(remaining: Int): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.more_items, remaining))
        .setOnClickListener {
            armScreenPush(carContext, scope, "SearchHistoryScreen (page)") {
                SearchHistoryScreen(carContext, navigationViewModel, page = page + 1)
            }
        }
        .build()

    private fun onEntrySelected(query: String) {
        armScreenPush(carContext, scope, "SearchScreen (history entry)") {
            SearchScreen(carContext, navigationViewModel, initialQuery = query)
        }
    }
}
