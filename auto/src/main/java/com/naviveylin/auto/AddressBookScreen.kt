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
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 *
 * The list presents at most the row capacity the connected host displays and keeps the remaining
 * contacts reachable through its final page row (spec: `car-list-row-limit`; design D1/D3) — the host
 * silently ignores the rows over that capacity, so an uncapped list loses the tail of a large address
 * book with no signal. A page carries the search text it was opened with, so it continues the same
 * filtered list (design D4).
 */
class AddressBookScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    /** The page of the contact list to show; 0 is its first page (spec: `car-list-row-limit`; design D3). */
    private val page: Int = 0,
    /**
     * The search text this list is filtered by on opening. A page row carries the text of the page that
     * opened it, so the page continues the sequence the driver was looking at rather than the unfiltered
     * contacts (spec: `car-list-row-limit` — The address-book page continues the list the driver was
     * shown; design D4).
     */
    private val initialQuery: String = ""
) : Screen(carContext) {

    private val scope = carScreenScope("AddressBookScreen")
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
    private var query = initialQuery
    private var notFound = false

    /** The page this screen shows; a changed search text returns to its first page (design D4). */
    private var currentPage = page

    /**
     * The row capacity this screen presents (spec: `car-list-row-limit`; design D2). Starts at the library
     * default so the first template — which the host may ask for before the query returns — is already
     * bounded, and is replaced by the host's answer on the screen's own scope.
     */
    private var rowCapacity: Int = CarListCapacityReader.LIBRARY_DEFAULT

    init {
        enableBackNavigation()
        readRowCapacity()
        loadContacts()
    }

    /**
     * Ask the host for this screen's row capacity, off the host thread, and re-render with the answer
     * (spec: `car-list-row-limit` — A car list never presents more rows than the host displays; design D2).
     * A host below car API 2, a host whose query fails and an unusable answer all take the library's
     * default instead ([CarListCapacityReader]), which the reader records as a coordinate-free
     * diagnostics line.
     */
    private fun readRowCapacity() {
        scope.launch {
            val capacity = withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }
            if (capacity != rowCapacity) {
                rowCapacity = capacity
                invalidate()
            }
        }
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

    override fun onGetTemplate(): SearchTemplate = carSearchTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carSearchTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): SearchTemplate {
        val builder = SearchTemplate.Builder(SearchCallbackImpl())
            .setShowKeyboardByDefault(true)
            .setHeaderAction(Action.BACK)

        // A page opened by a page row was built for a carried search text: keeping it in the field is what
        // makes the rows match what the driver reads there (design D4). A screen opened without one leaves
        // the field to the host.
        if (initialQuery.isNotBlank()) {
            builder.setInitialSearchText(initialQuery)
        }

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
            builder.addItem(Row.Builder().setTitle(carContext.getString(R.string.no_location_found)).build())
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
        // The filtered contacts, capped at the host's capacity, with the remainder behind the final page
        // row (spec: `car-list-row-limit` — A car list never presents more rows than the host displays;
        // Items beyond the capacity stay reachable). The arithmetic is the mapper's, so the pages partition
        // the filtered sequence: every contact is reachable exactly once.
        val contacts =
            FavoritesScreenMapper.page(filteredContacts, capacity = rowCapacity, page = currentPage)
        for (contact in contacts.rows) {
            builder.addItem(
                Row.Builder()
                    .setTitle(AddressBookScreenMapper.rowTitle(contact))
                    .addText(AddressBookScreenMapper.rowText(contact))
                    .setOnClickListener { onContactSelected(contact) }
                    .build()
            )
        }
        if (contacts.hasFurtherPage) {
            builder.addItem(pageRow(contacts.remaining))
        }
        return builder.build()
    }

    /**
     * The final row of a page holding more contacts than it shows: it names how many are left and opens the
     * next page of the same list (spec: `car-list-row-limit` — The final row opens the next page; The
     * address-book page continues the list the driver was shown; design D3).
     *
     * The next page is a pushed screen of the same class carrying this screen's search text, so the host's
     * back navigation returns to the page that opened it (spec: "Back navigation returns to the previous
     * page").
     */
    private fun pageRow(remaining: Int): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.more_items, remaining))
        .setOnClickListener {
            Log.d(TAG, "Opening the next address-book page: $remaining more")
            armScreenPush(carContext, scope, "AddressBookScreen (page)") {
                AddressBookScreen(
                    carContext = carContext,
                    navigationViewModel = navigationViewModel,
                    page = currentPage + 1,
                    initialQuery = query
                )
            }
        }
        .build()

    private fun onContactSelected(contact: ContactAddressBookEntry) {
        Log.d(TAG, "Selected contact '${contact.name}' with ${contact.addresses.size} addresses")
        when (val decision = AddressBookScreenMapper.decideSelection(contact)) {
            is AddressBookScreenMapper.Selection.Resolve -> resolve(decision.address)
            is AddressBookScreenMapper.Selection.Pick -> armScreenPush(
                carContext, scope, "AddressBookAddressPickerScreen"
            ) {
                AddressBookAddressPickerScreen(carContext, navigationViewModel, decision.contact)
            }
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
                armScreenPush(carContext, scope, "DetailsScreen (address book)") {
                    DetailsScreen(
                        carContext, navigationViewModel, best.lat, best.lon,
                        nameHint = best.label
                    )
                }
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
            // A search text that changed is a different list, and looking at a list starts at its first
            // page, so the shown page returns to 0 (design D4). A repeated text — the host may deliver the
            // field's own content again — leaves the page alone.
            if (searchText != query) {
                query = searchText
                currentPage = 0
            }
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
