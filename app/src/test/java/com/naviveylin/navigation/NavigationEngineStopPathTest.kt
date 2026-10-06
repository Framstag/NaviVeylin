package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.service.NavigationNotificationService
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import com.naviveylin.test.engineUnderTest

/**
 * The stop path of the process-scoped engine and its phone surface (spec:
 * `navigation-controller` — Stop navigation; `navigation-ongoing-notification` —
 * "Stop action for navigation").
 *
 * The notification's stop action and the car host's end action reach the same
 * engine stop without passing the Compose stop buttons, so these must hold rather
 * than the screen:
 *  - the engine releases its controller and leaves the navigating state, and
 *  - the phone surface clears the route panel the same way the in-app button does
 *    (panel non-navigating, route hidden) — otherwise the phone keeps showing the
 *    route after a stop from the shade (`TODO.md` §46).
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for
 * the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineStopPathTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * The engine's off-main work runs on this pair, so a case drains it instead of polling (spec:
     * `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
            }
        }
    }

    private fun routePanel(): RoutePanelViewModel =
        RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        ).apply { defaultDispatcher = mainDispatcherRule.dispatcher }

    private fun engine(): NavigationEngine = engineUnderTest(
        { client },
        LocationService(context),
        context,
        dispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)
    )

    /** Drain the injected schedulers, then assert — no wall-clock deadline. */
    private fun awaitState(condition: () -> Boolean) {
        computeDispatcher.scheduler.advanceUntilIdle()
        ioDispatcher.scheduler.advanceUntilIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    /**
     * Run the schedulers' currently due tasks without advancing virtual time, so a pending grace
     * deadline stays pending while the collectors catch up (spec: `route-planning-session` — the
     * stopped state lasts until its bounded grace ends, not until the test drains the scheduler).
     */
    private fun awaitDue(condition: () -> Boolean) {
        repeat(20) {
            if (condition()) return
            computeDispatcher.scheduler.runCurrent()
            ioDispatcher.scheduler.runCurrent()
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
        }
        if (!condition()) {
            throw AssertionError("State condition not met after running the due tasks")
        }
    }

    @Test
    fun stopNavigationClearsTheRoutePanel() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engine()
        val surface = NavigationViewModel(engine)
        val panel = routePanel()
        surface.setRoutePanelViewModel(panel)
        advanceUntilIdle()

        // The phone starts a session, so its surface owns the follow mode and the
        // panel follows the session.
        surface.start(client.routeToDeliver!!, Vehicle.CAR)
        awaitState({ engine.state.value.isNavigating })
        assertTrue("the panel follows the running session", panel.uiState.value.isNavigating)

        engine.stopNavigation()
        advanceUntilIdle()

        assertFalse("the panel is no longer navigating", panel.uiState.value.isNavigating)
        assertFalse("the route is hidden again", panel.routeVisible.value)
    }

    @Test
    fun notificationStopEndsNavigationAndClearsThePhoneView() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = engine()
            val surface = NavigationViewModel(engine)
            val panel = routePanel()
            surface.setRoutePanelViewModel(panel)
            surface.start(client.routeToDeliver!!, Vehicle.CAR)
            awaitState({ engine.state.value.isNavigating })
            assertTrue("navigation is active before the stop", engine.state.value.isNavigating)
            assertTrue("the panel follows the session", panel.uiState.value.isNavigating)

            // The shade's stop action: the service routes it into the one engine.
            NavigationNotificationService.handleAction(
                NavigationNotificationService.ACTION_STOP_NAVIGATION, engine
            )
            advanceUntilIdle()

            assertFalse("navigation ended", engine.state.value.isNavigating)
            assertFalse("the panel left navigation mode", panel.uiState.value.isNavigating)
            assertFalse("the drawn route is cleared", panel.routeVisible.value)
            // With navigation gone and no free driving, the notification controller
            // stops the foreground service — the notification withdraws.
            val notificationController = NavigationNotificationController(
                context, engine, DrivingModeProviderImpl()
            )
            assertFalse(
                "no driving state left to keep the notification",
                notificationController.shouldRun(engine.state.value, freeDriving = false)
            )
            notificationController.dispose()
        }

    /**
     * A stop with an open session keeps the route: the session owns it for its stopped state and
     * its grace window, so the adapter must not hide it (spec: `route-planning-session` — Grace
     * period after navigation is stopped). The no-session counterpart is
     * [stopNavigationClearsTheRoutePanel].
     */
    @Test
    fun adapterKeepsTheRouteWhileTheSessionIsStopped() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engine()
        val surface = NavigationViewModel(engine)
        val panel = routePanel()
        surface.setRoutePanelViewModel(panel)
        surface.start(client.routeToDeliver!!, Vehicle.CAR)
        awaitState({ engine.state.value.isNavigating })
        panel.openSession()
        awaitDue { panel.sessionState.value == RouteSessionState.REVIEWING }

        engine.stopNavigation()
        awaitDue { panel.sessionState.value == RouteSessionState.STOPPED }

        assertEquals(
            "the open session entered its stopped state",
            RouteSessionState.STOPPED, panel.sessionState.value
        )
        assertTrue("the stopped state keeps the route drawn", panel.routeVisible.value)
        assertFalse("navigation ended", engine.state.value.isNavigating)
        assertFalse("the panel left navigation mode", panel.uiState.value.isNavigating)
    }

    /**
     * Restart from the stopped state resumes on the route the session holds, without routing again
     * (spec: `route-planning-session` — Restart resumes navigation: "the route SHALL NOT need
     * recalculation").
     */
    @Test
    fun restartingFromTheStoppedStateResumesWithoutARecalculation() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = engine()
            val surface = NavigationViewModel(engine)
            val panel = routePanel()
            surface.setRoutePanelViewModel(panel)
            surface.start(client.routeToDeliver!!, Vehicle.CAR)
            awaitState({ engine.state.value.isNavigating })
            panel.openSession()
            awaitDue { panel.sessionState.value == RouteSessionState.REVIEWING }
            engine.stopNavigation()
            awaitDue { panel.sessionState.value == RouteSessionState.STOPPED }

            // The screen's Restart callback: start navigation on the session's own route.
            surface.start(client.routeToDeliver!!, Vehicle.CAR)
            panel.onNavigationRestarted()
            awaitState({ engine.state.value.isNavigating })

            assertNull("restart does not calculate a route", engine.state.value.calculation)
            assertEquals(RouteSessionState.INACTIVE, panel.sessionState.value)
            assertTrue("the route stays drawn for the resumed navigation", panel.routeVisible.value)
        }

    @Test
    fun stopRequestStopsTheEngine() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engine()
        val surface = NavigationViewModel(engine)
        val followModeChanges = mutableListOf<Boolean>()
        surface.setFollowModeCallback { followModeChanges += it }
        val panel = routePanel()
        surface.setRoutePanelViewModel(panel)
        surface.start(client.routeToDeliver!!, Vehicle.CAR)
        awaitState({ engine.state.value.isNavigating })

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP_NAVIGATION, engine
        )
        advanceUntilIdle()

        assertTrue("follow mode was enabled on start and disabled on stop",
            followModeChanges == listOf(true, false))
    }
}
