package com.naviveylin.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers that the address-book rationale dialog has been shown once
 * (spec: address-book-permission — decision remembered). One boolean in
 * SharedPreferences; never reset unless the app data is cleared.
 */
@Singleton
class AddressBookRationaleStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the rationale dialog has been shown on this install. */
    fun wasShown(): Boolean = prefs.getBoolean(KEY_SHOWN, false)

    /** Mark the rationale dialog as shown (called on every dismissal). */
    fun markShown() {
        prefs.edit().putBoolean(KEY_SHOWN, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "address_book"
        const val KEY_SHOWN = "address_book_rationale_shown"
    }
}
