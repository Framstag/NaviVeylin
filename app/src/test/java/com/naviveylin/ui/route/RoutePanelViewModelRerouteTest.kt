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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
 * Verifies the reroute route-visibility contract (spec: reroute-route-visibility):
 * [RoutePanelViewModel.updateLocationsForReroute] must NOT clear the drawn route
 * (routeResultFlow / clearRouteSignal / routeEntry preserved), while panel-driven
 * edits keep the historical clear behavior; and [RoutePanelViewModel.onError] must
 * emit on routeErrorEvent while leaving the last route intact.
 *
 * The snackbar *display* path (spec scenario "Calc failure shows snackbar while
 * navigating") is intentionally NOT covered by a Compose test here: it requires
 * the full [com.naviveylin.ui.map.MapCanvasScreen] composition (three Hilt
 * view models, map renderer init, navigation controller). The pieces are covered
 * at unit level (routeErrorEvent emission, this file) and the filter-by-
 * `isNavigating` collector is verified on-device (see tasks.md §5, logcat
 * `route calculation failed: <msg>` + snackbar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelRerouteTest {

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
    fun updateLocationsForReroute_updatesStartDest_withoutClearingRoute() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            val signalBefore = viewModel.clearRouteSignal.value
            val resultBefore = viewModel.routeResultFlow.value
            val entryBefore = viewModel.uiState.value.routeEntry

            viewModel.updateLocationsForReroute(entry("new start", 52.0, 13.0), entry("new dest", 52.9, 13.9))

            assertEquals("new start", viewModel.uiState.value.startLocation?.label)
            assertEquals("new dest", viewModel.uiState.value.destLocation?.label)
            // The drawn route must survive the reroute location update.
            assertEquals(resultBefore, viewModel.routeResultFlow.value)
            assertEquals(signalBefore, viewModel.clearRouteSignal.value)
            assertEquals(entryBefore, viewModel.uiState.value.routeEntry)
        }

    @Test
    fun setStartLocation_stillClearsRoute_forPanelEdits() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            val signalBefore = viewModel.clearRouteSignal.value

            viewModel.setStartLocation(entry("panel edit"))

            // Panel-driven edits keep the historical clear behavior.
            assertNull(viewModel.routeResultFlow.value)
            assertEquals(signalBefore + 1, viewModel.clearRouteSignal.value)
            assertNull(viewModel.uiState.value.routeEntry)
        }

    @Test
    fun onError_emitsRouteErrorEvent_andKeepsLastRoute() =
        runTest(mainDispatcherRule.dispatcher) {
            calculateAndAwaitSuccess()
            val errors = mutableListOf<String>()
            val collectJob = launch { viewModel.routeErrorEvent.collect { errors.add(it) } }

            client.routeToDeliver = null
            client.deliverRouteError = "No routable node near destination"
            viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
            viewModel.calculateRoute()
            advanceUntilIdle()

            assertTrue(errors.contains("No routable node near destination"))
            // Last drawn route is preserved after a failed reroute calc.
            assertNotNull(viewModel.routeResultFlow.value)
            assertNotNull(viewModel.uiState.value.routeEntry)
            assertEquals(
                RouteState.Error("No routable node near destination"),
                viewModel.uiState.value.routeState
            )
            collectJob.cancel()
        }
}
