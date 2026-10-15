package com.naviveylin.ui.route

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteCallback
import com.naviveylin.core.FailureReason
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.core.hasMapManagerAction
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Verifies the route panel's failure contract (spec: `route-panel-ui` — *Calculate route*, its failure
 * scenarios; `routing-failure-reporting` — *Each reason is worded by the surface*, *A reason whose cause
 * the user can act on offers the action*).
 *
 * The panel must carry the **cause** it was given — not one fixed message for every failure — and it must
 * name the affected end and offer the action that cause has. The wording itself is verified in
 * [RoutingFailureWordingTest]; this class verifies that the panel publishes and exposes the cause.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelFailureReasonTest {

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
            context = context,
            settingsStorage = SettingsStorage(context)
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

    /** Drive a failing calculation and return the failure the panel published. */
    private fun kotlinx.coroutines.test.TestScope.failWith(
        kind: Int,
        detail: String,
        distanceMeters: Double = Double.NaN
    ) = viewModel.uiState.value.run {
        client.routeToDeliver = null
        client.deliverRouteErrorKind = kind
        client.deliverRouteError = detail
        client.deliverRouteErrorDistanceMeters = distanceMeters
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        viewModel.calculateRoute()
        advanceUntilIdle()
    }

    @Test
    fun aFailureShowsTheReasonResourceNotOneFixedMessage() = runTest(mainDispatcherRule.dispatcher) {
        failWith(RouteCallback.KIND_TARGET_UNROUTABLE, "No routable node near destination")

        val state = viewModel.uiState.value
        assertEquals(FailureReason.TARGET_UNROUTABLE, state.routeFailure?.reason)
        assertEquals(RouteState.Error(state.routeFailure!!), state.routeState)
        // The rendered text is the wording for that reason, not the panel's fixed fallback.
        assertEquals(
            com.naviveylin.core.navigationFailureText(
                context = context,
                failure = state.routeFailure!!,
                unitMetersRes = com.naviveylin.R.string.distance_unit_m,
                unitKilometersRes = com.naviveylin.R.string.distance_unit_km
            ),
            state.error
        )
    }

    @Test
    fun theReasonAndNotTheMessageDecidesTheWording() = runTest(mainDispatcherRule.dispatcher) {
        // Two failures with the same text but different causes must not be the same failure: the first
        // panel to report a cause is the one whose wording is shown.
        failWith(RouteCallback.KIND_ROUTING_DATA_UNAVAILABLE, "Route calculation failed")
        val first = viewModel.uiState.value.routeFailure

        failWith(RouteCallback.KIND_NO_ROUTE_FOUND, "Route calculation failed")
        val second = viewModel.uiState.value.routeFailure

        assertEquals(FailureReason.ROUTING_DATA_UNAVAILABLE, first?.reason)
        assertEquals(FailureReason.NO_ROUTE_FOUND, second?.reason)
        assert(first != second) { "one sentence cannot stand for two causes" }
    }

    @Test
    fun anUnattachableDestinationNamesTheDestination() = runTest(mainDispatcherRule.dispatcher) {
        failWith(RouteCallback.KIND_TARGET_UNROUTABLE, "No routable node near destination")

        val failure = viewModel.uiState.value.routeFailure!!
        assertEquals(RouteEndpoint.TARGET, failure.endpoint)
        assertEquals(FailureReason.TARGET_UNROUTABLE, failure.reason)
    }

    @Test
    fun anActionableReasonOffersItsAction() = runTest(mainDispatcherRule.dispatcher) {
        // The cause a map manager can fix is what the panel's action is for (the panel's own
        // [com.naviveylin.ui.route.RoutePanel] renders it); the panel state carries the cause that
        // decides it.
        failWith(RouteCallback.KIND_ROUTING_DATA_UNAVAILABLE, "No databases loaded")

        val failure = viewModel.uiState.value.routeFailure!!
        assertEquals(FailureReason.ROUTING_DATA_UNAVAILABLE, failure.reason)
        org.junit.Assert.assertTrue(
            "the panel state offers the action for this cause",
            failure.hasMapManagerAction()
        )
    }

    @Test
    fun theNearestRoadDistanceReachesThePanel() = runTest(mainDispatcherRule.dispatcher) {
        failWith(
            RouteCallback.KIND_TARGET_UNROUTABLE,
            "No routable node near destination",
            distanceMeters = 7_000.0
        )

        assertEquals(7_000.0, viewModel.uiState.value.routeFailure?.nearestRoadDistanceMeters!!, 1e-9)
        org.junit.Assert.assertTrue(
            "the wording states the distance",
            viewModel.uiState.value.error!!.contains("7")
        )
    }

    @Test
    fun aRefusedRequestNamesItsOwnReason() = runTest(mainDispatcherRule.dispatcher) {
        // Without the precise grant the request never reaches the routing engine, and the panel still
        // reports a cause of its own (spec: `location-permissions` — Starting navigation requires
        // precise location).
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        viewModel.calculateRoute()
        advanceUntilIdle()

        assertEquals(FailureReason.PRECISE_LOCATION_REQUIRED, viewModel.uiState.value.routeFailure?.reason)
        assertEquals(0, client.routeCalculationCount)
    }

    @Test
    fun aClearedPanelCarriesNoFailure() = runTest(mainDispatcherRule.dispatcher) {
        failWith(RouteCallback.KIND_TARGET_UNROUTABLE, "No routable node near destination")
        org.junit.Assert.assertNotNull(viewModel.uiState.value.routeFailure)

        viewModel.cancelRoute()

        assertNull("a cleared panel publishes no cause", viewModel.uiState.value.routeFailure)
        assertNull(viewModel.uiState.value.error)
    }
}
