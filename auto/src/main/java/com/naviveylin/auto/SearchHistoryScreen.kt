package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto search history screen (shared with the phone app's search
 * history). Tapping an entry opens the search screen prefilled with the query.
 */
class SearchHistoryScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val historyProvider = entryPoint.autoSearchHistoryProvider()

    private var entries: List<String>? = null

    init {
        enableBackNavigation()
        scope.launch {
            entries = withContext(Dispatchers.Default) { historyProvider.load() }
            invalidate()
        }
    }

    override fun onGetTemplate(): ListTemplate {
        val itemList = ItemList.Builder()
        val list = entries
        if (list == null) {
            itemList.addItem(Row.Builder().setTitle("Loading...").build())
        } else if (list.isEmpty()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("No search history")
                    .addText("Search for places from the map to build up history")
                    .build()
            )
        } else {
            for (entry in list) {
                itemList.addItem(
                    Row.Builder()
                        .setTitle(entry)
                        .setOnClickListener { onEntrySelected(entry) }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Search history")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun onEntrySelected(query: String) {
        screenManager.push(SearchScreen(carContext, navigationViewModel, initialQuery = query))
    }
}
