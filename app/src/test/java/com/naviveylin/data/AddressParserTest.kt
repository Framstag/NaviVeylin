package com.naviveylin.data

import com.naviveylin.core.addressbook.ContactPostalAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the free-text address parser and the structured-components merge
 * (spec: fix-address-lookup-accuracy — full formatted address resolution,
 * postal-code-in-street parsing; spec: address-book-search — formatted
 * address source, formatted address completes partial components).
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

    // --- structured components merged with the formatted address
    // (spec: address-book-search — formatted address source)

    @Test
    fun `formatted address supplies every component when structured fields are empty`() {
        val components = AddressParser.components(
            street = "",
            postalCode = "",
            city = "",
            region = "",
            formatted = "Erbstollenstraße 10, 58454 Witten"
        )
        assertEquals("Erbstollenstraße", components.street)
        assertEquals("10", components.houseNumber)
        assertEquals("58454", components.postalCode)
        assertEquals("Witten", components.city)
    }

    @Test
    fun `formatted address completes a missing city`() {
        val components = AddressParser.components(
            street = "Erbstollenstraße 10",
            postalCode = "",
            city = "",
            region = "",
            formatted = "Erbstollenstraße 10, 58454 Witten"
        )
        assertEquals("Erbstollenstraße", components.street)
        assertEquals("58454", components.postalCode)
        assertEquals("Witten", components.city)
    }

    @Test
    fun `formatted address completes a missing street`() {
        val components = AddressParser.components(
            street = "",
            postalCode = "58454",
            city = "Witten",
            region = "",
            formatted = "Erbstollenstraße 10, 58454 Witten"
        )
        assertEquals("Erbstollenstraße", components.street)
        assertEquals("10", components.houseNumber)
        assertEquals("58454", components.postalCode)
        assertEquals("Witten", components.city)
    }

    @Test
    fun `structured values win over the formatted address`() {
        val components = AddressParser.components(
            street = "Hauptstraße 5",
            postalCode = "44339",
            city = "Dortmund",
            region = "",
            formatted = "Erbstollenstraße 10, 58454 Witten"
        )
        assertEquals("Hauptstraße", components.street)
        assertEquals("5", components.houseNumber)
        assertEquals("44339", components.postalCode)
        assertEquals("Dortmund", components.city)
    }

    @Test
    fun `embedded postal code is never a house number after the merge`() {
        val components = AddressParser.components(
            street = "Erbstollenstraße 10 58454",
            postalCode = "",
            city = "Witten",
            region = "",
            formatted = ""
        )
        assertEquals("Erbstollenstraße", components.street)
        assertEquals("10", components.houseNumber)
        assertEquals("58454", components.postalCode)
    }

    @Test
    fun `region is the city fallback without a formatted address`() {
        val components = AddressParser.components(
            street = "Erbstollenstraße 10",
            postalCode = "58454",
            city = "",
            region = "Nordrhein-Westfalen",
            formatted = ""
        )
        assertEquals("Nordrhein-Westfalen", components.city)
    }

    @Test
    fun `comma and space separated formatted addresses merge identically`() {
        val comma = AddressParser.components("", "", "", "", "Erbstollenstraße 10, 58454 Witten")
        val space = AddressParser.components("", "", "", "", "Erbstollenstraße 10 58454 Witten")
        assertEquals(comma, space)
    }

    @Test
    fun `sharp s in a formatted street survives the merge normalized`() {
        val components = AddressParser.components("", "", "Witten", "", "Hauptstraße 5, 58454 Witten")
        assertEquals(listOf("hauptstrasse"), AddressParser.tokenize(components.street))
        assertEquals("5", components.houseNumber)
    }

    // --- address identity key (spec: address-book-search "Identical addresses
    // from two accounts collapse")

    @Test
    fun `identity key ignores case and surrounding whitespace`() {
        val a = ContactPostalAddress(street = "Main Street 1", postalCode = "10115", city = "Berlin")
        val b = ContactPostalAddress(street = " main street 1 ", postalCode = " 10115 ", city = "BERLIN")
        assertEquals(AddressParser.identityKey(a), AddressParser.identityKey(b))
    }

    @Test
    fun `identity key folds sharp s`() {
        val a = ContactPostalAddress(street = "Hauptstraße 5", postalCode = "58454", city = "Witten")
        val b = ContactPostalAddress(street = "Hauptstrasse 5", postalCode = "58454", city = "Witten")
        assertEquals(AddressParser.identityKey(a), AddressParser.identityKey(b))
    }

    @Test
    fun `identity key matches component-only and formatted-only entries`() {
        val structured = ContactPostalAddress(
            street = "Main Street 1",
            postalCode = "10115",
            city = "Berlin"
        )
        val formatted = ContactPostalAddress(formatted = "Main Street 1, 10115 Berlin")
        assertEquals(AddressParser.identityKey(structured), AddressParser.identityKey(formatted))
    }

    @Test
    fun `identity key distinguishes different addresses`() {
        val a = ContactPostalAddress(street = "Main Street 1", postalCode = "10115", city = "Berlin")
        val b = ContactPostalAddress(street = "Main Street 1", postalCode = "10115", city = "Potsdam")
        val c = ContactPostalAddress(street = "Main Street 2", postalCode = "10115", city = "Berlin")
        assertNotEquals(AddressParser.identityKey(a), AddressParser.identityKey(b))
        assertNotEquals(AddressParser.identityKey(a), AddressParser.identityKey(c))
    }
}
