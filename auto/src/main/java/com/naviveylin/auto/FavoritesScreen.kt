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
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.orderedGroupNames
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel

/**
 * Android Auto screen for browsing favorite locations using [ListTemplate]
 * with sectioned lists (one list per favorite group). Backed by
 * [AutoFavoritesProvider] via [AutoEntryPoint].
 *
 * The starred mode (spec `auto-favorites` — starred favorites render as one ordered
 * list; spec `starred-ordering`) lists the stored starred order instead: one order that
 * spans all groups, so it is one list without group headers, read-only.
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

    /**
     * Shared-state observations for this screen (spec: auto/screen-observation; design D6):
     * [CarScreenObservations] owns how long the observation lives,
     * [FavoritesScreenObservations] owns what is observed. Started in `onStart`, stopped in
     * `onStop`, so a stopped screen issues no template invalidation and a reorder received while
     * stopped is applied once on the next start (`auto-favorites` — the clarity clause added by this
     * change).
     *
     * Declared before the `init` block on purpose: everything the observation touches is initialized
     * here, never by an `init` body that runs during construction (TODO.md §80).
     */
    private val observations = CarScreenObservations()
    private val screenObservations = FavoritesScreenObservations(
        observations = observations,
        favoritesProvider = favoritesProvider,
        onFavorites = ::onFavorites
    )

    private var favoritesData: Map<String, List<com.framstag.libosmscout.client.FavoriteLocation>> = emptyMap()

    /**
     * Stored group order (`AutoFavoritesProvider.groupOrder`): the section sequence.
     * Read from its own flow, not from the map's iteration order — a group reorder
     * leaves the map contents equal, so the map flow may not re-emit (spec
     * `auto-favorites` — AA place list follows the stored group order).
     */
    private var groupOrder: List<String> = emptyList()

    /**
     * The stored starred order (`AutoFavoritesProvider.starredOrder`): one sequence
     * spanning all groups, what the starred mode renders. Read from its own flow for
     * the same reason as the group order — the order is not a property the group map
     * can express.
     */
    private var starredOrder: List<StarredFavoriteLocation> = emptyList()
    private var loaded = false

    init {
        enableBackNavigation()
        // The favorites, group order and starred order are NOT collected here: they are this
        // screen's shared-state observation and live on the started period (`screenObservations`,
        // started in onStart / stopped in onStop), so a stopped screen never invalidates the host
        // and can never accumulate a second collector per start (spec: auto/screen-observation;
        // design D6).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                screenObservations.start()
            }
            override fun onStop(owner: LifecycleOwner) {
                // Every observation ends with the started period (spec: auto/screen-observation —
                // One instance of each observation per started period).
                observations.stop()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                // A destroy without a stop must not leave an observation running.
                observations.stop()
                // Cancel the screen's own scope (row actions, pushes) so its work does not outlive
                // the screen either.
                scope.cancel()
            }
        })
    }

    /**
     * One emitted list state of the started period (spec: auto/screen-observation; design D6):
     * favorites, the stored group order and the stored starred order, applied together and rendered
     * in place (spec: auto-favorites — favorites appear without re-entering the screen, and a
     * reorder updates the list/headers in place).
     */
    private fun onFavorites(
        favorites: Map<String, List<com.framstag.libosmscout.client.FavoriteLocation>>,
        order: List<String>,
        starred: List<StarredFavoriteLocation>
    ) {
        favoritesData = favorites
        groupOrder = order
        starredOrder = starred
        loaded = true
        invalidate()
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        val builder = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(
                        if (starredOnly) {
                            carContext.getString(R.string.starred_favorites)
                        } else {
                            carContext.getString(R.string.favorites)
                        }
                    )
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )

        if (!loaded) {
            builder.setSingleList(
                ItemList.Builder()
                    .addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
                    .build()
            )
        } else if (favoritesData.isEmpty() && starredOrder.isEmpty()) {
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
        } else if (starredOnly) {
            // One order spanning all groups: a single list without group headers, in
            // the stored starred sequence (spec `auto-favorites`).
            if (starredOrder.isEmpty()) {
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
            } else {
                val itemList = ItemList.Builder()
                for (entry in starredOrder) {
                    val fav = entry.favorite
                    itemList.addItem(
                        Row.Builder()
                            .setTitle(fav.name ?: carContext.getString(R.string.unnamed_favorite))
                            .addText(fav.attributes?.get("address") ?: "")
                            .setOnClickListener {
                                Log.d(TAG, "Starred favorite selected: ${fav.name}")
                                navigationViewModel.navigateTo(fav.lat, fav.lon)
                            }
                            .build()
                    )
                }
                builder.setSingleList(itemList.build())
            }
        } else {
            var added = false
            for (groupName in orderedGroupNames(groupOrder, favoritesData.keys)) {
                val favorites = favoritesData[groupName].orEmpty()
                if (favorites.isEmpty()) continue
                added = true

                val itemList = ItemList.Builder()
                for (fav in favorites) {
                    // Row tap selects the favorite (rows with a click listener
                    // must not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    itemList.addItem(
                        Row.Builder()
                            .setTitle(fav.name ?: carContext.getString(R.string.unnamed_favorite))
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
                                .setTitle(carContext.getString(R.string.no_favorites_saved))
                                .addText(carContext.getString(R.string.favorites_save_hint))
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
