package com.naviveylin.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
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
import com.naviveylin.navigation.NavigationEngine
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Verifies the session's camera lease (spec: `route-planning-session` — "Session holds
 * the camera while active"): opening a session suspends the drive preset, no
 * follow-driven camera move happens while it is open, and the follow toggle is refused
 * until the session ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSessionLeaseTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        File(context.filesDir, "maps/search_history.json").delete()
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
        routePanelViewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            context = context
        )
        routePanelViewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        navigationViewModel = NavigationViewModel(
            NavigationEngine({ client }, LocationService(context), context)
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun entry(label: String, lat: Double = 51.5136, lon: Double = 7.4653): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    private fun fix(lat: Double, lon: Double, time: Long): Location = Location("gps").apply {
        latitude = lat
        longitude = lon
        accuracy = 8f
        this.time = time
    }

    private fun kotlinx.coroutines.test.TestScope.wire() {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        viewModel.setScreenSize(400, 800)
        viewModel.setMapRendererForTest(renderer)
        viewModel.setNavigationViewModel(navigationViewModel)
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        advanceUntilIdle()
    }

    @Test
    fun `opening a session suspends the drive preset`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.onToggleFollowMode(true)
            assertTrue(viewModel.uiState.first { it.followMode }.followMode)

            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()

            assertEquals(RouteSessionState.EDITING, routePanelViewModel.sessionState.value)
            assertFalse(viewModel.uiState.value.followMode)
        }

    @Test
    fun `a position fix does not move the camera during a session`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.onToggleFollowMode(true)
            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()
            val viewport = viewModel.uiState.value.viewport

            locationService.setLocationForTest(fix(52.5, 13.4, 2_000L))
            advanceUntilIdle()

            assertEquals(viewport, viewModel.uiState.value.viewport)
        }

    @Test
    fun `follow re-engage is refused while the session holds the camera`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()

            viewModel.onToggleFollowMode(true)
            advanceUntilIdle()

            assertFalse("the session owns the camera", viewModel.uiState.value.followMode)
        }

    @Test
    fun `ending the session releases the camera`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()

            viewModel.dismissRoutePanel()
            advanceUntilIdle()

            assertFalse(viewModel.sessionHoldsCamera())
            viewModel.onToggleFollowMode(true)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.followMode)
        }

    @Test
    fun `the reported mode stays BROWSE while a session is active`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.onToggleFollowMode(true)
            assertEquals(MapMode.FREE_DRIVE, viewModel.mode)

            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()

            // No fourth map mode exists (spec: route-planning-session — the session is a
            // lease): with the drive preset suspended the mode is BROWSE.
            assertEquals(MapMode.BROWSE, viewModel.mode)
            assertFalse(viewModel.uiState.value.driveSuspended)
        }

    @Test
    fun `the mode returns to BROWSE after a session opened from free drive ends`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.onToggleFollowMode(true)
            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()

            viewModel.dismissRoutePanel()
            advanceUntilIdle()

            // The drive preset stays suspended (spec: map-modes — the session suspends it)
            // and the re-center affordance is what restores it: the toggle is accepted
            // again and puts the map back into free drive.
            assertEquals(MapMode.BROWSE, viewModel.mode)
            viewModel.onToggleFollowMode(true)
            advanceUntilIdle()
            assertEquals(MapMode.FREE_DRIVE, viewModel.mode)
        }

    @Test
    fun `a session opened on a route keeps the analysed camera move working`() =
        runTest(mainDispatcherRule.dispatcher) {
            wire()
            locationService.setLocationForTest(fix(51.5136, 7.4653, 1_000L))
            viewModel.openRoutePanelWithStart(entry("Destination"))
            advanceUntilIdle()
            val before = viewModel.uiState.value.viewport

            // The lease stops automatic moves only: the session's explicit analysis move
            // still happens (spec: route-analysis).
            routePanelViewModel.updateLocationsForReroute(entry("start"), entry("dest"))
            client.routeToDeliver = com.framstag.libosmscout.client.RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(48.0, 48.5, 49.0)
                longitudes = doubleArrayOf(2.0, 2.3, 2.6)
                distance = 120_000.0
                descriptions = arrayOf("Start: A  [0.0 km]", "Right onto B  [120.0 km]")
                instructionLats = doubleArrayOf(48.0, 48.5)
                instructionLons = doubleArrayOf(2.0, 2.3)
            }
            routePanelViewModel.calculateRoute()
            advanceUntilIdle()
            assertNotEquals(before, viewModel.uiState.value.viewport)

            val afterFit = viewModel.uiState.value.viewport
            routePanelViewModel.analyseStep(1)
            advanceUntilIdle()

            // The camera lands on the analysed segment's midpoint (48.25 for the fixture's first
            // leg), which is what the fit centres on the visible area.
            assertEquals(48.25, viewModel.uiState.value.viewport.centerLat, 0.001)
            assertNotEquals(afterFit, viewModel.uiState.value.viewport)
        }
}
