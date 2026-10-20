package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.PoiEntry
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The POI search's request lifecycle (spec: `poi-search`, spec: `search-request-lifecycle`).
 *
 * The POI sheet's search runs in its own slot, so a places search neither stops it nor clears it,
 * a second POI search replaces the first, and leaving the POI view abandons it. The half that
 * asserts a cancelled generation while a search is still running is the gate's own case: a
 * synchronous test double cannot hold a search open.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelPoiSearchCancellationTest {

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

    private fun poi(label: String): PoiEntry = PoiEntry().apply {
        this.label = label
        objectType = "amenity_cafe"
        lat = 51.5136
        lon = 7.4653
    }

    private fun label(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.4818
        lon = 7.2162
        name = label
        matchQuality = "match"
    }

    /** Spec: `poi-search` — a places search does not stop the POI search, nor clear its results. */
    @Test
    fun aPlacesSearchDoesNotAbortThePoiSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.nextPoiResults = arrayOf(poi("Mensa"))
        viewModel.onPoiCategorySelected("restaurants")
        viewModel.performPoiSearch()
        advanceUntilIdle()
        assertEquals(listOf("Mensa"), viewModel.uiState.value.poiResults.map { it.label })

        client.nextSearchResults = arrayOf(label("Bochum"))
        viewModel.onSearchQueryChanged("Boch")
        advanceTimeBy(400)
        runCurrent()

        assertEquals("each search runs in its own slot", listOf("poi", "places"), client.begunSearchSlots)
        assertEquals(
            "the POI results must survive a places search",
            listOf("Mensa"),
            viewModel.uiState.value.poiResults.map { it.label }
        )
    }

    /** Spec: `poi-search` — a newer POI search supersedes the running one. */
    @Test
    fun aNewerPoiSearchAbortsTheRunningOne() = runTest(mainDispatcherRule.dispatcher) {
        client.nextPoiResults = arrayOf(poi("First"))
        viewModel.onPoiCategorySelected("restaurants")
        viewModel.performPoiSearch()
        advanceUntilIdle()

        client.nextPoiResults = arrayOf(poi("Second"))
        viewModel.onPoiCategorySelected("hotels")
        viewModel.performPoiSearch()
        advanceUntilIdle()

        assertEquals("each POI search carries its own generation", 2, client.poiSearchGenerations.size)
        assertEquals(
            "only the newer category's results stay",
            listOf("Second"),
            viewModel.uiState.value.poiResults.map { it.label }
        )
    }

    /** Spec: `poi-search` — leaving the POI view abandons its search. */
    @Test
    fun closingThePoiViewAbandonsThePoiSearch() = runTest(mainDispatcherRule.dispatcher) {
        client.nextPoiResults = arrayOf(poi("Mensa"))
        viewModel.setSearchMode(SearchMode.POIS)
        viewModel.onPoiCategorySelected("restaurants")
        viewModel.performPoiSearch()
        advanceUntilIdle()
        assertEquals(listOf("Mensa"), viewModel.uiState.value.poiResults.map { it.label })

        // Leaving POIS mode is the POI view closing.
        viewModel.setSearchMode(SearchMode.PLACES)
        advanceUntilIdle()

        assertTrue("leaving the POI view clears its results", viewModel.uiState.value.poiResults.isEmpty())
        assertFalse(viewModel.uiState.value.isPoiSearching)
    }
}
