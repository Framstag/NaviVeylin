package com.naviveylin.di

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.FavoritesSnapshot
import com.naviveylin.data.FavoriteRepository
import dagger.Lazy
import kotlinx.coroutines.flow.StateFlow

/** Default group for favorites saved from the AA details screen. */
const val DEFAULT_FAVORITE_GROUP = "Favorites"

/**
 * [AutoFavoritesProvider] backed by the shared [FavoriteRepository] (same
 * store as the phone app). Extracted from the Hilt module for testability.
 *
 * Takes the repository as [Lazy] (spec: auto-map-renderer — "Renderer
 * initialization off the car-app main thread"): resolving this provider from a car
 * screen constructor must not construct the repository, because the repository
 * constructor injects the native `OSMScoutClient` (whose build syncs stylesheets,
 * dlopens and runs the native client build) — that work may not happen on the
 * car-app host thread.
 */
class AutoFavoritesProviderImpl(
    private val repository: Lazy<FavoriteRepository>
) : AutoFavoritesProvider {

    /**
     * Whether [repository] has been resolved at least once, so [snapshotIfResolved] can answer without
     * being the call that builds the native client.
     *
     * Set after `get()` returns, never before: a reader that saw it true while another thread is still
     * inside the repository's constructor would read the flows of a half-built store. Volatile because
     * the warmup resolves the provider on a background worker while the host thread may ask for the
     * snapshot.
     */
    @Volatile
    private var repositoryResolved = false

    /** The repository, recording that its (possibly expensive, first) resolution has happened. */
    private fun repository(): FavoriteRepository {
        val resolved = repository.get()
        repositoryResolved = true
        return resolved
    }

    /**
     * The resolved store's live state — the same `StateFlow`s the screens observe — or null before the
     * first resolution (spec `auto-favorites` — A ready store's state is published without waiting for
     * the hop). Every `value` read is a plain field read, so the car-app host thread may call this.
     */
    override fun snapshotIfResolved(): FavoritesSnapshot? {
        if (!repositoryResolved) return null
        val store = repository.get()
        return FavoritesSnapshot(
            favorites = store.favorites.value,
            groupOrder = store.groupOrder.value,
            starredOrder = store.starredOrder.value,
            storeReady = store.storeReady.value
        )
    }

    override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> =
        repository().favorites

    /**
     * Hands the car screen the same order channel the phone grid renders, so the
     * group headers follow a phone reorder even when the group map is unchanged
     * (spec `auto-favorites` — the order channel is why the headers can follow it).
     */
    override fun groupOrder(): StateFlow<List<String>> = repository().groupOrder

    /**
     * Hands the car screen the same starred order the phone chip bar renders, so the
     * starred list follows a phone reorder (spec `auto-favorites` — starred favorites
     * render as one ordered list; spec `starred-ordering`).
     */
    override fun starredOrder(): StateFlow<List<StarredFavoriteLocation>> =
        repository().starredOrder

    /**
     * Forwards the store's initialisation outcome, so a car surface can show its loading placeholder
     * only while the store is genuinely uninitialised (spec `auto-favorites` — The favourites list
     * distinguishes a loading store from an empty one). Like the other accessors this resolves the
     * repository — and, with it, the native client on first use — so a screen reaches it from a
     * background context, never from the car-app host thread (spec `auto-map-renderer` — A screen
     * observation resolves its provider off the host thread).
     */
    override fun storeReady(): StateFlow<Boolean> = repository().storeReady

    override suspend fun init(filePath: String): Boolean = repository().init(filePath)

    override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean =
        repository().addFavorite(DEFAULT_FAVORITE_GROUP, name, lat, lon)

    override suspend fun removeFavorite(lat: Double, lon: Double): Boolean {
        val store = repository()
        val match = findFavoriteByCoordinates(store.favorites.value, lat, lon) ?: return false
        return store.deleteFavorite(match.first, match.second)
    }
}

/**
 * Find the (group, name) of the favorite at the coordinates, across all
 * groups. Pure and testable.
 */
internal fun findFavoriteByCoordinates(
    favorites: Map<String, List<FavoriteLocation>>,
    lat: Double,
    lon: Double
): Pair<String, String>? =
    favorites.entries.firstNotNullOfOrNull { (group, favs) ->
        favs.firstOrNull { it.lat == lat && it.lon == lon }?.let { group to it.name }
    }
