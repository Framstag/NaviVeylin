package com.naviveylin.core

import com.framstag.libosmscout.client.FavoriteLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider for favorite locations, consumed by Android Auto screens.
 * Implemented in the [:app] module via Hilt.
 */
fun interface AutoFavoritesProvider {
    /** Reactive snapshot of all favorite locations, grouped by group name. */
    fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>>
}
