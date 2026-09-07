package com.naviveylin.ui.route

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import java.io.File

/**
 * Verifies the stop-navigation-hides-route contract (spec: route-panel-ui):
 * [RoutePanelViewModel.clearRouteFromMap] hides the route from the map (routeVisible
 * false + clearRouteSignal bump) while preserving the panel state and the
 * routeResultFlow, [RoutePanelViewModel.showRouteOnMap] makes it visible again,
 * and a new successful calculation (onSuccess) resets routeVisible to true so the
 * freshly calculated route is drawn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelRouteVisibilityTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient()
        viewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
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

    /** Drive a successful route calculation to Done; the route is on the map. */
    private fun TestScope.calculateAndAwaitSuccess() {
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = routeEntry()
        viewModel.calculateRoute()
        advanceUntilIdle()
        assertNotNull(viewModel.routeResultFlow.value)
        assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
    }

    @Test
    fun clearRouteFromMap_hidesRoute_preservesPanelState() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            val signalBefore = viewModel.clearRouteSignal.value
            val resultBefore = viewModel.routeResultFlow.value
            val entryBefore = viewModel.uiState.value.routeEntry
            val startBefore = viewModel.uiState.value.startLocation
            val destBefore = viewModel.uiState.value.destLocation
            val vehicleBefore = viewModel.uiState.value.vehicle

            viewModel.clearRouteFromMap()

            // Route hidden from the map...
            assertFalse(viewModel.routeVisible.value)
            assertEquals(signalBefore + 1, viewModel.clearRouteSignal.value)
            // ...but the panel state and the route data are fully preserved.
            assertEquals(resultBefore, viewModel.routeResultFlow.value)
            assertEquals(entryBefore, viewModel.uiState.value.routeEntry)
            assertEquals(startBefore, viewModel.uiState.value.startLocation)
            assertEquals(destBefore, viewModel.uiState.value.destLocation)
            assertEquals(vehicleBefore, viewModel.uiState.value.vehicle)
            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
        }

    @Test
    fun showRouteOnMap_makesRouteVisibleAgain() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.clearRouteFromMap()
            assertFalse(viewModel.routeVisible.value)

            viewModel.showRouteOnMap()

            assertTrue(viewModel.routeVisible.value)
            // Route data still intact for the map draw path.
            assertNotNull(viewModel.routeResultFlow.value)
        }

    @Test
    fun onSuccess_resetsRouteVisible_afterStop() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.clearRouteFromMap()
            assertFalse(viewModel.routeVisible.value)

            // Recalculate after a stop: the new route must be drawn again.
            client.routeToDeliver = routeEntry()
            viewModel.calculateRoute()
            advanceUntilIdle()

            assertTrue(viewModel.routeVisible.value)
            assertNotNull(viewModel.routeResultFlow.value)
            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
        }

    @Test
    fun clearRoute_resetsRouteVisible_toDefault() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            viewModel.clearRouteFromMap()
            assertFalse(viewModel.routeVisible.value)

            viewModel.clearRoute()

            // Full clear resets to the default visible state.
            assertTrue(viewModel.routeVisible.value)
            assertNull(viewModel.routeResultFlow.value)
            assertNull(viewModel.uiState.value.routeEntry)
        }
}
