package com.naviveylin.ui.route

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The route start/destination field's search request lifecycle (spec: `search-request-lifecycle`).
 *
 * The field's search runs in its own slot, and neither clearing the field nor switching the active
 * field leaves a search running: the pending request is dropped and the result area clears. The half
 * that asserts a cancelled *generation* while a search is still running is the gate's own case
 * (`SearchGateTest#abandonStopsTheRunningSearch`), because a synchronous test double cannot hold a
 * search open.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoutePanelViewModelSearchCancellationTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        val historyRepo = SearchHistoryRepository(context)
        historyRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context),
            context = context,
            settingsStorage = SettingsStorage(context)
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    private fun entry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.4818
        lon = 7.2162
        name = label
        matchQuality = "match"
    }

    /** Spec: `search-request-lifecycle` — abandon without a successor stops the search. */
    @Test
    fun clearingTheFieldAbandonsTheSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(entry("Bochum"))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(100)
        viewModel.onSearchQueryChanged("")
        advanceTimeBy(500)
        runCurrent()

        assertTrue(
            "the pending search must not run after the field was cleared",
            client.searchQueries.isEmpty()
        )
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertFalse(viewModel.uiState.value.isSearching)
    }

    /** Spec: `search-request-lifecycle` — switching the active field abandons its search too. */
    @Test
    fun switchingTheActiveFieldAbandonsTheSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(entry("Bochum"))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(100)
        viewModel.setActiveField(ActiveField.DEST)
        advanceTimeBy(500)
        runCurrent()

        assertTrue(client.searchQueries.isEmpty())
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    /** Spec: `search-request-lifecycle` — the route field's search runs in its own slot. */
    @Test
    fun theRouteSearchRunsInItsOwnSlot() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(entry("Bochum"))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("route"), client.begunSearchSlots)
        assertEquals(listOf("Bochum"), viewModel.uiState.value.searchResults.map { it.label })
        assertTrue("the request ended itself when it completed", client.liveSearchGenerations.isEmpty())
    }
}
