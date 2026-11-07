package com.naviveylin.core

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * The favourites store's whole state as one value: the four sources the car favourites screen
 * observes, captured together so a screen can render a consistent list (spec `auto-favorites`).
 */
data class FavoritesSnapshot(
    val favorites: Map<String, List<FavoriteLocation>>,
    val groupOrder: List<String>,
    val starredOrder: List<StarredFavoriteLocation>,
    val storeReady: Boolean
)

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
     * Whether the store's initialisation attempt has finished — success or failure.
     *
     * The favorites store publishes its contents from an empty map, so "not initialised yet" and
     * "initialised and holds no favorites" are the same [favoriteLocations] value. A surface that shows
     * a loading state has to tell them apart, and this is the signal that does: false until the
     * initialisation attempt ends, true afterwards whatever its outcome (spec `auto-favorites` — The
     * favourites list distinguishes a loading store from an empty one). A surface therefore shows its
     * loading placeholder only while this is false, never for a state it already holds.
     */
    fun storeReady(): StateFlow<Boolean>

    /**
     * The store's current state, readable **without a dispatch and without resolving the repository** —
     * or null while this provider has not resolved it yet.
     *
     * This is what keeps the car favourites screen's first template off the loading row when the
     * background hop that resolves the provider is itself late. That hop is a `withContext` onto a
     * shared background dispatcher, and on a cold session the map's first frame (stylesheet and type
     * config parse, tile retrieval, bitmap render) holds those threads and the CPU — measured on the
     * AAOS emulator: `favorites resolution entered` arrived 623–1715 ms after `favorites observation
     * start`, while the store had been ready for seconds (issue #110, spec `auto-favorites` — A ready
     * store's state is published without waiting for the hop). Reading the resolved repository's
     * `StateFlow.value`s is a plain field read: it produces no list, opens no database and never builds
     * the native client, which is why the host thread may call it.
     *
     * Null is the honest answer for a provider that has not resolved the repository: there is no state
     * to hand out yet, and a screen that showed one would present an uninitialised store as empty
     * (the failure the loading row exists to prevent).
     */
    fun snapshotIfResolved(): FavoritesSnapshot? = null

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
