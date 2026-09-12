package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun buildNavigationViewModel(): NavigationViewModel {
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
        return NavigationViewModel(
            routeClient, NavigationStateProvider(),
            LocationService(context), context
        )
    }

    @Test
    fun navigationEndRestoresBrowseMode() = runTest(mainDispatcherRule.dispatcher) {
        val navVm = buildNavigationViewModel()
        viewModel.setNavigationViewModel(navVm)
        advanceUntilIdle()

        // Start in BROWSE (follow off).
        assertFalse(viewModel.uiState.value.followMode)

        navVm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)
        // Route calculation runs on a real Dispatchers.Default thread — poll
        // with real time, advancing the virtual scheduler each round.
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !navVm.state.value.isNavigating) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
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
        viewModel.setNavigationViewModel(navVm)
        advanceUntilIdle()

        // Start in FREE_DRIVE.
        viewModel.enterFreeDrive()
        assertTrue(viewModel.uiState.value.followMode)

        navVm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !navVm.state.value.isNavigating) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        assertTrue(navVm.state.value.isNavigating)
        assertEquals(MapMode.NAVIGATION, viewModel.mode)

        navVm.stopNavigation()
        advanceUntilIdle()
        assertEquals("drive-before-nav lands back in FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
        assertTrue("follow restored", viewModel.uiState.value.followMode)
    }
}
