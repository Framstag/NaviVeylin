package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
 * Verifies that navigation end applies the representation preset of the
 * restored mode while keeping the viewport position and zoom where routing
 * ended (spec: map-modes — "Navigation end restores prior mode"). Regression
 * test for fix-browse-preset-on-mode-exit: previously the nav-end restore only
 * flipped followMode/driveSuspended and left the map rotated at the last
 * heading-up angle with driving zoom — BROWSE representation never applied.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelNavEndRestoreTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var viewportStorage: ViewportStorage

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewportStorage = ViewportStorage(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = viewportStorage,
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

    /** Start a route via startDirectRoute and wait until navigation is active. */
    private suspend fun TestScope.startNavigating(navVm: NavigationViewModel) {
        navVm.startDirectRoute(52.5200, 13.4050, 52.5300, 13.4100)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !navVm.state.value.isNavigating) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        assertTrue("navigation must become active", navVm.state.value.isNavigating)
        advanceUntilIdle()
    }

    @Test
    fun browseBeforeNavAppliesBrowseRepresentationKeepingViewport() =
        runTest(mainDispatcherRule.dispatcher) {
            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()
            assertFalse("start in BROWSE", viewModel.uiState.value.followMode)

            startNavigating(navVm)
            assertEquals("navigation overrides the mode", MapMode.NAVIGATION, viewModel.mode)

            // Simulate routing state: map rotated heading-up at driving zoom.
            viewModel.onManualRotation(-0.7)
            viewModel.updateMagnification(13.0)
            val routedViewport = viewModel.uiState.value.viewport
            assertEquals("routing left a non-zero angle", -0.7, routedViewport.angle, 1e-9)
            assertEquals("routing left driving zoom", 13.0, routedViewport.magnification, 1e-9)

            navVm.stopNavigation()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("browse-before-nav lands back in BROWSE", MapMode.BROWSE, viewModel.mode)
            assertFalse("follow must not leak from navigation", state.followMode)
            assertFalse("no drive suspension in browse", state.driveSuspended)
            assertTrue("browse north-up flag applied", state.freeFormNorthUp)
            assertFalse("no phantom browse drift", state.browseDrifted)
            assertEquals("rotation reset to north-up", 0.0, state.viewport.angle, 1e-9)
            // Position and zoom must stay where routing ended.
            assertEquals("zoom kept from routing end", 13.0, state.viewport.magnification, 1e-9)
            assertEquals("center lat kept", routedViewport.centerLat, state.viewport.centerLat, 1e-9)
            assertEquals("center lon kept", routedViewport.centerLon, state.viewport.centerLon, 1e-9)

            // The routing-end viewport must be persisted at navigation end so a
            // later map restore (app restart, screen recreation) resumes where
            // routing ended instead of jumping back to the app-start position
            // (fix: "ending routing resets location and zoom to app-start state").
            val saved = viewportStorage.load("default")
            assertEquals("persisted zoom is the routing-end zoom", 13.0, saved?.magnification ?: -1.0, 1e-9)
            assertEquals("persisted center lat is the routing-end lat",
                routedViewport.centerLat, saved?.centerLat ?: Double.NaN, 1e-9)
            assertEquals("persisted center lon is the routing-end lon",
                routedViewport.centerLon, saved?.centerLon ?: Double.NaN, 1e-9)
        }

    @Test
    fun suspendedDriveBeforeNavRestoresSuspendedFreeDrive() =
        runTest(mainDispatcherRule.dispatcher) {
            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()

            // Drive, then suspend the drive preset (pan) — follow off, suspension on.
            viewModel.enterFreeDrive()
            viewModel.disengageFollowMode()
            viewModel.onManualRotation(-0.5)
            assertFalse(viewModel.uiState.value.followMode)
            assertTrue(viewModel.uiState.value.driveSuspended)
            assertEquals("suspended drive is still FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
            val driveZoom = viewModel.uiState.value.viewport.magnification

            startNavigating(navVm)

            navVm.stopNavigation()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("suspended-drive-before-nav lands back in FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
            assertFalse("follow stays off (suspended)", state.followMode)
            assertTrue("pre-nav suspension restored", state.driveSuspended)
            // FREE_DRIVE restore must NOT apply the browse representation.
            assertEquals("rotation untouched in suspended drive", -0.5, state.viewport.angle, 1e-9)
            assertEquals("zoom kept", driveZoom, state.viewport.magnification, 1e-9)
        }

    @Test
    fun freeDriveBeforeNavRestoresFollowAndPreNavSuspension() =
        runTest(mainDispatcherRule.dispatcher) {
            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()

            viewModel.enterFreeDrive()
            assertTrue(viewModel.uiState.value.followMode)

            startNavigating(navVm)

            // Disengage mid-navigation (pan while navigating) — flags mutate, mode stays NAVIGATION.
            viewModel.disengageFollowMode()
            assertTrue("mid-nav suspension flag set", viewModel.uiState.value.driveSuspended)

            navVm.stopNavigation()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("drive-before-nav lands back in FREE_DRIVE", MapMode.FREE_DRIVE, viewModel.mode)
            assertTrue("follow restored from pre-nav state", state.followMode)
            assertFalse("pre-nav suspension restored over mid-nav suspend", state.driveSuspended)
        }

    @Test
    fun stopNavigationWhileStoppedIsIdempotent() =
        runTest(mainDispatcherRule.dispatcher) {
            val navVm = buildNavigationViewModel()
            viewModel.setNavigationViewModel(navVm)
            advanceUntilIdle()

            startNavigating(navVm)
            navVm.stopNavigation()
            advanceUntilIdle()
            assertFalse(navVm.state.value.isNavigating)
            assertEquals(MapMode.BROWSE, viewModel.mode)

            // Second stop must not crash or move state.
            navVm.stopNavigation()
            advanceUntilIdle()
            assertEquals(MapMode.BROWSE, viewModel.mode)
            assertFalse(viewModel.uiState.value.followMode)
        }
}
