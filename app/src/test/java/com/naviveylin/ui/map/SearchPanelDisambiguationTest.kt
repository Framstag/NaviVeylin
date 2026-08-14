package com.naviveylin.ui.map

import com.framstag.libosmscout.client.LocationEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPanelDisambiguationTest {

    @Test
    fun `empty fields returns empty string`() {
        val entry = LocationEntry()
        assertEquals("", buildDisambiguationDetail(entry))
    }

    @Test
    fun `name only`() {
        val entry = LocationEntry().apply {
            name = "Aldi"
        }
        assertEquals("Aldi", buildDisambiguationDetail(entry))
    }

    @Test
    fun `objectTypeName only`() {
        val entry = LocationEntry().apply {
            objectTypeName = "shop_supermarket"
        }
        assertEquals("shop_supermarket", buildDisambiguationDetail(entry))
    }

    @Test
    fun `postalArea only`() {
        val entry = LocationEntry().apply {
            postalArea = "44139"
        }
        assertEquals("44139", buildDisambiguationDetail(entry))
    }

    @Test
    fun `region tail only`() {
        val entry = LocationEntry().apply {
            region = arrayOf("Dortmund", "NRW", "DE")
        }
        assertEquals("Dortmund", buildDisambiguationDetail(entry))
    }

    @Test
    fun `all fields combined`() {
        val entry = LocationEntry().apply {
            name = "Aldi"
            objectTypeName = "shop_supermarket"
            postalArea = "44139"
            region = arrayOf("Dortmund", "NRW", "DE")
        }
        assertEquals(
            "Aldi · shop_supermarket · 44139 · Dortmund",
            buildDisambiguationDetail(entry)
        )
    }

    @Test
    fun `name and objectTypeName only`() {
        val entry = LocationEntry().apply {
            name = "Rewe"
            objectTypeName = "shop_supermarket"
        }
        assertEquals("Rewe · shop_supermarket", buildDisambiguationDetail(entry))
    }

    @Test
    fun `empty name omitted`() {
        val entry = LocationEntry().apply {
            name = ""
            objectTypeName = "shop_bakery"
            postalArea = "44139"
        }
        assertEquals("shop_bakery · 44139", buildDisambiguationDetail(entry))
    }

    @Test
    fun `null name omitted`() {
        val entry = LocationEntry().apply {
            name = null
            objectTypeName = "shop_bakery"
        }
        assertEquals("shop_bakery", buildDisambiguationDetail(entry))
    }

    @Test
    fun `null region omitted`() {
        val entry = LocationEntry().apply {
            name = "Bäckerei Schmidt"
            objectTypeName = "shop_bakery"
            region = null
        }
        assertEquals("Bäckerei Schmidt · shop_bakery", buildDisambiguationDetail(entry))
    }

    @Test
    fun `empty region array omitted`() {
        val entry = LocationEntry().apply {
            name = "Bäckerei Schmidt"
            region = emptyArray()
        }
        assertEquals("Bäckerei Schmidt", buildDisambiguationDetail(entry))
    }
}
