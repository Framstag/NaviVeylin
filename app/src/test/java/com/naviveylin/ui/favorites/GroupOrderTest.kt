package com.naviveylin.ui.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-logic tests for the group order (spec `group-ordering` — position, no-op
 * moves, invalid targets; spec `group-grid-display` — drag end commits the visible
 * order, aborted drags write nothing).
 */
class GroupOrderTest {

    private val order = listOf("Cities", "Work", "Home")

    @Test
    fun `move to the front`() {
        assertEquals(listOf("Home", "Cities", "Work"), GroupOrder.move(order, fromIndex = 2, toIndex = 0))
    }

    @Test
    fun `move to the back`() {
        assertEquals(listOf("Work", "Home", "Cities"), GroupOrder.move(order, fromIndex = 0, toIndex = 2))
    }

    @Test
    fun `move into the middle`() {
        assertEquals(listOf("Cities", "Home", "Work"), GroupOrder.move(order, fromIndex = 2, toIndex = 1))
    }

    @Test
    fun `a move onto the same index changes nothing`() {
        assertSame(order, GroupOrder.move(order, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun `out-of-bounds indices leave the order unchanged`() {
        assertSame(order, GroupOrder.move(order, fromIndex = 9, toIndex = 0))
        assertSame(order, GroupOrder.move(order, fromIndex = 0, toIndex = 9))
        assertSame(order, GroupOrder.move(order, fromIndex = -1, toIndex = 0))
    }

    @Test
    fun `a single group cannot be reordered`() {
        val single = listOf("Cities")

        assertSame(single, GroupOrder.move(single, fromIndex = 0, toIndex = 0))
    }

    @Test
    fun `commit index reports the position a drag ended in`() {
        val working = GroupOrder.move(order, fromIndex = 2, toIndex = 0)

        assertEquals(0, GroupOrder.commitIndex(working, order, "Home"))
    }

    @Test
    fun `commit index is null when nothing moved`() {
        assertNull(GroupOrder.commitIndex(order, order, "Work"))
    }

    @Test
    fun `commit index is null for a group that is gone`() {
        assertNull(GroupOrder.commitIndex(listOf("Cities"), order, "Home"))
        assertNull(GroupOrder.commitIndex(listOf("Cities", "Work", "Home", "Fresh"), order, "Fresh"))
    }
}
