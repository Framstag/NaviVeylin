package com.naviveylin.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the rationale-once flag (spec: address-book-permission — decision
 * remembered): false before first show, true after, persists across instances.
 */
@RunWith(RobolectricTestRunner::class)
class AddressBookRationaleStoreTest {

    @Test
    fun `flag false initially`() {
        val store = AddressBookRationaleStore(ApplicationProvider.getApplicationContext())
        assertFalse(store.wasShown())
    }

    @Test
    fun `markShown persists`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AddressBookRationaleStore(context).markShown()

        assertTrue(AddressBookRationaleStore(context).wasShown())
    }
}
