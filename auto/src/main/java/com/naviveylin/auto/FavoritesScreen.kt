package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.orderedGroupNames
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android Auto screen for browsing favorite locations using [ListTemplate]
 * with sectioned lists (one list per favorite group). Backed by
 * [AutoFavoritesProvider] via [AutoEntryPoint].
 *
 * Uses [ListTemplate] (not [PlaceListNavigationTemplate]): the place-list
 * template requires every non-browsable row to carry a distance span and every
 * browsable row to carry a click listener, neither of which applies to favorites
 * browsing — building it would fail with an IllegalArgumentException.
 */
class FavoritesScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val starredOnly: Boolean = false
) : Screen(carContext) {

    private val scope = carScreenScope("FavoritesScreen")

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()

    private var favoritesData: Map<String, List<com.framstag.libosmscout.client.FavoriteLocation>> = emptyMap()

    /**
     * Stored group order (`AutoFavoritesProvider.groupOrder`): the section sequence.
     * Read from its own flow, not from the map's iteration order — a group reorder
     * leaves the map contents equal, so the map flow may not re-emit (spec
     * `auto-favorites` — AA place list follows the stored group order).
     */
    private var groupOrder: List<String> = emptyList()
    private var loaded = false

    init {
        enableBackNavigation()
        // Collect the favorites flow reactively (parity with MapScreen,
        // DetailsScreen and the phone app): the screen updates in place when
        // the store finishes loading, so favorites appear without leaving and
        // re-entering the screen (spec: auto-favorites — favorites appear
        // without re-entering the screen). The group order rides the same
        // collector via `combine`, so the screen holds one observation rather
        // than a second bare launch.
        scope.launch {
            combine(
                favoritesProvider.favoriteLocations(),
                favoritesProvider.groupOrder()
            ) { favorites, order -> favorites to order }
                .collect { (favorites, order) ->
                    favoritesData = favorites
                    groupOrder = order
                    loaded = true
                    invalidate()
                }
        }
        // Cancel the collect when the screen is destroyed so collectors do not
        // accumulate across open/close cycles (pattern from MapScreen).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        val builder = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(if (starredOnly) "Starred favorites" else "Favorites")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )

        if (!loaded) {
            builder.setSingleList(
                ItemList.Builder()
                    .addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
                    .build()
            )
        } else if (favoritesData.isEmpty()) {
            builder.setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.no_favorites_saved))
                            .addText(carContext.getString(R.string.favorites_save_hint))
                            .build()
                    )
                    .build()
            )
        } else {
            var added = false
            for (groupName in orderedGroupNames(groupOrder, favoritesData.keys)) {
                val favorites = favoritesData[groupName].orEmpty()
                val groupFavorites = if (starredOnly) {
                    favorites.filter { it.attributes?.get("starred") == "true" }
                } else {
                    favorites
                }
                if (groupFavorites.isEmpty()) continue
                added = true

                val itemList = ItemList.Builder()
                for (fav in groupFavorites) {
                    // Row tap selects the favorite (rows with a click listener
                    // must not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    itemList.addItem(
                        Row.Builder()
                            .setTitle(fav.name ?: "Favorite")
                            .addText(fav.attributes?.get("address") ?: "")
                            .setOnClickListener {
                                Log.d(TAG, "Favorite selected: ${fav.name}")
                                navigationViewModel.navigateTo(fav.lat, fav.lon)
                            }
                            .build()
                    )
                }
                builder.addSectionedList(
                    SectionedItemList.create(itemList.build(), groupName)
                )
            }
            if (!added) {
                builder.setSingleList(
                    ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.no_starred_favorites))
                                .addText(carContext.getString(R.string.starred_hint))
                                .build()
                        )
                        .build()
                )
            }
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "FavoritesScreen"
    }
}
