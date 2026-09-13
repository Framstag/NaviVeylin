package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the car controller's reroute-restart state handling
 * (spec: auto/navigation-view — "Distance correct after reroute"): starting
 * navigation on a new route clears the previous route's steps, and the live
 * step-index lookup advances monotonically (spec: auto/navigation-view —
 * "Current step shown during navigation").
 *
 * Default Robolectric sandbox (no @Config, no @GraphicsMode) per the AGENTS.md
 * "JNI stub for unit tests" classloader rule — this class instantiates
 * [FakeOSMScoutClient].
 */
@RunWith(RobolectricTestRunner::class)
class AANavigationControllerStepIndexTest {

    private fun buildController(
        client: FakeOSMScoutClient = FakeOSMScoutClient()
    ): AANavigationController {
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        // navigateTo resolves its start position from the location provider.
        locationService.setGpsFixForTest(
            GpsFix(52.5200, 13.4050, 10.0, 50.0, Double.NaN, Double.NaN, System.currentTimeMillis())
        )
        return AANavigationController(
            client,
            NavigationStateProvider(),
            locationService
        )
    }

    /** Pump Robolectric's paused main looper until [condition] holds or timeout. */
    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("State condition not met within 5s")
    }

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230)
        longitudes = doubleArrayOf(13.4050, 13.4080)
        distance = 1000.0
    }

    private fun instr(description: String, distance: Double = 100.0) =
        RouteInstruction(distance, TurnType.LEFT, "Main St", description, "Turn left")

    @Test
    fun rerouteRestart_clearsStaleStepsFromPreviousRoute() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val controller = buildController(client)

        controller.navigateTo(52.5230, 13.4080)
        awaitState { controller.state.value.isNavigating }

        // Steps from the first route reach the UI.
        val listener = client.navigationListener!!
        listener.onRouteInstructions(arrayOf(instr("old step 1"), instr("old step 2")))
        awaitState {
            controller.state.value.instructions.isNotEmpty() &&
                controller.state.value.nextInstruction != null
        }

        // Reroute: a new route is calculated and navigation restarts — stale
        // steps must not survive into the new route's display.
        controller.navigateTo(52.5300, 13.4100)
        awaitState { controller.state.value.isNavigating }

        assertTrue(controller.state.value.instructions.isEmpty())
        assertNull(controller.state.value.nextInstruction)
        assertEquals(0, controller.state.value.currentStepIndex)
    }

    @Test
    fun onNextRouteInstruction_advancesIndexMonotonicallyThroughDuplicates() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val controller = buildController(client)

        controller.navigateTo(52.5230, 13.4080)
        awaitState { controller.state.value.isNavigating }

        val listener = client.navigationListener!!
        listener.onRouteInstructions(arrayOf(
            instr("Turn left into A"),
            instr("Straight on"),
            instr("Turn left into A")
        ))
        awaitState { controller.state.value.instructions.size == 3 }

        // Advance to the middle step ("Straight on").
        listener.onNextRouteInstruction(instr("Straight on"))
        awaitState { controller.state.value.currentStepIndex == 1 }

        // Live instruction repeats "Turn left into A": real occurrence is the
        // second one (index 2) — the lookup must NOT fall back to index 0.
        listener.onNextRouteInstruction(instr("Turn left into A"))
        awaitState { controller.state.value.currentStepIndex == 2 }
    }
}
