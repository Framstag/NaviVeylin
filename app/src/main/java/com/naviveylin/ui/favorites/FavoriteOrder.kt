package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation

/**
 * Pure index helpers for the favorite order inside a group.
 *
 * The group detail list renders a leading "Add favorite" item, so a lazy-list
 * index is one higher than the favorite index; [favoriteIndex] converts it.
 * Keeping the index math out of the composable makes the reorder rules
 * (bounds, no-op moves, and which index is actually committed after a drag)
 * unit-testable without a gesture.
 */
internal object FavoriteOrder {

    /** The favorite index belonging to a lazy-list item index. */
    fun favoriteIndex(lazyListIndex: Int): Int = lazyListIndex - 1

    /**
     * Returns [favorites] with the entry at [fromIndex] moved to [toIndex].
     *
     * Out-of-bounds indices and a move onto the same index return the input
     * unchanged, so a drag that never left its slot cannot change the order.
     */
    fun move(
        favorites: List<FavoriteLocation>,
        fromIndex: Int,
        toIndex: Int
    ): List<FavoriteLocation> {
        if (fromIndex == toIndex) return favorites
        if (fromIndex !in favorites.indices || toIndex !in favorites.indices) return favorites

        return favorites.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    /**
     * The index to persist for a favorite whose drag just ended: its position in
     * [working] when that differs from its position in [stored], otherwise null.
     *
     * Null covers both "nothing moved" and "the favorite is no longer part of
     * the group" (deleted or renamed while it was being dragged), so neither
     * case reaches the store.
     */
    fun commitIndex(
        working: List<FavoriteLocation>,
        stored: List<FavoriteLocation>,
        favName: String
    ): Int? {
        val newIndex = working.indexOfFirst { it.name == favName }
        if (newIndex < 0) return null

        val storedIndex = stored.indexOfFirst { it.name == favName }
        if (storedIndex < 0) return null

        return if (newIndex == storedIndex) null else newIndex
    }
}
