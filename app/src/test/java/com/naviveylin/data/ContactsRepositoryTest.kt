package com.naviveylin.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

/**
 * Verifies ContactsRepository reads postal addresses and display names from
 * the contacts provider (spec: address-book-search — searchable list of
 * persons with addresses). Uses a fake provider registered on the shadow
 * content resolver; contacts without addresses never appear.
 */
@RunWith(RobolectricTestRunner::class)
class ContactsRepositoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun registerProvider(
        postal: List<Map<String, Any?>>,
        contacts: List<Map<String, Any?>>
    ) {
        ShadowContentResolver.registerProviderInternal(
            ContactsContract.AUTHORITY,
            FakeContactsProvider(postal, contacts)
        )
    }

    private fun postalRow(
        id: Long,
        contactId: Long,
        street: String = "Main Street 1",
        city: String = "Berlin",
        postcode: String = "10115",
        formatted: String = "$street, $postcode $city"
    ) = mapOf(
        ContactsContract.CommonDataKinds.StructuredPostal._ID to id,
        ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID to contactId,
        ContactsContract.CommonDataKinds.StructuredPostal.STREET to street,
        ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE to postcode,
        ContactsContract.CommonDataKinds.StructuredPostal.CITY to city,
        ContactsContract.CommonDataKinds.StructuredPostal.REGION to "",
        ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY to "",
        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS to formatted
    )

    private fun contactRow(id: Long, name: String) = mapOf(
        ContactsContract.Contacts._ID to id,
        ContactsContract.Contacts.DISPLAY_NAME to name
    )

    @Test
    fun `returns only contacts with non-blank addresses`() {
        registerProvider(
            postal = listOf(
                postalRow(1, 10, street = "Main Street 1"),
                postalRow(2, 11, street = "   ", city = "  ", postcode = "", formatted = "")
            ),
            contacts = listOf(contactRow(10, "Alice"), contactRow(11, "Bob"))
        )

        val entries = ContactsRepository(context).contactsWithAddresses()

        assertEquals(listOf("Alice"), entries.map { it.name })
        assertEquals(1, entries[0].addresses.size)
    }

    @Test
    fun `multiple addresses of one contact are grouped`() {
        registerProvider(
            postal = listOf(
                postalRow(1, 10, street = "Main Street 1"),
                postalRow(2, 10, street = "Second Street 2", city = "Potsdam", postcode = "14467")
            ),
            contacts = listOf(contactRow(10, "Alice"))
        )

        val entries = ContactsRepository(context).contactsWithAddresses()

        assertEquals(1, entries.size)
        assertEquals("Alice", entries[0].name)
        assertEquals(2, entries[0].addresses.size)
        assertEquals(listOf("Main Street 1", "Second Street 2"), entries[0].addresses.map { it.street })
    }

    @Test
    fun `entries are sorted by display name`() {
        registerProvider(
            postal = listOf(
                postalRow(1, 10, street = "Main Street 1"),
                postalRow(2, 12, street = "Other Street 3"),
                postalRow(3, 11, street = "Third Street 4")
            ),
            contacts = listOf(
                contactRow(10, "Charlie"),
                contactRow(11, "alice"),
                contactRow(12, "Bob")
            )
        )

        val entries = ContactsRepository(context).contactsWithAddresses()

        assertEquals(listOf("alice", "Bob", "Charlie"), entries.map { it.name })
    }

    @Test
    fun `empty address book returns empty list`() {
        registerProvider(postal = emptyList(), contacts = emptyList())
        assertTrue(ContactsRepository(context).contactsWithAddresses().isEmpty())
    }

    /** Minimal in-memory contacts provider for the shadow content resolver. */
    private class FakeContactsProvider(
        private val postal: List<Map<String, Any?>>,
        private val contacts: List<Map<String, Any?>>
    ) : ContentProvider() {

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor = when {
            uri.toString().startsWith(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI.toString()) ->
                cursor(postal)
            uri.toString().startsWith(ContactsContract.Contacts.CONTENT_URI.toString()) ->
                cursor(contacts)
            else -> throw IllegalArgumentException("Unexpected URI $uri")
        }

        private fun cursor(rows: List<Map<String, Any?>>): Cursor {
            val columns = rows.firstOrNull()?.keys?.toList().orEmpty()
            val matrix = MatrixCursor(columns.toTypedArray())
            for (row in rows) {
                matrix.addRow(columns.map { row[it] })
            }
            return matrix
        }

        override fun getType(uri: Uri): String = "vnd.android.cursor.item"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
