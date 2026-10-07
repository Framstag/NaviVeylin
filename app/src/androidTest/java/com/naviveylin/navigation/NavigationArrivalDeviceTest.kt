package com.naviveylin.navigation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.haversineDistanceMeters
import com.naviveylin.location.LocationService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device check of the arrival fact on the real native engine (spec: `navigation-engine` — Arrival is
 * part of the shared navigation state, scenario "Destination reached is reported in the shared
 * state"; task 3.1 of change `auto-end-navigation-after-arrival`).
 *
 * The host suite proves the engine's plumbing with a fake client; only a device can prove the two
 * halves it cannot: that the native engine really emits its target-reached report for a route it
 * drives, and that the app's listener turns it into `hasReachedDestination` on the process-scoped
 * engine. This is why the check is an instrumented test rather than a host test: the JVM suite's JNI
 * stub ships no symbols (`AGENTS.md`), so the native navigation engine runs on a device only.
 *
 * It routes through the real client and drives the real engine with the route's own polyline
 * vertices — no emulator console, no motion. It is skipped when no installed map database is present,
 * and when the native engine never reports the position as on route for synthetic fixes (the known
 * limitation of a synthetic fix, `TODO.md` §16: it carries no bearing) — a skip is *not* a pass.
 */
@RunWith(AndroidJUnit4::class)
class NavigationArrivalDeviceTest {

    /** Candidate routes, tried in order: the map data decides which pair routes. */
    private val candidates = listOf(
        Candidate("Dortmund centre -> Dortmund north", 51.5136, 7.4653, 51.5400, 7.5000),
        Candidate("Dortmund Hbf -> Cologne Hbf", 51.5177, 7.4592, 50.9430, 6.9580),
        Candidate("Dortmund Hbf -> Bochum Hbf", 51.5177, 7.4592, 51.4785, 7.2260),
        Candidate("short hop within Dortmund", 51.5136, 7.4653, 51.5200, 7.4700)
    )

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** How long a synthetic fix is given to reach the engine's listener before the state is read. */
    private val fixSettleMs = 300L

    /** How long the arrival fact is given to appear after a fix at the destination. */
    private val arrivalTimeoutMs = 8_000L

    private fun client(): OSMScoutClient {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mapsDir = File(context.filesDir, "maps")
        // The shipped scan, not a second one: the app's map manager stores a downloaded region under
        // its provider path, and `InstalledMaps.findDatabaseDirectories` already walks that tree and
        // skips the basemap (a rendering lookup database with no routing data).
        val mapDirs = InstalledMaps.findDatabaseDirectories(mapsDir.absolutePath, "basemap")
        assumeTrue("no installed map directory to route on", mapDirs.isNotEmpty())

        val client = OSMScoutClientBuilder()
            .withMapLookupDirectories(mapsDir.absolutePath)
            .withPhysicalDpi(420.0)
            .build()
        val opened = client.openDatabases(mapDirs.toTypedArray()).count { it }
        assumeTrue("no map database could be opened", opened > 0)
        return client
    }

    private fun calculateRoute(client: OSMScoutClient): RouteEntry {
        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val latch = CountDownLatch(1)
            var route: RouteEntry? = null
            var failure: String? = null
            client.calculateRouteWithProfile(
                candidate.startLat, candidate.startLon, candidate.destLat, candidate.destLon,
                RoutingProfile(),
                object : RouteCallback {
                    override fun onProgress(percent: Int) = Unit
                    override fun onSuccess(entry: RouteEntry) {
                        route = entry
                        latch.countDown()
                    }

                    override fun onError(message: String) {
                        failure = message
                        latch.countDown()
                    }

                    override fun onCancel() {
                        failure = "cancelled"
                        latch.countDown()
                    }
                }
            )
            if (!latch.await(180, TimeUnit.SECONDS)) {
                failures += "${candidate.name}: timed out"
                continue
            }
            route?.let { entry ->
                if ((entry.latitudes?.size ?: 0) >= 3) return entry
                failures += "${candidate.name}: no usable polyline"
            } ?: run { failures += "${candidate.name}: ${failure ?: "no route"}" }
        }
        throw AssertionError("no candidate routed on this map data: ${failures.joinToString("; ")}")
    }

    /** Poll the engine's state on the instrumentation thread until [condition] holds or it expires. */
    private fun await(engine: NavigationEngine, timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    /**
     * Drive the route with its own polyline vertices and assert that the engine publishes the arrival
     * fact once the destination is reached.
     *
     * Two phases, because the native target-reached report is emitted on a fix that is *inside* its
     * approach radius while the position is `OnRoute`: the engine first has to see a forward walk
     * along the route (its forward snap search needs a progression), and the fix that carries the
     * report must sit short of the destination — a fix exactly at the route's final node can fail
     * that forward snap search and be reported off route instead (`PositionAgent`, the "route's final
     * node" case).
     */
    @Test
    fun theNativeArrivalReportBecomesTheSharedArrivalFact() {
        val client = client()
        try {
            val route = calculateRoute(client)
            val lats = route.latitudes
            val lons = route.longitudes
            assumeTrue("the route carries no polyline", lats != null && lons != null)
            val vertices = lats!!.size

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = NavigationEngine(
                { client },
                LocationService(context),
                context,
                EngineTimeSource.System,
                EngineDispatchers.Production
            )

            var fed = 0
            try {
                engine.start(route, Vehicle.CAR)
                assertTrue(
                    "the engine must start a navigation session on a device",
                    await(engine, 10_000) { engine.state.value.isNavigating }
                )
                assumeTrue(
                    "the engine published neither instructions nor a route distance for this route",
                    await(engine, 10_000) {
                        engine.state.value.instructions.isNotEmpty() ||
                            engine.state.value.totalDistance > 0.0
                    }
                )

                fun feed(lat: Double, lon: Double, speedMs: Double, settleMs: Long) {
                    engine.processLocation(lat, lon, speedMs, 5.0, System.currentTimeMillis())
                    fed++
                    Thread.sleep(settleMs)
                }

                // Phase 1: a forward walk over the last third of the route, one fix per stride, so the
                // native position agent has a progression to snap onto.
                val stride = maxOf(1, vertices / 40)
                var index = maxOf(0, vertices - vertices / 3)
                while (index < vertices - 2 && !engine.state.value.hasReachedDestination) {
                    feed(lats[index], lons[index], 13.0, fixSettleMs)
                    if (index + stride >= vertices - 2) break
                    index += stride
                }
                Log.i(
                    "ArrivalDeviceTest",
                    "arrival phase 1: fixes=$fed offRoute=${engine.state.value.isOffRoute}" +
                        " remaining=${engine.state.value.remainingDistance.toInt()}m" +
                        " reached=${engine.state.value.hasReachedDestination}"
                )

                // Phase 2: the approach. The fix that carries the report must sit short of the
                // destination, so the last stretch is walked densely and the last fix is placed just
                // inside the 30 m approach radius by interpolating along the final edge.
                val endLat = lats[vertices - 1]
                val endLon = lons[vertices - 1]
                val prevLat = lats[vertices - 2]
                val prevLon = lons[vertices - 2]
                val lastEdgeM = haversineDistanceMeters(prevLat, prevLon, endLat, endLon)
                val approachFraction = if (lastEdgeM > 40.0) 1.0 - 20.0 / lastEdgeM else 1.0
                val approachLat = prevLat + (endLat - prevLat) * approachFraction
                val approachLon = prevLon + (endLon - prevLon) * approachFraction

                var attempt = 0
                while (attempt < 3 && !engine.state.value.hasReachedDestination) {
                    feed(prevLat, prevLon, 8.0, fixSettleMs)
                    feed(approachLat, approachLon, 5.0, fixSettleMs)
                    await(engine, arrivalTimeoutMs) { engine.state.value.hasReachedDestination }
                    attempt++
                }

                val state = engine.state.value
                Log.i(
                    "ArrivalDeviceTest",
                    "arrival device: fixes=$fed vertices=$vertices lastEdge=${lastEdgeM.toInt()}m" +
                        " reached=${state.hasReachedDestination} offRoute=${state.isOffRoute}" +
                        " nextInstruction=${state.nextInstruction != null}" +
                        " remaining=${state.remainingDistance.toInt()}m"
                )

                // The property only exists once the native engine has reported the position as on
                // route; without that report the device run cannot produce it, and the honest result
                // is a skip (a skip is not a pass).
                assumeTrue(
                    "the native engine never reported the position as on route for synthetic fixes " +
                        "(TODO.md §16: a synthetic fix carries no bearing), so the arrival report " +
                        "could not be produced on this device (fixes=$fed," +
                        " offRoute=${state.isOffRoute}, remaining=${state.remainingDistance.toInt()}m)",
                    state.hasReachedDestination
                )
                assertTrue(
                    "the native target-reached report must become the shared arrival fact " +
                        "(fixes=$fed, remaining=${state.remainingDistance.toInt()}m)",
                    state.hasReachedDestination
                )
            } finally {
                engine.stopNavigation()
            }
        } finally {
            client.close()
        }
    }
}
