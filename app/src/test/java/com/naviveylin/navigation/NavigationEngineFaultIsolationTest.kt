package com.naviveylin.navigation

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.NavigationState
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Fault isolation of the process-scoped engine (spec: `navigation-engine` — Engine
 * coroutine fault is confined / Native failure is confined, not fatal;
 * `car-host-fault-isolation` — No fault escapes into the host path): a fault in the
 * engine's own work is recorded, raised as an engine-wide error and confined — never
 * the process, and the engine stays usable. The native boundary cases pass `Error`
 * throwables (`UnsatisfiedLinkError`), which the pre-change `catch (e: Exception)`
 * sites let escape.
 *
 * Default Robolectric sandbox — no `@Config`, no `@GraphicsMode` (AGENTS.md: the JNI
 * stub can load in exactly one classloader).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationEngineFaultIsolationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    // --- harness ---------------------------------------------------------------

    private fun buildEngine(
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        locationService: LocationService = LocationService(ApplicationProvider.getApplicationContext())
    ): NavigationEngine = NavigationEngine(
        { client },
        locationService,
        ApplicationProvider.getApplicationContext()
    )

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

    private fun route(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230, 52.5300)
        longitudes = doubleArrayOf(13.4050, 13.4080, 13.4100)
        distance = 5000.0
    }

    private fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun injectGpsFix(locationService: LocationService, lat: Double, lon: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<GpsFix?>
        flow.value = GpsFix(
            lat = lat,
            lon = lon,
            accuracy = 5.0,
            speedKmH = Double.NaN,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis()
        )
    }

    /** Engine faulted by an unexpected (non-native) failure, e.g. the stale-speed ticker. */
    private fun faultTheEngine(vm: NavigationEngine, message: String = "boom") {
        vm.engineScope.launch(CoroutineName("test-fault")) { throw IllegalStateException(message) }
    }

    private fun faultEntries(): List<String> =
        DiagnosticsLog.readEntries().filter { it.contains(ENGINE_FAULT_TAG) }

    // --- cases -----------------------------------------------------------------

    @Test
    fun theEngineScopeCarriesAFaultHandler() {
        val vm = buildEngine()

        assertNotNull(
            "the process-lifetime scope must confine its own faults",
            vm.engineScope.coroutineContext[CoroutineExceptionHandler]
        )
    }

    @Test
    fun aCoroutineFaultIsConfinedRecordedAndPublishedOnTheMainThread() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        val vm = buildEngine()
        val before = vm.state.value

        faultTheEngine(vm)

        // Nothing ran yet: the fault and its publication are dispatched to the main
        // thread, never performed synchronously on the failing thread.
        assertEquals(before, vm.state.value)

        awaitState { vm.state.value.errorMessage != null }

        assertEquals(SurfaceOrigin.ENGINE, vm.state.value.errorOrigin)
        assertTrue(vm.state.value.errorMessage!!.contains("Navigation engine fault"))
        assertTrue(vm.state.value.errorMessage!!.contains("test-fault"))
        assertEquals(1, faultEntries().size)
        assertTrue(
            "the scope survives its own child's fault (SupervisorJob + handler)",
            vm.engineScope.isActive
        )
    }

    @Test
    fun aConfinedFaultEndsTheActiveAttemptAndKeepsTheEngineUsable() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        injectGpsFix(locationService, 52.5200, 13.4050)
        val vm = buildEngine(client, locationService)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }

        faultTheEngine(vm)
        awaitState { vm.state.value.errorMessage != null }

        assertTrue("the faulting engine must not keep presenting guidance", !vm.state.value.isNavigating)
        assertEquals(SurfaceOrigin.ENGINE, vm.state.value.errorOrigin)

        // The engine is still usable: the fault ended one piece of work, not the engine.
        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun aNativeErrorAtTheAcquisitionBoundaryIsConfined() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply {
            routeCalculationError = UnsatisfiedLinkError("mock calculateRouteWithProfile")
        }
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        injectGpsFix(locationService, 52.5200, 13.4050)
        val vm = buildEngine(client, locationService)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitState { vm.state.value.errorMessage != null }
        assertEquals(1, client.routeCalculationCount)
        assertTrue(!vm.state.value.isNavigating)
        assertEquals(SurfaceOrigin.ENGINE, vm.state.value.errorOrigin)
        assertTrue(
            "a failed attempt must not keep the location lease",
            locationService.heldLeaseConsumers().isEmpty()
        )
    }

    @Test
    fun aNativeErrorAtTheStartBoundaryIsConfined() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply {
            routeToDeliver = route()
            navigationStartError = UnsatisfiedLinkError("mock startNavigationWithVehicle")
        }
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        injectGpsFix(locationService, 52.5200, 13.4050)
        val vm = buildEngine(client, locationService)

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)

        awaitState { vm.state.value.errorMessage != null }
        assertEquals(1, client.navigationStartCount)
        assertTrue(!vm.state.value.isNavigating)
        assertEquals(SurfaceOrigin.ENGINE, vm.state.value.errorOrigin)
        assertTrue(locationService.heldLeaseConsumers().isEmpty())
    }

    @Test
    fun aNativeErrorAtTheRoadLookupBoundaryIsConfinedAndNavigationContinues() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        val client = FakeOSMScoutClient().apply {
            roadAtError = UnsatisfiedLinkError("mock getRoadAt")
        }
        val vm = buildEngine(client)

        // The road lookup runs on Dispatchers.IO, so an escaping fault would not be
        // attributed to this test's main-thread looper: probe the thread's uncaught
        // handler directly, which is where a confined-neither fault ends up.
        val uncaught = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.set(e) }
        try {
            vm.updateRoadInfoFromPosition(
                NavigationPosition(NavigationState.OffRoute, 51.0, 7.0, 180.0, 5.0, "", "", "")
            )
            awaitState { client.roadAtLookupCalls.size == 1 }

            // A road-name lookup is cosmetic: the failure is confined and logged, it is
            // neither an engine error nor a reason to stop anything.
            assertNull(vm.state.value.errorMessage)
            assertTrue(
                "the fault never reached the engine's fault handler",
                faultEntries().isEmpty()
            )
            assertTrue(vm.engineScope.isActive)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
        assertNull(
            "the confined native failure must not reach the thread's uncaught handler",
            uncaught.get()
        )
    }

    @Test
    fun twoSurfacesKeepObservingThroughAConfinedFault() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        grantPreciseLocation()
        val client = FakeOSMScoutClient().apply { routeToDeliver = route() }
        val locationService = LocationService(ApplicationProvider.getApplicationContext())
        injectGpsFix(locationService, 52.5200, 13.4050)
        val vm = buildEngine(client, locationService)

        // Two surfaces observe the one shared state; neither may be torn down by a
        // fault in the engine they share (spec: car-host-fault-isolation — Engine
        // fault while a car session is live).
        val phoneSeen = mutableListOf<com.naviveylin.core.NavigationState>()
        val carSeen = mutableListOf<com.naviveylin.core.NavigationState>()
        val observers = CoroutineScope(Dispatchers.Main + Job())
        observers.launch { vm.state.collect { phoneSeen += it } }
        observers.launch { vm.state.collect { carSeen += it } }
        awaitState { phoneSeen.isNotEmpty() && carSeen.isNotEmpty() }

        vm.acquire(52.5200, 13.4050, 52.5300, 13.4100, Vehicle.CAR)
        awaitState { vm.state.value.isNavigating }
        faultTheEngine(vm)
        awaitState { phoneSeen.any { it.errorOrigin == SurfaceOrigin.ENGINE } }

        assertTrue("the phone observation kept running", phoneSeen.any { it.errorOrigin == SurfaceOrigin.ENGINE })
        assertTrue("the car observation kept running", carSeen.any { it.errorOrigin == SurfaceOrigin.ENGINE })
        assertTrue(
            "an engine error applies to both surfaces",
            phoneSeen.any { it.errorAppliesTo(SurfaceOrigin.PHONE) }
        )
        assertTrue(carSeen.any { it.errorAppliesTo(SurfaceOrigin.CAR) })
        observers.cancel()
    }
}
