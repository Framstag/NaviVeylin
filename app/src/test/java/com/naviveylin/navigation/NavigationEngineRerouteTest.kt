package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.TurnType
import com.framstag.libosmscout.client.Vehicle
import com.framstag.libosmscout.client.RouteInstruction
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The engine's reroute policy (spec: `reroute-trigger` — all requirements;
 * `navigation-engine` — Route acquisition independent of a surface UI).
 *
 * One policy applies to every surface (design D3): the 50 m fast path, the
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
    private lateinit var engine: NavigationEngine

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        engine = NavigationEngine({ client }, LocationService(context), context)
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        // A straight west-east line; an off-route point 200 m north deviates far
        // more than the 50 m fast path, one 10 m north stays marginal.
        latitudes = doubleArrayOf(52.5200, 52.5200)
        longitudes = doubleArrayOf(13.4000, 13.5000)
        distance = 7000.0
        descriptions = arrayOf(
            "Start navigation  [0.0 km, 0 min]",
            "Destination reached  [0.0 km, 0 min]"
        )
    }

    private fun awaitState(condition: () -> Boolean, pump: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            pump()
            advanceUntilIdleBlocking()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
    }

    private fun advanceUntilIdleBlocking() {
        // The engine publishes on Dispatchers.Main, which the test rule replaces
        // with the test dispatcher; pumping the looper keeps Robolectric content.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    /** Start a session as a surface would (engine.start with a calculated route). */
    private fun startSession(pump: () -> Unit) {
        engine.start(route(), Vehicle.BICYCLE)
        awaitState({ client.navigationListener != null }, pump)
        awaitState({ engine.state.value.isNavigating }, pump)
    }

    private fun listener() = client.navigationListener!!

    /**
     * Report an off-route position. A fresh, accurate fix is fed first by default,
     * because the engine's accuracy guard ignores off-route reports while no fix has
     * been seen (`lastGpsAccuracy < 0`) — as in production, where the location feed
     * runs ahead of the native reports.
     */
    private fun requestReroute(lat: Double, feedFreshFix: Boolean = true, pump: () -> Unit) {
        if (feedFreshFix) {
            engine.processLocation(52.5200, 13.4500, 17.8, 5.0, System.currentTimeMillis())
        }
        listener().onRerouteRequest(
            lat, 13.4500, 90.0, 52.5200, 13.5000
        )
        pump()
    }

    @Test
    fun largeDeviationConfirmsOnTheFastPath() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }
        assertEquals("a surface-acquired start calculates no route in the engine",
            0, client.routeCalculationCount)

        // ~200 m north of the route: above the 50 m fast path, first report.
        requestReroute(lat = 52.5218) { advanceUntilIdle() }
        awaitState({ client.routeCalculationCount == 1 }) { advanceUntilIdle() }

        assertEquals("the engine re-acquired the route itself", 1, client.routeCalculationCount)
    }

    @Test
    fun marginalDeviationNeedsTheConfirmationGate() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }

        // ~10 m north: below the fast path, so the first report only opens the
        // episode and a second report inside the 10 s confirmation window does not
        // confirm either (the gate requires the minimum off-route duration).
        requestReroute(lat = 52.52009) { advanceUntilIdle() }
        assertEquals("no reroute on the first marginal report", 0, client.routeCalculationCount)

        requestReroute(lat = 52.52009) { advanceUntilIdle() }
        assertEquals(
            "no reroute before the confirmation window elapsed",
            0, client.routeCalculationCount
        )
    }

    @Test
    fun cooldownBlocksARerouteCascade() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }

        requestReroute(lat = 52.5218) { advanceUntilIdle() }
        awaitState({ client.routeCalculationCount == 1 }) { advanceUntilIdle() }

        // The native engine reports every ~5 s while off route: within the 25 s
        // cooldown the next confirmed deviation must not start another reroute.
        requestReroute(lat = 52.5218) { advanceUntilIdle() }
        assertEquals("cascade blocked by the cooldown", 1, client.routeCalculationCount)
    }

    @Test
    fun poorAccuracyBlocksTheReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }

        // A fix with 150 m accuracy exceeds the 100 m guard.
        engine.processLocation(52.5200, 13.4500, 17.8, 150.0, System.currentTimeMillis())
        requestReroute(lat = 52.5218, feedFreshFix = false) { advanceUntilIdle() }

        assertEquals("accuracy guard withheld the reroute", 0, client.routeCalculationCount)
    }

    @Test
    fun tunnelExitBlocksTheReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }

        // A tunnel estimate within the 30 s guard window.
        listener().onPositionEstimate(
            com.framstag.libosmscout.client.NavigationPosition(
                com.framstag.libosmscout.client.NavigationState.EstimateInTunnel,
                52.5200, 13.4500, 90.0, 5.0, "", "", ""
            )
        )
        awaitState({ true }) { advanceUntilIdle() }
        engine.processLocation(52.5200, 13.4500, 17.8, 5.0, System.currentTimeMillis())
        requestReroute(lat = 52.5218) { advanceUntilIdle() }

        assertEquals("tunnel guard withheld the reroute", 0, client.routeCalculationCount)
    }

    @Test
    fun confirmedRerouteKeepsTheRetainedVehicleProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            startSession { advanceUntilIdle() }
            assertEquals("no engine acquisition for a surface-acquired start",
                0, client.routeCalculationCount)

            requestReroute(lat = 52.5218) { advanceUntilIdle() }
            awaitState({ client.routeCalculationCount == 1 }) { advanceUntilIdle() }

            assertEquals(
                "the reroute re-acquired with the retained profile, no surface present",
                Vehicle.BICYCLE, client.lastRouteProfile!!.vehicle
            )
        }

    @Test
    fun rerouteUsesTheRetainedDestination() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }

        // The session's destination identity is the acquired route's end point.
        assertEquals("the session carries its destination",
            13.5000, engine.state.value.destLon, 1e-9)

        requestReroute(lat = 52.5218) { advanceUntilIdle() }
        awaitState({ client.routeCalculationCount == 1 }) { advanceUntilIdle() }

        assertEquals(
            "reroute goes to the retained destination",
            13.5000, client.lastRouteDest!!.second, 1e-9
        )
    }

    @Test
    fun instructionListUpdatesAfterAReroute() = runTest(mainDispatcherRule.dispatcher) {
        startSession { advanceUntilIdle() }
        listener().onRouteInstructions(
            arrayOf(RouteInstruction(100.0, TurnType.LEFT, "Main St", "Turn left", "Turn left"))
        )
        awaitState({ engine.state.value.instructions.size == 1 }) { advanceUntilIdle() }

        requestReroute(lat = 52.5218) { advanceUntilIdle() }
        awaitState({ client.routeCalculationCount == 1 }) { advanceUntilIdle() }

        // The engine clears the previous route's steps on the restart and reports
        // the new instructions when they arrive.
        listener().onRouteInstructions(
            arrayOf(RouteInstruction(50.0, TurnType.RIGHT, "Alt St", "Turn right", "Turn right"))
        )
        awaitState({ engine.state.value.instructions.size == 1 }) { advanceUntilIdle() }

        assertEquals("Alt St", engine.state.value.instructions[0].streetName)
        assertFalse(engine.state.value.isRerouting)
    }
}
