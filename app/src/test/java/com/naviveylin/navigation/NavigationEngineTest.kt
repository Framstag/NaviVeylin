package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * The engine's native-listener plumbing (spec: `navigation-controller` —
 * Navigation state exposed as StateFlow): every callback lands in the shared
 * state, the road-info throttle holds its 2000 ms window, and the stale-speed
 * ticker zeroes a speed whose fix has gone stale (spec: gps-speed-priority).
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for
 * the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var engine: NavigationEngine

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient().apply {
            routeToDeliver = RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
                distance = 5000.0
            }
        }
        engine = NavigationEngine({ client }, LocationService(context), context)
    }

    private fun awaitState(condition: () -> Boolean, pump: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            pump()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
    }

    private fun startNavigating(pump: () -> Unit = {}) {
        engine.start(client.routeToDeliver!!, Vehicle.CAR)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && client.navigationListener == null) {
            pump()
            Thread.sleep(10)
        }
        assertNotNull("the native listener is captured on start", client.navigationListener)
    }

    private fun position(
        lat: Double = 52.5200,
        lon: Double = 13.4050,
        state: com.framstag.libosmscout.client.NavigationState =
            com.framstag.libosmscout.client.NavigationState.OnRoute,
        wayName: String = "",
        wayRef: String = "",
        wayType: String = "",
        bearing: Double = 90.0
    ) = NavigationPosition(state, lat, lon, bearing, 5.0, wayName, wayRef, wayType)

    @Test
    fun listenerCallbacksDriveTheSharedState() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating { advanceUntilIdle() }
        val listener = client.navigationListener!!

        listener.onPositionEstimate(position(lat = 52.5210, lon = 13.4060))
        listener.onLaneUpdate(
            true, 3, true, 0, 1, "", arrayOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON, LaneTurn.RIGHT)
        )
        val instruction =
            RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left")
        listener.onRouteInstructions(arrayOf(instruction))
        listener.onNextRouteInstruction(instruction)
        listener.onArrivalEstimate(1234L, 4200.0)
        listener.onCurrentSpeed(72.0)
        listener.onMaxAllowedSpeed(100.0)
        advanceUntilIdle()

        val state = engine.state.value
        assertEquals(52.5210, state.position!!.lat, 1e-9)
        assertEquals(52.5210, engine.positionFlow.value!!.lat, 1e-9)
        assertTrue("lane guidance mirrored", state.laneSuggested && state.laneCount == 3)
        assertEquals("instructions mirrored", 1, state.instructions.size)
        assertEquals(instruction.description, state.nextInstruction!!.description)
        assertEquals(1234L, state.etaMillis)
        assertEquals(4200.0, state.remainingDistance, 1e-9)
        assertEquals(72.0, state.currentSpeedKmH, 1e-9)
        assertEquals(100.0, state.maxSpeedKmH, 1e-9)
    }

    @Test
    fun onRouteWithWayInfoSetsRoadInfoWithoutLookup() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating { advanceUntilIdle() }
        client.navigationListener!!.onPositionEstimate(
            position(wayName = "Hauptstrasse", wayRef = "B 1", wayType = "highway_primary")
        )
        advanceUntilIdle()

        val info = engine.state.value.currentRoadInfo
        assertEquals("B 1", info?.ref)
        assertEquals("highway_primary", info?.typeName)
        assertEquals("Hauptstrasse", info?.name)
        assertTrue("no area lookup with a resolved way", client.roadAtLookupCalls.isEmpty())
    }

    @Test
    fun roadInfoThrottleHoldsItsWindow() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating { advanceUntilIdle() }
        val listener = client.navigationListener!!
        client.roadAt = null

        // Off route → the throttled bearing-aware lookup runs (nothing on route).
        listener.onPositionEstimate(position(lat = 52.5210, lon = 13.4060, state =
            com.framstag.libosmscout.client.NavigationState.OffRoute))
        // The lookup runs on Dispatchers.IO (real thread): poll briefly for it.
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && client.roadAtLookupCalls.isEmpty()) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        assertEquals("first lookup runs", 1, client.roadAtLookupCalls.size)

        // A second estimate ~1 km away inside the 2000 ms window must not look up.
        listener.onPositionEstimate(position(lat = 52.5310, lon = 13.4160, state =
            com.framstag.libosmscout.client.NavigationState.OffRoute))
        advanceUntilIdle()
        assertEquals("second estimate throttled", 1, client.roadAtLookupCalls.size)
    }

    @Test
    fun staleSpeedTickerZeroesAStaleSpeed() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating { advanceUntilIdle() }
        client.navigationListener!!.onCurrentSpeed(64.0)
        advanceUntilIdle()
        assertEquals(64.0, engine.state.value.currentSpeedKmH, 1e-9)

        // A fix far older than the staleness window: the displayed speed decays to 0.
        engine.processLocation(
            52.5200, 13.4050, 17.8, 5.0, System.currentTimeMillis() - 60_000
        )
        awaitState({ engine.state.value.currentSpeedKmH == 0.0 }) { Thread.sleep(50) }
        assertEquals(0.0, engine.state.value.currentSpeedKmH, 1e-9)
    }

    @Test
    fun stopClearsPositionFlowAndState() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating { advanceUntilIdle() }
        client.navigationListener!!.onPositionEstimate(position())
        advanceUntilIdle()
        assertNotNull(engine.positionFlow.value)

        engine.stopNavigation()
        advanceUntilIdle()

        assertNull("the position stream is cleared with the session", engine.positionFlow.value)
        assertTrue("no navigation state survives the stop", !engine.state.value.isNavigating)
        assertNull(engine.state.value.position)
    }

    // ── route distance (pure geometry, moved from the old phone-VM test) ──

    @Test
    fun computeRouteDistance_emptyArrays() {
        assertEquals(0.0, NavigationEngine.computeRouteDistance(DoubleArray(0), DoubleArray(0)), 1e-9)
    }

    @Test
    fun computeRouteDistance_singlePoint() {
        assertEquals(
            0.0,
            NavigationEngine.computeRouteDistance(doubleArrayOf(52.52), doubleArrayOf(13.405)),
            1e-9
        )
    }

    @Test
    fun computeRouteDistance_twoPoints() {
        val distance = NavigationEngine.computeRouteDistance(
            doubleArrayOf(52.5200, 52.5300), doubleArrayOf(13.4050, 13.4050)
        )
        assertEquals("one degree of latitude step is ~1113 m", 1113.0, distance, 5.0)
    }

    @Test
    fun computeRouteDistance_threePoints() {
        val distance = NavigationEngine.computeRouteDistance(
            doubleArrayOf(52.5200, 52.5250, 52.5300), doubleArrayOf(13.4050, 13.4100, 13.4150)
        )
        assertTrue("multi-segment distance accumulates", distance > 1000.0)
    }

    @Test
    fun computeRouteDistance_zeroDistance() {
        val distance = NavigationEngine.computeRouteDistance(
            doubleArrayOf(52.5200, 52.5200), doubleArrayOf(13.4050, 13.4050)
        )
        assertEquals(0.0, distance, 1e-6)
    }

    @Test
    fun computeRouteDistance_knownDistance() {
        // ~11.13 km of latitude (0.1°) at a constant longitude.
        val distance = NavigationEngine.computeRouteDistance(
            doubleArrayOf(52.5000, 52.6000), doubleArrayOf(13.4000, 13.4000)
        )
        assertEquals(11130.0, distance, 30.0)
    }

    @Test
    fun turnTypesRemainSelectableFromInstructions() {
        // Guards the instruction plumbing used by the step-index logic.
        val instruction =
            RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left")
        assertEquals(TurnType.LEFT, instruction.turnType)
    }
}
