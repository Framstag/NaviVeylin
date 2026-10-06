package com.naviveylin.route

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.NavigationListener
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.haversineDistanceMeters
import com.naviveylin.ui.route.StepAnchor
import com.naviveylin.ui.route.instructionAnchors
import com.naviveylin.ui.route.isInstructionLine
import com.naviveylin.ui.route.stepSegments
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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

    /** How long a synthetic fix is given to reach the engine's listener before it is read. */
    private val FIX_SETTLE_MS = 1_200L

    private fun client(): OSMScoutClient {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mapsDir = File(context.filesDir, "maps")
        // The shipped scan, not a second one: the app's map manager stores a downloaded region under
        // its provider path (`europe/germany/nordrhein-westfalen-<version>`), and
        // `InstalledMaps.findDatabaseDirectories` already walks that tree and skips the basemap (a
        // rendering lookup database with no routing data). Duplicating the walk here made the two
        // discoveries drift - the first version looked one level deep, found nothing and made every
        // case report "No databases loaded".
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
    fun perStepValuesAreTheStepsOwnLegs() {
        val client = client()
        try {
            val route = calculateRoute(client)

            val descriptions = route.descriptions
            assertNotNull("a calculated route must carry description lines", descriptions)
            val instructionLines = descriptions!!.count { isInstructionLine(it) }
            assertTrue("the route must have instructions", instructionLines > 0)

            val distances = route.instructionDistances
            val times = route.instructionTimes
            assertNotNull("per-step distances must be published", distances)
            assertNotNull("per-step times must be published", times)
            assertEquals(
                "one distance per instruction line",
                instructionLines, distances!!.size
            )
            assertEquals("one time per instruction line", instructionLines, times!!.size)

            // The native guard drops the values *and* the positions together, so a reader never sees a
            // half-filled route: either all four arrays are present and aligned, or all four are
            // absent (spec: osmscout-jni — Unaligned values are absent, not shifted). The app's own
            // fallback for the absent case is unit-tested; this is the device-side half of the rule - a
            // real dataset can only show the present-and-aligned branch.
            val arrays = listOf(route.instructionLats, route.instructionLons, distances, times)
            val present = arrays.count { it != null }
            assertTrue(
                "the four per-step arrays are all present or all absent, never partial " +
                    "(present=$present of ${arrays.size}, sizes=${arrays.map { it?.size }})",
                present == 0 || present == arrays.size
            )
            assertEquals(
                "the present arrays are aligned with the instruction lines",
                List(arrays.size) { instructionLines },
                arrays.map { it?.size }
            )

            // The start line owns no leg, and the values are the legs the analysis highlights.
            assertEquals("the start line owns a zero-length leg", 0.0, distances[0], 1.0)
            assertTrue("every leg distance is non-negative", distances.all { it >= 0.0 })
            assertTrue("the first turn onward carries legs", distances.drop(1).any { it > 100.0 })

            // Each step's values belong to the leg between two consecutive manoeuvres: an edge of the
            // leg would be a small fraction of that distance, which is the defect this change removes.
            // The independent witness is the manoeuvre positions themselves - the straight line between
            // them bounds the leg from below (a leg can only be longer than the line it connects).
            val anchors = instructionAnchors(route)
            var compared = 0
            if (anchors.size == instructionLines) {
                for (index in 1 until instructionLines) {
                    val straight = haversineDistanceMeters(
                        anchors[index - 1].lat, anchors[index - 1].lon,
                        anchors[index].lat, anchors[index].lon
                    )
                    if (straight < 100.0) continue
                    compared++
                    assertTrue(
                        "step $index covers ${distances[index].toInt()} m of the " +
                            "${straight.toInt()} m between its manoeuvres - that is an edge, not the leg",
                        distances[index] >= straight / 2.0
                    )
                    assertTrue(
                        "step $index's leg ${distances[index].toInt()} m is implausibly longer than the " +
                            "${straight.toInt()} m between its manoeuvres",
                        distances[index] <= straight * 2.5 + 200.0
                    )
                    // A leg of several hundred metres takes minutes, not the seconds of its last edge
                    // (spec: osmscout-jni — A city step is reported in minutes, not in seconds).
                    if (distances[index] >= 300.0) {
                        assertTrue(
                            "step $index's leg of ${distances[index].toInt()} m reports only " +
                                "${times[index].toInt()} s",
                            times[index] >= 20.0
                        )
                    }
                }
            }

            // The sum of the legs covers the route once and IS the route's total: the bridge publishes
            // the description's own total (spec: osmscout-jni - One route length for a calculated
            // route). The pre-fix bridge broke exactly this, where each row was the last geometry edge
            // before its manoeuvre (device 2026-10-04: "14 m" + "2 s" on a 17.3 km route, a few hundred
            // metres in total, i.e. two orders of magnitude short).
            //
            // Measured on device 2026-10-05 (Dortmund Hbf -> Cologne Hbf, ~70 km) BEFORE the fix: the
            // legs summed to 97 416 m while `route.distance` reported 72 771 m, from the router's
            // separately accumulated distance - a 25 % gap whose native half is filed as TODO.md 139
            // (§129 is the user-visible half). After it, the two are the same number: this case
            // therefore asserts agreement within rounding, not an order of magnitude.
            val sumMeters = distances.sum()
            val totalMeters = route.distance
            val sumSeconds = times!!.sum()
            // The measurement itself (task 5.1): numbers only, no coordinates (spec: auto-diagnostics).
            // Read it with `adb logcat -s RouteDeviceTest`; the ratio against `RouteEntry.distance`
            // is the number this change is judged by (legs = 1.0, the pre-fix router figure 0.748 and
            // the pre-fix fragments ~0.01).
            Log.i(
                "RouteDeviceTest",
                "per-step values: steps=${distances.size} sumM=${sumMeters.toInt()} " +
                    "totalM=${totalMeters.toInt()} ratio=${sumMeters / totalMeters} " +
                    "sumS=${sumSeconds.toInt()} firstM=${distances.first().toInt()} " +
                    "maxLegM=${distances.max().toInt()} maxLegS=${times.max().toInt()} " +
                    "legsCompared=$compared"
            )
            assertTrue("the route must report a distance", totalMeters > 1000.0)
            val ratio = sumMeters / totalMeters
            // Tightened by `fix-route-length-disagreement` (TODO.md 129/139): the bound was 0.5, loose
            // enough to accept the very defect the change removed (the router's figure sat at 0.748).
            // The route's total and its legs are the same figure now, so agreement is asserted within the
            // rounding of the per-step values - the measured pairs were exactly equal on all three routes.
            assertTrue(
                "per-step distances must sum to the route's total (sum=${sumMeters.toInt()} m, " +
                    "total=${totalMeters.toInt()} m, ratio=$ratio)",
                abs(ratio - 1.0) < 0.02
            )
            assertTrue(
                "per-step times must add up to a plausible drive (sum=${sumSeconds.toInt()} s)",
                sumSeconds > 0.0
            )
        } finally {
            client.close()
        }
    }

    /** Routes one explicit candidate pair, or returns null when it cannot be routed on this map data. */
    private fun routeFor(client: OSMScoutClient, candidate: Candidate): RouteEntry? {
        val latch = CountDownLatch(1)
        var route: RouteEntry? = null
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
                    latch.countDown()
                }

                override fun onCancel() {
                    latch.countDown()
                }
            }
        )
        if (!latch.await(180, TimeUnit.SECONDS)) return null
        return route
    }

    /**
     * THE MEASUREMENT of change `fix-route-length-disagreement` (tasks 1.1/1.2): a long and a short
     * route are each measured three ways. Two are the native figures - the router's overall distance
     * (`RouteEntry.distance`) and the description's own total (the sum of the per-step legs) - and
     * the third is independent of both: the length of the polyline the bridge published
     * (`latitudes`/`longitudes`) as great-circle distances between consecutive vertices. Whichever
     * native figure the polyline matches is the route's length as the driver drives it (`TODO.md`
     * 129 decides which of the two becomes the contract).
     *
     * Numbers and route names only, no coordinates (spec: `auto-diagnostics`). Read it with
     * `adb logcat -s RouteDeviceTest`.
     */
    @Test
    fun routeLengthsAreMeasuredForALongAndAShortRoute() {
        val client = client()
        try {
            // Every candidate, so one run reports both a long and a short route: which pairs are
            // routable is the map data's decision, not this test's.
            var routed = 0
            for (candidate in candidates) {
                val route = routeFor(client, candidate)
                if (route == null) {
                    Log.i("RouteDeviceTest", "lengths: ${candidate.name}: not routable on this map data")
                    continue
                }
                val lats = route.latitudes
                val lons = route.longitudes
                if (lats == null || lons == null || lats.size < 2) {
                    Log.i("RouteDeviceTest", "lengths: ${candidate.name}: no polyline")
                    continue
                }
                routed++
                var polylineMeters = 0.0
                for (index in 1 until lats.size) {
                    polylineMeters += haversineDistanceMeters(
                        lats[index - 1], lons[index - 1], lats[index], lons[index]
                    )
                }
                val legsSum = route.instructionDistances?.sum() ?: 0.0
                val total = route.distance
                Log.i(
                    "RouteDeviceTest",
                    "lengths: ${candidate.name}: vertices=${lats.size} " +
                        "routerTotalM=${total.toInt()} descriptionTotalM=${legsSum.toInt()} " +
                        "polylineM=${polylineMeters.toInt()} " +
                        "routerOverPoly=${total / polylineMeters} " +
                        "descriptionOverPoly=${legsSum / polylineMeters} " +
                        "descriptionOverRouter=${if (total > 0.0) legsSum / total else 0.0}"
                )
                assertTrue("the route must report a distance", total > 0.0)
                assertTrue("the published polyline must have a length", polylineMeters > 0.0)
            }
            assumeTrue("no candidate of the measurement pair routes on this map data", routed > 0)
        } finally {
            client.close()
        }
    }

    @Test
    fun liveGuidanceReportsTheRemainingTimeOfTheLegItApproaches() {
        val client = client()
        try {
            val route = calculateRoute(client)
            val anchors = instructionAnchors(route)
            assumeTrue("no per-step positions on this map data", anchors.size >= 3)
            val lats = route.latitudes
            val lons = route.longitudes
            val segments = stepSegments(lats, lons, anchors.size, anchors)

            // The first leg long enough to place two fixes inside it, kept near the route's start so
            // the engine's own route node is close to where the first fix lands.
            fun straightFor(index: Int): Double = haversineDistanceMeters(
                anchors[index - 1].lat, anchors[index - 1].lon,
                anchors[index].lat, anchors[index].lon
            )
            val legIndex = (1 until anchors.size).firstOrNull { straightFor(it) >= 300.0 } ?: 1
            val range = segments[legIndex]
            assumeTrue("the chosen leg has no polyline range", !range.isEmpty() && range.last > range.first)
            val startVertex = range.first
            val laterVertex = range.first + ((range.last - range.first) * 0.7).toInt()
            assumeTrue("the chosen leg is too short for two fixes", laterVertex > startVertex)

            val instructionList = java.util.Collections.synchronizedList(mutableListOf<RouteInstruction>())
            val latest = java.util.concurrent.atomic.AtomicReference<RouteInstruction?>(null)
            val listLatch = CountDownLatch(1)
            // Only the FIRST emission: the engine re-emits the list when the route changes, and after a
            // position update that list is a window from the new current node (fewer remaining steps),
            // so accumulating them would count the route's time once per emission.
            val firstListSeen = java.util.concurrent.atomic.AtomicBoolean(false)
            val listener = object : NavigationListener {
                override fun onRouteInstructions(instructions: Array<RouteInstruction>) {
                    if (firstListSeen.compareAndSet(false, true)) {
                        instructionList.addAll(instructions)
                        listLatch.countDown()
                    }
                }

                override fun onNextRouteInstruction(instruction: RouteInstruction) {
                    latest.set(instruction)
                }
            }

            val controller = client.startNavigation(route.routeHandle, listener)
            assumeTrue("the engine refused the route handle", controller != null)
            try {
                // Two fixes inside the same leg: both name the same manoeuvre, so the second report's
                // remaining time must be the smaller one (spec: osmscout-jni — The arrival estimate
                // shrinks while the leg is driven). No motion and no emulator console needed.
                fun fixAndRead(vertex: Int): Double {
                    controller!!.processLocation(
                        lats[vertex], lons[vertex], 13.0, 5.0, System.currentTimeMillis()
                    )
                    Thread.sleep(FIX_SETTLE_MS)
                    return latest.get()?.timeTo ?: -1.0
                }

                var before = -1.0
                for (attempt in 0 until 3) {
                    before = fixAndRead(startVertex)
                    if (before > 0.0) break
                }
                assumeTrue(
                    "the engine published no instruction for synthetic fixes (TODO 16: a synthetic " +
                        "fix carries no bearing, so PositionAgent may not establish travel direction)",
                    before > 0.0
                )
                val manoeuvre = latest.get()?.description ?: ""

                var after = -1.0
                for (attempt in 0 until 3) {
                    after = fixAndRead(laterVertex)
                    if (after >= 0.0 && latest.get()?.description == manoeuvre) break
                }
                Log.i(
                    "RouteDeviceTest",
                    "live guidance: leg=$legIndex beforeS=${before.toInt()} afterS=${after.toInt()} " +
                        "steps=${instructionList.size}"
                )
                assertEquals(
                    "the fixes must stay inside one leg", manoeuvre, latest.get()?.description ?: ""
                )
                assertTrue(
                    "the remaining time of '$manoeuvre' must shrink while its leg is driven " +
                        "(before=${before.toInt()} s, after=${after.toInt()} s)",
                    after > 0.0 && after < before
                )

                // The engine's instruction list and the route's per-step values are the same legs, so
                // their times must agree (spec: osmscout-jni — The two step lists of one route agree).
                if (listLatch.await(5, TimeUnit.SECONDS) && instructionList.isNotEmpty()) {
                    val listSeconds = instructionList.sumOf { it.timeTo }
                    val perStepSeconds = route.instructionTimes?.sum() ?: 0.0
                    Log.i(
                        "RouteDeviceTest",
                        "step-list parity: engineS=${listSeconds.toInt()} perStepS=${perStepSeconds.toInt()} " +
                            "engineSteps=${instructionList.size} perStepCount=${route.instructionTimes?.size}"
                    )
                    val tolerance = maxOf(60.0, perStepSeconds * 0.25)
                    assertTrue(
                        "the engine's instruction list (${listSeconds.toInt()} s) and the route's " +
                            "per-step times (${perStepSeconds.toInt()} s) must describe the same legs",
                        kotlin.math.abs(listSeconds - perStepSeconds) <= tolerance
                    )
                }
            } finally {
                controller.stop()
            }
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

                // A step owns the leg that **leads to** its manoeuvre (spec: route-analysis), so the
                // segment's LAST vertex is the step's own manoeuvre and its FIRST vertex is the
                // previous step's. Before this was corrected the segment was oriented the other way
                // and the assertion compared the previous manoeuvre's vertex with this step's
                // anchor - which is why it reported 198 m (2026-10-05, TODO.md §131).
                val ownAnchor: StepAnchor = anchors[index]
                val ownOffset = haversineDistanceMeters(
                    ownAnchor.lat, ownAnchor.lon, lats[range.last], lons[range.last]
                )
                assertTrue(
                    "step $index's segment ends ${ownOffset.toInt()} m away from its manoeuvre",
                    ownOffset <= maxOffsetMeters
                )
                if (index > 0) {
                    val previous: StepAnchor = anchors[index - 1]
                    val startOffset = haversineDistanceMeters(
                        previous.lat, previous.lon, lats[range.first], lons[range.first]
                    )
                    assertTrue(
                        "step $index's segment starts ${startOffset.toInt()} m away from the " +
                            "previous manoeuvre",
                        startOffset <= maxOffsetMeters
                    )
                }
            }
        } finally {
            client.close()
        }
    }
}
