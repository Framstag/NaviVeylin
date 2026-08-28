package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Address-book permission-driven visibility (spec: address-book-permission —
 * permission state drives feature visibility): grant -> available, deny ->
 * hidden, later grant (resume refresh) -> available again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAddressBookPermissionTest {

    private lateinit var context: Context

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newViewModel(): MapCanvasViewModel {
        val client = FakeOSMScoutClient()
        val vm = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            context = context
        )
        vm.defaultDispatcher = mainDispatcherRule.dispatcher
        return vm
    }

    private fun grantContacts() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    private fun denyContacts() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun deniedPermissionHidesAddressBook() {
        denyContacts()

        val vm = newViewModel()
        vm.refreshAddressBookAvailability()

        assertFalse(vm.uiState.value.addressBookAvailable)
    }

    @Test
    fun grantMakesAddressBookAvailable() {
        grantContacts()

        val vm = newViewModel()
        vm.refreshAddressBookAvailability()

        assertTrue(vm.uiState.value.addressBookAvailable)
    }

    @Test
    fun laterGrantAfterDenyBecomesAvailable() {
        denyContacts()
        val vm = newViewModel()
        vm.refreshAddressBookAvailability()
        assertFalse(vm.uiState.value.addressBookAvailable)

        // User grants in system settings, app resumes -> refresh again
        grantContacts()
        vm.refreshAddressBookAvailability()

        assertTrue(vm.uiState.value.addressBookAvailable)
    }

    @Test
    fun revocationAfterGrantHidesAddressBook() {
        grantContacts()
        val vm = newViewModel()
        vm.refreshAddressBookAvailability()
        assertTrue(vm.uiState.value.addressBookAvailable)

        // User revokes in system settings, app resumes -> refresh again
        denyContacts()
        vm.refreshAddressBookAvailability()

        assertFalse(vm.uiState.value.addressBookAvailable)
    }

    @Test
    fun initReflectsCurrentlyGrantedPermission() {
        grantContacts()

        val vm = newViewModel()

        assertTrue(vm.uiState.value.addressBookAvailable)
    }

    @Test
    fun resolvedAddressBookEntryOpensDetailsSheet() = kotlinx.coroutines.test.runTest(
        mainDispatcherRule.dispatcher
    ) {
        grantContacts()
        val vm = newViewModel()
        vm.openAddressBookSheet()
        assertTrue(vm.uiState.value.showAddressBookSheet)

        val entry = com.framstag.libosmscout.client.LocationEntry().apply {
            label = "Main Street 1 Berlin"
            lat = 52.5
            lon = 13.4
        }
        vm.onAddressBookResultSelected(entry)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showAddressBookSheet)
        assertTrue(vm.uiState.value.showDetailsSheet)
        assertTrue(vm.uiState.value.detailsFromAddressBook)
        assertEquals("Main Street 1 Berlin", vm.uiState.value.selectedLocation?.label)
    }

    @Test
    fun backFromAddressBookDetailsReturnsToAddressBook() = kotlinx.coroutines.test.runTest(
        mainDispatcherRule.dispatcher
    ) {
        grantContacts()
        val vm = newViewModel()
        vm.openAddressBookSheet()
        val entry = com.framstag.libosmscout.client.LocationEntry().apply {
            label = "Main Street 1 Berlin"
            lat = 52.5
            lon = 13.4
        }
        vm.onAddressBookResultSelected(entry)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showDetailsSheet)

        // Back gesture on the details sheet returns to the address-book search.
        vm.dismissDetailsSheet()

        assertFalse(vm.uiState.value.showDetailsSheet)
        assertTrue(vm.uiState.value.showAddressBookSheet)
        assertFalse(vm.uiState.value.detailsFromAddressBook)
    }

    @Test
    fun showOnMapFromAddressBookDetailsStaysOnMap() = kotlinx.coroutines.test.runTest(
        mainDispatcherRule.dispatcher
    ) {
        grantContacts()
        val vm = newViewModel()
        vm.openAddressBookSheet()
        val entry = com.framstag.libosmscout.client.LocationEntry().apply {
            label = "Main Street 1 Berlin"
            lat = 52.5
            lon = 13.4
        }
        vm.onAddressBookResultSelected(entry)
        advanceUntilIdle()

        vm.showOnMap()

        assertFalse(vm.uiState.value.showDetailsSheet)
        assertFalse(vm.uiState.value.showAddressBookSheet)
        assertFalse(vm.uiState.value.detailsFromAddressBook)
    }
}
