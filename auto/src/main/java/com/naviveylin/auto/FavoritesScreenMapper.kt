package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation

/**
 * Pure functions for building [FavoritesScreen] content.
 * Extracted for testability.
 */
object FavoritesScreenMapper {

    /**
     * Build a display description for a [FavoriteLocation].
     * Uses the address attribute if available, otherwise empty string.
     */
    fun buildDescription(fav: FavoriteLocation): String {
        return fav.attributes["address"] ?: ""
    }
}
