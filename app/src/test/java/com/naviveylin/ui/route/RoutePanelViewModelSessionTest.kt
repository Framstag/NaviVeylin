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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Verifies the session state machine (spec: `route-planning-session`): the session's
 * states, its only two exits, and the review-during-navigation variant where ending
 * the session leaves the route to navigation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutePanelViewModelSessionTest {

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

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
        descriptions = arrayOf("Start: A  [0.0 km]", "Left onto B  [1.2 km]")
        instructionLats = doubleArrayOf(52.5200, 52.5230)
        instructionLons = doubleArrayOf(13.4050, 13.4080)
    }

    private fun entry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        LocationEntry().apply {
            this.label = label
            this.lat = lat
            this.lon = lon
            matchQuality = "coordinate"
        }

    private fun TestScope.calculateAndAwaitSuccess() {
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = routeEntry()
        viewModel.calculateRoute()
        advanceUntilIdle()
        assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
    }

    @Test
    fun `a fresh state owner has no session`() =
        runTest(mainDispatcherRule.dispatcher) {
            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
        }

    @Test
    fun `opening a session without a route starts editing`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()

            assertEquals(RouteSessionState.EDITING, viewModel.sessionState.value)
        }

    @Test
    fun `a calculated route moves the session to reviewing`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()

            assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
        }

    @Test
    fun `a reroute outside a session does not open one`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Car-only start / background reroute: the map adopts the route, but no
            // session exists, so no session state may appear.
            viewModel.adoptRoute(routeEntry(), com.framstag.libosmscout.client.Vehicle.CAR)

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
        }

    @Test
    fun `opening a session on an adopted route reviews it`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.adoptRoute(routeEntry(), com.framstag.libosmscout.client.Vehicle.CAR)

            viewModel.openSession()

            assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
        }

    @Test
    fun `opening a session twice keeps the state`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()

            viewModel.openSession()

            assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
        }

    @Test
    fun `ending the session clears the route and resets the state`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            val signalBefore = viewModel.clearRouteSignal.value

            viewModel.endSession()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(signalBefore + 1, viewModel.clearRouteSignal.value)
            assertNull(viewModel.routeResultFlow.value)
            assertNull(viewModel.uiState.value.routeEntry)
            assertEquals(RouteState.Idle, viewModel.uiState.value.routeState)
            assertNull(viewModel.uiState.value.startLocation)
            assertNull(viewModel.uiState.value.destLocation)
        }

    @Test
    fun `ending without a session changes nothing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val signalBefore = viewModel.clearRouteSignal.value

            viewModel.endSession()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(signalBefore, viewModel.clearRouteSignal.value)
        }

    @Test
    fun `clearing the route keeps the session open for a new plan`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()

            viewModel.clearRoute()

            assertEquals(RouteSessionState.EDITING, viewModel.sessionState.value)
        }

    @Test
    fun `start navigation ends the session but keeps the route drawn`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            val signalBefore = viewModel.clearRouteSignal.value

            viewModel.setNavigating(true)

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(signalBefore, viewModel.clearRouteSignal.value)
            assertNotNull(viewModel.routeResultFlow.value)
            assertNotNull(viewModel.uiState.value.routeEntry)
            assertEquals(true, viewModel.uiState.value.isNavigating)
        }

    @Test
    fun `ending a review during navigation keeps the route`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            viewModel.setNavigating(true)
            viewModel.openSession()
            assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
            val signalBefore = viewModel.clearRouteSignal.value

            viewModel.endSession()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(signalBefore, viewModel.clearRouteSignal.value)
            assertNotNull(viewModel.routeResultFlow.value)
        }

    @Test
    fun `opening a session during navigation reviews read-only`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.setNavigating(true)

            viewModel.openSession()

            assertEquals(RouteSessionState.REVIEWING, viewModel.sessionState.value)
            assertEquals(true, viewModel.uiState.value.isNavigating)
        }

    @Test
    fun `stopping navigation moves an open session into its stopped state`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            viewModel.setNavigating(true)
            viewModel.openSession()

            viewModel.setNavigating(false)

            assertEquals(RouteSessionState.STOPPED, viewModel.sessionState.value)
            assertEquals(false, viewModel.uiState.value.isNavigating)
        }

    @Test
    fun `stopping navigation without a session opens none`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.setNavigating(true)
            viewModel.setNavigating(false)

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
        }

    /** Open a session on a calculated route and stop navigation, leaving the grace running. */
    private fun TestScope.stoppedSession() {
        viewModel.openSession()
        calculateAndAwaitSuccess()
        viewModel.setNavigating(true)
        viewModel.openSession()
        viewModel.setNavigating(false)
        assertEquals(RouteSessionState.STOPPED, viewModel.sessionState.value)
        assertNotNull(viewModel.routeResultFlow.value)
    }

    @Test
    fun `grace expiry ends the session and clears the route`() =
        runTest(mainDispatcherRule.dispatcher) {
            stoppedSession()

            advanceTimeBy(RoutePanelViewModel.GRACE_PERIOD_MS - 1)
            assertEquals(RouteSessionState.STOPPED, viewModel.sessionState.value)
            assertNotNull(viewModel.routeResultFlow.value)

            advanceTimeBy(1)
            advanceUntilIdle()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertNull(viewModel.routeResultFlow.value)
            assertNull(viewModel.uiState.value.routeEntry)
        }

    @Test
    fun `restarting navigation inside the grace keeps the route and cancels expiry`() =
        runTest(mainDispatcherRule.dispatcher) {
            stoppedSession()

            viewModel.onNavigationRestarted()
            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(true, viewModel.uiState.value.isNavigating)
            assertNotNull(viewModel.routeResultFlow.value)

            advanceTimeBy(RoutePanelViewModel.GRACE_PERIOD_MS * 2)
            advanceUntilIdle()

            // The cancelled deadline never fires: the route is still the one the
            // navigation is driving on.
            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertNotNull(viewModel.routeResultFlow.value)
        }

    @Test
    fun `ending from the stopped state clears the route immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            stoppedSession()

            viewModel.endSession()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertNull(viewModel.routeResultFlow.value)

            // The cancelled deadline must not end a session twice or clear a later route.
            calculateAndAwaitSuccess()
            advanceTimeBy(RoutePanelViewModel.GRACE_PERIOD_MS * 2)
            advanceUntilIdle()
            assertNotNull(viewModel.routeResultFlow.value)
        }

    @Test
    fun `a stopped session is not persisted across a state-owner recreation`() =
        runTest(mainDispatcherRule.dispatcher) {
            stoppedSession()

            // Process death: a fresh owner (the same dependencies, new instance) starts
            // from the empty state (spec: `route-planning-session` — Grace state does
            // not survive process death).
            val recreated = RoutePanelViewModel(
                client = client,
                favoriteRepository = FavoriteRepository(client),
                searchHistoryRepository = SearchHistoryRepository(context),
                locationService = LocationService(context),
                context = context
            )

            assertEquals(RouteSessionState.INACTIVE, recreated.sessionState.value)
            assertNull(recreated.routeResultFlow.value)
            assertNull(recreated.uiState.value.routeEntry)
            assertEquals(RouteState.Idle, recreated.uiState.value.routeState)
            assertTrue(recreated.routeVisible.value)
        }

    @Test
    fun `grace expiry while nothing is on screen writes only the session end`() =
        runTest(mainDispatcherRule.dispatcher) {
            // No surface is composed in this test: the expiry must run entirely in the
            // state owner, ending the session once — a second clear request or a
            // lingering overlay flag would be a UI write from a background tick.
            stoppedSession()
            val signalBefore = viewModel.clearRouteSignal.value
            viewModel.analyseStep(0)

            advanceTimeBy(RoutePanelViewModel.GRACE_PERIOD_MS)
            advanceUntilIdle()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertEquals(signalBefore + 1, viewModel.clearRouteSignal.value)
            assertNull(viewModel.uiState.value.analysedStepIndex)
        }
}
