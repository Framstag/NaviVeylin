package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.location.LocationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import com.naviveylin.test.engineUnderTest

/**
 * Errors carry the surface that caused them (spec: `navigation-engine` — Errors
 * carry the surface that caused them; design D4): a car-attributed error is
 * presented on the car and not on the phone, an engine-wide error reaches every
 * surface.
 *
 * Runs under Robolectric with the default sandbox (AGENTS.md classloader rule for
 * the JNI stub `.so`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineErrorOriginTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var engine: NavigationEngine

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
        client = FakeOSMScoutClient()
        engine = engineUnderTest(
            { client },
            LocationService(context),
            context,
            dispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)
        )
    }

    @Test
    fun carErrorIsPresentedOnTheCarOnly() = runTest(mainDispatcherRule.dispatcher) {
        engine.reportError("No matching location found", SurfaceOrigin.CAR)

        val state = engine.state.value
        assertEquals("No matching location found", state.errorMessage)
        assertEquals(SurfaceOrigin.CAR, state.errorOrigin)
        assertTrue("the car presents it", state.errorAppliesTo(SurfaceOrigin.CAR))
        assertFalse("the phone does not", state.errorAppliesTo(SurfaceOrigin.PHONE))
    }

    @Test
    fun phoneErrorIsPresentedOnThePhoneOnly() = runTest(mainDispatcherRule.dispatcher) {
        engine.reportError("Route panel failure", SurfaceOrigin.PHONE)

        val state = engine.state.value
        assertTrue("the phone presents it", state.errorAppliesTo(SurfaceOrigin.PHONE))
        assertFalse("the car does not", state.errorAppliesTo(SurfaceOrigin.CAR))
    }

    @Test
    fun engineErrorReachesEverySurface() = runTest(mainDispatcherRule.dispatcher) {
        engine.reportError("Route calculation failed. Try again.", SurfaceOrigin.ENGINE)

        val state = engine.state.value
        assertTrue("every surface presents an engine error",
            state.errorAppliesTo(SurfaceOrigin.PHONE) && state.errorAppliesTo(SurfaceOrigin.CAR))
    }

    @Test
    fun originlessErrorIsTreatedAsEngineWide() = runTest(mainDispatcherRule.dispatcher) {
        // A producer that does not set an origin must not be silently hidden.
        val state = com.naviveylin.core.NavigationState(errorMessage = "boom")
        assertTrue(state.errorAppliesTo(SurfaceOrigin.PHONE))
        assertTrue(state.errorAppliesTo(SurfaceOrigin.CAR))
    }

    @Test
    fun clearErrorClearsTheOriginToo() = runTest(mainDispatcherRule.dispatcher) {
        engine.reportError("No matching location found", SurfaceOrigin.CAR)
        engine.clearError()

        assertNull("no message survives", engine.state.value.errorMessage)
        assertNull("no origin survives", engine.state.value.errorOrigin)
        assertFalse(engine.state.value.errorAppliesTo(SurfaceOrigin.CAR))
    }

    @Test
    fun failedRouteCalculationRaisesAnEngineError() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engineUnderTest(
            {
                FakeOSMScoutClient().apply {
                    routeToDeliver = null
                    deliverRouteError = "Route calculation failed. Try again."
                }
            },
            LocationService(context),
            context,
            dispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)
        )

        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        // Drain the injected schedulers instead of waiting on the wall clock (spec:
        // `unit-test-suite-runtime` — Awaiting state, not a deadline).
        repeat(2) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        }
        assertEquals("Route calculation failed. Try again.", engine.state.value.errorMessage)
        assertEquals(SurfaceOrigin.ENGINE, engine.state.value.errorOrigin)
        assertFalse("no navigation started", engine.state.value.isNavigating)
    }
}
