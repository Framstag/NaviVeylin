package com.naviveylin.ui.map

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        historyRepo = SearchHistoryRepository(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    private fun resultEntry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        name = label
        matchQuality = "match"
    }

    @Test
    fun selectingResultRecordsHistoryEntry() = runTest {
        viewModel.onSearchQueryChanged("Dortmund Hbf")
        viewModel.onSearchResultSelected(resultEntry("Dortmund Hbf"))

        val entries = historyRepo.history.first { it.isNotEmpty() }
        assertEquals(1, entries.size)
        assertEquals("Dortmund Hbf", entries[0].text)
    }

    @Test
    fun typingAloneRecordsNothing() = runTest {
        viewModel.onSearchQueryChanged("Dortmund")
        viewModel.clearSearch()
        assertEquals(0, historyRepo.history.value.size)
    }

    @Test
    fun currentLocationSelectionRecordsNothing() = runTest {
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
}
