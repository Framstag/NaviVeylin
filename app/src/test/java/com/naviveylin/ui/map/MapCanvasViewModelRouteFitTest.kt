package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationStateProvider
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the route-overview camera fit (spec: route-map-overview): when a
 * calculated route is drawn, the camera centers on the route bounding box and
 * zooms so start, target, and polyline fit inside the **visible map area**
 * (canvas minus the route panel's covered height); the fit is one-shot per
 * result, runs after a layout-settle delay, is suppressed while navigating or
 * following, and degrades safely on empty or invalid geometry.
 *
 * Harness mirrors [MapCanvasViewModelRouteVisibilityTest]: real [MapRenderer]
 * on the test scheduler, FakeOSMScoutClient recording the route arrays, a real
 * [NavigationViewModel] wired so the navigating guard is exercised against real
 * navigation state (not a null view model), and the canvas pixel size fed via
 * [MapCanvasViewModel.setScreenSize]. The route panel's covered height is
 * reported through [RoutePanelViewModel.setSheetCoveredHeightPx]. Runs under
 * Robolectric with the default sandbox (AGENTS.md classloader rule for the
 * stub .so).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelRouteFitTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        routePanelViewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        )
        routePanelViewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        navigationViewModel = NavigationViewModel(
            client, NavigationStateProvider(),
            LocationService(context), context
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Route from (48.0,2.0) to (49.0,2.6) via (48.5,2.3): bbox [48,49,2,2.6]. */
    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(48.0, 48.5, 49.0)
        longitudes = doubleArrayOf(2.0, 2.3, 2.6)
        distance = 120000.0
    }

    /** Long route spanning ~3 degrees of latitude (spec R1 — below-floor zoom). */
    private fun longRouteEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 2L
        latitudes = doubleArrayOf(45.0, 46.0, 47.0, 48.0)
        longitudes = doubleArrayOf(5.0, 5.5, 6.0, 6.5)
        distance = 330000.0
    }

    private fun entry(label: String, lat: Double, lon: Double): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    /**
     * Expected fit magnification for [bbox] on a 400x800 canvas with
     * [visibleHeight] px, at the DPI the view model renders with — the fit is
     * defined against the renderer's ground resolution, not the 96-dpi reference.
     */
    private fun expectedFitMag(bbox: DoubleArray, visibleHeight: Int = 800): Double =
        MapCanvasViewModel.Companion.computeAreaZoom(
            bbox, 400, visibleHeight, minZoom = 4.0, dpi = projectionDpi()
        )

    /** DPI the view model projects with (Robolectric mdpi = 160). */
    private fun projectionDpi(): Double =
        context.resources.displayMetrics.densityDpi.toDouble()

    /**
     * Inject a renderer on the test scheduler, feed the canvas size, wire the
     * panel and a real navigation view model (harness fix: the navigating guard
     * must run against real navigation state, spec R2).
     */
    private fun TestScope.wireRendererAndPanel() {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        viewModel.setScreenSize(400, 800)
        viewModel.setMapRendererForTest(renderer)
        viewModel.setNavigationViewModel(navigationViewModel)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
    }

    /** Deliver a route result without letting the settle-delay fit run yet. */
    private fun TestScope.scheduleRouteResultAndAwaitArrival(
        startLat: Double, startLon: Double,
        destLat: Double, destLon: Double,
        route: RouteEntry = routeEntry()
    ) {
        routePanelViewModel.updateLocationsForReroute(
            entry("start", startLat, startLon), entry("dest", destLat, destLon)
        )
        client.routeToDeliver = route
        routePanelViewModel.calculateRoute()
        // runCurrent executes the already-scheduled continuations (route result
        // reaches the map collector, fit scheduled) WITHOUT advancing virtual
        // time, so the settle window is still open.
        runCurrent()
    }

    /** Drive a successful route calculation with the given endpoints and polyline. */
    private fun TestScope.calculateAndAwaitRender(
        startLat: Double, startLon: Double,
        destLat: Double, destLon: Double,
        route: RouteEntry = routeEntry()
    ) {
        routePanelViewModel.updateLocationsForReroute(
            entry("start", startLat, startLon), entry("dest", destLat, destLon)
        )
        client.routeToDeliver = route
        routePanelViewModel.calculateRoute()
        advanceUntilIdle()
        // Pass the renderer's debounce window so the render reaches the client.
        advanceTimeBy(1_000)
        advanceUntilIdle()
    }

    @Test
    fun routeCalculation_fitsViewportToBoundingBox() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        assertEquals(48.5, vp.centerLat, 1e-9)
        assertEquals(2.3, vp.centerLon, 1e-9)
        assertEquals(expectedFitMag(doubleArrayOf(48.0, 49.0, 2.0, 2.6)), vp.magnification, 1e-9)
        // The route must be drawn on the map as well (spec R1).
        assertNotNull("route must be drawn after fit", client.lastRouteLats)
    }

    @Test
    fun longTrip_zoomsBelowAreaFloor_bothEndpointsFit() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender(45.0, 5.0, 48.0, 6.5, route = longRouteEntry())

        val vp = viewModel.uiState.value.viewport
        val expected = expectedFitMag(doubleArrayOf(45.0, 48.0, 5.0, 6.5))
        // The area-favorites floor (14) must not clip the trip overview.
        assert(expected < 14.0) { "expected zoom-out below 14, got $expected" }
        assertEquals(expected, vp.magnification, 1e-9)
        assertEquals(46.5, vp.centerLat, 1e-9)
        assertEquals(5.75, vp.centerLon, 1e-9)
    }

    @Test
    fun staleReEmission_doesNotRefit_userMovedViewportKept() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)
        // Fitted once.
        assertEquals(48.5, viewModel.uiState.value.viewport.centerLat, 1e-9)

        // User pans/zooms away.
        viewModel.updateCenter(51.0, 7.0)
        viewModel.updateMagnification(12.0)

        // Screen re-entry re-subscribes the collector: the stored (result, visible)
        // pair re-emits — the unchanged result must not re-fit (spec R1).
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(51.0, viewModel.uiState.value.viewport.centerLat, 1e-9)
        assertEquals(7.0, viewModel.uiState.value.viewport.centerLon, 1e-9)
        assertEquals(12.0, viewModel.uiState.value.viewport.magnification, 1e-9)
    }

    @Test
    fun rerouteWhileNavigating_keepsViewport() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        // Put the driver's viewport somewhere fixed.
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)

        // Navigation starts (startNavigation sets isNavigating synchronously;
        // the fake returns a null controller without throwing).
        navigationViewModel.startNavigation(routeEntry(), Vehicle.CAR)
        advanceUntilIdle()
        assertTrue(
            "harness must wire a real navigating NavigationViewModel",
            navigationViewModel.state.value.isNavigating
        )

        // New route result arrives mid-drive (reroute): draw, do NOT refit.
        calculateAndAwaitRender(49.0, 2.5, 50.0, 3.5)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(13.0, vp.magnification, 1e-9)
        assertNotNull("rerouted route must still be drawn", client.lastRouteLats)
    }

    @Test
    fun routeCalculationWhileNavigating_keepsViewport() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)
        navigationViewModel.startNavigation(routeEntry(), Vehicle.CAR)
        advanceUntilIdle()
        assertTrue(navigationViewModel.state.value.isNavigating)
        val before = viewModel.uiState.value.viewport

        // A route calculated in the panel while navigating must not refit (R2).
        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        assertEquals(before, viewModel.uiState.value.viewport)
        assertNotNull("route must still be drawn while navigating", client.lastRouteLats)
    }

    @Test
    fun routeCalculationWhileFollowing_keepsViewport() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.onToggleFollowMode(true)
        assertEquals(15.0, viewModel.uiState.value.viewport.magnification, 1e-9)
        // The follow guard, not navigation, must suppress the fit (R2).
        assertFalse(navigationViewModel.state.value.isNavigating)

        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(15.0, vp.magnification, 1e-9)
        assertNotNull("route must still be drawn while following", client.lastRouteLats)
    }

    @Test
    fun navigationStartingDuringSettleWindow_cancelsPendingFit() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)

        // Route arrives while the fit is allowed → the fit is scheduled.
        scheduleRouteResultAndAwaitArrival(48.0, 2.0, 49.0, 2.6)
        assertNotEquals("route result must not fit synchronously", 48.5, viewModel.uiState.value.viewport.centerLat, 1e-9)

        // Navigation starts inside the settle window: the guards are re-checked
        // before the fit applies, so the driver's viewport wins (R2).
        navigationViewModel.startNavigation(routeEntry(), Vehicle.CAR)
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(13.0, vp.magnification, 1e-9)
    }

    @Test
    fun favoriteSelectedWhileFollowing_disablesFollowAndFits() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.value.followMode)

        // Picking a favorite is a destination selection: it leaves follow mode so
        // the deliberate calculation produces the overview (spec R1 — route
        // calculated from a favorite while free-driving).
        viewModel.onFavoriteSelected(FavoriteLocation("Home", 48.0, 2.0))
        advanceUntilIdle()
        assertFalse("favorite selection must disengage follow mode", viewModel.uiState.value.followMode)

        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        assertEquals(48.5, vp.centerLat, 1e-9)
        assertEquals(2.3, vp.centerLon, 1e-9)
        assertEquals(expectedFitMag(doubleArrayOf(48.0, 49.0, 2.0, 2.6)), vp.magnification, 1e-9)
    }

    @Test
    fun openRoutePanelWithStartWhileFollowing_disablesFollow() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.onToggleFollowMode(true)
        assertTrue(viewModel.uiState.value.followMode)

        viewModel.openRoutePanelWithStart(entry("dest", 49.0, 2.6))
        advanceUntilIdle()

        assertFalse("opening the route panel must disengage follow mode", viewModel.uiState.value.followMode)
        assertTrue("route panel must open", viewModel.uiState.value.showRoutePanel)
    }

    @Test
    fun sheetInset_fitUsesVisibleAreaHeightAndShiftedCenter() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        // The open route panel covers the bottom 600 px, leaving 200 px visible —
        // the visible height is then the limiting dimension of the fit.
        routePanelViewModel.setSheetCoveredHeightPx(600)
        advanceUntilIdle()

        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        val bbox = doubleArrayOf(48.0, 49.0, 2.0, 2.6)
        val visibleMag = expectedFitMag(bbox, visibleHeight = 200)
        // Magnification fits the *visible* height, not the full canvas.
        assertEquals(visibleMag, vp.magnification, 1e-9)
        assert(visibleMag < expectedFitMag(bbox, visibleHeight = 800)) {
            "visible-area fit must zoom out more than the full-canvas fit"
        }

        // The bbox midpoint lands on the visible-area center (y = 100) and the
        // whole route stays above the panel's top edge (y = 200), verified with
        // the renderer's own projection.
        val projected = ProjectionUtils.viewport(
            vp.centerLat, vp.centerLon, vp.magnification, 400, 800, projectionDpi(), vp.angle
        )
        val (midX, midY) = projected.geoToScreen(48.5, 2.3)
        assertEquals("midpoint centered horizontally", 200.0, midX, 1.0)
        assertEquals("midpoint on the visible-area center", 100.0, midY, 1.0)
        val startY = projected.geoToScreen(48.0, 2.0).second
        val destY = projected.geoToScreen(49.0, 2.6).second
        assert(startY >= 0.0 && startY <= 200.0) { "start marker must be visible, y=$startY" }
        assert(destY >= 0.0 && destY <= 200.0) { "target marker must be visible, y=$destY" }
    }

    @Test
    fun settleWindow_gestureCancelsPendingFit() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)

        scheduleRouteResultAndAwaitArrival(48.0, 2.0, 49.0, 2.6)
        // User pans inside the settle window — the user-moved viewport must win.
        viewModel.updateCenter(51.0, 7.0)
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        val vp = viewModel.uiState.value.viewport
        assertEquals(51.0, vp.centerLat, 1e-9)
        assertEquals(7.0, vp.centerLon, 1e-9)
    }

    @Test
    fun fullyCoveredCanvas_skipsFit() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)
        // Nothing of the canvas is visible (covered height == canvas height).
        routePanelViewModel.setSheetCoveredHeightPx(800)
        advanceUntilIdle()

        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(13.0, vp.magnification, 1e-9)
        assertNotNull("route must still be drawn", client.lastRouteLats)
    }

    @Test
    fun emptyPolyline_fallsBackToEndpointMidpoint() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        val empty = RouteEntry().apply {
            routeHandle = 3L
            latitudes = DoubleArray(0)
            longitudes = DoubleArray(0)
        }
        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6, route = empty)

        val vp = viewModel.uiState.value.viewport
        assertEquals("center on start/target midpoint", 48.5, vp.centerLat, 1e-9)
        assertEquals(2.3, vp.centerLon, 1e-9)
        assertEquals(expectedFitMag(doubleArrayOf(48.0, 49.0, 2.0, 2.6)), vp.magnification, 1e-9)
    }

    @Test
    fun invalidCoordinates_leaveViewportUntouched() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(11.0)
        val empty = RouteEntry().apply {
            routeHandle = 4L
            latitudes = DoubleArray(0)
            longitudes = DoubleArray(0)
        }
        // Start/dest all invalid (NaN): no usable coordinates at all.
        calculateAndAwaitRender(Double.NaN, Double.NaN, Double.NaN, Double.NaN, route = empty)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(11.0, vp.magnification, 1e-9)
    }
}
