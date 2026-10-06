package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the search-history snapshot rule in [MapCanvasViewModel]: an entry
 * is recorded only when a search result is selected (never while typing), and
 * picking an entry from history fills the search box.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSearchHistoryTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var historyRepo: SearchHistoryRepository
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        historyRepo = SearchHistoryRepository(context)
        historyRepo.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun resultEntry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        name = label
        matchQuality = "match"
    }

    @Test
    fun selectingResultRecordsHistoryEntry() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSearchQueryChanged("Dortmund Hbf")
        viewModel.onSearchResultSelected(resultEntry("Dortmund Hbf"))

        val entries = historyRepo.history.first { it.isNotEmpty() }
        assertEquals(1, entries.size)
        assertEquals("Dortmund Hbf", entries[0].text)
    }

    @Test
    fun typingAloneRecordsNothing() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSearchQueryChanged("Dortmund")
        viewModel.clearSearch()
        assertEquals(0, historyRepo.history.value.size)
    }

    @Test
    fun currentLocationSelectionRecordsNothing() = runTest(mainDispatcherRule.dispatcher) {
        // "Current Location" is a convenience entry selected from an empty
        // query; it must not be recorded as a search selection.
        viewModel.onSearchResultSelected(resultEntry("Current Location"))
        viewModel.uiState.first { it.showDetailsSheet }
        assertEquals(0, historyRepo.history.value.size)
    }

    @Test
    fun historyEntrySelectionFillsSearchQuery() {
        viewModel.onHistoryEntrySelected("Café Central")
        assertEquals("Café Central", viewModel.uiState.value.searchQuery)
    }

    /**
     * The chip replays the search, not only the text: after a committed selection
     * emptied the results, tapping the chip for the same text must run the search
     * again and publish its results (spec: search-history — History selection
     * replays the search).
     */
    @Test
    fun historyChipReplayAfterSelectionPublishesResults() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(resultEntry("Bochum"))

        viewModel.onSearchQueryChanged("Bochum")
        advanceTimeBy(400)
        runCurrent()
        assertTrue(
            "precondition: typing the query publishes results",
            viewModel.uiState.value.searchResults.isNotEmpty()
        )

        viewModel.onSearchResultSelected(resultEntry("Bochum"))
        advanceUntilIdle()
        assertTrue(
            "precondition: selecting a result empties the results list",
            viewModel.uiState.value.searchResults.isEmpty()
        )
        assertEquals("", viewModel.uiState.value.searchQuery)

        viewModel.onHistoryEntrySelected("Bochum")
        advanceTimeBy(400)
        runCurrent()

        assertEquals("Bochum", viewModel.uiState.value.searchQuery)
        assertTrue(
            "tapping the chip must replay the search and publish its results",
            viewModel.uiState.value.searchResults.isNotEmpty()
        )
    }

    /**
     * The list the chip row renders holds each text once, however often the search was
     * committed (spec: search-history — No duplicate entry for the same search text).
     */
    @Test
    fun committingTheSameSearchThreeTimesYieldsOneChip() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(resultEntry("Bochum"))

        repeat(3) {
            viewModel.onSearchQueryChanged("Bochum")
            viewModel.onSearchResultSelected(resultEntry("Bochum"))
        }
        // Read the settled state: sampling the first non-empty emission would see only the
        // first of the three writes and could not distinguish a duplicate from a re-use.
        advanceUntilIdle()

        assertEquals(1, historyRepo.history.value.count { it.text == "Bochum" })
    }
}
