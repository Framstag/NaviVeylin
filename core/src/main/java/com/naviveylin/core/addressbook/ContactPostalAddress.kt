package com.naviveylin.core.addressbook

/**
 * One postal address of a contact, read from
 * `ContactsContract.CommonDataKinds.StructuredPostal`.
 *
 * Components may be blank; [displayText] and [queryText] normalize them away.
 */
data class ContactPostalAddress(
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    /** Raw formatted address from the contact provider, used as fallback. */
    val formatted: String = ""
) {

    /** True when every component and the formatted text are blank. */
    val isBlank: Boolean
        get() = displayText.isBlank()

    /** Non-blank components joined with ", ", falling back to [formatted]. */
    val displayText: String
        get() {
            val parts = listOf(street, postalCode, city, region, country)
                .filter { it.isNotBlank() }
            return if (parts.isNotEmpty()) parts.joinToString(", ") else formatted.trim()
        }

    /** Search string for the offline location search, components joined by space. */
    val queryText: String
        get() {
            // Street (with house number) + city + region. Postal code is not
            // part of the string query (it is passed to the structured form
            // search instead); country is dropped — its name rarely matches
            // the index (transliteration mismatch, e.g. "Germany" vs
            // "Deutschland") and only adds noise.
            val parts = listOf(street, city, region)
                .filter { it.isNotBlank() }
            return parts.joinToString(" ")
        }
}

/**
 * One contact from the device address book together with all its postal
 * addresses (only non-blank addresses are included).
 */
data class ContactAddressBookEntry(
    val contactId: Long,
    val name: String,
    val addresses: List<ContactPostalAddress>
) {
    /** True when the contact has no usable postal address. */
    val hasAddress: Boolean get() = addresses.isNotEmpty()
}
