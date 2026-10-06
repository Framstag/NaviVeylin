package com.naviveylin.navigation

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.location.LocationService
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import com.naviveylin.core.EngineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import com.naviveylin.test.engineUnderTest

/**
 * The in-flight route calculation in the shared state
 * (spec: `route-calculation-feedback`).
 *
 * Default Robolectric sandbox — no `@Config`, no `@GraphicsMode` (AGENTS.md: the JNI
 * stub for `OSMScoutClient` loads in exactly one sandbox classloader).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineCalculationStateTest {

    /**
     * The engine's off-main work runs on these schedulers (production uses real pools), so a case
     * drains them instead of polling (spec: `unit-test-suite-runtime` — Awaiting state, not a
     * deadline).
     */
    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()
    private val engineDispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)

    private lateinit var logFile: File

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        if (::logFile.isInitialized) logFile.delete()
    }

    private fun buildEngine(
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        locationService: LocationService = LocationService(ApplicationProvider.getApplicationContext())
    ): NavigationEngine =
        engineUnderTest(
            { client },
            locationService,
            ApplicationProvider.getApplicationContext(),
            dispatchers = engineDispatchers
        )

    /** Drain the injected schedulers and the looper, then assert — no wall-clock deadline. */
    private fun awaitState(condition: () -> Boolean) {
        repeat(2) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }
        if (!condition()) {
            throw AssertionError("State condition not met after draining the test scheduler")
        }
    }

    /**
     * Wait for the [index]-th held route callback: the engine calculates on
     * `Dispatchers.Default`, so the fake is handed its callback off the test thread.
     */
    private fun awaitCallback(client: FakeOSMScoutClient, index: Int = 0): RouteCallback {
        awaitState { client.pendingRouteCallbacks.size > index }
        return client.pendingRouteCallbacks[index]
    }

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    /** Enable the diagnostics file and return it (measurement entry assertions). */
    private fun initDiagnostics(): File {
        val context = ApplicationProvider.getApplicationContext<Application>()
        logFile = File(context.filesDir, "diagnostics/route-calculation-test.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.initForTest(logFile)
        return logFile
    }

    @Test
    fun acquisitionPublishesTheCalculationInFlightWithAGrowingToken() {
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val engine = buildEngine(client)

        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        val first = engine.state.value.calculation
        assertNotNull("the calculation in flight is part of the state", first)
        assertEquals(1L, first!!.token)
        assertEquals(52.5300, first.destLat, 0.0)
        assertEquals(13.4100, first.destLon, 0.0)
        assertNull("nothing has been reported yet", first.percent)
        assertFalse("the route has not arrived", engine.state.value.isNavigating)

        engine.acquire(52.5200, 13.4050, 52.5350, 13.4200, Vehicle.CAR)

        val second = engine.state.value.calculation
        assertEquals("a new request gets a new token", 2L, second!!.token)
        assertEquals(52.5350, second.destLat, 0.0)
    }

    @Test
    fun progressIsPublishedCappedAndClearedWithTheCalculation() {
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val engine = buildEngine(client)
        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        val callback = awaitCallback(client)

        callback.onProgress(17)
        awaitState { engine.state.value.calculation?.percent == 17 }

        callback.onProgress(120)
        awaitState { engine.state.value.calculation?.percent == 99 }
        assertTrue(
            "the route has not arrived, so the wait is not complete",
            engine.state.value.calculation != null
        )

        callback.onSuccess(routeEntry())
        awaitState { engine.state.value.calculation == null }
    }

    @Test
    fun aSupersededCalculationNeverTouchesTheLiveOne() {
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val engine = buildEngine(client)

        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        val superseded = awaitCallback(client, 0)
        engine.acquire(52.5200, 13.4050, 52.5350, 13.4200, Vehicle.CAR)
        val live = awaitCallback(client, 1)

        superseded.onProgress(50)
        superseded.onError("No route available")
        superseded.onCancel()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the live calculation keeps owning the state",
            2L,
            engine.state.value.calculation?.token
        )
        assertNull("a superseded report is not published", engine.state.value.calculation?.percent)
        assertNull("a superseded failure is not published", engine.state.value.errorMessage)
        assertFalse(
            "a superseded success must not start navigation on an invalid route handle",
            engine.state.value.isNavigating
        )

        live.onSuccess(routeEntry())
        awaitState { engine.state.value.isNavigating }
        assertNull(engine.state.value.calculation)
        assertEquals("only the live calculation started", null, engine.state.value.errorMessage)
    }

    @Test
    fun aFailingCalculationClearsTheWaitAndPublishesTheError() {
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val engine = buildEngine(client)
        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitCallback(client).onError("No route available")

        awaitState { engine.state.value.errorMessage != null }
        assertNull("the wait is over, failed or not", engine.state.value.calculation)
        assertFalse(engine.state.value.isNavigating)
    }

    @Test
    fun startingNavigationOnASurfaceRouteEndsTheWait() {
        val client = FakeOSMScoutClient().apply { holdRouteDelivery = true }
        val engine = buildEngine(client)
        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        assertNotNull(engine.state.value.calculation)

        engine.start(routeEntry(), Vehicle.CAR)

        awaitState { engine.state.value.isNavigating }
        assertNull(
            "navigation starting is the end of the wait, whoever calculated the route",
            engine.state.value.calculation
        )
    }

    @Test
    fun theEndOfACalculationIsRecordedWithoutCoordinates() {
        val file = initDiagnostics()
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = routeEntry()
            progressToReport = listOf(5, 40)
        }
        val engine = buildEngine(client)

        engine.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { engine.state.value.isNavigating }

        val entry = DiagnosticsLog.readEntries().last { it.contains(NavigationEngine.ROUTE_TAG) }
        assertTrue(entry, entry.contains("source=acquisition"))
        assertTrue(entry, entry.contains("outcome=ok"))
        assertTrue(entry, entry.contains("duration="))
        assertTrue("the reported progress count reaches the entry", entry.contains("percents=2"))
        assertFalse("no coordinate precision in the entry", entry.contains("52.5"))
        assertFalse("no coordinate precision in the entry", entry.contains("13.4"))
        assertTrue("the entry is in the configured file", file.readText().contains("outcome=ok"))
    }
}
