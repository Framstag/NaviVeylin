package com.naviveylin.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the query attribute split (spec: search-result-ranking —
 * "Query attributes classified as criteria or context").
 */
class SearchQueryParserTest {

    @Test
    fun `city name becomes name tokens`() {
        val criteria = SearchQueryParser.criteriaOf("Waltrop")
        assertEquals(listOf("waltrop"), criteria.nameTokens)
        assertNull(criteria.postalCode)
        assertNull(criteria.houseNumber)
        assertTrue(criteria.namesName)
    }

    @Test
    fun `full formatted address splits into name, house number and postal code`() {
        val criteria = SearchQueryParser.criteriaOf("Erbstollenstraße 10, 58454 Witten")

        assertEquals(listOf("erbstollenstrasse", "witten"), criteria.nameTokens)
        assertEquals("10", criteria.houseNumber)
        assertEquals("58454", criteria.postalCode)
    }

    @Test
    fun `postal code alone names no street`() {
        val criteria = SearchQueryParser.criteriaOf("58454")

        assertTrue(criteria.nameTokens.isEmpty())
        assertEquals("58454", criteria.postalCode)
        assertFalse(criteria.namesName)
    }

    @Test
    fun `house number with letter suffix is recognised`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße 12a Dortmund")
        assertEquals("12a", criteria.houseNumber)
        assertEquals(listOf("hauptstrasse", "dortmund"), criteria.nameTokens)
    }

    @Test
    fun `house number range is recognised`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße 1-3")
        assertEquals("1-3", criteria.houseNumber)
    }

    @Test
    fun `empty query names nothing`() {
        val criteria = SearchQueryParser.criteriaOf("   ")
        assertTrue(criteria.nameTokens.isEmpty())
        assertNull(criteria.postalCode)
        assertNull(criteria.houseNumber)
    }

    @Test
    fun `comma and semicolon both separate the query parts`() {
        val criteria = SearchQueryParser.criteriaOf("Hauptstraße 12;58454;Witten")
        assertEquals(listOf("hauptstrasse", "witten"), criteria.nameTokens)
        assertEquals("12", criteria.houseNumber)
        assertEquals("58454", criteria.postalCode)
    }

    @Test
    fun `query without a house number names none`() {
        val criteria = SearchQueryParser.criteriaOf("Erbstollenstraße")
        assertNull(criteria.houseNumber)
        assertFalse(criteria.namesHouseNumber)
    }
}
