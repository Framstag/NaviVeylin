package com.naviveylin.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the comparison folding used by the ranking (spec:
 * search-result-ranking — transliterated spelling, case).
 */
class SearchFoldingTest {

    @Test
    fun `sharp s folds to ss on both sides`() {
        assertEquals(foldForSearchMatch("Erbstollenstraße"), foldForSearchMatch("Erbstollenstrasse"))
    }

    @Test
    fun `diacritics are stripped`() {
        assertEquals("gunnemannshof", foldForSearchMatch("Günnemannshof"))
        assertEquals("cafe central", foldForSearchMatch("Café Central"))
    }

    @Test
    fun `case is folded`() {
        assertEquals("waltrop", foldForSearchMatch("WALTROP"))
    }

    @Test
    fun `punctuation and repeated whitespace collapse`() {
        assertEquals("am birkenbaum 6", foldForSearchMatch("  Am  Birkenbaum,  6 "))
    }

    @Test
    fun `tokens split a folded name`() {
        assertEquals(listOf("waltroper", "strasse"), foldedTokens("Waltroper Straße"))
        assertEquals(emptyList<String>(), foldedTokens("   "))
    }
}
