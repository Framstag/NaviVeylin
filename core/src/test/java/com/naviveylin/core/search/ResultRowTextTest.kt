package com.naviveylin.core.search

import com.framstag.libosmscout.client.LocationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The row text composition (change `search-result-row-content`).
 *
 * One case per delta scenario of the two owning specs, each case named after the
 * scenario it exercises: `location-search` — Result item display, and `auto-search` —
 * Search results displayed as list.
 */
class ResultRowTextTest {

    private fun entry(label: String, region: Array<String>? = null): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.region = region
        }

    // ── spec: location-search — Result item display ──────────────────────────

    @Test
    fun `Result shows label and region`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.adminRegionHierarchy = "Eving/Dortmund/Arnsberg/Nordrhein-Westfalen/Deutschland"

        assertEquals("Rosenweg 20 - Dortmund", rowTitle(e))
        // The hierarchy stays in the details sheet: no part of the row may carry it.
        val rendered = listOfNotNull(rowTitle(e), rowDetail(e, false, null)).joinToString("\n")
        assertFalse(rendered.contains("Eving"))
        assertFalse(rendered.contains("Nordrhein"))
        assertFalse(rendered.contains("/"))
    }

    @Test
    fun `Result without a region shows the label alone`() {
        assertEquals("Rosenweg 20", rowTitle(entry("Rosenweg 20")))
        assertEquals("Rosenweg 20", rowTitle(entry("Rosenweg 20", emptyArray())))
        assertEquals("Rosenweg 20", rowTitle(entry("Rosenweg 20", arrayOf(""))))
        assertEquals("Rosenweg 20", rowTitle(entry("Rosenweg 20", arrayOf("   "))))
    }

    @Test
    fun `Region name is not repeated when the label is that name`() {
        assertEquals("Dortmund", rowTitle(entry("Dortmund", arrayOf("Dortmund"))))
        // The search's own folding governs the comparison, so case and spelling differ.
        assertEquals("dortmund", rowTitle(entry("dortmund", arrayOf("Dortmund"))))
        assertEquals("Erbstollenstrasse", rowTitle(entry("Erbstollenstrasse", arrayOf("Erbstollenstraße"))))
    }

    @Test
    fun `Duplicate results show disambiguation fields`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.objectTypeName = "restaurant"
        e.postalArea = "44139"

        assertEquals("restaurant · 44139", rowDetail(e, isDuplicate = true, resolvedAddress = null))
        // The admin region name is on the title, so the detail line does not repeat it.
        assertFalse(rowDetail(e, isDuplicate = true, resolvedAddress = null)!!.contains("Dortmund"))
    }

    @Test
    fun `Duplicate results keep the object's own name`() {
        val e = entry("Hauptstraße 20", arrayOf("Witten"))
        e.name = "Aldi"
        e.objectTypeName = "shop_supermarket"
        e.postalArea = "58454"

        assertEquals("Aldi · shop_supermarket · 58454", rowDetail(e, isDuplicate = true, resolvedAddress = null))
    }

    @Test
    fun `A repeated name identical to the label is not repeated in the detail line`() {
        val e = entry("Aldi", arrayOf("Dortmund"))
        e.name = "Aldi"
        e.objectTypeName = "shop_supermarket"

        assertEquals("shop_supermarket", rowDetail(e, isDuplicate = true, resolvedAddress = null))
    }

    @Test
    fun `A row without a duplicate label and without an address has no detail line`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.objectTypeName = "building"
        e.postalArea = "44139"

        assertNull(rowDetail(e, isDuplicate = false, resolvedAddress = null))
    }

    @Test
    fun `A name hit shows the address resolved at its position`() {
        val e = entry("Aldi", arrayOf("Dortmund"))
        e.name = "Aldi"
        e.objectTypeName = "shop_supermarket"
        e.matchedComponent = "poi"

        assertEquals("Hauptstraße 20", rowDetail(e, isDuplicate = false, resolvedAddress = "Hauptstraße 20"))
        // The first line keeps the label and the admin region name.
        assertEquals("Aldi - Dortmund", rowTitle(e))
    }

    @Test
    fun `A name hit without an indexed address stays readable`() {
        val e = entry("Aldi", arrayOf("Dortmund"))
        e.name = "Aldi"
        e.matchedComponent = "poi"

        assertNull(rowDetail(e, isDuplicate = false, resolvedAddress = null))
        assertNull(rowDetail(e, isDuplicate = false, resolvedAddress = ""))
        assertNull(rowDetail(e, isDuplicate = false, resolvedAddress = "   "))
        assertEquals("Aldi - Dortmund", rowTitle(e))
    }

    @Test
    fun `An address hit is not given its address twice`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.objectTypeName = "building"
        e.postalArea = "44139"
        e.hasHouseNumber = true
        e.matchedComponent = "address"

        assertEquals("Rosenweg 20 - Dortmund", rowTitle(e))
        // Its caller resolves no address for it (design D8), and the label already carries
        // street and house number.
        assertNull(rowDetail(e, isDuplicate = false, resolvedAddress = null))
        assertEquals(1, Regex("Rosenweg").findAll(rowTitle(e)).count())
    }

    // ── spec: auto-search — Search results displayed as list ─────────────────

    @Test
    fun `Results list shown`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.postalArea = "44139"

        assertEquals("Rosenweg 20 - Dortmund", rowTitle(e))
        assertEquals("44139", carRowDescription(e))
    }

    @Test
    fun `Postal code is shown once`() {
        val e = entry("Rosenweg 20", arrayOf("Dortmund"))
        e.postalArea = "44139"

        assertEquals(1, Regex("44139").findAll(carRowDescription(e)).count())
        // The region list the row used to render repeated it: [adminRegion, postalArea].
        assertFalse(carRowDescription(e).contains("Dortmund"))
    }

    @Test
    fun `Region name is not repeated when the label is that name - car`() {
        assertEquals("Dortmund", rowTitle(entry("Dortmund", arrayOf("Dortmund"))))
        assertEquals(1, Regex("Dortmund").findAll(rowTitle(entry("Dortmund", arrayOf("Dortmund")))).count())
    }

    @Test
    fun `Resolved street address is a phone-only row fact`() {
        val e = entry("Aldi", arrayOf("Dortmund"))
        e.name = "Aldi"
        e.objectTypeName = "shop_supermarket"
        e.postalArea = "44139"
        e.matchedComponent = "poi"

        // The car line is the postal area; the resolved street cannot reach it, because a
        // car Row's two text lines are the description and the distance.
        assertEquals("44139", carRowDescription(e))
        assertFalse(carRowDescription(e).contains("Hauptstraße"))
        assertTrue(rowDetail(e, isDuplicate = false, resolvedAddress = "Hauptstraße 20")!!.contains("Hauptstraße"))
    }

    @Test
    fun `Car description is empty when the entry carries no postal area`() {
        assertEquals("", carRowDescription(entry("Rosenweg 20", arrayOf("Dortmund"))))
    }

    // ── The per-row address lookup's predicate and its text (design D8) ───────

    @Test
    fun `Only a name hit needs an address resolved at its position`() {
        val poi = entry("Aldi", arrayOf("Dortmund")).apply { matchedComponent = "poi" }
        assertTrue(needsResolvedAddress(poi))

        // A street hit and an address hit already carry street and house number in the label.
        assertFalse(needsResolvedAddress(entry("Erbstollenstraße", arrayOf("Witten")).apply { matchedComponent = "location" }))
        assertFalse(needsResolvedAddress(entry("Erbstollenstraße 10", arrayOf("Witten")).apply { matchedComponent = "address" }))
        // A region, a coordinate or a free-text hit has no address to find.
        assertFalse(needsResolvedAddress(entry("Dortmund", arrayOf("Dortmund")).apply { matchedComponent = "adminRegion" }))
        assertFalse(needsResolvedAddress(entry("51.5136, 7.4653").apply { matchedComponent = "coordinate" }))
        assertFalse(needsResolvedAddress(entry("Aldi").apply { matchedComponent = "freeText" }))
        // Nothing to identify the row by.
        assertFalse(needsResolvedAddress(entry("").apply { matchedComponent = "poi" }))
    }

    @Test
    fun `The resolved address is the street and the house number, or nothing`() {
        assertEquals("Hauptstraße 20", resolvedAddressText(arrayOf("Hauptstraße", "20", "Dortmund", "44139")))
        assertEquals("Hauptstraße", resolvedAddressText(arrayOf("Hauptstraße", "", "Dortmund", "44139")))
        assertEquals("20", resolvedAddressText(arrayOf("", "20", "Dortmund", "44139")))
        assertNull(resolvedAddressText(arrayOf("", "", "Dortmund", "44139")))
        assertNull(resolvedAddressText(null))
        assertNull(resolvedAddressText(emptyArray()))
        assertNull(resolvedAddressText(arrayOf("   ", "  ")))
    }
}
