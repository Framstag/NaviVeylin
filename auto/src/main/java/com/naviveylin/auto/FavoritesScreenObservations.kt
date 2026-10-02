package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import kotlinx.coroutines.flow.combine

/**
 * What the car favorites list observes (spec: auto/screen-observation; design D6).
 *
 * This owns *what* is observed and [CarScreenObservations] owns *how long* it lives: the screen
 * starts this in `onStart` and stops it in `onStop`, so the list state is observed exactly once per
 * started period and a stopped screen issues no template invalidation. The three observed sources are
 * the favorites, the stored group order and the stored starred order; the group and starred order
 * rides the same observation as the favorites because the three are combined into one list, and the
 * order flows are separate sources for a reason — a reorder leaves the group map's contents equal, so
 * the map flow may not re-emit (`auto-favorites` — AA place list follows the stored group order).
 *
 * The effects stay on the screen (they write its state fields and call `invalidate()`), so they arrive
 * as one callback — this class is testable without a `CarContext`.
 *
 * A change received while the screen is stopped is not lost: all three sources are `StateFlow`s, so
 * the observation re-established on the next start delivers the current value once (`auto-favorites`
 * — a reorder received while the list is stopped is applied on the next start).
 */
internal class FavoritesScreenObservations(
    private val observations: CarScreenObservations,
    private val favoritesProvider: AutoFavoritesProvider,
    private val onFavorites: (
        favorites: Map<String, List<FavoriteLocation>>,
        groupOrder: List<String>,
        starredOrder: List<StarredFavoriteLocation>
    ) -> Unit
) {

    /** Establish the screen's observation for the started period that began. */
    fun start() {
        observations.start()
        observations.observe(KEY_FAVORITES) {
            combine(
                favoritesProvider.favoriteLocations(),
                favoritesProvider.groupOrder(),
                favoritesProvider.starredOrder()
            ) { favorites, order, starred -> Triple(favorites, order, starred) }
                .collect { (favorites, order, starred) ->
                    onFavorites(favorites, order, starred)
                }
        }
    }

    private companion object {
        /**
         * One key for the whole list state: the three sources are combined into the one list the screen
         * renders (design D6), so the screen holds one observation rather than three collectors that
         * would have to agree before a rebuild.
         */
        const val KEY_FAVORITES = "favorites"
    }
}
