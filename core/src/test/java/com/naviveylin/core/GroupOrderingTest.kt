package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [orderedGroupNames], the rule both group-listing surfaces use to pair
 * the stored group order with the groups their map holds (spec `group-ordering` —
 * group order is what every group-listing surface renders).
 *
 * Plain JUnit: the helper is pure and touches neither Android nor the native stub.
 */
class GroupOrderingTest {

    @Test
    fun `the stored order is the sequence of the listed groups`() {
        assertEquals(
            listOf("Home", "Cities", "Work"),
            orderedGroupNames(
                order = listOf("Home", "Cities", "Work"),
                known = setOf("Cities", "Work", "Home")
            )
        )
    }

    @Test
    fun `a group the order does not name is appended, not dropped`() {
        assertEquals(
            listOf("Home", "Cities", "Fresh"),
            orderedGroupNames(
                order = listOf("Home", "Cities"),
                known = setOf("Cities", "Fresh", "Home")
            )
        )
    }

    @Test
    fun `a name the map no longer holds is skipped`() {
        assertEquals(
            listOf("Work", "Cities"),
            orderedGroupNames(
                order = listOf("Home", "Work", "Cities"),
                known = setOf("Cities", "Work")
            )
        )
    }

    @Test
    fun `an empty order falls back to the map order`() {
        assertEquals(
            listOf("Cities", "Work"),
            orderedGroupNames(order = emptyList(), known = setOf("Cities", "Work"))
        )
    }

    @Test
    fun `no groups yields no names`() {
        assertEquals(emptyList<String>(), orderedGroupNames(order = emptyList(), known = emptySet()))
    }

    @Test
    fun `every known group appears exactly once`() {
        val names = orderedGroupNames(
            order = listOf("Home", "Cities", "Work"),
            known = setOf("Cities", "Work", "Home")
        )

        assertEquals(names.size, names.toSet().size)
    }
}
