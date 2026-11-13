package com.naviveylin.core.search

import com.framstag.libosmscout.client.PoiEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one POI row-title composer both variants use (change `poi-name-filter`, spec: `auto-search` —
 * A car POI row names its operator or brand).
 *
 * The rule is the one `poi-search`'s "POI results list" already shipped on the phone; this case is
 * the proof that the car's row names the attribute a filter could have matched.
 */
class PoiRowTextTest {

    private fun entry(label: String = "", operator: String? = null, brand: String? = null): PoiEntry =
        PoiEntry().apply {
            this.label = label
            this.operator = operator
            this.brand = brand
        }

    @Test
    fun bothVariantsComposeTheSameTitle() {
        val named = entry(label = "Supermarkt", operator = "REWE")
        val unnamed = entry(operator = "REWE")
        val branded = entry(label = "Supermarkt", operator = "REWE", brand = "REWE Markt")

        assertEquals("Supermarkt (REWE)", poiDisplayLabel(named))
        assertEquals("REWE", poiDisplayLabel(unnamed))
        assertEquals("brand preferred over operator", "Supermarkt (REWE Markt)", poiDisplayLabel(branded))
        assertEquals("no name, brand or operator", "(unnamed)", poiDisplayLabel(entry()))
    }
}
