package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.BasemapReloadNotifier
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the route-overview camera fit (spec: route-map-overview): when a
 * calculated route is drawn, the camera centers on the route bounding box and
 * zooms so start, target, and polyline fit; the fit is one-shot per result,
 * suppressed while navigating or following, and degrades safely on empty or
 * invalid geometry.
 *
 * Harness mirrors [MapCanvasViewModelRouteVisibilityTest]: real [MapRenderer]
 * on the test scheduler, FakeOSMScoutClient recording the route arrays. The
 * canvas pixel size is fed via [MapCanvasViewModel.setScreenSize] because the
 * fit consumes it. Runs under Robolectric with the default sandbox
 * (AGENTS.md classloader rule for the stub .so).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelRouteFitTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel

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

    /** Inject a renderer on the test scheduler, feed the canvas size, wire the panel. */
    private fun TestScope.wireRendererAndPanel() {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        viewModel.setScreenSize(400, 800)
        viewModel.setMapRendererForTest(renderer)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
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
        val expected = MapCanvasViewModel.Companion.computeAreaZoom(
            doubleArrayOf(48.0, 49.0, 2.0, 2.6), 400, 800, minZoom = 4.0
        )
        assertEquals(expected, vp.magnification, 1e-9)
        // The route must be drawn on the map as well (spec R1).
        assertNotNull("route must be drawn after fit", client.lastRouteLats)
    }

    @Test
    fun longTrip_zoomsBelowAreaFloor_bothEndpointsFit() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender(45.0, 5.0, 48.0, 6.5, route = longRouteEntry())

        val vp = viewModel.uiState.value.viewport
        val expected = MapCanvasViewModel.Companion.computeAreaZoom(
            doubleArrayOf(45.0, 48.0, 5.0, 6.5), 400, 800, minZoom = 4.0
        )
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
        val navVm = NavigationViewModel(
            client, NavigationStateProvider(),
            LocationService(context), context
        )
        viewModel.setNavigationViewModel(navVm)
        // Put the driver's viewport somewhere fixed.
        viewModel.updateCenter(50.0, 3.0)
        viewModel.updateMagnification(13.0)

        // Navigation starts (startNavigation sets isNavigating synchronously;
        // the fake returns a null controller without throwing).
        navVm.startNavigation(routeEntry(), Vehicle.CAR)
        advanceUntilIdle()
        assertEquals(true, navVm.state.value.isNavigating)

        // New route result arrives mid-drive (reroute): draw, do NOT refit.
        calculateAndAwaitRender(49.0, 2.5, 50.0, 3.5)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(13.0, vp.magnification, 1e-9)
        assertNotNull("rerouted route must still be drawn", client.lastRouteLats)
    }

    @Test
    fun routeCalculationWhileFollowing_keepsViewport() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        viewModel.updateCenter(50.0, 3.0)
        viewModel.onToggleFollowMode(true)
        assertEquals(15.0, viewModel.uiState.value.viewport.magnification, 1e-9)

        calculateAndAwaitRender(48.0, 2.0, 49.0, 2.6)

        val vp = viewModel.uiState.value.viewport
        assertEquals(50.0, vp.centerLat, 1e-9)
        assertEquals(3.0, vp.centerLon, 1e-9)
        assertEquals(15.0, vp.magnification, 1e-9)
        assertNotNull("route must still be drawn while following", client.lastRouteLats)
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
        val expected = MapCanvasViewModel.Companion.computeAreaZoom(
            doubleArrayOf(48.0, 49.0, 2.0, 2.6), 400, 800, minZoom = 4.0
        )
        assertEquals(expected, vp.magnification, 1e-9)
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
