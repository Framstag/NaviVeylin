package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the phone follow-mode vehicle anchor selection (spec:
 * smooth-follow — Vehicle position anchor in follow mode): the routing
 * anchor applies while route guidance is active, the free-driving anchor
 * otherwise, and the persisted ids resolve to presets.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelVehicleAnchorTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        settingsStorage = SettingsStorage(context)
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
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

    private suspend fun persistAnchors(routing: String, freeDriving: String) {
        val current = settingsStorage.load()
        settingsStorage.save(current.copy(routingAnchorId = routing, freeDrivingAnchorId = freeDriving))
    }

    private fun emitFix(lat: Double, lon: Double) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = lat, lon = lon,
                accuracy = 5.0, speedKmH = 40.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = System.currentTimeMillis()
            )
        )
    }

    @Test
    fun freeDrivingAnchorAppliedWithoutGuidance() = runTest(mainDispatcherRule.dispatcher) {
        persistAnchors(routing = VehicleAnchorPosition.BOTTOM_RIGHT.id, freeDriving = VehicleAnchorPosition.TOP_CENTER.id)
        // Reload the view model so the persisted settings are picked up.
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(
            VehicleAnchorPosition.TOP_CENTER,
            viewModel.uiState.value.activeFollowAnchor
        )
    }

    @Test
    fun routingAnchorAppliedWhileNavigating() = runTest(mainDispatcherRule.dispatcher) {
        val navVm = buildNavigationViewModel()
        viewModel.setNavigationViewModel(navVm)
        advanceUntilIdle()

        startNavigating(navVm)
        // The picker path: the user chooses the routing anchor while guidance
        // is active — the active anchor flips immediately.
        viewModel.setRoutingAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        advanceUntilIdle()
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT,
            viewModel.uiState.value.activeFollowAnchor
        )
        // And the choice persists to the shared storage.
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT.id,
            settingsStorage.load().routingAnchorId
        )

        // Free-driving anchor chosen before/independent of guidance.
        viewModel.setFreeDrivingAnchor(VehicleAnchorPosition.TOP_CENTER)
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT,
            viewModel.uiState.value.activeFollowAnchor
        )

        // End guidance: the free-driving anchor takes over (spec:
        // "free-driving anchor otherwise").
        navVm.stopNavigation()
        advanceUntilIdle()
        viewModel.setFreeDrivingAnchor(VehicleAnchorPosition.TOP_CENTER)
        assertEquals(
            VehicleAnchorPosition.TOP_CENTER,
            viewModel.uiState.value.activeFollowAnchor
        )
    }

    @Test
    fun defaultAnchorsAreCenter() = runTest(mainDispatcherRule.dispatcher) {
        // Fresh settings (no anchor keys): the app defaults to center/center,
        // reproducing the pre-feature framing.
        val current = settingsStorage.load()
        settingsStorage.save(current.copy(routingAnchorId = "", freeDrivingAnchorId = ""))
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        advanceUntilIdle()
        emitFix(52.51, 13.40)
        advanceUntilIdle()
        assertEquals(VehicleAnchorPosition.DEFAULT, viewModel.uiState.value.activeFollowAnchor)
    }

    private fun buildNavigationViewModel(): NavigationViewModel {
        val routeClient = FakeOSMScoutClient().apply {
            routeToDeliver = com.framstag.libosmscout.client.RouteEntry().apply {
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
        assertEquals("navigation must become active", true, navVm.state.value.isNavigating)
        advanceUntilIdle()
    }
}
