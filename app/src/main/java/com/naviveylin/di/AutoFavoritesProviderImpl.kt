package com.naviveylin.di

import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.data.FavoriteRepository
import kotlinx.coroutines.flow.StateFlow

/** Default group for favorites saved from the AA details screen. */
const val DEFAULT_FAVORITE_GROUP = "Favorites"

/**
 * [AutoFavoritesProvider] backed by the shared [FavoriteRepository] (same
 * store as the phone app). Extracted from the Hilt module for testability.
 */
class AutoFavoritesProviderImpl(
    private val repository: FavoriteRepository
) : AutoFavoritesProvider {

    override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> =
        repository.favorites

    override suspend fun init(filePath: String): Boolean = repository.init(filePath)

    override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean =
        repository.addFavorite(DEFAULT_FAVORITE_GROUP, name, lat, lon)

    override suspend fun removeFavorite(lat: Double, lon: Double): Boolean {
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
