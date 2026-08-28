package com.naviveylin.data

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads contacts with postal addresses from the device address book via
 * [ContactsContract]. Implements [AddressBookContactsProvider] for the phone
 * app and, through it, the Android Auto screen.
 *
 * Called from a background dispatcher (never the main thread). Contacts are
 * read on demand and never persisted.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AddressBookContactsProvider {

    private val resolver: ContentResolver get() = context.contentResolver

    @SuppressLint("Recycle")
    override fun contactsWithAddresses(): List<ContactAddressBookEntry> {
        val byContact = LinkedHashMap<Long, MutableList<ContactPostalAddress>>()
        queryPostalAddresses(byContact)
        if (byContact.isEmpty()) return emptyList()

        val names = queryDisplayNames(byContact.keys)

        return byContact
            .map { (id, addresses) -> ContactAddressBookEntry(id, names[id] ?: "", addresses) }
            .sortedBy { it.name.lowercase() }
    }

    private fun queryPostalAddresses(byContact: MutableMap<Long, MutableList<ContactPostalAddress>>) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.StructuredPostal._ID,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredPostal.STREET,
            ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
            ContactsContract.CommonDataKinds.StructuredPostal.CITY,
            ContactsContract.CommonDataKinds.StructuredPostal.REGION,
            ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
        )
        resolver.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            if (cursor.columnCount == 0) return@use
            val colContactId = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID
            )
            val colStreet = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.STREET
            )
            val colPostcode = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE
            )
            val colCity = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.CITY
            )
            val colRegion = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.REGION
            )
            val colCountry = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
            )
            val colFormatted = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
            )
            while (cursor.moveToNext()) {
                val address = ContactPostalAddress(
                    street = cursor.getString(colStreet).orEmpty().trim(),
                    postalCode = cursor.getString(colPostcode).orEmpty().trim(),
                    city = cursor.getString(colCity).orEmpty().trim(),
                    region = cursor.getString(colRegion).orEmpty().trim(),
                    country = cursor.getString(colCountry).orEmpty().trim(),
                    formatted = cursor.getString(colFormatted).orEmpty().trim()
                )
                if (address.isBlank) continue
                byContact
                    .getOrPut(cursor.getLong(colContactId)) { mutableListOf() }
                    .add(address)
            }
        } ?: Log.w(TAG, "queryPostalAddresses: no cursor from provider")
    }

    @SuppressLint("Recycle")
    private fun queryDisplayNames(contactIds: Set<Long>): Map<Long, String> {
        if (contactIds.isEmpty()) return emptyMap()
        val selection = ContactsContract.Contacts._ID + " IN (" +
            contactIds.joinToString(",") + ")"
        val names = HashMap<Long, String>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            selection, null, null
        )?.use { cursor ->
            val colId = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val colName = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                names[cursor.getLong(colId)] = cursor.getString(colName).orEmpty().trim()
            }
        }
        return names
    }

    private companion object {
        const val TAG = "ContactsRepository"
    }
}
