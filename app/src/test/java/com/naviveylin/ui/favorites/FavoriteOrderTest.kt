package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic tests for the favorite order inside a group (spec `fav-ordering` —
 * position, no-op moves, per-group scope; spec `fav-management-ui` — drag end
 * commits the visible order, dropped commits).
 */
class FavoriteOrderTest {

    private fun favorites(vararg names: String): List<FavoriteLocation> =
        names.map { FavoriteLocation(it, 0.0, 0.0) }

    private fun names(list: List<FavoriteLocation>): List<String> = list.map { it.name }

    @Test
    fun `lazy list index is offset by the header item`() {
        assertEquals(0, FavoriteOrder.favoriteIndex(1))
        assertEquals(2, FavoriteOrder.favoriteIndex(3))
    }

    @Test
    fun `move to the front`() {
        val moved = FavoriteOrder.move(favorites("A", "B", "C"), fromIndex = 2, toIndex = 0)

        assertEquals(listOf("C", "A", "B"), names(moved))
    }

    @Test
    fun `move to the middle keeps the other favorites in order`() {
        val moved = FavoriteOrder.move(favorites("A", "B", "C", "D"), fromIndex = 0, toIndex = 2)

        assertEquals(listOf("B", "C", "A", "D"), names(moved))
    }

    @Test
    fun `move to the last position`() {
        val moved = FavoriteOrder.move(favorites("A", "B", "C"), fromIndex = 0, toIndex = 2)

        assertEquals(listOf("B", "C", "A"), names(moved))
    }

    @Test
    fun `move onto the same index changes nothing`() {
        val original = favorites("A", "B", "C")

        val moved = FavoriteOrder.move(original, fromIndex = 1, toIndex = 1)

        assertEquals(listOf("A", "B", "C"), names(moved))
        // The same instance is returned, so no recomposition is triggered.
        assertEquals(original, moved)
    }

    @Test
    fun `out of bounds indices are ignored`() {
        val original = favorites("A", "B")

        assertEquals(original, FavoriteOrder.move(original, fromIndex = -1, toIndex = 0))
        assertEquals(original, FavoriteOrder.move(original, fromIndex = 0, toIndex = 5))
        assertEquals(original, FavoriteOrder.move(original, fromIndex = 0, toIndex = -1))
    }

    @Test
    fun `a single favorite group cannot be reordered`() {
        val original = favorites("Only")

        assertEquals(original, FavoriteOrder.move(original, fromIndex = 0, toIndex = 0))
        assertEquals(original, FavoriteOrder.move(original, fromIndex = 0, toIndex = 3))
    }

    @Test
    fun `commit index reports the position the favorite ended up in`() {
        val stored = favorites("A", "B", "C")
        val working = FavoriteOrder.move(stored, fromIndex = 2, toIndex = 0)

        assertEquals(0, FavoriteOrder.commitIndex(working, stored, "C"))
    }

    @Test
    fun `commit index is null when nothing moved`() {
        val stored = favorites("A", "B", "C")

        assertNull(FavoriteOrder.commitIndex(stored, stored, "B"))
    }

    @Test
    fun `commit index is null when the dragged favorite disappeared`() {
        val stored = favorites("A", "B", "C")
        val working = favorites("A", "C")

        assertNull(FavoriteOrder.commitIndex(working, stored, "B"))
        assertNull(FavoriteOrder.commitIndex(favorites("A", "B", "Renamed"), stored, "C"))
    }

    @Test
    fun `commit index is null for an unknown favorite`() {
        val stored = favorites("A", "B")

        assertNull(FavoriteOrder.commitIndex(stored, stored, "Missing"))
    }

    @Test
    fun `commit index follows the dragged favorite across several swaps`() {
        var working = favorites("A", "B", "C", "D")
        working = FavoriteOrder.move(working, fromIndex = 0, toIndex = 1)
        working = FavoriteOrder.move(working, fromIndex = 1, toIndex = 2)
        working = FavoriteOrder.move(working, fromIndex = 2, toIndex = 3)

        assertEquals(listOf("B", "C", "D", "A"), names(working))
        assertEquals(3, FavoriteOrder.commitIndex(working, favorites("A", "B", "C", "D"), "A"))
    }
}
