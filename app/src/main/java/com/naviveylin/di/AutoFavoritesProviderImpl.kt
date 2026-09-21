package com.naviveylin.di

import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
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

    override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> =
        repository.get().favorites

    override suspend fun init(filePath: String): Boolean = repository.get().init(filePath)

    override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean =
        repository.get().addFavorite(DEFAULT_FAVORITE_GROUP, name, lat, lon)

    override suspend fun removeFavorite(lat: Double, lon: Double): Boolean {
        val repository = repository.get()
        val match = findFavoriteByCoordinates(repository.favorites.value, lat, lon) ?: return false
        return repository.deleteFavorite(match.first, match.second)
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
