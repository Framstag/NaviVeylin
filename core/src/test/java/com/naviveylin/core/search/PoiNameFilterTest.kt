package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The POI name filter and the candidate-to-display derivation (change `poi-name-filter`, spec:
 * `poi-search` — A name filter narrows a POI result list; A filtered search asks for a candidate set,
 * not a page).
 *
 * One case per delta scenario. The cases assert the matching itself and the derivation both surfaces
 * share, never a surface's rendering — the phone panel and the car screen have their own cases.
 */
class PoiNameFilterTest {

    private fun entry(
        label: String = "",
        operator: String? = null,
        brand: String? = null,
        distance: Double = 0.0
    ): PoiEntry = PoiEntry().apply {
        this.label = label
        this.operator = operator
        this.brand = brand
        this.distance = distance
        objectType = "shop"
    }

    private fun matches(term: String, e: PoiEntry): Boolean =
        matchesPoiFilter(e, foldForSearchMatch(term))

    // ── spec: poi-search — A name filter narrows a POI result list ───────────

    @Test
    fun aTermMatchesTheName() {
        val shop = entry(label = "REWE Markt")

        assertTrue(matches("rewe", shop))
        assertTrue("folding agrees on case", matches("REWE", shop))
    }

    @Test
    fun aTermMatchesTheOperator() {
        val shop = entry(label = "Supermarkt", operator = "REWE Markt GmbH")

        assertTrue("operator is part of the match", matches("rewe", shop))
        assertFalse("the name does not carry the term", matches("aldi", shop))
    }

    @Test
    fun aTermMatchesTheBrand() {
        val station = entry(label = "Tankstelle", brand = "Shell")

        assertTrue(matches("shell", station))
    }

    @Test
    fun foldingMakesAQueryMatchADiacriticSpelling() {
        val shop = entry(operator = "Müller")

        assertTrue("muller and Müller agree under the search's folding", matches("muller", shop))
        assertTrue("MÜLLER folds too", matches("MÜLLER", shop))
    }

    @Test
    fun anEmptyFilterMatchesEveryEntry() {
        val shop = entry(label = "Supermarkt", operator = "REWE")

        assertTrue(matches("", shop))
    }

    @Test
    fun aTermThatMatchesNothingIsRefused() {
        val shop = entry(label = "Supermarkt", operator = "REWE Markt GmbH", brand = "REWE")

        assertFalse(matches("aldi", shop))
    }

    // ── spec: poi-search — A filtered search asks for a candidate set ────────

    @Test
    fun theDisplayedListIsTheNearestMatches() {
        val candidates = listOf(
            entry(label = "Bäckerei", distance = 10.0),
            entry(label = "REWE Markt", distance = 20.0),
            entry(label = "Kiosk", distance = 30.0),
            entry(label = "REWE City", distance = 40.0)
        )

        val displayed = filterPoiEntries(candidates, "rewe", displayMax = 5)

        assertEquals(
            "the walk's nearest-first order survives the filter",
            listOf("REWE Markt", "REWE City"),
            displayed.map { it.label }
        )
    }

    @Test
    fun theDisplayMaxBoundsTheMatches() {
        val candidates = (1..20).map { entry(label = "REWE $it", distance = it.toDouble()) }

        val displayed = filterPoiEntries(candidates, "rewe", displayMax = 5)

        assertEquals(5, displayed.size)
        assertEquals(listOf("REWE 1", "REWE 2", "REWE 3", "REWE 4", "REWE 5"), displayed.map { it.label })
    }

    @Test
    fun anEmptyFilterShowsEveryCandidate() {
        val candidates = (1..3).map { entry(label = "Ort $it") }

        assertEquals(candidates, filterPoiEntries(candidates, "", displayMax = 100))
    }
}
