package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the free-text address parser (spec: fix-address-lookup-accuracy —
 * full formatted address resolution, postal-code-in-street parsing).
 */
class AddressParserTest {

    @Test
    fun `full address with postal code and city parses`() {
        val parsed = AddressParser.parse("Erbstollenstraße 10, 58454 Witten")!!
        assertEquals("Erbstollenstraße", parsed.street)
        assertEquals("Erbstollenstraße 10", parsed.streetWithHouse)
        assertEquals("10", parsed.houseNumber)
        assertEquals("58454", parsed.postalCode)
        assertEquals("Witten", parsed.city)
    }

    @Test
    fun `postal code between house and city parses`() {
        val parsed = AddressParser.parse("Erbstollenstraße 10 58454 Witten")!!
        assertEquals("Erbstollenstraße", parsed.street)
        assertEquals("10", parsed.houseNumber)
        assertEquals("58454", parsed.postalCode)
        assertEquals("Witten", parsed.city)
    }

    @Test
    fun `postal code glued to street without house parses`() {
        val parsed = AddressParser.parse("Erbstollenstraße 58454 Witten")!!
        assertEquals("Erbstollenstraße", parsed.street)
        assertEquals("", parsed.houseNumber)
        assertEquals("58454", parsed.postalCode)
        assertEquals("Witten", parsed.city)
    }

    @Test
    fun `leading house number parses`() {
        val parsed = AddressParser.parse("10 Erbstollenstraße, 58454 Witten")!!
        assertEquals("Erbstollenstraße", parsed.street)
        assertEquals("10", parsed.houseNumber)
        assertEquals("Witten", parsed.city)
    }

    @Test
    fun `city only is not an address query`() {
        assertNull(AddressParser.parse("Witten"))
    }

    @Test
    fun `postal code only is not an address query`() {
        assertNull(AddressParser.parse("58454 Witten"))
    }

    @Test
    fun `house range parses`() {
        val parsed = AddressParser.parse("Musterweg 1-3, 12345 Musterstadt")!!
        assertEquals("Musterweg", parsed.street)
        assertEquals("1-3", parsed.houseNumber)
        assertEquals("12345", parsed.postalCode)
        assertEquals("Musterstadt", parsed.city)
    }

    @Test
    fun `street field with embedded postal normalizes`() {
        // Contact street field carries the postal code and the separate
        // postal-code field is empty: the PLZ must not be taken as the house
        // number (spec: address-book-search "Postal code in street field
        // parses correctly").
        val (street, house, postal) = AddressParser.normalizeStreetField("Erbstollenstraße 10 58454", "")
        assertEquals("Erbstollenstraße", street)
        assertEquals("10", house)
        assertEquals("58454", postal)
    }

    @Test
    fun `separate postal code field wins over embedded`() {
        val (street, house, postal) = AddressParser.normalizeStreetField("Erbstollenstraße 10", "58454")
        assertEquals("Erbstollenstraße", street)
        assertEquals("10", house)
        assertEquals("58454", postal)
    }

    @Test
    fun `tokenize normalizes sharp s`() {
        assertEquals(listOf("hauptstrasse"), AddressParser.tokenize("Hauptstraße"))
        assertEquals(listOf("hauptstrasse", "10"), AddressParser.tokenize("Hauptstraße 10"))
    }

    @Test
    fun `street evidence matches transliterated labels`() {
        val entry = com.framstag.libosmscout.client.LocationEntry().apply {
            label = "Hauptstraße 10"
            objectType = "address"
            matchQuality = "match"
        }
        assertTrue(AddressRanker.hasStreetEvidence(entry, AddressParser.tokenize("Hauptstraße")))
    }
}
