package com.naviveylin.route

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.haversineDistanceMeters
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.AfterClass
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device check of the position a calculated route publishes its start at (spec: `osmscout-jni` — *The
 * published route starts at the requested start position*, *A start on the network is published
 * unchanged*, *A snapped start does not split one route into two lengths*; `route-analysis` — *The route's
 * start anchor is the published start position*).
 *
 * This runs on a device because the JVM suite's JNI stub ships no symbols: `ResolveRoutePosition`'s
 * snapping, `TransformRouteDataToPoints` and the step collector exist only in the real `.so`.
 *
 * **What the cases assert.** The router uses the caller's start coordinate only to find a routable node and
 * then discards it, so on an off-network start the published polyline and the first step began at the
 * snapped node. The contract is that both begin at the requested position, with the approach to the
 * snapped node as the polyline's first segment — while a start that is already the routed node publishes
 * that vertex exactly, with no duplicate leading vertex.
 *
 * **One client for the class, not one per case** (measured 2026-10-29): a second `OSMScoutClient` built in
 * the same process against the same map directory answered `No databases loaded` for its route while the
 * first one worked, and the case passed when run alone — a build-up artefact of four clients in one
 * process, not a property of the route.
 *
 * **The map set is whatever is installed** — `europe/andorra` on the reference AVD, the smallest routable
 * region the provider offers — and every coordinate below is inside its bounding box. The hillside start
 * is off the carriageway by construction; when a dataset has no routable data near it the case says so
 * rather than passing quietly, because the off-network shape is what it exists to measure.
 *
 * Every message reports metres and names, never a coordinate pair: the numbers are what a run is read for.
 */
@RunWith(AndroidJUnit4::class)
class RouteStartPositionDeviceTest {

    private data class Point(val name: String, val lat: Double, val lon: Double)

    private data class Outcome(val route: RouteEntry?, val detail: String?)

    companion object {

        /** The snap tolerance the bridge decides with (`kRouteStartSnapToleranceMeters`). */
        private const val requestedStartToleranceMeters = 1.0

        /** The tolerance the analysis' "position lies on the drawn polyline" claim uses. */
        private const val polylineMatchMeters = 60.0

        /** The agreement the published numbers owe each other (spec: `osmscout-jni` — one route, one length). */
        private const val lengthTolerance = 0.02

        /** A start on the carriageway of the valley road, for the derived unchanged case. */
        private val roadStart = Point("Andorra la Vella, centre", 42.5078, 1.5211)

        /**
         * A start on the hillside above the valley road — off the routable network by more than a hundred
         * metres, so the approach the route must publish is the one this change is about.
         */
        private val offNetworkStart = Point("hillside above Andorra la Vella", 42.5150, 1.5350)

        /** A town centre of the same dataset, so both routes are real ones across the installed region. */
        private val target = Point("Encamp, town centre", 42.53550, 1.58100)

        private var client: OSMScoutClient? = null

        @JvmStatic
        @BeforeClass
        fun openMaps() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val mapsDir = File(context.filesDir, "maps")
            val mapDirs = InstalledMaps.findDatabaseDirectories(mapsDir.absolutePath, "basemap")
            assumeTrue("no installed map directory to route on", mapDirs.isNotEmpty())

            val built = OSMScoutClientBuilder()
                .withMapLookupDirectories(mapsDir.absolutePath)
                .withPhysicalDpi(420.0)
                .build()
            val opened = built.openDatabases(mapDirs.toTypedArray()).count { it }
            assumeTrue("no map database could be opened", opened > 0)
            client = built
        }

        @JvmStatic
        @AfterClass
        fun closeMaps() {
            client?.close()
            client = null
        }

        private fun route(from: Point, to: Point): Outcome =
            routeWith(from.lat, from.lon, 0L, null, to, 0L, null)

        /**
         * The same calculation with the caller's object references — the form a search result is routed
         * with, where the router may resolve the endpoint through the object instead of the coordinate.
         */
        private fun routeStartingAtObject(entry: LocationEntry, to: Point): Outcome =
            routeWith(
                entry.lat, entry.lon, entry.objectFileOffset, entry.objectType,
                to, 0L, null
            )

        private fun routeWith(
            startLat: Double,
            startLon: Double,
            startObjOffset: Long,
            startObjType: String?,
            to: Point,
            destObjOffset: Long,
            destObjType: String?
        ): Outcome {
            val latch = CountDownLatch(1)
            var route: RouteEntry? = null
            var detail: String? = null

            client!!.calculateRouteWithObjectsAsync(
                startLat, startLon, startObjOffset, startObjType,
                to.lat, to.lon, destObjOffset, destObjType,
                RoutingProfile(),
                object : RouteCallback {
                    override fun onProgress(percent: Int) = Unit

                    override fun onSuccess(entry: RouteEntry) {
                        route = entry
                        latch.countDown()
                    }

                    override fun onErrorDetail(
                        kind: Int,
                        message: String,
                        nearestRoadDistanceMeters: Double
                    ) {
                        detail = message
                        latch.countDown()
                    }

                    override fun onError(message: String) {
                        detail = message
                        latch.countDown()
                    }

                    override fun onCancel() {
                        latch.countDown()
                    }
                }
            )

            assertTrue("the calculation did not answer in 30 s", latch.await(30, TimeUnit.SECONDS))
            return Outcome(route, detail)
        }

        /** The length of the drawn polyline, on the same sphere the app's own distance uses. */
        private fun polylineLengthMeters(lats: DoubleArray, lons: DoubleArray): Double {
            var sum = 0.0
            for (i in 1 until lats.size) {
                sum += haversineDistanceMeters(lats[i - 1], lons[i - 1], lats[i], lons[i])
            }
            return sum
        }

        /** The distance from a position to the nearest vertex of the drawn polyline. */
        private fun distanceToPolyline(lat: Double, lon: Double, lats: DoubleArray, lons: DoubleArray): Double {
            var nearest = Double.POSITIVE_INFINITY
            for (i in lats.indices) {
                val d = haversineDistanceMeters(lat, lon, lats[i], lons[i])
                if (d < nearest) nearest = d
            }
            return nearest
        }

        private fun metres(value: Double) = "${value.roundToInt()} m"

        /** Routes and asserts that the map produced one, naming the bridge's own reason when it did not. */
        private fun routedEntry(from: Point, to: Point): RouteEntry {
            val outcome = route(from, to)
            assertNotNull(
                "a route from ${from.name} to ${to.name} across the installed map must route " +
                    "(${outcome.detail})",
                outcome.route
            )
            return outcome.route!!
        }

        /**
         * The first search result that carries an OSM object reference: the shape a user's tap on a search
         * row routes with, where the position is the object's own (a building's, a POI's) and not a road.
         */
        private fun searchObjectStart(query: String): LocationEntry? =
            client!!.searchLocations(query, 10, 0L)
                ?.firstOrNull { it.objectFileOffset != 0L && !it.objectType.isNullOrEmpty() }
    }

    @Test
    fun publishedStartIsTheRequestedStartPosition() {
        val entry = routedEntry(offNetworkStart, target)

        val lats = entry.latitudes
        val lons = entry.longitudes
        assertNotNull("the route carries a polyline", lats)
        assertNotNull("the route carries a polyline", lons)
        assertTrue("the polyline has at least two points", lats!!.size >= 2)

        val publishedGap = haversineDistanceMeters(offNetworkStart.lat, offNetworkStart.lon, lats[0], lons!![0])
        val startStep = entry.steps?.firstOrNull()
        val stepGap = startStep?.let { haversineDistanceMeters(offNetworkStart.lat, offNetworkStart.lon, it.lat, it.lon) }
        val approach = haversineDistanceMeters(lats[0], lons[0], lats[1], lons[1])

        android.util.Log.i(
            "RouteStartPosition",
            "requested start vs published first point ${metres(publishedGap)}, " +
                "vs first step ${stepGap?.let { metres(it) } ?: "no step"}, " +
                "first segment ${metres(approach)}, total ${metres(entry.distance)}"
        )

        assertTrue(
            "the published polyline starts ${metres(publishedGap)} from the requested start " +
                "(${offNetworkStart.name}); it must start where the caller asked to depart",
            publishedGap <= requestedStartToleranceMeters
        )
        assertNotNull("the route carries structured steps", startStep)
        assertTrue(
            "the first step is published ${stepGap?.let { metres(it) } ?: "without a position"} " +
                "from the requested start; it must be the same position as the polyline's first point",
            stepGap != null && stepGap <= requestedStartToleranceMeters
        )

        // The approach is a real first segment, not a duplicated vertex: the requested position is the
        // polyline's first point and the snapped node the second one. When the start is on the network
        // there is no approach at all, and this case would pass trivially — so it says which it saw.
        assertTrue(
            "the chosen start was already on the routable network (first segment ${metres(approach)}); " +
                "this case measures an off-network start and needs one",
            approach > requestedStartToleranceMeters
        )
        assertTrue(
            "the start step publishes distance-from-start ${metres(startStep!!.distanceFromStart)}; " +
                "the route's start is the route's start",
            abs(startStep.distanceFromStart) <= requestedStartToleranceMeters
        )
    }

    @Test
    fun firstStepPositionLiesOnThePolyline() {
        val entry = routedEntry(offNetworkStart, target)

        val lats = entry.latitudes
        val lons = entry.longitudes
        val startStep = entry.steps?.firstOrNull()
        assertNotNull("the route carries a polyline", lats)
        assertNotNull("the route carries a polyline", lons)
        assertNotNull("the route carries structured steps", startStep)

        val offPolyline = distanceToPolyline(startStep!!.lat, startStep.lon, lats!!, lons!!)
        android.util.Log.i(
            "RouteStartPosition",
            "first step is ${metres(offPolyline)} from the nearest polyline vertex"
        )
        assertTrue(
            "the first step's position is ${metres(offPolyline)} from the drawn polyline; " +
                "an analysed step must be locatable on the route it belongs to",
            offPolyline <= polylineMatchMeters
        )
    }

    @Test
    fun snappedStartKeepsOneRouteLength() {
        val entry = routedEntry(offNetworkStart, target)

        val total = entry.distance
        val steps: Array<RouteStep>? = entry.steps
        assertNotNull("the route carries structured steps", steps)
        val legs = steps!!.sumOf { it.legDistance }
        val drawn = polylineLengthMeters(entry.latitudes!!, entry.longitudes!!)
        val lastDistance = steps.last().distanceFromStart

        android.util.Log.i(
            "RouteStartPosition",
            "lengths: total ${metres(total)}, step legs ${metres(legs)}, " +
                "last step from start ${metres(lastDistance)}, drawn polyline ${metres(drawn)}"
        )

        assertTrue(
            "the published total ${metres(total)} and the step legs ${metres(legs)} are one route, " +
                "but they differ by more than ${(lengthTolerance * 100).roundToInt()} %",
            abs(legs - total) <= total * lengthTolerance
        )
        assertTrue(
            "the published total ${metres(total)} and the drawn polyline ${metres(drawn)} are one " +
                "route, but they differ by more than ${(lengthTolerance * 100).roundToInt()} %",
            abs(drawn - total) <= total * lengthTolerance
        )
    }

    /**
     * A start that **is** the routed node publishes that vertex unchanged — the case that keeps the change
     * from prepending an approach to an ordinary route. It is derived rather than guessed: the first
     * calculation's published first vertex is the router's own node, so the second calculation starts
     * exactly there. It passes before the change and must keep passing after it (task 1.3 predicted
     * exactly that; the guard is against a duplicate leading vertex once the prepend exists).
     */
    @Test
    fun routeWithAnOnNetworkStartIsPublishedUnchanged() {
        val first = routedEntry(roadStart, target)
        val node = Point(
            "the routed first vertex",
            first.latitudes!![0],
            first.longitudes!![0]
        )

        val entry = routedEntry(node, target)
        val lats = entry.latitudes
        val lons = entry.longitudes
        assertNotNull("the route carries a polyline", lats)
        assertTrue("the polyline has at least two points", lats!!.size >= 2)

        val gap = haversineDistanceMeters(node.lat, node.lon, lats[0], lons!![0])
        val firstSegment = haversineDistanceMeters(lats[0], lons[0], lats[1], lons[1])

        android.util.Log.i(
            "RouteStartPosition",
            "on-network start: published first point ${metres(gap)} from the request, " +
                "first segment ${metres(firstSegment)}"
        )

        assertTrue(
            "a start that is the routed node publishes that vertex, but it is ${metres(gap)} away",
            gap <= requestedStartToleranceMeters
        )
        assertTrue(
            "an on-network start must not add a duplicate first vertex, but the first segment is " +
                metres(firstSegment),
            firstSegment > requestedStartToleranceMeters
        )
    }

    /**
     * A start given as an **object reference** — a search result, whose position is the object's own and
     * not a road — publishes that object's position as the route's start, exactly as a plain coordinate
     * does. The object's position is what the user tapped; the road the object sits on is not.
     */
    @Test
    fun startFromAnObjectOffsetIsPublished() {
        val objectStart = searchObjectStart("Andorra la Vella")
        assertNotNull(
            "the installed dataset yielded no search result with an OSM object reference, so the " +
                "object-start case has nothing to route from",
            objectStart
        )

        val outcome = routeStartingAtObject(objectStart!!, target)
        assertNotNull(
            "a route from the object '${objectStart.label}' to ${target.name} must route " +
                "(${outcome.detail})",
            outcome.route
        )

        val entry = outcome.route!!
        val lats = entry.latitudes
        val lons = entry.longitudes
        assertNotNull("the route carries a polyline", lats)
        assertTrue("the polyline has at least two points", lats!!.size >= 2)

        val publishedGap = haversineDistanceMeters(objectStart.lat, objectStart.lon, lats[0], lons!![0])
        val startStep = entry.steps?.firstOrNull()
        val stepGap = startStep?.let { haversineDistanceMeters(objectStart.lat, objectStart.lon, it.lat, it.lon) }
        val approach = haversineDistanceMeters(lats[0], lons[0], lats[1], lons[1])

        android.util.Log.i(
            "RouteStartPosition",
            "object start '${objectStart.label}' (${objectStart.objectType}): published first point " +
                "${metres(publishedGap)} from the object, vs first step " +
                "${stepGap?.let { metres(it) } ?: "no step"}, first segment ${metres(approach)}"
        )

        assertTrue(
            "the published polyline starts ${metres(publishedGap)} from the object the caller passed; " +
                "an object start is published as that object's position",
            publishedGap <= requestedStartToleranceMeters
        )
        assertTrue(
            "the first step is published ${stepGap?.let { metres(it) } ?: "without a position"} from the " +
                "object; it must be the same position as the polyline's first point",
            stepGap != null && stepGap <= requestedStartToleranceMeters
        )
        assertTrue(
            "the object's position was already the routed node (first segment ${metres(approach)}); " +
                "the case needs an object that sits off the network",
            approach > requestedStartToleranceMeters
        )
    }
}
