package com.naviveylin.auto

import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapper tests for [AddressBookScreen] (spec: address-book-search — searchable
 * list of persons with addresses): filtering by name and row content.
 */
class AddressBookScreenMapperTest {

    private val alice = ContactAddressBookEntry(
        1, "Alice",
        listOf(ContactPostalAddress(street = "Main Street 1", city = "Berlin"))
    )
    private val bob = ContactAddressBookEntry(
        2, "Bob",
        listOf(
            ContactPostalAddress(street = "Second Street 2", city = "Potsdam"),
            ContactPostalAddress(street = "Third Street 3", city = "Potsdam")
        )
    )

    @Test
    fun blankQueryReturnsAll() {
        val result = AddressBookScreenMapper.filterContacts(listOf(alice, bob), "")
        assertEquals(listOf(alice, bob), result)
    }

    @Test
    fun filterIsCaseInsensitiveByName() {
        assertEquals(
            listOf(alice),
            AddressBookScreenMapper.filterContacts(listOf(alice, bob), "ALI")
        )
        assertEquals(
            listOf(bob),
            AddressBookScreenMapper.filterContacts(listOf(alice, bob), "b")
        )
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(
            emptyList<ContactAddressBookEntry>(),
            AddressBookScreenMapper.filterContacts(listOf(alice, bob), "zzz")
        )
    }

    @Test
    fun rowContentJoinsAddresses() {
        assertEquals("Alice", AddressBookScreenMapper.rowTitle(alice))
        assertEquals("Main Street 1, Berlin", AddressBookScreenMapper.rowText(alice))
        assertEquals(
            "Second Street 2, Potsdam · Third Street 3, Potsdam",
            AddressBookScreenMapper.rowText(bob)
        )
    }

    @Test
    fun singleAddressResolvesDirectly() {
        val decision = AddressBookScreenMapper.decideSelection(alice)
        assertEquals(
            AddressBookScreenMapper.Selection.Resolve(alice.addresses[0]),
            decision
        )
    }

    @Test
    fun multiAddressDefersToPicker() {
        val decision = AddressBookScreenMapper.decideSelection(bob)
        assertEquals(
            AddressBookScreenMapper.Selection.Pick(bob),
            decision
        )
    }
}
