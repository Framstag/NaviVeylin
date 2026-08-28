package com.naviveylin.ui.addressbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
 * Full-screen address-book person search sheet (spec: address-book-search).
 *
 * Flow: searchable contact list -> (multi-address contact: address pick) ->
 * resolution -> resolved [LocationEntry] handed to [onResultSelected], which
 * opens the existing details view on the map screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookSheet(
    onDismiss: () -> Unit,
    onResultSelected: (LocationEntry) -> Unit,
    viewModel: AddressBookViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Load contacts on first composition (explicit so tests control timing).
    LaunchedEffect(Unit) { viewModel.start() }

    BackHandler { onDismiss() }

    // A successful resolution is emitted once; hand it to the map screen.
    LaunchedEffect(state.resolvedEntry) {
        state.resolvedEntry?.let { entry ->
            onResultSelected(entry)
            viewModel.consumeResolved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.address_book_menu_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.address_book_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field — only meaningful on the contact list
            if (state.selectedContact == null && !state.isResolving) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.address_book_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
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

                state.resolutionError -> CenteredBox {
                    Text(
                        stringResource(R.string.address_book_not_found),
                        style = MaterialTheme.typography.bodyLarge
                    )
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
