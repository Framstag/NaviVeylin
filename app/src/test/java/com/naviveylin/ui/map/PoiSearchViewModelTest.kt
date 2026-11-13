package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.PoiCategories
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.search.POI_CANDIDATE_CAP
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
import kotlinx.coroutines.test.advanceUntilIdle
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
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
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

    /**
     * Whether a position projects inside the canvas at the state's viewport.
     *
     * Used instead of asserting a magnification number: the spec asks for both the
     * current location and the POI to be visible, and that is what must hold when the
     * fit's floor changes (spec: poi-search — Details via single click).
     */
    private fun isVisible(state: MapCanvasUiState, lat: Double, lon: Double): Boolean {
        val dpi = context.resources.displayMetrics.densityDpi.toDouble()
        val vp = ProjectionUtils.viewport(
            state.viewport.centerLat, state.viewport.centerLon, state.viewport.magnification,
            CANVAS_WIDTH, CANVAS_HEIGHT, dpi, state.viewport.angle
        )
        val (x, y) = vp.geoToScreenRotated(lat, lon)
        return x >= 0.0 && x <= CANVAS_WIDTH.toDouble() && y >= 0.0 && y <= CANVAS_HEIGHT.toDouble()
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

        // The POI gate calls the generation-carrying type-name entry point, so the request's type
        // names are what the double records for the chosen category (change `fix-search-cancellation`).
        assertEquals(
            PoiCategories.getTypeNames(PoiCategories.HOTELS)!!.toList(),
            client.poiSearchTypeNames.single()
        )
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
        viewModel.setScreenSize(CANVAS_WIDTH, CANVAS_HEIGHT)
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
        // Both positions must be visible at the applied magnification — not the old
        // area-favorites floor of 14: the fix is far enough that a floor-14 fit cannot
        // show it (spec: poi-search — Distant POI zooms out below the area-favorites
        // floor, which is what this assertion used to pin to 14.0 before that
        // behaviour changed).
        assertTrue(
            "the POI must be visible (mag=${state.viewport.magnification})",
            isVisible(viewModel.uiState.value, 51.5136, 7.4653)
        )
        assertTrue(
            "the current location must be visible (mag=${state.viewport.magnification})",
            isVisible(viewModel.uiState.value, 51.5, 7.4)
        )
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
        assertEquals("no auto re-search", 1, client.poiSearchTypeNames.size)
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
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            context = context,
            settingsStorage = SettingsStorage(context)
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

    private companion object {
        /** Canvas these cases lay out (`setScreenSize`). */
        const val CANVAS_WIDTH = 1080
        const val CANVAS_HEIGHT = 2100
    }

    // --- The name filter and the candidate set (spec: `poi-search` — A name filter narrows a POI
    // result list; A filtered search asks for a candidate set, not a page; change `poi-name-filter`)

    /** A full page of candidates: the walk's display-limit answer. */
    private fun displayLimitCandidates(): Array<PoiEntry> =
        (1..MapCanvasViewModel.MAX_POI_RESULTS).map {
            poiEntry("POI $it", "shop_supermarket", it.toDouble())
        }.toTypedArray()

    private fun searches(): Int = client.poiSearchGenerations.size

    @Test
    fun aFilterChangeRunsNoSearch() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = arrayOf(
            poiEntry("REWE Markt", "shop_supermarket", 200.0),
            poiEntry("Kiosk", "shop_kiosk", 300.0)
        )
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.isNotEmpty() }
        val before = searches()

        viewModel.onPoiFilterChanged("rewe")
        advanceUntilIdle()

        assertEquals("a filter keystroke ran no walk", before, searches())
        assertEquals(listOf("REWE Markt"), viewModel.uiState.value.poiResults.map { it.label })
    }

    @Test
    fun aCategoryChangeStillNeedsTheSearchButton() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = arrayOf(poiEntry("REWE Markt", "shop_supermarket", 200.0))
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.isNotEmpty() }
        val before = searches()

        viewModel.onPoiCategorySelected(PoiCategories.HOTELS)
        advanceUntilIdle()

        assertEquals("the category change ran no walk", before, searches())
        assertFalse(viewModel.uiState.value.isPoiSearching)
    }

    @Test
    fun theFirstNonEmptyFilterWidensTheFetchOnce() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = displayLimitCandidates()
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.size == MapCanvasViewModel.MAX_POI_RESULTS }

        viewModel.onPoiFilterChanged("POI 1")
        val widened = viewModel.uiState.first { it.poiWidened }
        advanceUntilIdle()

        assertEquals(
            "the first non-empty filter asked the walk for the candidate cap",
            listOf(MapCanvasViewModel.MAX_POI_RESULTS, POI_CANDIDATE_CAP),
            client.poiSearchCandidateCounts
        )
        assertTrue("the widened set is filtered locally", widened.poiResults.isNotEmpty())
    }

    @Test
    fun laterFilterKeystrokesAskTheBackendForNothing() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = displayLimitCandidates()
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.size == MapCanvasViewModel.MAX_POI_RESULTS }
        viewModel.onPoiFilterChanged("P")
        viewModel.uiState.first { it.poiWidened }
        advanceUntilIdle()
        val afterWiden = searches()

        viewModel.onPoiFilterChanged("P1")
        viewModel.onPoiFilterChanged("P12")
        advanceUntilIdle()

        assertEquals("later keystrokes ran no walk", afterWiden, searches())
        assertEquals(POI_CANDIDATE_CAP, client.poiSearchCandidateCounts.last())
    }

    @Test
    fun aNewSearchNarrowsTheFetchAgain() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = displayLimitCandidates()
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.size == MapCanvasViewModel.MAX_POI_RESULTS }
        viewModel.onPoiFilterChanged("P")
        viewModel.uiState.first { it.poiWidened }
        advanceUntilIdle()

        // Clearing the filter is local, and the next explicit search asks for the display limit.
        viewModel.onPoiFilterChanged("")
        advanceUntilIdle()
        viewModel.performPoiSearch()
        advanceUntilIdle()

        assertEquals(MapCanvasViewModel.MAX_POI_RESULTS, client.poiSearchCandidateCounts.last())
    }

    @Test
    fun aFailedWidenKeepsTheRowsOnScreen() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = displayLimitCandidates()
        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiCandidates.size == MapCanvasViewModel.MAX_POI_RESULTS }
        val rowsBefore = viewModel.uiState.value.poiResults

        client.poiSearchError = RuntimeException("native boom")
        viewModel.onPoiFilterChanged("POI 1")
        val failed = viewModel.uiState.first { it.poiSearchError != null }
        advanceUntilIdle()

        assertEquals("the previous candidates stay", rowsBefore, failed.poiCandidates)
        assertTrue("a matching row stays on screen", failed.poiResults.isNotEmpty())
        assertFalse("no spinner replaces the readable list", failed.isPoiSearching)
    }

    @Test
    fun aFilterWithoutACategoryRunsNothing() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()

        viewModel.onPoiFilterChanged("rewe")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.poiCategory)
        assertEquals("no category, no walk", 0, searches())
    }

    @Test
    fun aFilteredSearchAsksForTheCandidateCap() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiFilterChanged("rewe")
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = arrayOf(poiEntry("REWE Markt", "shop_supermarket", 200.0))

        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }

        assertEquals(listOf(POI_CANDIDATE_CAP), client.poiSearchCandidateCounts)
    }

    @Test
    fun theDisplayedListIsTheNearestMatches() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiFilterChanged("rewe")
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = (1..(MapCanvasViewModel.MAX_POI_RESULTS + 50)).map {
            poiEntry("REWE $it", "shop_supermarket", it.toDouble())
        }.toTypedArray()

        viewModel.performPoiSearch()
        val state = viewModel.uiState.first { it.poiResults.isNotEmpty() }

        assertEquals(
            "the displayed list is the nearest display-max matches",
            MapCanvasViewModel.MAX_POI_RESULTS,
            state.poiResults.size
        )
        assertEquals("REWE 1", state.poiResults.first().label)
    }

    @Test
    fun aMatchPastTheDisplayLimitIsFound() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiFilterChanged("rewe")
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        val candidates = (1..MapCanvasViewModel.MAX_POI_RESULTS).map {
            poiEntry("Bäckerei $it", "shop_bakery", it.toDouble())
        } + poiEntry("REWE Markt", "shop_supermarket", 9000.0)
        client.nextPoiResults = candidates.toTypedArray()

        viewModel.performPoiSearch()
        val state = viewModel.uiState.first { it.poiResults.isNotEmpty() }

        assertEquals(listOf("REWE Markt"), state.poiResults.map { it.label })
    }

    @Test
    fun anUnfilteredSearchAsksForTheDisplayLimit() = runTest(mainDispatcherRule.dispatcher) {
        openPoiSearch()
        viewModel.onPoiCategorySelected(PoiCategories.GROCERY)
        client.nextPoiResults = arrayOf(poiEntry("REWE Markt", "shop_supermarket", 200.0))

        viewModel.performPoiSearch()
        viewModel.uiState.first { it.poiResults.isNotEmpty() }

        assertEquals(listOf(MapCanvasViewModel.MAX_POI_RESULTS), client.poiSearchCandidateCounts)
    }
}
