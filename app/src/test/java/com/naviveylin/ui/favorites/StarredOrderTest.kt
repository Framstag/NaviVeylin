package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-logic tests for the starred order (spec `starred-ordering` — position,
 * no-op moves, a favorite that disappears mid-drag; spec `fav-starred-chip-bar` —
 * a drag ends committing the visible order, an aborted drag writes nothing).
 *
 * The order spans groups, so the identity of an entry is the `(group, name)` pair:
 * the same favorite name can exist in two groups and the two must stay distinct.
 */
class StarredOrderTest {

    private fun fav(name: String) = FavoriteLocation(name, 1.0, 2.0)

    private val order = listOf(
        "Cities" to fav("Berlin"),
        "Cities" to fav("Paris"),
        "Work" to fav("Office")
    )

    @Test
    fun `move to the front`() {
        assertEquals(
            listOf("Work" to "Office", "Cities" to "Berlin", "Cities" to "Paris"),
            StarredOrder.move(order, fromIndex = 2, toIndex = 0).map { it.first to it.second.name }
        )
    }

    @Test
    fun `move to the back`() {
        assertEquals(
            listOf("Cities" to "Paris", "Work" to "Office", "Cities" to "Berlin"),
            StarredOrder.move(order, fromIndex = 0, toIndex = 2).map { it.first to it.second.name }
        )
    }

    @Test
    fun `move across groups keeps the entry's own group`() {
        val moved = StarredOrder.move(order, fromIndex = 2, toIndex = 0)

        assertEquals("Work", moved[0].first)
        assertEquals("Office", moved[0].second.name)
    }

    @Test
    fun `a move onto the same index changes nothing`() {
        assertSame(order, StarredOrder.move(order, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun `out-of-bounds indices leave the order unchanged`() {
        assertSame(order, StarredOrder.move(order, fromIndex = 9, toIndex = 0))
        assertSame(order, StarredOrder.move(order, fromIndex = 0, toIndex = 9))
        assertSame(order, StarredOrder.move(order, fromIndex = -1, toIndex = 0))
    }

    @Test
    fun `a single starred favorite cannot be reordered`() {
        val single = listOf("Cities" to fav("Berlin"))

        assertSame(single, StarredOrder.move(single, fromIndex = 0, toIndex = 0))
    }

    @Test
    fun `commit index reports the position a drag ended in`() {
        val working = StarredOrder.move(order, fromIndex = 2, toIndex = 0)

        assertEquals(0, StarredOrder.commitIndex(working, order, "Work", "Office"))
    }

    @Test
    fun `commit index is null when nothing moved`() {
        assertNull(StarredOrder.commitIndex(order, order, "Cities", "Paris"))
    }

    @Test
    fun `commit index is null for a favorite that is gone`() {
        // Unstarred, deleted or renamed while it was being dragged: the entry is in
        // neither the working copy nor the stored order.
        assertNull(StarredOrder.commitIndex(order, order, "Work", "Missing"))
        assertNull(
            StarredOrder.commitIndex(
                order.filterNot { it.second.name == "Office" },
                order,
                "Work",
                "Office"
            )
        )
    }

    @Test
    fun `same-named favorites in different groups are distinct entries`() {
        val home = listOf("Cities" to fav("Home"), "Work" to fav("Home"))

        val moved = StarredOrder.move(home, fromIndex = 1, toIndex = 0)

        assertEquals(listOf("Work" to "Home", "Cities" to "Home"), moved.map { it.first to it.second.name })
        // Each identity resolves to its own position: only the dragged one commits a
        // change, the namesake in the other group is a separate entry (moved along by
        // the shift, not by its own drag).
        assertEquals(0, StarredOrder.commitIndex(moved, home, "Work", "Home"))
        assertEquals(1, StarredOrder.commitIndex(moved, home, "Cities", "Home"))
        // With nothing dragged, neither identity reports a change.
        assertNull(StarredOrder.commitIndex(home, home, "Cities", "Home"))
        assertNull(StarredOrder.commitIndex(home, home, "Work", "Home"))
    }

    @Test
    fun `keys identify an entry by group and name`() {
        val entry = "Cities" to fav("Home")

        assertEquals("Cities_Home", StarredOrder.keyOf(entry))
        assertNotEquals(StarredOrder.keyOf(entry), StarredOrder.keyOf("Work" to fav("Home")))
    }
}
