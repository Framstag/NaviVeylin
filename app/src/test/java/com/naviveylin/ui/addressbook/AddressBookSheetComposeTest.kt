package com.naviveylin.ui.addressbook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the address picker of the Contacts search mode (spec:
 * address-book-search — selecting a person with multiple addresses, identical
 * addresses from two accounts collapse). Duplicate addresses must render once
 * and must not break the list (the picker used the address hashCode as its
 * item key, which collides for two identical addresses).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddressBookSheetComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val duplicateAddresses = ContactPostalAddress(
        street = "Erbstollenstraße 10",
        postalCode = "58454",
        city = "Witten"
    )

    /** One person whose address is stored twice (two synchronized accounts). */
    private val duplicated = ContactAddressBookEntry(
        contactId = 1,
        name = "Alice",
        addresses = listOf(duplicateAddresses, duplicateAddresses.copy())
    )

    /** One person with two genuinely different addresses. */
    private val multiAddress = ContactAddressBookEntry(
        contactId = 2,
        name = "Bob",
        addresses = listOf(
            ContactPostalAddress(street = "Main Street 1", postalCode = "10115", city = "Berlin"),
            ContactPostalAddress(street = "Second Street 2", postalCode = "14467", city = "Potsdam")
        )
    )

    private var resolvedLabel: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        resolvedLabel = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(contact: ContactAddressBookEntry): AddressBookViewModel {
        val vm = AddressBookViewModel(
            contactsProvider = AddressBookContactsProvider { listOf(contact) },
            searchProvider = AddressBookSearchProvider { address ->
                listOf(
                    com.framstag.libosmscout.client.LocationEntry().apply {
                        label = address.street
                        lat = 51.45
                        lon = 7.41
                    }
                )
            }
        )
        vm.defaultDispatcher = UnconfinedTestDispatcher()
        vm.start()
        return vm
    }

    private fun launchPicker(contact: ContactAddressBookEntry): AddressBookViewModel {
        val vm = newViewModel(contact)
        vm.onPersonSelected(contact)
        composeRule.setContent {
            AddressBookSearchContent(
                onResultSelected = { resolvedLabel = it.label },
                viewModel = vm
            )
        }
        composeRule.waitForIdle()
        return vm
    }

    @Test
    fun duplicateAddressesRenderOnceAndDoNotBreakThePicker() {
        launchPicker(duplicated)

        // The picker is open and lists the address exactly once — a duplicate
        // LazyColumn item key would have thrown during composition.
        composeRule.onAllNodesWithText("Erbstollenstraße 10, 58454, Witten").assertCountEquals(1)
    }

    @Test
    fun duplicateAddressesResolveThroughTheSingleRow() {
        val vm = launchPicker(duplicated)

        composeRule.onNodeWithText("Erbstollenstraße 10, 58454, Witten").performClick()
        composeRule.waitForIdle()

        assertEquals("Erbstollenstraße 10", resolvedLabel)
        // Resolution consumed the emitted entry and closed the picker.
        assertNull(vm.uiState.value.resolvedEntry)
        assertNull(vm.uiState.value.selectedContact)
    }

    @Test
    fun distinctAddressesAreBothSelectable() {
        launchPicker(multiAddress)

        composeRule.onAllNodesWithText("Main Street 1, 10115, Berlin").assertCountEquals(1)
        composeRule.onAllNodesWithText("Second Street 2, 14467, Potsdam").assertCountEquals(1)

        composeRule.onNodeWithText("Second Street 2, 14467, Potsdam").performClick()
        composeRule.waitForIdle()

        assertEquals("Second Street 2", resolvedLabel)
    }

    @Test
    fun pickerShowsThePersonNameAsHeading() {
        launchPicker(multiAddress)

        composeRule.onNodeWithText("Bob").assertIsDisplayed()
    }
}
