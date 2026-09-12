package com.naviveylin.ui.addressbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.R
import com.naviveylin.core.addressbook.ContactAddressBookEntry

/**
 * Address-book person search embedded as the Contacts mode of the unified
 * search dialog (spec: address-book-search, search-dialog — Contacts mode).
 *
 * Flow: searchable contact list -> (multi-address contact: address pick) ->
 * resolution -> resolved [LocationEntry] handed to [onResultSelected], which
 * opens the existing details view on the map screen. The search field lives
 * in the dialog's shared search bar; the query is wired to the
 * [AddressBookViewModel] by the caller.
 */
@Composable
fun AddressBookSearchContent(
    onResultSelected: (LocationEntry) -> Unit,
    viewModel: AddressBookViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // A successful resolution is emitted once; hand it to the map screen.
    LaunchedEffect(state.resolvedEntry) {
        state.resolvedEntry?.let { entry ->
            onResultSelected(entry)
            viewModel.consumeResolved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Transient error from the last failed resolution (spec:
        // address-book-search — Address not found). Rendered as a banner, not
        // a state: the list below stays interactive and any query edit or
        // reload clears it (design D1).
        if (state.resolutionError && !state.isLoading && !state.isResolving) {
            Text(
                text = stringResource(R.string.address_book_not_found),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator() }

            state.loadFailed -> CenteredBox {
                Text(
                    "Could not read the address book",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            state.isResolving -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.address_book_resolving))
                }
            }

            state.selectedContact != null -> AddressPicker(
                contact = state.selectedContact!!,
                onAddressSelected = viewModel::onAddressSelected,
                onBack = viewModel::clearSelectedContact
            )

            state.filteredContacts.isEmpty() -> CenteredBox {
                Text(
                    stringResource(
                        if (state.query.isBlank()) R.string.address_book_empty
                        else R.string.address_book_no_match
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else -> ContactList(
                contacts = state.filteredContacts,
                onPersonSelected = viewModel::onPersonSelected
            )
        }
    }
}

@Composable
private fun ContactList(
    contacts: List<ContactAddressBookEntry>,
    onPersonSelected: (ContactAddressBookEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(contacts, key = { it.contactId }) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPersonSelected(contact) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = contact.name.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.addresses.joinToString(" · ") { it.displayText },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressPicker(
    contact: ContactAddressBookEntry,
    onAddressSelected: (com.naviveylin.core.addressbook.ContactPostalAddress) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.address_book_pick_address))
            }
        }
        Text(
            text = contact.name.ifBlank { "Unknown" },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(contact.addresses, key = { it.hashCode() }) { address ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddressSelected(address) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = address.displayText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
