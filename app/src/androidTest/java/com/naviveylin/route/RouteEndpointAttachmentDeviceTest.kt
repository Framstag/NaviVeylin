package com.naviveylin.route

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.navigation.navigationFailureOf
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device check of the route-endpoint attachment (spec: `osmscout-jni` — *An endpoint beside a
 * direction-restricted road is still attached*; `routing-failure-reporting` — *A reason that concerns one
 * end of the route identifies that end*).
 *
 * This runs on a device because the JVM suite's JNI stub ships no symbols: the router's own candidate
 * selection and its failure kinds exist only in the real `.so`.
 *
 * **What makes the assertion independent of the installed dataset.** The defect this change removes is not
 * "a route failed" — a point in water legitimately has no route — but "**a road was right there and could
 * not be attached**". The bridge reports how far the nearest road the router could use is, so the case
 * fails exactly when an endpoint could not be attached while a usable road is closer than
 * [nearbyRoadMeters]. On the unfixed selection that is what a destination beside a one-way road produced:
 * the nearest way was taken, could not carry the endpoint, and the calculation stopped there — with the
 * same nearby road reported as the nearest one.
 *
 * The map set is whatever is installed — `europe/andorra` on the reference AVD, the smallest routable region
 * the provider offers (5.6 MB) — and every coordinate below is inside its bounding box. The case reports
 * every candidate it tried, so a dataset without routable data is visible as such instead of looking like
 * a code failure.
 */
@RunWith(AndroidJUnit4::class)
class RouteEndpointAttachmentDeviceTest {

    /** A usable road closer than this that could not be attached is the defect, not a missing map. */
    private val nearbyRoadMeters = 1_000.0

    private data class Point(val name: String, val lat: Double, val lon: Double)

    private data class Outcome(val route: RouteEntry?, val failure: NavigationFailure?, val detail: String?)

    /** Andorra la Vella to Pas de la Casa: a working route across the installed region. */
    private val workingRoute = Point("Andorra la Vella", 42.5078, 1.5211) to
        Point("Pas de la Casa", 42.5430, 1.7330)

    /**
     * Targets that sit inside a town block or beside a carriageway — the shapes whose nearest routable way
     * is a one-way street, a slip road or a dual carriageway. The fix's contract is that a road this close
     * is attached, whatever direction it may be driven in.
     */
    private val crowdedTargets = listOf(
        Point("Andorra la Vella, block interior", 42.50740, 1.52020),
        Point("Andorra la Vella, avenue corridor", 42.50820, 1.52540),
        Point("Escaldes-Engordany, block interior", 42.50934, 1.53690),
        Point("Sant Julià de Lòria, town centre", 42.46310, 1.49230),
        Point("La Massana, town centre", 42.54560, 1.51420),
        Point("Ordino, town centre", 42.55600, 1.53300),
        Point("Encamp, town centre", 42.53550, 1.58100),
        Point("Canillo, town centre", 42.56730, 1.59750)
    )

    /**
     * Barcelona, ~100 km outside the installed region: no way of this dataset is anywhere near it, so the
     * target genuinely cannot be attached and the failure must say so. Deterministic, unlike a guess at a
     * road-free point inside a mountain country.
     */
    private val noRoadTarget = Point("Barcelona (outside the map)", 41.3874, 2.1686)

    private fun client(): OSMScoutClient {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mapsDir = File(context.filesDir, "maps")
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

    private fun route(client: OSMScoutClient, from: Point, to: Point): Outcome {
        val latch = CountDownLatch(1)
        var route: RouteEntry? = null
        var failure: NavigationFailure? = null
        var detail: String? = null

        client.calculateRouteWithProfile(
            from.lat, from.lon, to.lat, to.lon,
            RoutingProfile(),
            object : RouteCallback {
                override fun onProgress(percent: Int) = Unit

                override fun onSuccess(entry: RouteEntry) {
                    route = entry
                    latch.countDown()
                }

                /** The kinded report this change added — the device half of `RouteCallback`. */
                override fun onErrorDetail(
                    kind: Int,
                    message: String,
                    nearestRoadDistanceMeters: Double
                ) {
                    failure = navigationFailureOf(kind, message, nearestRoadDistanceMeters)
                    detail = message
                    latch.countDown()
                }

                override fun onError(message: String) {
                    onErrorDetail(RouteCallback.KIND_UNKNOWN, message, Double.NaN)
                }

                override fun onCancel() {
                    latch.countDown()
                }
            }
        )

        assertTrue("the calculation did not answer in 30 s", latch.await(30, TimeUnit.SECONDS))
        return Outcome(route, failure, detail)
    }

    @Test
    fun aWorkingRouteStillRoutes() {
        val client = client()
        try {
            val (from, to) = workingRoute
            val outcome = route(client, from, to)

            assertNotNull(
                "a route across the installed map must still route (${outcome.detail})",
                outcome.route
            )
            assertTrue(
                "the route has geometry",
                (outcome.route?.latitudes?.size ?: 0) > 1
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun everyTargetWithARoadNearbyIsAttached() {
        val client = client()
        val report = StringBuilder()
        val defects = mutableListOf<String>()
        try {
            val start = Point("Andorra la Vella", 42.5078, 1.5211)

            crowdedTargets.forEach { target ->
                val outcome = route(client, start, target)
                val failure = outcome.failure
                val distance = failure?.nearestRoadDistanceMeters

                report.append("\n  ${target.name}: ")
                    .append(
                        when {
                            outcome.route != null -> "routed (${outcome.route!!.latitudes.size} points)"
                            else -> "failed reason=${failure?.reason} nearestRoadM=${distance ?: "unknown"}"
                        }
                    )

                // The defect shape: an endpoint reported unattachable although a usable road is right
                // there. On the unfixed selection this is what a target beside a one-way way produced.
                if (failure != null &&
                    (failure.reason == FailureReason.TARGET_UNROUTABLE ||
                        failure.reason == FailureReason.START_UNROUTABLE) &&
                    distance != null && distance < nearbyRoadMeters
                ) {
                    defects += "${target.name}: ${failure.reason}, nearest road ${distance.toInt()} m away"
                }
            }
        } finally {
            client.close()
        }

        assertTrue(
            "a road nearer than ${nearbyRoadMeters.toInt()} m could not be attached:$report\n" +
                "defects: $defects",
            defects.isEmpty()
        )
        android.util.Log.i("RouteEndpointAttachment", "candidates:$report")
    }

    @Test
    fun anEndpointWithNoRoadNearbyNamesItsEnd() {
        val client = client()
        try {
            val outcome = route(client, Point("Andorra la Vella", 42.5078, 1.5211), noRoadTarget)

            assertTrue(
                "nothing near Barcelona is in this dataset, so the case needs a failure " +
                    "(${outcome.route?.latitudes?.size} points routed)",
                outcome.route == null && outcome.failure != null
            )
            assertEquals(
                "the failing end is named: ${outcome.failure}",
                RouteEndpoint.TARGET,
                outcome.failure!!.endpoint
            )
            assertEquals(
                "and the cause is the unattachable end: ${outcome.failure}",
                FailureReason.TARGET_UNROUTABLE,
                outcome.failure!!.reason
            )
            assertEquals(
                "the failure is the engine's own, so every surface presents it",
                com.naviveylin.core.SurfaceOrigin.ENGINE,
                outcome.failure!!.origin
            )
            // The native description is a log detail, never the user's message.
            assertTrue(
                "the description names the failing end: ${outcome.detail}",
                outcome.detail?.contains("destination") == true
            )
        } finally {
            client.close()
        }
    }
}
