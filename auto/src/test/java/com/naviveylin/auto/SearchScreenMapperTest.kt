package com.naviveylin.auto

import com.framstag.libosmscout.client.LocationEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchScreenMapperTest {

    @Test
    fun buildDescription_withPostalAreaAndRegion() {
        val entry = LocationEntry().apply {
            postalArea = "44339"
            region = arrayOf("Eving", "Dortmund", "NRW")
        }
        assertEquals("44339 — Eving, Dortmund, NRW", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_postalAreaOnly() {
        val entry = LocationEntry().apply {
            postalArea = "10115"
            region = emptyArray()
        }
        assertEquals("10115", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_regionOnly() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = arrayOf("Mitte", "Berlin")
        }
        assertEquals("Mitte, Berlin", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_empty() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = emptyArray()
        }
        assertEquals("", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_singleRegion() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = arrayOf("Berlin")
        }
        assertEquals("Berlin", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_withPostalAndSingleRegion() {
        val entry = LocationEntry().apply {
            postalArea = "80331"
            region = arrayOf("München")
        }
        assertEquals("80331 — München", SearchScreenMapper.buildDescription(entry))
    }
}
