package com.naviveylin.route

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.haversineDistanceMeters
import com.naviveylin.ui.route.StepAnchor
import com.naviveylin.ui.route.instructionAnchors
import com.naviveylin.ui.route.isInstructionLine
import com.naviveylin.ui.route.stepSegments
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
 * Device-only check of the per-step manoeuvre positions (spec: `route-analysis`, task 10.3
 * of change `route-planning-session`): the native instruction arrays must be index-aligned
 * with the instruction lines, and each step's position must lie on the route polyline.
 *
 * This is why the check is an instrumented test rather than a host test: the JNI stub used
 * by the JVM suite ships no symbols, so `RouteDescription` extraction runs on a device only.
 * It routes directly through the client (no Compose map), so it does not depend on the
 * emulator's rendering performance. It is skipped when no installed map database is present.
 */
@RunWith(AndroidJUnit4::class)
class RouteInstructionPositionDeviceTest {

    /**
     * Candidate routes, tried in order: the check only needs *a* route whose instructions
     * and polyline can be compared, so the map data decides which pair works. The names are
     * reported in the failure message, so a dataset without routable data is visible as
     * such instead of looking like a code failure.
     */
    private val candidates = listOf(
        Candidate("Dortmund centre -> Dortmund north", 51.5136, 7.4653, 51.5400, 7.5000),
        Candidate("Dortmund Hbf -> Cologne Hbf", 51.5177, 7.4592, 50.9430, 6.9580),
        Candidate("Dortmund Hbf -> Bochum Hbf", 51.5177, 7.4592, 51.4785, 7.2260),
        Candidate("short hop within Dortmund", 51.5136, 7.4653, 51.5200, 7.4700),
    )

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** A position must sit within this distance of the polyline it claims to be on. */
    private val maxOffsetMeters = 60.0

    private fun client(): OSMScoutClient {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mapsDir = File(context.filesDir, "maps")
        // The basemap is a rendering lookup database with no routing data, and the app opens it
        // through its own lookup directory rather than as a route database. Handing it to
        // openDatabases() together with the real maps made the routing service fail with
        // "Route calculation failed" on a dataset that does route (the first device run).
        val mapDirs = mapsDir.listFiles { file -> file.isDirectory && file.name != "basemap" }
            ?.map { it.absolutePath }
            .orEmpty()
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
                if (entry.descriptions?.isNotEmpty() == true) return entry
                failures += "${candidate.name}: no description lines"
            } ?: run { failures += "${candidate.name}: ${failure ?: "no route"}" }
        }
        throw AssertionError("no candidate routed on this map data: ${failures.joinToString("; ")}")
    }

    @Test
    fun instructionPositionsAreAlignedWithTheInstructionLines() {
        val client = client()
        try {
            val route = calculateRoute(client)

            val descriptions = route.descriptions
            assertNotNull("a calculated route must carry description lines", descriptions)
            val instructionLines = descriptions!!.count { isInstructionLine(it) }
            assertTrue("the route must have instructions", instructionLines > 0)

            val lats = route.instructionLats
            val lons = route.instructionLons
            assertNotNull("per-instruction latitudes must be published", lats)
            assertNotNull("per-instruction longitudes must be published", lons)
            assertEquals(
                "one position per instruction line (the '--- Route ---' header is excluded)",
                instructionLines, lats!!.size
            )
            assertEquals(instructionLines, lons!!.size)

            val anchors = instructionAnchors(route)
            assertEquals(
                "the app-side guard must accept the aligned native arrays",
                instructionLines, anchors.size
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun everyInstructionPositionLiesOnTheRoutePolyline() {
        val client = client()
        try {
            val route = calculateRoute(client)
            val anchors = instructionAnchors(route)
            assumeTrue("no per-step positions on this map data", anchors.isNotEmpty())

            val lats = route.latitudes
            val lons = route.longitudes
            assertTrue("the route must have a polyline", lats.size >= 2)

            anchors.forEachIndexed { index, anchor ->
                val nearest = (lats.indices).minOf { vertex ->
                    haversineDistanceMeters(anchor.lat, anchor.lon, lats[vertex], lons[vertex])
                }
                assertTrue(
                    "instruction $index at (${anchor.lat}, ${anchor.lon}) is ${nearest.toInt()} m " +
                        "off the polyline (limit ${maxOffsetMeters.toInt()} m)",
                    nearest <= maxOffsetMeters
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun analysedStepSegmentsStayMonotonicAndOnThePolyline() {
        val client = client()
        try {
            val route = calculateRoute(client)
            val anchors = instructionAnchors(route)
            assumeTrue("no per-step positions on this map data", anchors.isNotEmpty())

            val lats = route.latitudes
            val lons = route.longitudes
            val stepCount = anchors.size
            val segments = stepSegments(lats, lons, stepCount, anchors)

            assertEquals("one range per step", stepCount, segments.size)

            // Ranges are non-decreasing and never run past the polyline: the monotonic match
            // is what keeps a folded route (a U-turn visiting the same area twice) from
            // highlighting the earlier visit.
            var previousStart = -1
            segments.forEachIndexed { index, range ->
                if (range.isEmpty()) return@forEachIndexed
                assertTrue("step $index starts before the previous step", range.first >= previousStart)
                assertTrue("step $index runs past the polyline", range.last < lats.size)
                previousStart = range.first

                // The step's own manoeuvre is at its segment's first vertex.
                val anchor: StepAnchor = anchors[index]
                val offset = haversineDistanceMeters(
                    anchor.lat, anchor.lon, lats[range.first], lons[range.first]
                )
                assertTrue(
                    "step $index's segment starts ${offset.toInt()} m away from its manoeuvre",
                    offset <= maxOffsetMeters
                )
            }
        } finally {
            client.close()
        }
    }
}
