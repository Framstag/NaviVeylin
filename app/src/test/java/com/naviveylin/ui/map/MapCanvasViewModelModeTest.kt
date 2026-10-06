package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.Vehicle
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationEngine
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.naviveylin.test.engineUnderTest

/**
 * Verifies the explicit map mode model (spec: map-modes): the derived MapMode
 * enum, the mode presets (enterFreeDrive/exitFreeDrive/resetDrivePreset), and
 * the pre-navigation mode snapshot (navigation end restores the prior mode).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelModeTest {

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
            searchHistoryRepository = SearchHistoryRepository(context),
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

    // --- Mode derivation matrix ---

    @Test
    fun modeDefaultsToBrowse() {
        assertEquals("no follow, no navigation → BROWSE", MapMode.BROWSE, viewModel.mode)
    }

    @Test
    fun followModeDerivesFreeDrive() {
        viewModel.onToggleFollowMode(true)
        assertEquals(MapMode.FREE_DRIVE, viewModel.mode)
    }

    @Test
    fun suspendedDriveStillDerivesFreeDrive() {
        // A suspended drive (follow off, driveSuspended on) must stay FREE_DRIVE,
        // not collapse to BROWSE.
        viewModel.enterFreeDrive()
        viewModel.disengageFollowMode()
        assertFalse(viewModel.uiState.value.followMode)
        assertTrue(viewModel.uiState.value.driveSuspended)
        assertEquals("suspended drive stays FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
    }

    // --- Mode presets ---

    @Test
    fun enterFreeDriveAppliesDrivePreset() {
        viewModel.enterFreeDrive()

        val state = viewModel.uiState.value
        assertTrue("follow on", state.followMode)
        assertTrue("auto-zoom on", state.autoZoomEnabled)
        assertFalse("heading-up (navNorthUp=false)", state.navNorthUp)
        assertFalse("drive active", state.driveSuspended)
        assertEquals("driving zoom", 15.0, state.viewport.magnification, 1e-9)
        assertEquals(MapMode.FREE_DRIVE, viewModel.mode)
    }

    @Test
    fun exitFreeDriveAppliesBrowsePreset() {
        viewModel.enterFreeDrive()
        viewModel.exitFreeDrive()

        val state = viewModel.uiState.value
        assertFalse("follow off", state.followMode)
        assertTrue("browse north-up", state.freeFormNorthUp)
        assertFalse("no suspension", state.driveSuspended)
        assertEquals("north-up angle", 0.0, state.viewport.angle, 1e-9)
        assertEquals(MapMode.BROWSE, viewModel.mode)
    }

    @Test
    fun resetDrivePresetRestoresStandardDriveValues() {
        viewModel.enterFreeDrive()
        viewModel.updateMagnification(6.0)
        assertTrue(viewModel.uiState.value.driveSuspended)

        viewModel.resetDrivePreset()

        val state = viewModel.uiState.value
        assertTrue(state.followMode)
        assertTrue(state.autoZoomEnabled)
        assertFalse(state.navNorthUp)
        assertFalse(state.driveSuspended)
        assertEquals(MapMode.FREE_DRIVE, viewModel.mode)
    }

    // --- Pre-navigation mode snapshot ---

    private fun buildNavigationViewModel(): NavigationEngine {
        val routeClient = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
                descriptions = arrayOf(
                    "Start navigation  [0.0 km, 0 min]",
                    "Destination reached  [0.0 km, 0 min]"
                )
            }
        }
        return engineUnderTest(
            { routeClient },
            LocationService(context), context
        )
    }

    @Test
    fun navigationEndRestoresBrowseMode() = runTest(mainDispatcherRule.dispatcher) {
        val navVm = buildNavigationViewModel()
        viewModel.setNavigationViewModel(NavigationViewModel(navVm))
        advanceUntilIdle()

        // Start in BROWSE (follow off).
        assertFalse(viewModel.uiState.value.followMode)

        navVm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        // The route calculation is real background work (native lookups), so the case awaits the engine's
        // observable state on a real dispatcher — no clock, no sleep, no bounded poll
        // (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
        withContext(Dispatchers.Default) { navVm.state.first { it.isNavigating } }
        assertTrue(navVm.state.value.isNavigating)
        assertEquals("navigation overrides the mode", MapMode.NAVIGATION, viewModel.mode)

        navVm.stopNavigation()
        advanceUntilIdle()
        assertFalse(navVm.state.value.isNavigating)
        assertEquals("browse-before-nav lands back in BROWSE", MapMode.BROWSE, viewModel.mode)
        assertFalse("follow must not leak from navigation", viewModel.uiState.value.followMode)
    }

    @Test
    fun navigationEndRestoresFreeDriveMode() = runTest(mainDispatcherRule.dispatcher) {
        val navVm = buildNavigationViewModel()
        viewModel.setNavigationViewModel(NavigationViewModel(navVm))
        advanceUntilIdle()

        // Start in FREE_DRIVE.
        viewModel.enterFreeDrive()
        assertTrue(viewModel.uiState.value.followMode)

        navVm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        withContext(Dispatchers.Default) { navVm.state.first { it.isNavigating } }
        assertTrue(navVm.state.value.isNavigating)
        assertEquals(MapMode.NAVIGATION, viewModel.mode)

        navVm.stopNavigation()
        advanceUntilIdle()
        assertEquals("drive-before-nav lands back in FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
        assertTrue("follow restored", viewModel.uiState.value.followMode)
    }

    /**
     * The status card's stop restores the pre-navigation mode (spec: `map-modes` — "The status
     * card's stop restores prior mode"). The screen wires the adapter's follow callback
     * (`MapCanvasScreen`: `setFollowModeCallback { viewModel.onToggleFollowMode(it) }`) *and* the
     * session adapter to the same surface, so navigation start forces follow on through the object
     * that will restore it afterwards; the pre-navigation snapshot must not observe that forced
     * value — the device measured `mode restored follow=true` after a browse-before navigation
     * (`TODO.md` §140).
     */
    @Test
    fun cardStopRestoresBrowseMode() = runTest(mainDispatcherRule.dispatcher) {
        val routeClient = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
                descriptions = arrayOf(
                    "Start navigation  [0.0 km, 0 min]",
                    "Destination reached  [0.0 km, 0 min]"
                )
            }
        }
        val navVm = engineUnderTest({ routeClient }, LocationService(context), context)
        val surface = NavigationViewModel(navVm)
        // The screen's wiring, in the screen's order: the session adapter is bound first, the map
        // surface last (MapCanvasScreen's two LaunchedEffects) — including the pre-navigation
        // snapshot the adapter takes when a session starts.
        surface.setFollowModeCallback { viewModel.onToggleFollowMode(it) }
        surface.setPreNavigationSnapshotCallback { viewModel.snapshotPreNavigationMode() }
        surface.setRoutePanelViewModel(
            RoutePanelViewModel(
                client = routeClient,
                favoriteRepository = FavoriteRepository(routeClient),
                searchHistoryRepository = SearchHistoryRepository(context),
                locationService = LocationService(context),
                context = context
            ).apply { defaultDispatcher = mainDispatcherRule.dispatcher }
        )
        viewModel.setNavigationViewModel(surface)
        advanceUntilIdle()

        assertFalse("the surface starts in BROWSE", viewModel.uiState.value.followMode)

        // The phone's own session starts navigation: the adapter forces follow on.
        surface.start(routeClient.routeToDeliver!!, Vehicle.CAR)
        withContext(Dispatchers.Default) { navVm.state.first { it.isNavigating } }
        assertTrue("navigation is running", navVm.state.value.isNavigating)
        // The adapter observes the same engine state on the main dispatcher, after the calculation
        // landed on its real dispatcher — await that observable state, then read the surface.
        withContext(Dispatchers.Default) { viewModel.uiState.first { it.followMode } }
        assertTrue("navigation forced follow on", viewModel.uiState.value.followMode)

        // The card's stop control ends navigation through the one engine.
        navVm.stopNavigation()
        advanceUntilIdle()

        assertEquals(
            "the card's stop restores the pre-navigation mode",
            MapMode.BROWSE, viewModel.mode
        )
        assertFalse(
            "the forced follow must not leak into the restore",
            viewModel.uiState.value.followMode
        )
    }
}
