package com.naviveylin.core

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider for favorite locations, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
interface AutoFavoritesProvider {

    /** Reactive snapshot of all favorite locations, grouped by group name. */
    fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>>

    /**
     * Reactive group order (spec `group-ordering`): the sequence in which the groups
     * appear, emitted as its own flow because a map cannot express "same contents,
     * different order" — a reorder produces a map equal to the previous one, so a
     * consumer iterating [favoriteLocations] would keep the stale order. Consumers
     * that list groups read their sequence from here; the map supplies the contents.
     */
    fun groupOrder(): StateFlow<List<String>>

    /**
     * Reactive starred order (spec `starred-ordering`): the starred favorites in their
     * stored order, each entry naming the group that holds it. Its own flow rather than
     * a derivation from [favoriteLocations], because the order spans all groups — the
     * group map cannot express a sequence that crosses groups, and the rules behind the
     * stored order (positions, the fallback for a file without them) live in the store.
     */
    fun starredOrder(): StateFlow<List<StarredFavoriteLocation>>

    /**
     * Initialize the favorites repository with the JSON persistence path
     * (typically `filesDir/favorites.json`). Must be called before
     * [favoriteLocations] returns any data; mirrors the phone app's
     * `MapCanvasViewModel.initMap` favorite setup.
     */
    suspend fun init(filePath: String): Boolean

    /**
     * Add a favorite with the given name at the coordinates (default group).
     * Returns false when the repository is not initialized or the add fails.
     */
    suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean

    /**
     * Remove the favorite at the coordinates (any group). Returns false when
     * no matching favorite exists or the removal fails.
     */
    suspend fun removeFavorite(lat: Double, lon: Double): Boolean
}
