package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies the map draw gate (spec: route-panel-ui — "Stop navigation hides
 * route from map"): the route is drawn only while [RoutePanelViewModel.routeVisible]
 * is true. Regression for the StateFlow re-collection trap: after a stop, a
 * re-subscription of the combine collector (screen re-entry) must NOT redraw the
 * hidden route; restarting navigation (showRouteOnMap) must redraw it.
 *
 * The renderer is injected via [MapCanvasViewModel.setMapRendererForTest] and
 * created on the test's backgroundScope, so renders complete deterministically
 * under the test scheduler; the fake client records the route arrays passed to
 * the native render call.
 *
 * Runs under Robolectric with the default sandbox: FakeOSMScoutClient triggers
 * OSMScoutClient's static System.loadLibrary (stubbed .so), which requires the
 * default classloader (AGENTS.md classloader rule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelRouteVisibilityTest {

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

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    /** Inject a renderer on the test scheduler and wire the route panel. */
    private fun TestScope.wireRendererAndPanel() {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        viewModel.setMapRendererForTest(renderer)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
    }

    /** Drive a successful route calculation; the route is on the map. */
    private fun TestScope.calculateAndAwaitRender() {
        routePanelViewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = routeEntry()
        routePanelViewModel.calculateRoute()
        advanceUntilIdle()
        // The renderer's debounce loop parks on a conflated channel; advance the
        // virtual clock past the debounce window so the render reaches the client.
        advanceTimeBy(1_000)
        advanceUntilIdle()
    }

    @Test
    fun routeDrawnWhenVisible() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender()

        assertNotNull("route must be drawn while visible", client.lastRouteLats)
    }

    @Test
    fun clearRouteFromMap_hidesRouteFromRenderer() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender()
        assertNotNull(client.lastRouteLats)

        routePanelViewModel.clearRouteFromMap()
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertNull("route must be cleared from the renderer after stop", client.lastRouteLats)
    }

    @Test
    fun reEntry_doesNotRedrawHiddenRoute() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender()
        routePanelViewModel.clearRouteFromMap()
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertNull(client.lastRouteLats)

        // Screen re-entry re-subscribes the combine collector; the StateFlow
        // re-emits the current (result, visible=false) pair — the route must NOT
        // be redrawn (regression for the re-collection trap).
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertNull("hidden route must not reappear on collector re-subscription", client.lastRouteLats)
    }

    @Test
    fun showRouteOnMap_redrawsRoute() = runTest(mainDispatcherRule.dispatcher) {
        wireRendererAndPanel()
        calculateAndAwaitRender()
        routePanelViewModel.clearRouteFromMap()
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertNull(client.lastRouteLats)

        routePanelViewModel.showRouteOnMap()
        advanceUntilIdle()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertNotNull("restarting navigation must redraw the route", client.lastRouteLats)
    }
}
