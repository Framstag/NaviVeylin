package com.naviveylin.ui.route

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
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
 * Verifies the routing snapshot rule in [RoutePanelViewModel]: selecting a
 * search result as start/destination records a history entry; selecting from
 * an empty query (e.g. "Current Location") records nothing.
 */
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelSearchHistoryTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var historyRepo: SearchHistoryRepository
    private lateinit var viewModel: RoutePanelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        historyRepo = SearchHistoryRepository(context)
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = historyRepo,
            locationService = LocationService(context)
        )
    }

    @After
    fun tearDown() {
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
    fun selectingDestinationRecordsHistoryEntry() = runTest {
        viewModel.setActiveField(ActiveField.DEST)
        viewModel.onSearchQueryChanged("Dortmund Hbf")
        viewModel.selectSearchResult(resultEntry("Dortmund Hbf"))

        val entries = historyRepo.history.first { it.isNotEmpty() }
        assertEquals(1, entries.size)
        assertEquals("Dortmund Hbf", entries[0].text)
    }

    @Test
    fun selectingStartRecordsHistoryEntry() = runTest {
        viewModel.setActiveField(ActiveField.START)
        viewModel.onSearchQueryChanged("Hauptstraße 12")
        viewModel.selectSearchResult(resultEntry("Hauptstraße 12"))

        val entries = historyRepo.history.first { it.isNotEmpty() }
        assertEquals("Hauptstraße 12", entries[0].text)
    }

    @Test
    fun emptyQuerySelectionRecordsNothing() = runTest {
        // Convenience entries ("Current Location") are selected from an empty
        // query; they must not be recorded as search selections.
        viewModel.setActiveField(ActiveField.START)
        viewModel.selectSearchResult(resultEntry("Current Location"))
        assertEquals(0, historyRepo.history.value.size)
    }
}
