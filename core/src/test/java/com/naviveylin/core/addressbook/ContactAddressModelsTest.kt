package com.naviveylin.core.addressbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model tests (spec: address-book-search — list of persons with addresses).
 * Covers normalization of blank address components for display and query.
 */
class ContactAddressModelsTest {

    @Test
    fun `displayText joins non-blank components`() {
        val address = ContactPostalAddress(
            street = "Main Street 1",
            postalCode = "10115",
            city = "Berlin",
            region = "",
            country = "Germany"
        )
        assertEquals("Main Street 1, 10115, Berlin, Germany", address.displayText)
    }

    @Test
    fun `displayText drops blank components in the middle`() {
        val address = ContactPostalAddress(
            street = "Main Street 1",
            postalCode = "",
            city = "Berlin",
            region = "Berlin",
            country = ""
        )
        assertEquals("Main Street 1, Berlin, Berlin", address.displayText)
    }

    @Test
    fun `displayText falls back to formatted when all components blank`() {
        val address = ContactPostalAddress(formatted = "1 Main Street, Berlin")
        assertEquals("1 Main Street, Berlin", address.displayText)
    }

    @Test
    fun `fully blank address is blank`() {
        assertTrue(ContactPostalAddress().isBlank)
        assertFalse(ContactPostalAddress(city = "Berlin").isBlank)
    }

    @Test
    fun `queryText uses search-relevant components only`() {
        val address = ContactPostalAddress(
            street = "Main Street 1",
            postalCode = "10115",
            city = "Berlin",
            region = "",
            country = "Germany"
        )
        // Postal code and country excluded from the string query (postal code
        // goes to the structured form search; country is transliteration noise).
        assertEquals("Main Street 1 Berlin", address.queryText)
    }

    @Test
    fun `entry hasAddress reflects addresses`() {
        assertTrue(
            ContactAddressBookEntry(1, "Alice", listOf(ContactPostalAddress(city = "Berlin"))).hasAddress
        )
        assertFalse(ContactAddressBookEntry(2, "Bob", emptyList()).hasAddress)
    }
}
