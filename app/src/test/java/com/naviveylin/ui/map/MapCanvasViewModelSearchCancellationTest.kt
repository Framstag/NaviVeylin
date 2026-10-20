package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone places search's request lifecycle (spec: `search-request-lifecycle`,
 * spec: `location-search` — search suggestions follow the newest query text).
 *
 * A query shorter than the minimum length runs nothing and leaves no result in the
 * results area; the results area must never hold the previous query's results
 * (the defect this suite pins: the one-character request is dropped by the
 * pipeline filter, so the in-flight search of the longer text publishes under the
 * shorter one).
 *
 * The half that asserts a cancelled *generation* needs the recording double and is
 * added with the gate wiring (change task 3.4).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MapCanvasViewModelSearchCancellationTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
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

    private fun result(label: String, lat: Double, lon: Double): LocationEntry = LocationEntry().apply {
        this.label = label
        type = "object"
        objectType = "amenity_cafe"
        objectTypeName = "cafe"
        this.lat = lat
        this.lon = lon
        name = label
        matchQuality = "match"
        refType = "node"
    }

    /**
     * Spec: `search-request-lifecycle` — the minimum query length is 2 characters;
     * a one-character query runs nothing and shows no result of a previous query.
     */
    @Test
    fun oneCharacterQueryShowsNoResults() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(result("Bochum", 51.4818, 7.2162))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("Boch"), client.searchQueries)
        assertTrue(
            "the two-character query must publish its results",
            viewModel.uiState.value.searchResults.isNotEmpty()
        )

        viewModel.onSearchQueryChanged("B")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(
            "a one-character query must leave the results area empty, not holding 'Boch' results",
            viewModel.uiState.value.searchResults.isEmpty()
        )
    }

    /**
     * Spec: `search-request-lifecycle` — falling below the minimum length aborts the
     * running search. The clear and the "no search runs" half are asserted here; the
     * cancelled generation is asserted once the double records cancels (task 3.4).
     */
    @Test
    fun shorteningTheQueryToOneCharacterClearsAndAborts() = runTest(mainDispatcherRule.dispatcher) {
        client.nextSearchResults = arrayOf(result("Bochum", 51.4818, 7.2162))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(400)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSearching)

        viewModel.onSearchQueryChanged("B")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(
            "the results area must be empty below the minimum length",
            viewModel.uiState.value.searchResults.isEmpty()
        )
        assertFalse("no search may stay in flight below the minimum length", viewModel.uiState.value.isSearching)
        assertEquals("no search may be issued for a one-character query", listOf("Boch"), client.searchQueries)
    }

    /**
     * Spec: `search-request-lifecycle` — a superseded generation is aborted and the newest text's
     * results are the ones displayed.
     */
    @Test
    fun newerTextAbortsTheRunningSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.searchResultsByQuery["Boch"] = arrayOf(result("Bochum", 51.4818, 7.2162))
        client.searchResultsByQuery["Bochu"] = arrayOf(result("Bochum Hbf", 51.4900, 7.2200))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(400)
        runCurrent()
        viewModel.onSearchQueryChanged("Bochu")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("Boch", "Bochu"), client.searchQueries)
        assertEquals("each request carries its own generation", 2, client.searchGenerations.size)
        assertEquals(
            "the newest text's results are the displayed ones",
            listOf("Bochum Hbf"),
            viewModel.uiState.value.searchResults.map { it.entry.label }
        )
    }

    /**
     * Spec: `location-search` — a superseded request never publishes: a query replaced inside the
     * debounce window never reaches the client at all.
     */
    @Test
    fun aSupersededSearchNeverPublishes() = runTest(mainDispatcherRule.dispatcher) {
        client.searchResultsByQuery["Boch"] = arrayOf(result("Bochum", 51.4818, 7.2162))
        client.searchResultsByQuery["Bochu"] = arrayOf(result("Bochum Hbf", 51.4900, 7.2200))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(100)
        viewModel.onSearchQueryChanged("Bochu")
        advanceTimeBy(400)
        runCurrent()

        assertEquals("only the newest text may run", listOf("Bochu"), client.searchQueries)
        assertEquals(
            listOf("Bochum Hbf"),
            viewModel.uiState.value.searchResults.map { it.entry.label }
        )
    }

    /** Spec: `search-dialog` — leaving Places mode abandons its search (the dialog is dismissed). */
    @Test
    fun dismissingTheDialogAbandonsTheSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.searchResultsByQuery["Boch"] = arrayOf(result("Bochum", 51.4818, 7.2162))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(100)
        viewModel.clearSearch()
        advanceTimeBy(500)
        runCurrent()

        assertTrue("no search may run after the dialog was dismissed", client.searchQueries.isEmpty())
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    /** Spec: `search-dialog` — switching away from Places abandons its search. */
    @Test
    fun switchingToPoiModeAbandonsThePlacesSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.searchResultsByQuery["Boch"] = arrayOf(result("Bochum", 51.4818, 7.2162))

        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(100)
        viewModel.setSearchMode(SearchMode.POIS)
        advanceTimeBy(500)
        runCurrent()

        assertTrue("the places search must not run after the mode switch", client.searchQueries.isEmpty())
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }
}
