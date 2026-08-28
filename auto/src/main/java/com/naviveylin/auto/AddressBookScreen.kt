package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SearchTemplate.SearchCallback
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto address-book person search (spec: address-book-search).
 *
 * SearchTemplate that lists contacts with at least one postal address,
 * filtered by name as the driver types. Selecting a contact with a single
 * address resolves it immediately; multiple addresses open an address picker
 * ([AddressBookAddressPickerScreen]). The resolved object opens the existing
 * [DetailsScreen]. Backed by [AddressBookContactsProvider] and
 * [AddressBookSearchProvider] via [AutoEntryPoint].
 */
class AddressBookScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val contactsProvider: AddressBookContactsProvider =
        entryPoint.addressBookContactsProvider()
    private val searchProvider: AddressBookSearchProvider =
        entryPoint.addressBookSearchProvider()

    private var allContacts: List<ContactAddressBookEntry> = emptyList()
    private var filteredContacts: List<ContactAddressBookEntry> = emptyList()
    private var isLoading = true
    private var query = ""
    private var notFound = false

    init {
        enableBackNavigation()
        loadContacts()
    }

    private fun loadContacts() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val contacts = withContext(Dispatchers.Default) {
                try {
                    contactsProvider.contactsWithAddresses()
                } catch (e: Exception) {
                    Log.e(TAG, "contactsWithAddresses failed", e)
                    emptyList()
                }
            }
            allContacts = contacts
            filteredContacts = AddressBookScreenMapper.filterContacts(contacts, query)
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): SearchTemplate {
        val builder = SearchTemplate.Builder(SearchCallbackImpl())
            .setShowKeyboardByDefault(true)
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            builder.setLoading(true)
        } else {
            builder.setItemList(buildList())
        }
        return builder.build()
    }

    private fun buildList(): ItemList {
        val builder = ItemList.Builder()
        if (notFound) {
            builder.addItem(Row.Builder().setTitle("No location found for this address").build())
            return builder.build()
        }
        if (filteredContacts.isEmpty()) {
            val message = if (allContacts.isEmpty()) {
                "No contacts with addresses"
            } else {
                "No matching contacts"
            }
            builder.addItem(Row.Builder().setTitle(message).build())
            return builder.build()
        }
        for (contact in filteredContacts) {
            builder.addItem(
                Row.Builder()
                    .setTitle(AddressBookScreenMapper.rowTitle(contact))
                    .addText(AddressBookScreenMapper.rowText(contact))
                    .setOnClickListener { onContactSelected(contact) }
                    .build()
            )
        }
        return builder.build()
    }

    private fun onContactSelected(contact: ContactAddressBookEntry) {
        Log.d(TAG, "Selected contact '${contact.name}' with ${contact.addresses.size} addresses")
        when (val decision = AddressBookScreenMapper.decideSelection(contact)) {
            is AddressBookScreenMapper.Selection.Resolve -> resolve(decision.address)
            is AddressBookScreenMapper.Selection.Pick -> screenManager.push(
                AddressBookAddressPickerScreen(carContext, navigationViewModel, decision.contact)
            )
        }
    }

    private fun resolve(
        address: com.naviveylin.core.addressbook.ContactPostalAddress
    ) {
        filterJob?.cancel()
        filterJob = scope.launch {
            val results = withContext(Dispatchers.Default) {
                try {
                    searchProvider.resolveAddress(address)
                } catch (e: Exception) {
                    Log.e(TAG, "resolveAddress failed", e)
                    emptyList()
                }
            }
            val best = results.firstOrNull()
            if (best != null) {
                Log.d(TAG, "Resolved '${address.displayText}' -> ${best.label}")
                carContext.getCarService(ScreenManager::class.java).push(
                    DetailsScreen(
                        carContext, navigationViewModel, best.lat, best.lon,
                        nameHint = best.label
                    )
                )
            } else {
                // No location found: show a message row instead of the list.
                notFound = true
                invalidate()
                Log.d(TAG, "No location found for '${address.displayText}'")
            }
        }
    }

    private inner class SearchCallbackImpl : SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            query = searchText
            notFound = false
            filterJob?.cancel()
            filterJob = scope.launch {
                delay(AddressBookScreenMapper.SEARCH_DEBOUNCE_MS)
                filteredContacts = AddressBookScreenMapper.filterContacts(allContacts, searchText)
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AddressBookScreen"
    }
}
