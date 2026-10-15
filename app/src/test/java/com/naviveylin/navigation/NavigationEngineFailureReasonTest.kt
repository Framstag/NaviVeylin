package com.naviveylin.navigation

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.test.engineUnderTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies that the engine publishes the **cause** of a failed route attempt, and that an engine-wide
 * failure is one every surface presenting navigation can act on (spec: `navigation-engine` — *Errors
 * carry the surface that caused them*; `routing-failure-reporting` — *A failed route calculation reports
 * a reason*; `osmscout-jni` — *A failed routing call reports a reason*).
 *
 * Runs under Robolectric with the DEFAULT sandbox — no `@Config(sdk = …)`, which `AGENTS.md` forbids
 * because a second sandbox in one JVM cannot load the JNI stub the fake client needs.
 *
 * The phone's *rendering* of an engine failure (the snackbar the carrier shows) is not covered here: it
 * needs the full map-screen composition. What this class proves is the value that carrier gates on — a
 * failure that applies to `SurfaceOrigin.PHONE`; the on-device run in task 6.4 shows the snackbar itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineFailureReasonTest {

    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()
    private val engineDispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)

    @Before
    fun grantPreciseLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun buildEngine(client: FakeOSMScoutClient,
                            locationService: LocationService): NavigationEngine =
        engineUnderTest(
            { client },
            locationService,
            ApplicationProvider.getApplicationContext(),
            dispatchers = engineDispatchers
        )

    /** Inject a location into the private LocationService flow (test only). */
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

    @Test
    fun anEngineRaisedErrorReachesThePhoneCarrier() =
        runTest(computeDispatcher) {
            val client = FakeOSMScoutClient().apply {
                routeToDeliver = null
                deliverRouteErrorKind = RouteCallback.KIND_TARGET_UNROUTABLE
                deliverRouteError = "No routable node near destination"
                deliverRouteErrorDistanceMeters = 7_000.0
            }
            val locationService = LocationService(ApplicationProvider.getApplicationContext())
            injectGpsFix(locationService, 52.5200, 13.4050)
            val vm = buildEngine(client, locationService)

            vm.navigateTo(52.5300, 13.4100, "Dest")

            awaitState { vm.state.value.failure != null }

            val failure = vm.state.value.failure!!
            assertEquals(FailureReason.TARGET_UNROUTABLE, failure.reason)
            assertEquals(RouteEndpoint.TARGET, failure.endpoint)
            assertEquals(7_000.0, failure.nearestRoadDistanceMeters!!, 1e-9)
            assertEquals(SurfaceOrigin.ENGINE, failure.origin)
            // The engine's own failure is engine-wide: both surfaces present it, which is what the phone
            // carrier gates on and what the car notice gates on (spec: `navigation-engine` — its new
            // "Engine-wide error reaches the phone" scenario).
            assertTrue("the phone presents it", failure.appliesTo(SurfaceOrigin.PHONE))
            assertTrue("the car presents it too", failure.appliesTo(SurfaceOrigin.CAR))
        }

    @Test
    fun anEngineErrorWithNoSurfaceIsStillObservableInState() =
        runTest(computeDispatcher) {
            val vm = buildEngine(
                FakeOSMScoutClient(),
                LocationService(ApplicationProvider.getApplicationContext())
            )

            vm.reportFailure(NavigationFailure(reason = FailureReason.ROUTING_DATA_UNAVAILABLE))

            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(
                FailureReason.ROUTING_DATA_UNAVAILABLE,
                vm.state.value.failure?.reason
            )
        }

    @Test
    fun aFailureStillNotifiesACallerThatReadsOnlyTheMessage() {
        // The interface's default keeps a caller written before onErrorDetail existed working: it receives
        // the failure through the callback it always implemented (spec: `osmscout-jni` — "An existing caller
        // keeps its behaviour").
        val seen = mutableListOf<String>()
        val legacyCaller = object : RouteCallback {
            override fun onProgress(percent: Int) = Unit
            override fun onSuccess(route: com.framstag.libosmscout.client.RouteEntry) = Unit
            override fun onError(message: String) {
                seen.add(message)
            }

            override fun onCancel() = Unit
        }

        legacyCaller.onErrorDetail(RouteCallback.KIND_NO_ROUTE_FOUND, "no route", Double.NaN)

        assertEquals(listOf("no route"), seen)
    }

    @Test
    fun aCancelledCalculationPublishesNoReason() =
        runTest(computeDispatcher) {
            val client = FakeOSMScoutClient().apply {
                routeToDeliver = null
                holdRouteDelivery = true
            }
            val locationService = LocationService(ApplicationProvider.getApplicationContext())
            injectGpsFix(locationService, 52.5200, 13.4050)
            val vm = buildEngine(client, locationService)

            vm.navigateTo(52.5300, 13.4100, "Dest")
            awaitState { client.pendingRouteCallbacks.isNotEmpty() }

            vm.cancelAcquisition()
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(
                "a cancellation is not a failure and carries no reason",
                vm.state.value.failure
            )
        }

    @Test
    fun aThrownFaultBecomesTheEngineFailureReason() =
        runTest(computeDispatcher) {
            val client = FakeOSMScoutClient().apply {
                routeToDeliver = null
                routeCalculationError = UnsatisfiedLinkError("mock calculateRouteWithProfile")
            }
            val locationService = LocationService(ApplicationProvider.getApplicationContext())
            injectGpsFix(locationService, 52.5200, 13.4050)
            val vm = buildEngine(client, locationService)

            vm.navigateTo(52.5300, 13.4100, "Dest")

            awaitState { vm.state.value.failure != null }

            val failure = vm.state.value.failure!!
            assertEquals(FailureReason.ENGINE_FAILURE, failure.reason)
            assertEquals(SurfaceOrigin.ENGINE, failure.origin)
            // The native fault's message is a log detail, never the user's message.
            assertEquals("mock calculateRouteWithProfile", failure.detail)
            assertTrue("the process survives the fault", !vm.state.value.isNavigating)
        }

    @Test
    fun aCarOnlyFailureDoesNotReachThePhoneCarrier() {
        // The phone's carrier shows what [phoneFailureOf] returns; a failure another surface caused is not
        // the phone's to show (spec: `navigation-engine` — *Car deep-link failure does not appear on the
        // phone*), and an engine-wide one is.
        val carOnly = com.naviveylin.core.NavigationState(
            failure = NavigationFailure(
                reason = FailureReason.UNKNOWN,
                origin = SurfaceOrigin.CAR,
                surfaceMessage = "No matching location found"
            )
        )
        assertNull("a car-raised failure stays on the car", phoneFailureOf(carOnly))

        val engineWide = com.naviveylin.core.NavigationState(
            failure = NavigationFailure(reason = FailureReason.ENGINE_FAILURE)
        )
        assertEquals(
            "an engine-wide failure is the phone's to show",
            FailureReason.ENGINE_FAILURE,
            phoneFailureOf(engineWide)?.reason
        )

        val noneAtAll = com.naviveylin.core.NavigationState()
        assertNull("and a state without a failure shows nothing", phoneFailureOf(noneAtAll))
    }

    @Test
    fun aFailedAttemptClearsTheFailureOnTheNextSuccess() =
        runTest(computeDispatcher) {
            val client = FakeOSMScoutClient().apply {
                routeToDeliver = null
                deliverRouteErrorKind = RouteCallback.KIND_NO_ROUTE_FOUND
                deliverRouteError = "no route"
            }
            val locationService = LocationService(ApplicationProvider.getApplicationContext())
            injectGpsFix(locationService, 52.5200, 13.4050)
            val vm = buildEngine(client, locationService)

            vm.navigateTo(52.5300, 13.4100, "Dest")
            awaitState { vm.state.value.failure != null }

            client.routeToDeliver = com.framstag.libosmscout.client.RouteEntry().apply {
                routeHandle = 1L
                latitudes = doubleArrayOf(52.5200, 52.5300)
                longitudes = doubleArrayOf(13.4050, 13.4100)
                distance = 1_500.0
            }
            vm.navigateTo(52.5300, 13.4100, "Dest")
            awaitState { vm.state.value.failure == null }

            assertNull("a successful attempt leaves no cause behind", vm.state.value.failure)
        }
}
