package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.NavigationState
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.test.engineUnderTest
import com.naviveylin.ui.route.RoutePanelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * One engine, two surfaces in one process (spec: `navigation-engine` — Exactly one
 * navigation engine per process, One navigation state shared by all surfaces,
 * Per-surface view state never moves into the engine).
 *
 * The phone surface is the real adapter; the car surface is modelled the way the
 * car app observes the engine (`AutoEntryPoint.navigationViewModel()` → state and
 * position flow), without a car-screen harness.
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for
 * the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineTwoSurfaceTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var engine: NavigationEngine

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * The engine's off-main work runs on this pair (production uses real pools), so a case drains
     * it instead of polling (spec: `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "maps/search_history.json").delete()
        client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        locationService = LocationService(context)
        engine = engineUnderTest(
            { client },
            locationService,
            context,
            dispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)
        )
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    private fun routePanel(): RoutePanelViewModel =
        RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            context = context
        ).apply { defaultDispatcher = mainDispatcherRule.dispatcher }

    /**
     * Await [condition] by draining the injected schedulers and the looper — bounded and free of
     * wall-clock time (spec: `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private fun awaitState(what: String, condition: () -> Boolean) {
        repeat(20) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
        }
        throw AssertionError(
            "$what: not met after draining the test scheduler " +
                "(navigating=${engine.state.value.isNavigating}, " +
                "position=${engine.positionFlow.value != null}, " +
                "engineError=${engine.state.value.errorMessage})"
        )
    }

    private fun awaitState(condition: () -> Boolean) = awaitState("state condition", condition)

    @Test
    fun oneNativeControllerForTwoSurfacesAndSharedState() =
        runTest(mainDispatcherRule.dispatcher) {
            val phone = NavigationViewModel(engine)
            val panel = routePanel()
            val phoneFollow = mutableListOf<Boolean>()
            phone.setFollowModeCallback { phoneFollow += it }
            phone.setRoutePanelViewModel(panel)

            // The car surface observes the same engine (as :auto does).
            val carStates = mutableListOf<NavigationState>()
            val carPositions = mutableListOf<NavigationPosition?>()
            // A surface collector observes every emission synchronously (unconfined), so an
            // emission cannot be lost to a scheduler round trip; the drains in `awaitState` are
            // then the only thing a case waits on.
            backgroundScope.launch(Dispatchers.Unconfined) { engine.state.collect { carStates += it } }
            backgroundScope.launch(Dispatchers.Unconfined) {
                engine.positionFlow.collect { carPositions += it }
            }

            // Phone starts the session.
            phone.start(route(), Vehicle.CAR)
            awaitState { engine.state.value.isNavigating }
            awaitState { carStates.any { it.isNavigating } }

            assertEquals("exactly one native controller", 1, client.navigationStartCount)

            // Both surfaces see the same emissions.
            client.navigationListener!!.onPositionEstimate(
                NavigationPosition(
                    com.framstag.libosmscout.client.NavigationState.OnRoute,
                    52.5210, 13.4060, 90.0, 5.0, "Hauptstrasse", "", "highway_primary"
                )
            )
            awaitState { carPositions.filterNotNull().isNotEmpty() }
            assertEquals(
                "the car follows the same position stream as the engine state",
                engine.state.value.position!!.lat,
                carPositions.filterNotNull().last().lat,
                1e-9
            )
            assertEquals(
                "the car observes the same navigating state",
                engine.state.value.isNavigating,
                carStates.last().isNavigating
            )
            assertEquals("the phone started the session, so it follows",
                listOf(true), phoneFollow)

            // Stop from the other surface ends it for both.
            engine.stopNavigation()
            awaitState { !engine.state.value.isNavigating }
            assertFalse("the phone left navigation too", panel.uiState.value.isNavigating)
            assertEquals("the phone released follow mode on the shared stop",
                listOf(true, false), phoneFollow)
        }

    @Test
    fun carInitiatedSessionDoesNotImposeViewStateOnThePhone() =
        runTest(mainDispatcherRule.dispatcher) {
            val phone = NavigationViewModel(engine)
            val panel = routePanel()
            val phoneFollow = mutableListOf<Boolean>()
            phone.setFollowModeCallback { phoneFollow += it }
            phone.setRoutePanelViewModel(panel)
            advanceUntilIdle()

            // Car-only start (deep link): the engine acquires on its own. The
            // navigation gate needs the precise grant.
            org.robolectric.Shadows.shadowOf(
                ApplicationProvider.getApplicationContext<android.app.Application>()
            ).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
            val fix = com.naviveylin.location.GpsFix(
                lat = 52.5200, lon = 13.4050, accuracy = 5.0, speedKmH = Double.NaN,
                smoothedBearing = Double.NaN, markerBearing = Double.NaN,
                time = System.currentTimeMillis()
            )
            val locationField = LocationService::class.java.getDeclaredField("_location")
            locationField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (locationField.get(locationService) as kotlinx.coroutines.flow.MutableStateFlow<com.naviveylin.location.GpsFix?>)
                .value = fix
            engine.navigateTo(52.5300, 13.4100, "Ziel")
            awaitState { engine.state.value.isNavigating }

            assertTrue(
                "the phone's follow mode is not imposed by a car-initiated session",
                phoneFollow.isEmpty()
            )
            assertTrue(
                "the phone presents the phone surface's own session state",
                engine.state.value.isNavigating && panel.uiState.value.isNavigating
            )
            assertEquals("one controller for the car-only session",
                1, client.navigationStartCount)
        }
}
