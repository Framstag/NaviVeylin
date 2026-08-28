package com.naviveylin.core.addressbook

/**
 * Provider for contacts that have at least one postal address.
 * Implemented in the [:app] module via Hilt; consumed by the phone
 * address-book sheet and the Android Auto [AddressBookScreen].
 *
 * Mirrors the [com.naviveylin.core.AutoSearchProvider] pattern so [:auto]
 * (which does not depend on [:app]) can access contacts without duplicating
 * the ContactsContract query.
 */
fun interface AddressBookContactsProvider {

    /** All contacts that have at least one non-blank postal address. */
    fun contactsWithAddresses(): List<ContactAddressBookEntry>
}
