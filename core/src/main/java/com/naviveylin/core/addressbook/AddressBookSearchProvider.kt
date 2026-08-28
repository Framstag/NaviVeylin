package com.naviveylin.core.addressbook

import com.framstag.libosmscout.client.LocationEntry

/**
 * Resolves a contact's postal address into map locations via the offline
 * libosmscout search backend. Implemented in the [:app] module via Hilt;
 * consumed by the phone address-book sheet and the Android Auto
 * [AddressBookScreen] so both surfaces share one resolution strategy
 * (query building, fallback, ranking).
 */
fun interface AddressBookSearchProvider {

    /**
     * Resolve [address] to candidate [LocationEntry]s, best match first.
     * Empty when nothing could be resolved.
     */
    fun resolveAddress(address: ContactPostalAddress): List<LocationEntry>
}
