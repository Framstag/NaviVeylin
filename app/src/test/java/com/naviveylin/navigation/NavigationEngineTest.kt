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
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.core.SpeedStaleness
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
import com.naviveylin.test.engineUnderTest

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

    /**
     * The engine's injected time source: a case moves [nowMillis] instead of waiting for real time
     * (spec: `unit-test-suite-runtime` — Injected time instead of a real clock).
     */
    /**
     * Test hook invoked inside the injected time source, i.e. after the tick has decided to look at
     * the state. It is the deterministic window in which another publisher can write — no thread and
     * no sleep is involved (spec: `navigation-engine` — A background writer cannot revert a
     * concurrent publication).
     */
    private var clockGate: (() -> Unit)? = null

    private var nowMillis = 1_000_000L
    private val clock = EngineTimeSource {
        clockGate?.invoke()
        nowMillis
    }

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
        engine = engineUnderTest(
            { client },
            LocationService(context),
            context,
            timeSource = clock,
            dispatchers = EngineDispatchers(
                compute = mainDispatcherRule.dispatcher,
                io = mainDispatcherRule.dispatcher
            )
        )
    }

    /**
     * Start a session as a surface would, then drain the test scheduler: the listener capture and
     * the first state publication are main-confined work the scheduler owns (spec:
     * `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private fun startNavigating() {
        engine.start(client.routeToDeliver!!, Vehicle.CAR)
        drainScheduler()
        assertNotNull("the native listener is captured on start", client.navigationListener)
    }

    private fun drainScheduler() {
        repeat(2) { mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle() }
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
        startNavigating()
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

        // The recorded failure mode of this case: a background tick that fires after these
        // publications must not revert any of them (spec: `navigation-engine` — A field published
        // during a background tick is not lost). Driven through the injected clock, so the tick is
        // stale by construction instead of stale by host load.
        nowMillis += SpeedStaleness.STALE_SPEED_MS + 1
        engine.tickStaleness()

        val state = engine.state.value
        assertEquals(52.5210, state.position!!.lat, 1e-9)
        assertEquals(52.5210, engine.positionFlow.value!!.lat, 1e-9)
        assertTrue("lane guidance mirrored", state.laneSuggested && state.laneCount == 3)
        assertEquals("instructions mirrored", 1, state.instructions.size)
        assertEquals(instruction.description, state.nextInstruction!!.description)
        assertEquals(1234L, state.etaMillis)
        assertEquals(4200.0, state.remainingDistance, 1e-9)
        assertEquals(100.0, state.maxSpeedKmH, 1e-9)
        // Only the stale-speed field is the tick's business.
        assertEquals(0.0, state.currentSpeedKmH, 1e-9)
    }

    @Test
    fun routeWithoutGeometryStartsNavigationWithoutPublishingGeometry() =
        runTest(mainDispatcherRule.dispatcher) {
            // The bridge hands geometry over as platform types, so a route can arrive without a
            // polyline: navigation still starts, and the shared state publishes no geometry
            // (spec: `navigation-engine` — Acquisition without usable polyline geometry).
            val geometryLess = RouteEntry().apply {
                routeHandle = 1L
                distance = 5000.0
            }

            engine.start(geometryLess, Vehicle.CAR)
            advanceUntilIdle()

            assertTrue(engine.state.value.isNavigating)
            assertNull(engine.state.value.routeLats)
            assertNull(engine.state.value.routeLons)
            assertEquals(0.0, engine.state.value.totalDistance, 1e-9)
        }

    @Test
    fun onRouteWithWayInfoSetsRoadInfoWithoutLookup() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating()
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
        startNavigating()
        val listener = client.navigationListener!!
        client.roadAt = null

        // Off route → the throttled bearing-aware lookup runs (nothing on route).
        listener.onPositionEstimate(position(lat = 52.5210, lon = 13.4060, state =
            com.framstag.libosmscout.client.NavigationState.OffRoute))
        advanceUntilIdle()
        assertEquals("first lookup runs", 1, client.roadAtLookupCalls.size)

        // A second estimate ~1 km away inside the 2000 ms window must not look up.
        listener.onPositionEstimate(position(lat = 52.5310, lon = 13.4160, state =
            com.framstag.libosmscout.client.NavigationState.OffRoute))
        advanceUntilIdle()
        assertEquals("second estimate throttled", 1, client.roadAtLookupCalls.size)

        // One millisecond past the window, the next estimate looks up again — the clock is moved,
        // not waited out (spec: `unit-test-suite-runtime` — Injected time instead of a real clock).
        nowMillis += NavigationEngine.ROAD_INFO_THROTTLE_MS + 1
        listener.onPositionEstimate(position(lat = 52.5410, lon = 13.4260, state =
            com.framstag.libosmscout.client.NavigationState.OffRoute))
        advanceUntilIdle()
        assertEquals("past the window the lookup runs again", 2, client.roadAtLookupCalls.size)
    }

    @Test
    fun staleSpeedTickerZeroesAStaleSpeed() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating()
        client.navigationListener!!.onCurrentSpeed(64.0)
        advanceUntilIdle()
        assertEquals(64.0, engine.state.value.currentSpeedKmH, 1e-9)

        // A fix far older than the staleness window: the displayed speed decays to 0 on the next
        // tick — driven through the injected clock, never by waiting for a real second.
        engine.processLocation(52.5200, 13.4050, 17.8, 5.0, nowMillis - 60_000)
        nowMillis += SpeedStaleness.STALE_SPEED_MS + 1
        engine.tickStaleness()

        assertEquals(0.0, engine.state.value.currentSpeedKmH, 1e-9)
    }

    @Test
    fun aStaleSpeedTickDoesNotRevertAConcurrentPublication() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating()
        client.navigationListener!!.onMaxAllowedSpeed(100.0)
        advanceUntilIdle()
        assertEquals(100.0, engine.state.value.maxSpeedKmH, 1e-9)

        // Another publisher writes while the tick is about to apply its own change (the hook runs
        // inside the tick's read of its time source). The recorded failure mode is exactly this: a
        // tick that writes back a snapshot it read earlier reverts the other writer's field
        // (spec: `navigation-engine` — A background writer cannot revert a concurrent publication).
        clockGate = { engine.reportError("concurrent publish", SurfaceOrigin.CAR) }
        nowMillis += SpeedStaleness.STALE_SPEED_MS + 1
        engine.tickStaleness()
        clockGate = null

        assertEquals("the tick's own field decays", 0.0, engine.state.value.currentSpeedKmH, 1e-9)
        assertEquals(
            "the concurrent publication survives the tick",
            "concurrent publish",
            engine.state.value.errorMessage
        )
        assertEquals("the recorded victim field survives the tick", 100.0, engine.state.value.maxSpeedKmH, 1e-9)
    }

    @Test
    fun theStaleSpeedTickerDecaysOnlyPastTheWindow() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating()
        client.navigationListener!!.onCurrentSpeed(64.0)
        advanceUntilIdle()

        // The production loop, run on the test scheduler so virtual time drives it.
        val ticker = launch { engine.runStaleSpeedTicker() }
        try {
            advanceTimeBy(NavigationEngine.SPEED_STALE_TICK_MS)
            runCurrent()
            assertEquals(
                "a tick inside the staleness window leaves the displayed speed alone",
                64.0,
                engine.state.value.currentSpeedKmH,
                1e-9
            )

            // Move the fix past the window: the next tick decays the displayed speed.
            nowMillis += SpeedStaleness.STALE_SPEED_MS + 1
            advanceTimeBy(NavigationEngine.SPEED_STALE_TICK_MS)
            runCurrent()
            assertEquals(0.0, engine.state.value.currentSpeedKmH, 1e-9)
        } finally {
            ticker.cancel()
        }
    }

    @Test
    fun stopClearsPositionFlowAndState() = runTest(mainDispatcherRule.dispatcher) {
        startNavigating()
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

    // ── the total navigation publishes (spec: osmscout-jni — One route length for a calculated route) ──

    @Test
    fun routeTotalDistance_usesTheRouteOwnLengthNotASecondPolylineSum() {
        // Legs 48 000 + 49 416 against a native total of 72 771: the route's own length wins, so the
        // progress denominator is the number the card and the step list show (TODO.md 129/139).
        val route = RouteEntry().apply {
            distance = 72_771.0
            instructionDistances = doubleArrayOf(48_000.0, 49_416.0)
            instructionTimes = doubleArrayOf(2_000.0, 2_100.0)
            descriptions = arrayOf("--- Route ---", "Turn 1  []", "Destination: Koeln")
        }

        assertEquals(
            97_416.0,
            NavigationEngine.routeTotalDistanceMeters(
                route, doubleArrayOf(51.5177, 50.9430), doubleArrayOf(7.4592, 6.9580)
            ),
            1e-6
        )
    }

    @Test
    fun routeTotalDistance_withoutUsableGeometryStaysZero() {
        // The documented rule for a geometry-less route survives even when the bridge handed over a
        // length (spec: navigation-engine — Acquisition without usable polyline geometry).
        val route = RouteEntry().apply {
            distance = 5000.0
            instructionDistances = doubleArrayOf(0.0, 5000.0)
            descriptions = arrayOf("--- Route ---", "Turn 1  []")
        }

        assertEquals(0.0, NavigationEngine.routeTotalDistanceMeters(route, null, null), 1e-9)
        assertEquals(
            0.0,
            NavigationEngine.routeTotalDistanceMeters(route, doubleArrayOf(52.52), doubleArrayOf(13.405)),
            1e-9
        )
    }

    @Test
    fun routeTotalDistance_fallsBackToThePolylineWhenTheRouteCarriesNoLength() {
        // A route with geometry but neither per-step values nor a native distance: the polyline sum is
        // still the better answer than 0, which is what this replaces.
        val route = RouteEntry().apply { distance = 0.0 }
        val lats = doubleArrayOf(52.5200, 52.5300)
        val lons = doubleArrayOf(13.4050, 13.4050)

        assertEquals(
            NavigationEngine.computeRouteDistance(lats, lons),
            NavigationEngine.routeTotalDistanceMeters(route, lats, lons),
            1e-6
        )
        assertTrue(NavigationEngine.routeTotalDistanceMeters(route, lats, lons) > 1000.0)
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
