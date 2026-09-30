package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation

/**
 * Pure index helpers for the starred order (spec `starred-ordering`).
 *
 * Mirrors [FavoriteOrder] and [GroupOrder] for the chip bar. Two things differ: the
 * order spans all groups, so a chip's identity is the `(group, name)` pair — the same
 * favorite name can exist in two groups — and the bar has no leading header item, so a
 * lazy-row index maps 1:1 onto the starred order.
 *
 * Keeping the index math out of the composable makes the bar's rules (bounds, no-op
 * moves, and which index is actually committed after a drag) unit-testable without a
 * gesture.
 */
internal object StarredOrder {

    /** The stable identity of a starred entry, used as its lazy-row key. */
    fun keyOf(entry: Pair<String, FavoriteLocation>): String = "${entry.first}_${entry.second.name}"

    /**
     * Returns [order] with the entry at [fromIndex] moved to [toIndex].
     *
     * [toIndex] is the position in the list **after** the dragged entry was removed,
     * which is the basis the native `moveStarredFavorite` index uses. Out-of-bounds
     * indices and a move onto the same index return the input unchanged, so a drag
     * that never left its slot cannot change the order.
     */
    fun move(
        order: List<Pair<String, FavoriteLocation>>,
        fromIndex: Int,
        toIndex: Int
    ): List<Pair<String, FavoriteLocation>> {
        if (fromIndex == toIndex) return order
        if (fromIndex !in order.indices || toIndex !in order.indices) return order

        return order.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    /**
     * The index to persist for a starred favorite whose drag just ended: its position
     * in [working] when that differs from its position in [stored], otherwise null.
     *
     * Null covers both "nothing moved" and "the favorite is no longer starred" (it was
     * unstarred, deleted or renamed while it was being dragged), so neither case reaches
     * the store.
     */
    fun commitIndex(
        working: List<Pair<String, FavoriteLocation>>,
        stored: List<Pair<String, FavoriteLocation>>,
        groupName: String,
        favName: String
    ): Int? {
        val newIndex = working.indexOfFirst { isEntry(it, groupName, favName) }
        if (newIndex < 0) return null

        val storedIndex = stored.indexOfFirst { isEntry(it, groupName, favName) }
        if (storedIndex < 0) return null

        return if (newIndex == storedIndex) null else newIndex
    }

    private fun isEntry(
        entry: Pair<String, FavoriteLocation>,
        groupName: String,
        favName: String
    ): Boolean = entry.first == groupName && entry.second.name == favName
}
