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
import com.naviveylin.core.DiagnosticsLog
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
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

    /**
     * A geometry-less adoption writes a `ROUTE` diagnostics line; the buffer is process-global, so
     * it is emptied here rather than leaking into a sibling class's assertion.
     */
    @After
    fun tearDown() {
        DiagnosticsLog.reset()
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

    private fun TestScope.calculateAndAwaitSuccess(): Unit {
        viewModel.updateLocationsForReroute(entry("start"), entry("dest"))
        client.routeToDeliver = routeEntry()
        viewModel.calculateRoute()
        advanceUntilIdle()
        assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
    }

    /**
     * A route the bridge hands over without geometry: the arrays are platform types and may be
     * absent rather than empty (spec: `route-map-overview` — Degenerate route geometry degrades
     * safely: absent counts as an empty polyline).
     */
    private fun geometryLessRouteEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        distance = 5000.0
        descriptions = arrayOf("Start: A  [0.0 km]", "Left onto B  [1.2 km]")
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
    fun `adopting a route without geometry adopts the session and publishes no geometry`() =
        runTest(mainDispatcherRule.dispatcher) {
            // The engine can hand the panel a route whose polyline arrays are absent (car-only
            // adopt / reroute re-acquisition): the adopt path must not throw, and it publishes no
            // geometry (spec: `route-map-overview` — Adoption of a route without geometry keeps the
            // session; spec: `navigation-engine` — Acquisition without usable polyline geometry).
            viewModel.adoptRoute(geometryLessRouteEntry(), com.framstag.libosmscout.client.Vehicle.CAR)

            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertNull(viewModel.routeResultFlow.value)
            assertTrue(viewModel.routeVisible.value)
        }

    @Test
    fun `an adopted route without geometry keeps the geometry already drawn`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.adoptRoute(routeEntry(), com.framstag.libosmscout.client.Vehicle.CAR)
            val drawn = viewModel.routeResultFlow.value
            assertNotNull(drawn)

            viewModel.adoptRoute(geometryLessRouteEntry(), com.framstag.libosmscout.client.Vehicle.CAR)

            assertSame(
                "the map keeps the geometry it displayed (spec: route-map-overview)",
                drawn,
                viewModel.routeResultFlow.value
            )
        }

    @Test
    fun `a calculation without geometry publishes the endpoint fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            // A calculated route without coordinates still completes and publishes a result: the
            // polyline is empty and the requested endpoints carry the camera fallback
            // (spec: `route-map-overview` — Empty polyline falls back to endpoints). Nothing on the
            // adopt path publishes a route that has no geometry at all.
            viewModel.updateLocationsForReroute(entry("start", 51.5, 7.4), entry("dest", 52.5, 8.4))
            client.routeToDeliver = geometryLessRouteEntry()
            viewModel.calculateRoute()
            advanceUntilIdle()

            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
            val result = viewModel.routeResultFlow.value
            assertNotNull(result)
            assertEquals(0, result!!.routeLats.size)
            assertEquals(51.5, result.startLat, 1e-9)
            assertEquals(52.5, result.destLat, 1e-9)
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

    /**
     * The stop entry reports whether an open session took the route over: the caller that owns the
     * map's route visibility keeps it drawn when it did and clears it when it did not
     * (spec: `route-planning-session` — Grace period after navigation is stopped; spec: `map-modes`
     * — navigation end).
     */
    @Test
    fun `a stop with an open session reports that the session took the route over`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            viewModel.setNavigating(true)
            viewModel.openSession()

            val tookTheRoute = viewModel.onNavigationStopped()

            assertTrue("an open session owns the route for its grace window", tookTheRoute)
            assertEquals(RouteSessionState.STOPPED, viewModel.sessionState.value)
            assertNotNull("the stopped state keeps the route drawn", viewModel.routeResultFlow.value)
        }

    @Test
    fun `a stop without a session reports that no session took the route over`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.setNavigating(true)

            val tookTheRoute = viewModel.onNavigationStopped()

            assertEquals(false, tookTheRoute)
            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
        }

    /**
     * Opening the session during navigation (or touching a field in it) must not wipe the route
     * navigation is running on: the review shows that route, so the panel has to keep it
     * (spec: `route-planning-session` — Reviewing a route during navigation is read-only; the
     * route belongs to navigation).
     */
    @Test
    fun `opening a session during navigation keeps the adopted route`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()
            calculateAndAwaitSuccess()
            viewModel.setNavigating(true)
            viewModel.openSession()
            val adopted = viewModel.uiState.value.routeEntry
            assertNotNull("the review reviews a route", adopted)

            viewModel.setDestLocation(entry("dest"))
            viewModel.setStartLocation(entry("start"))

            assertSame(
                "the route navigation runs on survives the session",
                adopted, viewModel.uiState.value.routeEntry
            )
            assertEquals(RouteState.Done, viewModel.uiState.value.routeState)
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

    /**
     * The session can end without the user asking for it — its grace expiring is the one path that
     * does — and the surface must close with it (spec: `route-planning-session` — Ending the session
     * removes its surface: "Grace expiry closes an open surface", "No surface survives the
     * session").
     */
    @Test
    fun `grace expiry closes the surface`() =
        runTest(mainDispatcherRule.dispatcher) {
            stoppedSession()

            advanceTimeBy(RoutePanelViewModel.GRACE_PERIOD_MS)
            advanceUntilIdle()

            assertEquals(RouteSessionState.INACTIVE, viewModel.sessionState.value)
            assertTrue(
                "a session that ended by itself closes a shown surface",
                sessionEndClosesTheSurface(viewModel.sessionState.value, panelShown = true)
            )
        }

    @Test
    fun `opening a session does not close its surface immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.openSession()

            assertEquals(RouteSessionState.EDITING, viewModel.sessionState.value)
            assertEquals(
                false,
                sessionEndClosesTheSurface(viewModel.sessionState.value, panelShown = true)
            )
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
