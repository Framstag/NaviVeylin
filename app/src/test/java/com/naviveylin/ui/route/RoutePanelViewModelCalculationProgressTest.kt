package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The percentage the phone route panel shows while it calculates a route
 * (spec: `route-panel-ui` — Progress percentage during calculation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelCalculationProgressTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: RoutePanelViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
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

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    /** Start a calculation the fake keeps open, so progress can be driven. */
    private fun TestScope.startHeldCalculation(): com.framstag.libosmscout.client.RouteCallback {
        client.holdRouteDelivery = true
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        viewModel.calculateRoute()
        advanceUntilIdle()
        return client.pendingRouteCallbacks.single()
    }

    @Test
    fun thePanelReportsNoPercentageBeforeTheEngineReportsOne() =
        runTest(mainDispatcherRule.dispatcher) {
            startHeldCalculation()

            assertEquals(RouteState.Calculating(percent = null), viewModel.uiState.value.routeState)
        }

    @Test
    fun aReportedPercentageReachesThePanelState() =
        runTest(mainDispatcherRule.dispatcher) {
            val callback = startHeldCalculation()

            callback.onProgress(37)
            advanceUntilIdle()

            assertEquals(RouteState.Calculating(percent = 37), viewModel.uiState.value.routeState)
        }

    @Test
    fun thePercentageNeverCompletesTheWait() =
        runTest(mainDispatcherRule.dispatcher) {
            val callback = startHeldCalculation()

            callback.onProgress(100)
            advanceUntilIdle()

            assertEquals(
                "100 % belongs to the route that arrived, not to a report",
                RouteState.Calculating(percent = 99),
                viewModel.uiState.value.routeState
            )
        }

    @Test
    fun aLateProgressReportDoesNotOverwriteTheFinishedRoute() =
        runTest(mainDispatcherRule.dispatcher) {
            val callback = startHeldCalculation()

            client.holdRouteDelivery = false
            callback.onSuccess(routeEntry())
            advanceUntilIdle()
            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)

            callback.onProgress(80)
            advanceUntilIdle()

            assertEquals(
                "progress after the result must not resurrect the calculating state",
                RouteState.Done,
                viewModel.uiState.value.routeState
            )
        }

    @Test
    fun cancellingClearsThePercentageWithTheState() =
        runTest(mainDispatcherRule.dispatcher) {
            val callback = startHeldCalculation()
            callback.onProgress(12)
            advanceUntilIdle()

            viewModel.cancelRoute()
            advanceUntilIdle()

            assertEquals(RouteState.Idle, viewModel.uiState.value.routeState)
            assertEquals(1, client.cancelRouteCount)
            assertNull(viewModel.uiState.value.error)
        }
}
