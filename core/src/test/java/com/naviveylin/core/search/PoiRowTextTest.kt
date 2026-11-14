package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared POI row title (change `poi-name-filter`; spec: `poi-search` — Result item
 * display; spec: `auto-search` — A car POI row names its operator or brand).
 *
 * `poiDisplayLabel` was private in the phone's search dialog, so the car composed a title
 * of its own and a row that survived a filter on the operator could not show it
 * (design D6). The cases here are the composer's own; the surfaces' cases
 * (`PoiResultsScreenTest.aRowTitleCarriesTheOperator`, `anUnnamedRowShowsTheOperatorAlone`)
 * drive the same fixture through the car row and the phone's `PoiResultItem` renders this
 * function directly, which is what "one composer" means.
 */
class PoiRowTextTest {

    private fun poi(label: String = "", operator: String? = null, brand: String? = null): PoiEntry =
        PoiEntry().apply {
            this.label = label
            this.operator = operator
            this.brand = brand
        }

    @Test
    fun bothVariantsComposeTheSameTitle() {
        // The fixtures the two surfaces' own cases render: the composer is their single answer.
        assertEquals("Supermarkt (REWE)", poiDisplayLabel(poi("Supermarkt", operator = "REWE")))
        assertEquals("REWE", poiDisplayLabel(poi("", operator = "REWE")))
        assertEquals("Tankstelle (Shell)", poiDisplayLabel(poi("Tankstelle", brand = "Shell")))
    }

    @Test
    fun theBrandIsPreferredOverTheOperator() {
        assertEquals(
            "Tankstelle (Shell)",
            poiDisplayLabel(poi("Tankstelle", operator = "Shell Deutschland", brand = "Shell"))
        )
    }

    @Test
    fun aNameThatIsTheAttributeIsNotRepeated() {
        assertEquals("REWE", poiDisplayLabel(poi("REWE", operator = "REWE")))
        assertEquals("Shell", poiDisplayLabel(poi("Shell", brand = "Shell")))
    }

    @Test
    fun anEntryWithNothingToShowReadsUnnamed() {
        assertEquals("(unnamed)", poiDisplayLabel(poi()))
    }
}
