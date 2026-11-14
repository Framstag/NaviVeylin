package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The POI name filter and the derivation *candidates + term → displayed list*
 * (change `poi-name-filter`; spec: `poi-search` — A name filter narrows a POI result list;
 * A filtered search asks for a candidate set, not a page).
 *
 * One case per delta scenario of the two requirements, named after the scenario it
 * exercises. The cases drive the entry fields the native boundary already carries —
 * `label`, `operator`, `brand` (`PoiEntry.java`) — because nothing else has to be added
 * to the boundary for a filter to narrow a list (design D1).
 */
class PoiNameFilterTest {

    private fun poi(
        label: String = "",
        operator: String? = null,
        brand: String? = null,
        distance: Double = 100.0
    ): PoiEntry = PoiEntry().apply {
        this.label = label
        this.operator = operator
        this.brand = brand
        this.distance = distance
        objectType = "shop_supermarket"
        lat = 51.5136
        lon = 7.4653
    }

    /** The displayed list for a term, at the phone's display limit. */
    private fun displayed(term: String, vararg candidates: PoiEntry): List<String> =
        filterPoiEntries(candidates.toList(), term, DISPLAY_MAX).map { it.label }

    private companion object {
        /** Stands in for either surface's display limit; the filter never invents one. */
        const val DISPLAY_MAX = 100
    }

    // ── spec: poi-search — A name filter narrows a POI result list ───────────

    @Test
    fun aTermMatchesTheName() {
        val rewe = poi(label = "REWE Markt")
        val bakery = poi(label = "Bäckerei Schmitt")

        assertEquals(listOf("REWE Markt"), displayed("rewe", rewe, bakery))
    }

    @Test
    fun aTermMatchesTheOperator() {
        val shop = poi(label = "Supermarkt", operator = "REWE Markt GmbH")
        val other = poi(label = "Supermarkt 2", operator = "EDEKA")

        assertEquals(listOf("Supermarkt"), displayed("rewe", shop, other))
    }

    @Test
    fun aTermMatchesTheBrand() {
        val shell = poi(label = "Tankstelle", brand = "Shell")
        val aral = poi(label = "Tankstelle Nord", brand = "Aral")

        assertEquals(listOf("Tankstelle"), displayed("shell", shell, aral))
    }

    @Test
    fun foldingMakesAQueryMatchADiacriticSpelling() {
        val muller = poi(label = "Bäckerei", operator = "Müller")

        assertEquals(listOf("Bäckerei"), displayed("muller", muller))
        // …and the other way round: a folded term matches a plain spelling too.
        assertEquals(listOf("Bäckerei"), displayed("MÜLLER", muller))
    }

    @Test
    fun anEmptyTermShowsEveryCandidate() {
        val rewe = poi(label = "REWE")
        val bakery = poi(label = "Bäckerei")

        assertEquals(listOf("REWE", "Bäckerei"), displayed("", rewe, bakery))
        assertEquals(
            "a blank term is the unfiltered list, not a term matching nothing",
            listOf("REWE", "Bäckerei"),
            displayed("   ", rewe, bakery)
        )
    }

    @Test
    fun aTermThatMatchesNothingNarrowsToNothing() {
        assertTrue(displayed("aldi", poi(label = "REWE")).isEmpty())
        assertFalse(matchesPoiFilter(poi(label = "REWE"), foldForSearchMatch("aldi")))
    }

    @Test
    fun theNearestMatchesOfTheFilteredSetAreDisplayed() {
        // Four matches, nearest first as the walk returned them; the display limit is three.
        val candidates = listOf(
            poi(label = "REWE 1", distance = 10.0),
            poi(label = "Bäckerei", distance = 20.0),
            poi(label = "REWE 2", distance = 30.0),
            poi(label = "REWE 3", distance = 40.0),
            poi(label = "REWE 4", distance = 50.0)
        )

        assertEquals(
            listOf("REWE 1", "REWE 2", "REWE 3"),
            filterPoiEntries(candidates, "rewe", 3).map { it.label }
        )
    }
}
