package com.naviveylin.ui.addressbook

import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Address-book search view model tests (spec: address-book-search — searchable
 * list, filter, multi-address selection, resolution, not-found feedback).
 * Runs under Robolectric so android.util.Log (used by the view model) is
 * mocked and the view-model coroutines run to completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddressBookViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val alice = ContactAddressBookEntry(
        1, "Alice",
        listOf(ContactPostalAddress(street = "Main Street 1", city = "Berlin"))
    )
    private val bob = ContactAddressBookEntry(
        2, "Bob",
        listOf(
            ContactPostalAddress(street = "Second Street 2", city = "Potsdam"),
            ContactPostalAddress(street = "Third Street 3", city = "Potsdam")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        contacts: List<ContactAddressBookEntry> = listOf(alice, bob),
        resolve: (ContactPostalAddress) -> com.framstag.libosmscout.client.LocationEntry? = { null }
    ): AddressBookViewModel {
        val vm = AddressBookViewModel(
            contactsProvider = AddressBookContactsProvider { contacts },
            searchProvider = AddressBookSearchProvider { address ->
                resolve(address)?.let { listOf(it) } ?: emptyList()
            }
        )
        vm.defaultDispatcher = dispatcher
        return vm
    }

    private fun TestScope.started(vm: AddressBookViewModel): AddressBookViewModel {
        vm.start()
        advanceUntilIdle()
        return vm
    }

    private fun entry(label: String): com.framstag.libosmscout.client.LocationEntry =
        com.framstag.libosmscout.client.LocationEntry().apply {
            this.label = label
            lat = 52.5
            lon = 13.4
        }

    @Test
    fun `loads contacts with addresses`() = runTest(dispatcher) {
        val vm = started(newViewModel())

        assertEquals(listOf("Alice", "Bob"), vm.uiState.value.filteredContacts.map { it.name })
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `filter is case-insensitive by name`() = runTest(dispatcher) {
        val vm = started(newViewModel())

        vm.onQueryChanged("ALI")
        assertEquals(listOf("Alice"), vm.uiState.value.filteredContacts.map { it.name })

        vm.onQueryChanged("o")
        assertEquals(listOf("Bob"), vm.uiState.value.filteredContacts.map { it.name })
    }

    @Test
    fun `clearing the query restores the list`() = runTest(dispatcher) {
        val vm = started(newViewModel())

        vm.onQueryChanged("ALI")
        vm.onQueryChanged("")
        assertEquals(listOf("Alice", "Bob"), vm.uiState.value.filteredContacts.map { it.name })
    }

    @Test
    fun `no contacts with addresses shows empty list`() = runTest(dispatcher) {
        val vm = started(newViewModel(contacts = emptyList()))

        assertTrue(vm.uiState.value.filteredContacts.isEmpty())
        assertTrue(vm.uiState.value.allContacts.isEmpty())
    }

    @Test
    fun `load failure surfaces without crash`() = runTest(dispatcher) {
        val vm = AddressBookViewModel(
            contactsProvider = AddressBookContactsProvider {
                throw IllegalStateException("provider broke")
            },
            searchProvider = AddressBookSearchProvider { emptyList() }
        )
        vm.defaultDispatcher = dispatcher
        started(vm)

        assertTrue(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `single-address contact resolves immediately`() = runTest(dispatcher) {
        val vm = newViewModel(resolve = { entry("Main Street 1 Berlin") })
        advanceUntilIdle()

        vm.onPersonSelected(alice)
        advanceUntilIdle()

        assertEquals("Main Street 1 Berlin", vm.uiState.value.resolvedEntry?.label)
        assertFalse(vm.uiState.value.isResolving)
    }

    @Test
    fun `multi-address contact defers to address pick`() = runTest(dispatcher) {
        var resolved = false
        val vm = newViewModel(resolve = {
            resolved = true
            entry("x")
        })
        advanceUntilIdle()

        vm.onPersonSelected(bob)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.selectedContact)
        assertNull(vm.uiState.value.resolvedEntry)
        assertFalse(resolved)

        vm.onAddressSelected(bob.addresses[1])
        advanceUntilIdle()

        assertTrue(resolved)
        assertNull(vm.uiState.value.selectedContact)
    }

    @Test
    fun `unresolved address shows error`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onPersonSelected(alice)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.resolutionError)
        assertNull(vm.uiState.value.resolvedEntry)
    }

    @Test
    fun `query change after failed resolution clears error and restores list`() =
        runTest(dispatcher) {
            val vm = started(newViewModel())

            vm.onPersonSelected(alice)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.resolutionError)

            vm.onQueryChanged("B")

            assertEquals(listOf("Bob"), vm.uiState.value.filteredContacts.map { it.name })
            assertFalse(vm.uiState.value.resolutionError)
        }

    @Test
    fun `reload after failed resolution clears error and shows contacts`() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onPersonSelected(alice)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.resolutionError)

            vm.loadContacts()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.resolutionError)
            assertEquals(listOf("Alice", "Bob"), vm.uiState.value.filteredContacts.map { it.name })
        }

    @Test
    fun `consumeResolved clears a stale resolution error`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onPersonSelected(alice)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.resolutionError)

        vm.consumeResolved()

        assertFalse(vm.uiState.value.resolutionError)
    }

    @Test
    fun `multi-address pick survives failed resolution and reselect reopens picker`() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onPersonSelected(bob)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.selectedContact)

            vm.onAddressSelected(bob.addresses[1])
            advanceUntilIdle()
            // Failed resolution clears the picker and sets the error…
            assertNull(vm.uiState.value.selectedContact)
            assertTrue(vm.uiState.value.resolutionError)

            // …but re-selecting the contact re-opens the picker with no stale error.
            vm.onPersonSelected(bob)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.selectedContact)
            assertFalse(vm.uiState.value.resolutionError)
        }

    @Test
    fun `consumeResolved clears the emitted entry`() = runTest(dispatcher) {
        val vm = newViewModel(resolve = { entry("Main Street 1 Berlin") })
        advanceUntilIdle()

        vm.onPersonSelected(alice)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.resolvedEntry)

        vm.consumeResolved()
        assertNull(vm.uiState.value.resolvedEntry)
        assertFalse(vm.uiState.value.isResolving)
    }
}
