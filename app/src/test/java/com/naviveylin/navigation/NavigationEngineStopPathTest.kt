package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.service.NavigationNotificationService
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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

    private fun engine(): NavigationEngine = NavigationEngine({ client }, LocationService(context), context)

    /** Pump Robolectric's paused main looper until [condition] holds or timeout. */
    private fun awaitState(condition: () -> Boolean, pump: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            pump()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
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
        awaitState({ engine.state.value.isNavigating }, { advanceUntilIdle() })
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
            awaitState({ engine.state.value.isNavigating }, { advanceUntilIdle() })
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

    @Test
    fun stopRequestStopsTheEngine() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engine()
        val surface = NavigationViewModel(engine)
        val followModeChanges = mutableListOf<Boolean>()
        surface.setFollowModeCallback { followModeChanges += it }
        val panel = routePanel()
        surface.setRoutePanelViewModel(panel)
        surface.start(client.routeToDeliver!!, Vehicle.CAR)
        awaitState({ engine.state.value.isNavigating }, { advanceUntilIdle() })

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP_NAVIGATION, engine
        )
        advanceUntilIdle()

        assertTrue("follow mode was enabled on start and disabled on stop",
            followModeChanges == listOf(true, false))
    }
}
