package com.naviveylin.auto

import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress

/**
 * Pure functions for building [AddressBookScreen] content.
 * Extracted for testability (same pattern as [SearchScreenMapper]).
 */
object AddressBookScreenMapper {

    const val SEARCH_DEBOUNCE_MS = 300L

    /** What to do when a contact row is selected (spec: multi-address pick). */
    sealed interface Selection {
        /** Exactly one address: resolve it directly. */
        data class Resolve(val address: ContactPostalAddress) : Selection

        /** More than one address: let the driver pick one first. */
        data class Pick(val contact: ContactAddressBookEntry) : Selection
    }

    fun decideSelection(contact: ContactAddressBookEntry): Selection =
        if (contact.addresses.size == 1) {
            Selection.Resolve(contact.addresses[0])
        } else {
            Selection.Pick(contact)
        }

    /** Filter contacts by name fragment (case-insensitive contains). */
    fun filterContacts(
        contacts: List<ContactAddressBookEntry>,
        query: String
    ): List<ContactAddressBookEntry> {
        val q = query.trim()
        if (q.isEmpty()) return contacts
        return contacts.filter { it.name.contains(q, ignoreCase = true) }
    }

    /** Row title for a contact (falls back to a generic label when nameless). */
    fun rowTitle(contact: ContactAddressBookEntry): String =
        contact.name.ifBlank { "Contact" }

    /** Row text: the contact's addresses, joined. */
    fun rowText(contact: ContactAddressBookEntry): String =
        contact.addresses.joinToString(" · ") { it.displayText }
}
