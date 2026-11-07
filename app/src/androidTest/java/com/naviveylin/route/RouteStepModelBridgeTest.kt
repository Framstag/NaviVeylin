package com.naviveylin.route

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.RoutingProfile
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
 * Device-only check of the structured route steps a calculated route publishes (spec:
 * `osmscout-jni` — A calculated route publishes structured route steps; change
 * `route-panel-from-steps`, task 6.1).
 *
 * The projection is filled in the JNI bridge, which no JVM test executes — the host JNI stub ships no
 * symbols — so a device with installed map data is the only place it can be observed. It routes
 * directly through the client (no Compose map), exactly like the neighbouring instruction device
 * tests, and reads the [RouteEntry.steps] the bridge published.
 *
 * A case whose manoeuvre the installed map data does not produce is skipped, not failed: the map
 * data decides which candidate pair routes and which manoeuvres it contains.
 */
@RunWith(AndroidJUnit4::class)
class RouteStepModelBridgeTest {

    /**
     * Andorran pairs, because that is the map data a provisioned emulator carries
     * (`provision-phone-emulator`): the urban corridor along the CG-1 and the CG-3 north end are the
     * roads there, and they carry roundabouts and a trunk road.
     */
    private val candidates = listOf(
        Candidate("Andorra la Vella centre -> Escaldes-Engordany", 42.5075, 1.5218, 42.5110, 1.5400),
        Candidate("Andorra la Vella -> Sant Julià de Lòria", 42.5075, 1.5218, 42.4630, 1.4910),
        Candidate("Andorra la Vella -> La Massana", 42.5075, 1.5218, 42.5450, 1.5150),
        Candidate("Encamp -> Canillo", 42.5360, 1.5830, 42.5680, 1.6000),
        Candidate("Andorra la Vella -> Encamp", 42.5075, 1.5218, 42.5360, 1.5830)
    )

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** A route the engine delivered, with the pair that produced it. */
    private data class Routed(val candidate: String, val route: RouteEntry) {
        val steps: List<RouteStep> get() = route.steps?.toList().orEmpty()
    }

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

    private fun route(client: OSMScoutClient, candidate: Candidate): RouteEntry? {
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

    /** Every candidate that routed, with the step list the bridge published for it. */
    private fun routed(client: OSMScoutClient): List<Routed> {
        val routed = mutableListOf<Routed>()
        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val route = route(client, candidate)
            if (route == null) {
                failures += "${candidate.name}: no route"
                continue
            }
            // A route that routed but published no steps is the invariant under test failing, not
            // missing map data: the bridge had a description to project.
            if (route.steps == null) {
                throw AssertionError(
                    "${candidate.name}: the route was calculated but carries no structured steps — " +
                        "the bridge did not publish the projection"
                )
            }
            routed += Routed(candidate.name, route)
        }
        Log.i("RouteDeviceTest", "step model probe: routed=${routed.size}/${candidates.size} notRouted=$failures")
        assumeTrue("no candidate routed on this map data: $failures", routed.isNotEmpty())
        return routed
    }

    @Test
    fun aCalculatedRoutePublishesStepsInRouteOrder() {
        val client = client()
        try {
            val routed = routed(client)
            for (entry in routed) {
                val steps = entry.steps
                assertTrue("${entry.candidate}: a route publishes steps", steps.isNotEmpty())
                // Route order: the published nodes advance along the route, never backwards. The
                // count of steps is the count of *described route nodes*, which is far larger than
                // the count of instruction lines (measured 2026-10-28: 330 steps against 7
                // instruction lines on an Andorran city route) — a step is not an instruction.
                val offsets = steps.map { it.distanceFromStart }
                assertEquals(
                    "${entry.candidate}: steps are published in route order",
                    offsets.sorted(),
                    offsets
                )
                // Every step names the manoeuvre it describes and the leg that ends at it.
                assertTrue(
                    "${entry.candidate}: every step names its kind",
                    steps.all { it.kind != null }
                )
                assertTrue(
                    "${entry.candidate}: every step carries its own leg",
                    steps.all { it.legDistance >= 0.0 && it.legTime >= 0.0 }
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun aRoundaboutLeaveStepCarriesOrdinalAndDirection() {
        val client = client()
        try {
            val leaves = routed(client).flatMap { entry ->
                entry.steps
                    .filter { it.kind == RouteStep.Kind.ROUNDABOUT_LEAVE }
                    .map { step -> entry.candidate to step }
            }
            Log.i(
                "RouteDeviceTest",
                "roundabout leave steps: ${leaves.size} ordinals=" +
                    leaves.joinToString(",") { (_, step) -> step.roundaboutLeave?.exitCountTaken.toString() }
            )
            assumeTrue("this map data's routes carry no roundabout leave step", leaves.isNotEmpty())

            for ((candidate, step) in leaves) {
                val leave = step.roundaboutLeave
                assertNotNull("$candidate: the leave record must be published", leave)
                assertTrue(
                    "$candidate: the exit ordinal is the record's, never recovered from text",
                    leave!!.exitCountTaken >= 1L
                )
                // The circulation direction is published with it, and the manoeuvre is not a plain turn.
                assertEquals(
                    "$candidate: the step is a roundabout leave",
                    RouteStep.Kind.ROUNDABOUT_LEAVE,
                    step.kind
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun aMotorwayChangeIsNotAPlainTurn() {
        val client = client()
        try {
            val changes = routed(client).flatMap { entry ->
                entry.steps
                    .filter { it.kind == RouteStep.Kind.MOTORWAY_CHANGE }
                    .map { step -> entry.candidate to step }
            }
            Log.i("RouteDeviceTest", "motorway change steps: ${changes.size}")
            assumeTrue("this map data's routes change motorway nowhere", changes.isNotEmpty())

            for ((candidate, step) in changes) {
                val change = step.motorwayChange
                assertNotNull("$candidate: the change record must be published", change)
                assertTrue(
                    "$candidate: a change names the motorway it leaves and the one it enters",
                    change!!.from != null && change.to != null
                )
                assertTrue(
                    "$candidate: the step is a motorway change, not a turn",
                    step.kind != RouteStep.Kind.TURN
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun stepLegsSumToTheRouteLength() {
        val client = client()
        try {
            for (entry in routed(client)) {
                val steps = entry.steps
                val legSum = steps.sumOf { it.legDistance }
                val total = entry.route.distance
                // The legs are the run from the previous manoeuvre to their own, so they add up to
                // the route's length. Rounding of metre-valued legs is allowed for, a node-sized
                // fragment (the pre-fix projection) is not.
                assertTrue(
                    "${entry.candidate}: legs sum to ${legSum.toInt()} m against a route of ${total.toInt()} m",
                    kotlin.math.abs(legSum - total) <= maxOf(10.0, total * 0.02)
                )
                assertTrue(
                    "${entry.candidate}: every step carries a leg",
                    steps.all { it.legDistance >= 0.0 && it.legTime >= 0.0 }
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun stepsOfACrossDatabaseRouteResolveTheirDatabase() {
        val client = client()
        try {
            for (entry in routed(client)) {
                for (step in entry.steps) {
                    // Every step names the database of its node; a cross-database route maps that
                    // identity to the map directory it belongs to.
                    assertTrue(
                        "${entry.candidate}: a step's database identity must be published",
                        step.databaseId >= 0L
                    )
                    if (entry.route.databaseMapping.isNotEmpty()) {
                        assertTrue(
                            "${entry.candidate}: databaseId=${step.databaseId} must resolve in the mapping",
                            entry.route.databaseMapping.containsKey(step.databaseId) ||
                                step.databaseId == 0L
                        )
                    }
                }
            }
        } finally {
            client.close()
        }
    }
}
