package com.naviveylin.core

import com.framstag.libosmscout.client.FavoriteLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider for favorite locations, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
interface AutoFavoritesProvider {

    /** Reactive snapshot of all favorite locations, grouped by group name. */
    fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>>

    /**
     * Initialize the favorites repository with the JSON persistence path
     * (typically `filesDir/favorites.json`). Must be called before
     * [favoriteLocations] returns any data; mirrors the phone app's
     * `MapCanvasViewModel.initMap` favorite setup.
     */
    suspend fun init(filePath: String): Boolean
}
