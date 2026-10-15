package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.NavigationState
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.TurnType
import com.framstag.libosmscout.client.Vehicle
import com.framstag.libosmscout.client.RouteInstruction
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.FailureReason
import com.naviveylin.data.SettingsStorage
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.naviveylin.test.engineUnderTest

/**
 * The engine's reroute policy (spec: `reroute-trigger` — all requirements;
 * `navigation-engine` — Route acquisition independent of a surface UI).
 *
 * One policy applies to every surface (design D3): the 30 m fast path, the
 * confirmation gate for a marginal deviation, the 25 s cooldown against a
 * cascade, the 100 m accuracy guard and the 30 s tunnel guard. A confirmed
 * reroute re-acquires with the retained vehicle profile and needs no surface —
 * the test never creates one.
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for
 * the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineRerouteTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var engine: NavigationEngine

    /**
     * The engine's settings store: the vehicle's usual maximum speed is resolved from it per
     * calculation (spec: `routing-max-speed`), so a case states the stored values it needs.
     */
    private lateinit var storage: SettingsStorage

    /**
     * The engine's injected time source and dispatchers: every coroutine the engine starts runs on
     * the test scheduler, so a case drains it instead of waiting for real threads (spec:
     * `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private var nowMillis = 1_000_000L
    private val clock = EngineTimeSource { nowMillis }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Backs the diagnostics-file assertion of the reroute latency line. */
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        locationService = LocationService(context)
        storage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        engine = engineUnderTest(
            { client },
            locationService,
            context,
            timeSource = clock,
            dispatchers = EngineDispatchers(
                compute = mainDispatcherRule.dispatcher,
                io = mainDispatcherRule.dispatcher
            ),
            settingsStorage = storage
        )
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        // A straight west-east line; an off-route point ~40 m north deviates beyond
        // the 30 m fast path, one 10 m north stays marginal.
        latitudes = doubleArrayOf(52.5200, 52.5200)
        longitudes = doubleArrayOf(13.4000, 13.5000)
        distance = 7000.0
        descriptions = arrayOf(
            "Start navigation  [0.0 km, 0 min]",
            "Destination reached  [0.0 km, 0 min]"
        )
    }

    /**
     * Wait for [condition] by draining the test scheduler — no wall-clock deadline, so a condition
     * that cannot be met fails at once instead of after five real seconds (spec:
     * `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private fun awaitState(condition: () -> Boolean) {
        advanceUntilIdleBlocking()
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    private fun advanceUntilIdleBlocking() {
        // The engine publishes on Dispatchers.Main, which the rule replaces with the test dispatcher;
        // idling Robolectric's looper keeps any work it posted ordered ahead of the assertions.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    /** Start a session as a surface would (engine.start with a calculated route). */
    private fun startSession(vehicle: Vehicle = Vehicle.BICYCLE) {
        engine.start(route(), vehicle)
        awaitState { client.navigationListener != null }
        awaitState { engine.state.value.isNavigating }
    }

    private fun listener() = client.navigationListener!!

    /**
     * Report an off-route position. A fresh, accurate fix is fed first by default,
     * because the engine's accuracy guard ignores off-route reports while no fix has
     * been seen (`lastGpsAccuracy < 0`) — as in production, where the location feed
     * runs ahead of the native reports.
     */
    private fun requestReroute(lat: Double, feedFreshFix: Boolean = true) {
        if (feedFreshFix) {
            engine.processLocation(52.5200, 13.4500, 17.8, 5.0, nowMillis)
        }
        listener().onRerouteRequest(
            lat, 13.4500, 90.0, 52.5200, 13.5000
        )
    }

    @Test
    fun largeDeviationConfirmsOnTheFastPath() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        assertEquals("a surface-acquired start calculates no route in the engine",
            0, client.routeCalculationCount)

        // ~40 m north of the route: above the 30 m fast path (and below the retired
        // 50 m threshold), first report.
        requestReroute(lat = 52.52036)
        awaitState { client.routeCalculationCount == 1 }

        assertEquals("the engine re-acquired the route itself", 1, client.routeCalculationCount)
        assertEquals(
            "the native calculation ran on the injected dispatcher (the test thread)",
            Thread.currentThread(),
            client.lastRouteCalculationThread
        )
    }

    @Test
    fun marginalDeviationNeedsTheConfirmationGate() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        // ~10 m north: below the 30 m fast path, so the first report only opens the
        // episode and a second report with no time between them does not confirm
        // either (the gate requires the 4 s minimum off-route duration).
        requestReroute(lat = 52.52009)
        assertEquals("no reroute on the first marginal report", 0, client.routeCalculationCount)

        requestReroute(lat = 52.52009)
        assertEquals(
            "no reroute before the confirmation floor elapsed",
            0, client.routeCalculationCount
        )
    }

    @Test
    fun marginalDeviationConfirmsAfterTheFourSecondFloor() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        // ~10 m north: below the 30 m fast path, so the first report only opens the episode.
        // Each report is drained before the clock moves: the engine's report handling is
        // queued on the test dispatcher, so the gate reads the clock when the report runs,
        // not when it was raised.
        requestReroute(lat = 52.52009)
        advanceUntilIdleBlocking()
        assertEquals("no reroute on the first marginal report", 0, client.routeCalculationCount)

        // The native engine keeps reporting while off-route; the device clock moves
        // between those reports and the engine's own floor is 4 s.
        nowMillis += 2_000
        requestReroute(lat = 52.52009)
        advanceUntilIdleBlocking()
        assertEquals("still inside the 4 s floor", 0, client.routeCalculationCount)

        nowMillis += 2_000
        requestReroute(lat = 52.52009)
        awaitState { client.routeCalculationCount == 1 }
        assertEquals("the floor elapsed, the engine re-acquired", 1, client.routeCalculationCount)
    }

    @Test
    fun cooldownBlocksARerouteCascade() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        requestReroute(lat = 52.5218)
        awaitState { client.routeCalculationCount == 1 }

        // The native engine reports every ~5 s while off route: within the 25 s
        // cooldown the next confirmed deviation must not start another reroute.
        requestReroute(lat = 52.5218)
        assertEquals("cascade blocked by the cooldown", 1, client.routeCalculationCount)
    }

    @Test
    fun poorAccuracyBlocksTheReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        // A fix with 150 m accuracy exceeds the 100 m guard.
        engine.processLocation(52.5200, 13.4500, 17.8, 150.0, nowMillis)
        requestReroute(lat = 52.5218, feedFreshFix = false)

        assertEquals("accuracy guard withheld the reroute", 0, client.routeCalculationCount)
    }

    @Test
    fun tunnelExitBlocksTheReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        // A tunnel estimate within the 30 s guard window.
        listener().onPositionEstimate(
            com.framstag.libosmscout.client.NavigationPosition(
                com.framstag.libosmscout.client.NavigationState.EstimateInTunnel,
                52.5200, 13.4500, 90.0, 5.0, "", "", ""
            )
        )
        awaitState { true }
        engine.processLocation(52.5200, 13.4500, 17.8, 5.0, nowMillis)
        requestReroute(lat = 52.5218)

        assertEquals("tunnel guard withheld the reroute", 0, client.routeCalculationCount)
    }

    @Test
    fun confirmedRerouteKeepsTheRetainedVehicleProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            startSession()
            assertEquals("no engine acquisition for a surface-acquired start",
                0, client.routeCalculationCount)

            requestReroute(lat = 52.5218)
            awaitState { client.routeCalculationCount == 1 }

            assertEquals(
                "the reroute re-acquired with the retained profile, no surface present",
                Vehicle.BICYCLE, client.lastRouteProfile!!.vehicle
            )
        }

    @Test
    fun rerouteUsesTheRetainedDestination() = runTest(mainDispatcherRule.dispatcher) {
        startSession()

        // The session's destination identity is the acquired route's end point.
        assertEquals("the session carries its destination",
            13.5000, engine.state.value.destLon, 1e-9)

        requestReroute(lat = 52.5218)
        awaitState { client.routeCalculationCount == 1 }

        assertEquals(
            "reroute goes to the retained destination",
            13.5000, client.lastRouteDest!!.second, 1e-9
        )
    }

    @Test
    fun instructionListUpdatesAfterAReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        listener().onRouteInstructions(
            arrayOf(RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left"))
        )
        awaitState { engine.state.value.instructions.size == 1 }

        requestReroute(lat = 52.5218)
        awaitState { client.routeCalculationCount == 1 }

        // The engine clears the previous route's steps on the restart and reports
        // the new instructions when they arrive.
        listener().onRouteInstructions(
            arrayOf(RouteInstruction(50.0, TurnType.RIGHT, "Alt St", "Turn right", "Turn right"))
        )
        awaitState { engine.state.value.instructions.size == 1 }

        assertEquals("Alt St", engine.state.value.instructions[0].streetName)
        assertFalse(engine.state.value.isRerouting)
    }

    /**
     * A failed reroute must not release the lease the running navigation holds: the engine keeps
     * feeding fixes from it, and `LocationService` stops device updates when the last lease goes
     * (spec: `navigation-engine` — A failed route attempt releases only the lease it took).
     */
    @Test
    fun aFailedRerouteKeepsTheRunningNavigationLease() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        assertTrue(
            "navigating holds the navigation lease",
            locationService.heldLeaseConsumers().contains(LocationConsumers.NAV_ENGINE)
        )

        // Hold the reroute's calculation so its failure is delivered explicitly (the shape a thin
        // map set produces on device: "No routable node near destination").
        client.holdRouteDelivery = true
        requestReroute(lat = 52.5218)
        awaitState { client.routeCalculationCount == 1 }

        client.pendingRouteCallbacks.first().onErrorDetail(
            RouteCallback.KIND_TARGET_UNROUTABLE, "No routable node near destination", Double.NaN
        )
        awaitState { engine.state.value.failure != null }

        assertTrue(
            "a failed reroute must not starve the running guidance of position updates",
            locationService.heldLeaseConsumers().contains(LocationConsumers.NAV_ENGINE)
        )
        assertTrue("the failed reroute does not end the session", engine.state.value.isNavigating)
        assertEquals(
            "the failure is still published, with its cause",
            FailureReason.TARGET_UNROUTABLE,
            engine.state.value.failure?.reason
        )
    }

    /**
     * A reroute that fails must stop claiming one is running. `onRouteInstructions` is the only
     * other writer that clears the flag, and a failed attempt never reaches an instruction list,
     * so the car template would keep mapping `isRerouting` to a loading trip with no turn (spec:
     * `rerouting-visual-feedback` — Failed reroute ends rerouting state, Off-route survives a
     * failed reroute, No surface is told a reroute is running after it ended).
     */
    @Test
    fun aFailedRerouteEndsTheReroutingState() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        listener().onRouteInstructions(
            arrayOf(RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left"))
        )
        awaitState { engine.state.value.instructions.size == 1 }

        // Hold the reroute's calculation so its failure is delivered explicitly (the shape a thin
        // map set produces on device: "No routable node near destination").
        client.holdRouteDelivery = true
        requestReroute(lat = 52.5218)
        awaitState { client.routeCalculationCount == 1 }
        assertTrue("the attempt announces itself", engine.state.value.isRerouting)

        client.pendingRouteCallbacks.first().onErrorDetail(
            RouteCallback.KIND_TARGET_UNROUTABLE, "No routable node near destination", Double.NaN
        )
        awaitState { engine.state.value.failure != null }

        assertFalse(
            "a failed reroute must not keep the surfaces on a reroute",
            engine.state.value.isRerouting
        )
        assertTrue(
            "the vehicle is still off route after the failed attempt",
            engine.state.value.isOffRoute
        )
        assertTrue("the failed reroute does not end the session", engine.state.value.isNavigating)
        assertEquals(
            "the running route's steps survive the failed attempt",
            1, engine.state.value.instructions.size
        )
        assertEquals("Main St", engine.state.value.instructions[0].streetName)
        assertEquals(
            "the failure is still published, with its cause",
            FailureReason.TARGET_UNROUTABLE,
            engine.state.value.failure?.reason
        )

        // A later emission must not resurrect the claim: the car rebuilds its template from any
        // state change, so a stale `true` would come back with the next one.
        listener().onNextRouteInstruction(
            RouteInstruction(80.0, TurnType.RIGHT, "Alt St", "Turn right", "Turn right")
        )
        awaitState { engine.state.value.nextInstruction?.streetName == "Alt St" }
        assertFalse(engine.state.value.isRerouting)
    }

    /**
     * The same rule on the other failure path: a throwable out of the calculation call itself (the
     * `catch` in `calculateAndStart`) ends the reroute claim and publishes the error, instead of
     * leaving the car on a loading trip (spec: `rerouting-visual-feedback` — Failed reroute ends
     * rerouting state; `navigation-engine` — Native failure is confined, not fatal).
     */
    @Test
    fun aThrowingRouteCallEndsTheReroutingState() = runTest(mainDispatcherRule.dispatcher) {
        startSession()
        client.routeCalculationError = IllegalStateException("native routing blew up")

        requestReroute(lat = 52.5218)
        awaitState { engine.state.value.failure != null }

        assertFalse(
            "a throwing calculation ends the reroute claim",
            engine.state.value.isRerouting
        )
        assertTrue("the session keeps running", engine.state.value.isNavigating)
        assertTrue(
            "the off-route cue stays: the vehicle is still off route",
            engine.state.value.isOffRoute
        )
        assertEquals(FailureReason.ENGINE_FAILURE, engine.state.value.failure?.reason)
        assertEquals("native routing blew up", engine.state.value.failure?.detail)
    }

    /**
     * The reroute confirmation line is numbers-only: the off-route age, the elapsed floor and
     * the deviation, with `unknown` where a value was not measured (spec: `reroute-trigger` —
     * End-to-end latency bound on either surface; `Logging.md` — no coordinates).
     */
    @Test
    fun rerouteDiagnosticLineCarriesNumbersAndNoCoordinates() {
        assertEquals(
            "reroute confirmed offroute_ms=9000 confirm_ms=4000 deviation_m=unknown",
            NavigationEngine.rerouteDiagnosticLine(
                offrouteMs = 9_000L, confirmMs = 4_000L, deviationM = null
            )
        )
        assertEquals(
            "reroute confirmed offroute_ms=unknown confirm_ms=0 deviation_m=40.0",
            NavigationEngine.rerouteDiagnosticLine(
                offrouteMs = null, confirmMs = 0L, deviationM = 40.0
            )
        )
        assertEquals(
            "a NaN deviation reads unknown, not NaN",
            "reroute confirmed offroute_ms=5000 confirm_ms=0 deviation_m=unknown",
            NavigationEngine.rerouteDiagnosticLine(
                offrouteMs = 5_000L, confirmMs = 0L, deviationM = Double.NaN
            )
        )
    }

    /**
     * A confirmed reroute writes exactly one coordinate-free measurement line (spec:
     * `reroute-trigger` — End-to-end latency bound on either surface): the off-route age comes
     * from the last `OnRoute` estimate, the confirmation floor from the gate, and the deviation
     * from the fast path.
     */
    @Test
    fun aConfirmedRerouteWritesTheLatencyDiagnosticLine() =
        runTest(mainDispatcherRule.dispatcher) {
            DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
            try {
                startSession()

                // An OnRoute estimate stamps the off-route age baseline.
                listener().onPositionEstimate(
                    NavigationPosition(
                        NavigationState.OnRoute, 52.5200, 13.4500, 90.0, 5.0, "Main St", "", ""
                    )
                )
                awaitState { engine.state.value.position?.state == NavigationState.OnRoute }

                nowMillis += 9_000
                // ~40 m north: the 30 m fast path confirms on the first report.
                requestReroute(lat = 52.52036)
                awaitState { client.routeCalculationCount == 1 }

                val line = DiagnosticsLog.readEntries().single {
                    it.contains(NavigationEngine.REROUTE_TAG)
                }
                assertTrue("the off-route age is measured", line.contains("offroute_ms=9000"))
                assertTrue(
                    "the fast-path confirmation elapsed no floor",
                    line.contains("confirm_ms=0")
                )
                val deviation = Regex("deviation_m=([0-9.]+)")
                    .find(line)?.groupValues?.get(1)?.toDouble()
                assertNotNull("the deviation is a number", deviation)
                assertTrue(
                    "the deviation is the ~40 m fast-path distance",
                    deviation!! in 35.0..45.0
                )
                assertFalse(
                    "no coordinate reaches the line",
                    line.contains("52.5") || line.contains("13.45")
                )
            } finally {
                DiagnosticsLog.reset()
            }
        }

    // --- The vehicle's usual maximum speed on every calculation path (spec: `routing-max-speed`
    // — Every calculation path carries the vehicle's maximum speed; A speed change does not restart
    // a calculation) ---

    @Test
    fun navigationEngineAcquisitionCarriesTheStoredCarSpeed() =
        runTest(mainDispatcherRule.dispatcher) {
            storage.update { it.copy(carMaxSpeedKmh = 90) }

            // The surface-less acquisition of a car destination (`navigateTo`'s path) is the one the
            // car uses.
            engine.acquire(52.5200, 13.4000, 52.5300, 13.5000, Vehicle.CAR)
            awaitState { client.routeCalculationCount == 1 }

            val profile = client.lastRouteProfile
            assertEquals(Vehicle.CAR, profile?.vehicle)
            assertEquals(90.0, profile?.maxSpeedKmh ?: -1.0, 0.0)
        }

    @Test
    fun navigationEngineRerouteUsesTheStoredPedestrianSpeed() =
        runTest(mainDispatcherRule.dispatcher) {
            storage.update { it.copy(footMaxSpeedKmh = 4) }
            startSession(Vehicle.PEDESTRIAN)
            assertEquals("no engine acquisition for a surface-acquired start",
                0, client.routeCalculationCount)

            requestReroute(lat = 52.5218)
            awaitState { client.routeCalculationCount == 1 }

            val profile = client.lastRouteProfile
            assertEquals(Vehicle.PEDESTRIAN, profile?.vehicle)
            assertEquals("the reroute uses the pedestrian's stored speed", 4.0,
                profile?.maxSpeedKmh ?: -1.0, 0.0)
        }

    @Test
    fun movingASpeedSliderDoesNotAlterARunningRoute() =
        runTest(mainDispatcherRule.dispatcher) {
            storage.update { it.copy(bicycleMaxSpeedKmh = 20) }
            startSession()
            val startsBefore = client.navigationStartCount

            // The user moves the bicycle slider while the session runs.
            storage.update { it.copy(bicycleMaxSpeedKmh = 12) }
            awaitState { true }

            assertEquals(
                "a speed change recalculates nothing", 0, client.routeCalculationCount
            )
            assertTrue("the session keeps running", engine.state.value.isNavigating)
            assertEquals(
                "the running route is not replaced", startsBefore, client.navigationStartCount
            )

            // The next reroute is what picks the new value up.
            requestReroute(lat = 52.5218)
            awaitState { client.routeCalculationCount == 1 }
            assertEquals(12.0, client.lastRouteProfile?.maxSpeedKmh ?: -1.0, 0.0)
        }
}
