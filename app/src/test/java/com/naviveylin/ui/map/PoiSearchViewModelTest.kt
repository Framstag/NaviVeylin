package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the POI search flow in [MapCanvasViewModel] (spec: poi-search):
 * no category preselection and no preload on open, explicit search trigger,
 * result surfacing, single-click details that center the map and fit current
 * location + POI, the 100 km radius range, viewport restore on sheet close,
 * and the close semantics for the details sheet (plain dismiss reopens the
 * POI sheet; Route/Show actions keep it closed).
 */
@RunWith(RobolectricTestRunner::class)
class PoiSearchViewModelTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
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

    private fun poiEntry(label: String, objectType: String, distance: Double): PoiEntry =
        PoiEntry().apply {
            this.label = label
            this.objectType = objectType
            this.distance = distance
            lat = 51.5136
            lon = 7.4653
        }

    /** Open the unified dialog and switch to POIs mode (spec: search-dialog). */
    private fun openPoiSearch() {
        viewModel.openSearch()
        viewModel.setSearchMode(SearchMode.POIS)
    }

    /** True while the search dialog is open in POIs mode. */
    private fun isPoiSearchOpen(state: MapCanvasUiState): Boolean =
        state.searchOpen && state.searchMode == SearchMode.POIS

    @Test
    fun openPoiSearchHasNoCategoryAndNoResults() {
        openPoiSearch()

        val state = viewModel.uiState.value
        assertTrue(isPoiSearchOpen(state))
        assertNull("no category preselected", state.poiCategory)
        assertTrue("no preloaded results", state.poiResults.isEmpty())
        assertFalse(state.isPoiSearching)
        assertTrue("no search triggered on open", client.poiSearchCategories.isEmpty())
    }

    @Test
    fun performPoiSearchWithoutCategoryDoesNothing() {
        openPoiSearch()

        viewModel.performPoiSearch()

        val state = viewModel.uiState.value
        assertFalse(state.isPoiSearching)
        assertTrue("native search not called", client.poiSearchCategories.isEmpty())
    }

    @Test
    fun performPoiSearchPopulatesResults() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(
            poiEntry("Hotel Central", "tourism_hotel", 1200.0),
            poiEntry("Motel West", "tourism_motel", 3400.0)
        )

        viewModel.performPoiSearch()
        val state = viewModel.uiState.first { it.poiResults.isNotEmpty() }

        assertEquals(PoiCategories.HOTELS, client.poiSearchCategories.single())
        assertEquals(2, state.poiResults.size)
        assertEquals("Hotel Central", state.poiResults[0].label)
        assertFalse(state.isPoiSearching)
        assertNull(state.poiSearchError)
    }

    @Test
    fun performPoiSearchFailureSetsError() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.poiSearchError = RuntimeException("native boom")

        viewModel.performPoiSearch()
        val state = viewModel.uiState.first { it.poiSearchError != null }

        assertNotNull(state.poiSearchError)
        assertTrue(state.poiResults.isEmpty())
        assertFalse(state.isPoiSearching)
    }

    @Test
    fun clickOpensDetailsCentersMapAndClosesPoiSheet() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.RESTAURANTS)
        client.nextPoiResults = arrayOf(poiEntry("Pizzeria Roma", "amenity_restaurant", 500.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        val magBefore = viewModel.uiState.value.viewport.magnification

        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])

        val state = viewModel.uiState.first { it.showDetailsSheet }
        assertFalse("POI sheet closed on selection", isPoiSearchOpen(state))
        assertTrue(state.detailsFromPoiSearch)
        assertNotNull(state.selectedLocation)
        assertEquals("Pizzeria Roma", state.selectedLocation!!.label)
        assertEquals("map centered on POI", 51.5136, state.viewport.centerLat, 1e-9)
        assertEquals("map centered on POI", 7.4653, state.viewport.centerLon, 1e-9)
        assertEquals("no GPS fix: zoom unchanged", magBefore, state.viewport.magnification, 1e-9)
    }

    @Test
    fun searchCapturesCenterAndClickSetsSelectionHighlight() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.RESTAURANTS)
        client.nextPoiResults = arrayOf(poiEntry("Pizzeria Roma", "amenity_restaurant", 500.0))
        viewModel.performPoiSearch()
        val searched = viewModel.uiState.first { it.poiResults.isNotEmpty() }
        assertEquals("search center captured", searched.viewport.centerLat, searched.poiSearchCenterLat, 1e-9)
        assertEquals("search center captured", searched.viewport.centerLon, searched.poiSearchCenterLon, 1e-9)
        assertTrue("no selection before click", searched.poiSelectedLat.isNaN())

        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])

        val state = viewModel.uiState.first { it.showDetailsSheet }
        assertEquals("selected POI lat", 51.5136, state.poiSelectedLat, 1e-9)
        assertEquals("selected POI lon", 7.4653, state.poiSelectedLon, 1e-9)

        // Plain dismiss reopens the POI sheet; the embedded map keeps the highlight.
        viewModel.dismissDetailsSheet()
        val reopened = viewModel.uiState.first { it.searchOpen && it.searchMode == SearchMode.POIS }
        assertEquals("highlight survives reopen", 51.5136, reopened.poiSelectedLat, 1e-9)
        assertEquals("highlight survives reopen", 7.4653, reopened.poiSelectedLon, 1e-9)
    }

    @Test
    fun closePoiSearchClearsSelectionHighlight() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 1200.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])
        viewModel.uiState.first { it.showDetailsSheet }
        viewModel.dismissDetailsSheet()
        viewModel.uiState.first { it.searchOpen && it.searchMode == SearchMode.POIS }

        viewModel.closeSearch()

        val state = viewModel.uiState.value
        assertTrue(state.poiSelectedLat.isNaN())
        assertTrue(state.poiSelectedLon.isNaN())
    }

    @Test
    fun fitZoomShowsCurrentLocationAndPoi() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.setScreenSize(1080, 2100)
        viewModel.updateMagnification(18.0)
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        // GPS fix far from the POI
        locationService.setLocationForTest(Location("gps").apply {
            latitude = 51.5
            longitude = 7.4
            accuracy = 8f
        })
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 12000.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        val magBefore = viewModel.uiState.value.viewport.magnification

        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])

        val state = viewModel.uiState.first { it.showDetailsSheet }
        assertTrue(
            "zoom zoomed out to fit both locations (before=$magBefore, after=${state.viewport.magnification})",
            state.viewport.magnification < magBefore
        )
        assertEquals("fit floor reached", 14.0, state.viewport.magnification, 1e-9)
    }

    @Test
    fun closePoiSearchRestoresViewportSnapshot() = runTest(mainDispatcherRule.dispatcher) {
        // Pre-search viewport
        viewModel.updateCenter(52.0, 8.0)
        val magBefore = viewModel.uiState.value.viewport.magnification
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 900.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])
        viewModel.uiState.first { it.showDetailsSheet }
        // Selection moved the map
        assertEquals(51.5136, viewModel.uiState.value.viewport.centerLat, 1e-9)

        // Reopen the POI sheet (plain dismiss) and close it explicitly
        viewModel.dismissDetailsSheet()
        assertTrue(isPoiSearchOpen(viewModel.uiState.value))
        viewModel.closeSearch()

        val state = viewModel.uiState.value
        assertFalse(isPoiSearchOpen(state))
        assertEquals("center restored", 52.0, state.viewport.centerLat, 1e-9)
        assertEquals("center restored", 8.0, state.viewport.centerLon, 1e-9)
        assertEquals("zoom restored", magBefore, state.viewport.magnification, 1e-9)
    }

    @Test
    fun changingCategoryDoesNotRerunSearchOrClearResults() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 900.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }

        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        viewModel.onPoiRadiusChanged(20000.0)

        val state = viewModel.uiState.value
        assertEquals("results not cleared by selection change", 1, state.poiResults.size)
        assertEquals("no auto re-search", 1, client.poiSearchCategories.size)
        assertEquals(PoiCategories.GROCERY, state.poiCategory)
        assertEquals(20000.0, state.poiRadiusMeters, 1e-9)
    }

    @Test
    fun poiRadiusStepsReach100Km() {
        assertEquals(8, MapCanvasViewModel.POI_RADIUS_STEPS_M.size)
        assertEquals(100000.0, MapCanvasViewModel.POI_RADIUS_STEPS_M.last(), 1e-9)
        assertEquals(5000.0, MapCanvasViewModel.DEFAULT_POI_RADIUS_METERS, 1e-9)
    }

    @Test
    fun plainDismissReopensPoiSheetWithResults() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 900.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])
        viewModel.uiState.first { it.showDetailsSheet }

        viewModel.dismissDetailsSheet()

        val state = viewModel.uiState.value
        assertTrue("plain dismiss reopens POI sheet", isPoiSearchOpen(state))
        assertFalse(state.showDetailsSheet)
        assertEquals("results intact", 1, state.poiResults.size)
    }

    @Test
    fun showActionCentersMapAndKeepsPoiSheetClosed() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = arrayOf(poiEntry("Supermarkt", "shop_supermarket", 300.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])
        viewModel.uiState.first { it.showDetailsSheet }

        viewModel.showOnMap()

        val state = viewModel.uiState.value
        assertFalse(state.showDetailsSheet)
        assertFalse("show action keeps POI sheet closed", isPoiSearchOpen(state))
        assertFalse(state.detailsFromPoiSearch)
        assertEquals("map centered on POI", 51.5136, state.viewport.centerLat, 1e-9)
        assertEquals("map centered on POI", 7.4653, state.viewport.centerLon, 1e-9)
    }

    @Test
    fun routeActionKeepsPoiSheetClosed() = runTest(mainDispatcherRule.dispatcher) {
        val routeVm = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        ).apply { defaultDispatcher = mainDispatcherRule.dispatcher }
        viewModel.setRoutePanelViewModel(routeVm)

        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        client.nextPoiResults = arrayOf(poiEntry("Hotel Central", "tourism_hotel", 800.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }
        viewModel.onPoiEntryClick(viewModel.uiState.value.poiResults[0])
        viewModel.uiState.first { it.showDetailsSheet }

        // LocationDetailsSheet invokes onRouteToLocation() then onDismiss()
        viewModel.openRoutePanelWithStart(viewModel.uiState.value.selectedLocation)
        viewModel.dismissDetailsSheet()

        val state = viewModel.uiState.value
        assertTrue("route panel open", state.showRoutePanel)
        assertFalse(state.showDetailsSheet)
        assertFalse("route action keeps POI sheet closed", isPoiSearchOpen(state))
    }
}
