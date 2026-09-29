package com.naviveylin.ui.favorites

/**
 * Pure index helpers for the group order (spec `group-ordering`).
 *
 * Mirrors [FavoriteOrder] for the group grid. The grid has no leading header item,
 * so a lazy-grid index maps 1:1 onto the group order and there is no
 * `favoriteIndex` equivalent here.
 *
 * Keeping the index math out of the composable makes the reorder rules (bounds,
 * no-op moves, and which index is actually committed after a drag) unit-testable
 * without a gesture.
 */
internal object GroupOrder {

    /**
     * Returns [groups] with the entry at [fromIndex] moved to [toIndex].
     *
     * [toIndex] is the position in the list **after** the dragged entry was removed,
     * which is the basis the native `moveGroup` index uses. Out-of-bounds indices and
     * a move onto the same index return the input unchanged, so a drag that never
     * left its slot cannot change the order.
     */
    fun move(groups: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex == toIndex) return groups
        if (fromIndex !in groups.indices || toIndex !in groups.indices) return groups

        return groups.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    /**
     * The index to persist for a group whose drag just ended: its position in
     * [working] when that differs from its position in [stored], otherwise null.
     *
     * Null covers both "nothing moved" and "the group is no longer in the store"
     * (deleted or renamed while it was being dragged), so neither case reaches the
     * store.
     */
    fun commitIndex(working: List<String>, stored: List<String>, groupName: String): Int? {
        val newIndex = working.indexOf(groupName)
        if (newIndex < 0) return null

        val storedIndex = stored.indexOf(groupName)
        if (storedIndex < 0) return null

        return if (newIndex == storedIndex) null else newIndex
    }
}
