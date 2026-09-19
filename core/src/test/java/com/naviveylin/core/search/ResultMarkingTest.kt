package com.naviveylin.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared row marking decision (spec: search-result-ranking —
 * "Perfect-match marking and cross-surface parity").
 */
class ResultMarkingTest {

    @Test
    fun `neither fact yields no marking`() {
        assertEquals(ResultMarking.NONE, resultMarkingOf(isFavorite = false, isPerfect = false))
    }

    @Test
    fun `favorite alone yields the heart`() {
        assertEquals(ResultMarking.FAVORITE, resultMarkingOf(isFavorite = true, isPerfect = false))
    }

    @Test
    fun `perfect match alone yields the perfect marking`() {
        assertEquals(ResultMarking.PERFECT, resultMarkingOf(isFavorite = false, isPerfect = true))
    }

    @Test
    fun `both facts yield the composite marking`() {
        assertEquals(
            ResultMarking.FAVORITE_AND_PERFECT,
            resultMarkingOf(isFavorite = true, isPerfect = true)
        )
    }

    @Test
    fun `marking exposes which facts it carries`() {
        assertTrue(ResultMarking.FAVORITE_AND_PERFECT.isFavorite)
        assertTrue(ResultMarking.FAVORITE_AND_PERFECT.isPerfect)
        assertTrue(ResultMarking.FAVORITE.isFavorite)
        assertFalse(ResultMarking.FAVORITE.isPerfect)
        assertTrue(ResultMarking.PERFECT.isPerfect)
        assertFalse(ResultMarking.PERFECT.isFavorite)
        assertFalse(ResultMarking.NONE.isFavorite)
        assertFalse(ResultMarking.NONE.isPerfect)
    }
}
