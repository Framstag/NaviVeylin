package com.naviveylin.ui.addressbook

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen state for the address-book person search sheet.
 *
 * @param selectedContact set when a person with multiple addresses is picked
 *   (address selection step pending); null otherwise.
 * @param resolutionError true when the last resolution found no location.
 * @param resolvedEntry emitted once when a resolution succeeds; the sheet
 *   hands it to the map screen (details view) and calls [consumeResolved].
 */
data class AddressBookUiState(
    val query: String = "",
    val allContacts: List<ContactAddressBookEntry> = emptyList(),
    val filteredContacts: List<ContactAddressBookEntry> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val selectedContact: ContactAddressBookEntry? = null,
    val isResolving: Boolean = false,
    val resolutionError: Boolean = false,
    val resolvedEntry: LocationEntry? = null
)

/**
 * Address-book person search (spec: address-book-search — searchable list of
 * persons with addresses, multi-address selection, address resolution).
 * Contacts are read on demand via [AddressBookContactsProvider]; resolution
 * goes through [AddressBookSearchProvider] (shared with Android Auto).
 */
@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val contactsProvider: AddressBookContactsProvider,
    private val searchProvider: AddressBookSearchProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressBookUiState())
    val uiState: StateFlow<AddressBookUiState> = _uiState.asStateFlow()

    /** Background dispatcher; swapped to a test dispatcher in unit tests. */
    @VisibleForTesting
    internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Load all contacts with addresses (background), then show them.
     * Called by the sheet on first composition (and re-callable for retry).
     */
    fun start() {
        loadContacts()
    }

    /** Load all contacts with addresses (background), then show them. */
    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
            val contacts = try {
                contactsProvider.contactsWithAddresses()
            } catch (e: Exception) {
                Log.w(TAG, "contactsWithAddresses failed: ${e.message}")
                null
            }
            if (contacts == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                allContacts = contacts,
                filteredContacts = filter(contacts, _uiState.value.query),
                isLoading = false
            )
        }
    }

    /** Filter the contact list by name (case-insensitive contains). */
    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            query = query,
            filteredContacts = filter(_uiState.value.allContacts, query)
        )
    }

    /**
     * Person selected: exactly one address resolves immediately; multiple
     * addresses defer to [onAddressSelected] (spec: address-book-search).
     */
    fun onPersonSelected(entry: ContactAddressBookEntry) {
        if (entry.addresses.size == 1) {
            resolve(entry.addresses[0])
        } else {
            _uiState.value = _uiState.value.copy(selectedContact = entry)
        }
    }

    /** Address picked for a multi-address contact; resolve it. */
    fun onAddressSelected(address: ContactPostalAddress) {
        resolve(address)
    }

    /** Back out of the multi-address selection step. */
    fun clearSelectedContact() {
        _uiState.value = _uiState.value.copy(selectedContact = null)
    }

    /** Consume the emitted resolution result after the sheet hands it over. */
    fun consumeResolved() {
        _uiState.value = _uiState.value.copy(
            resolvedEntry = null,
            selectedContact = null,
            isResolving = false
        )
    }

    private fun resolve(address: ContactPostalAddress) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolving = true,
                resolutionError = false,
                selectedContact = null
            )
            val results = withContext(defaultDispatcher) {
                try {
                    searchProvider.resolveAddress(address)
                } catch (e: Exception) {
                    Log.e(TAG, "resolveAddress failed", e)
                    emptyList()
                }
            }
            val best = results.firstOrNull()
            _uiState.value = if (best != null) {
                _uiState.value.copy(isResolving = false, resolvedEntry = best)
            } else {
                _uiState.value.copy(isResolving = false, resolutionError = true)
            }
        }
    }

    private fun filter(contacts: List<ContactAddressBookEntry>, query: String): List<ContactAddressBookEntry> {
        val q = query.trim()
        return if (q.isEmpty()) {
            contacts
        } else {
            contacts.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    private companion object {
        const val TAG = "AddressBookViewModel"
    }
}
