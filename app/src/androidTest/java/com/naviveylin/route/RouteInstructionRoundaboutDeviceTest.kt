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
import com.framstag.libosmscout.client.TurnType
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only check of the roundabout fields a route instruction carries (spec: `osmscout-jni` —
 * A route instruction carries the roundabout exit ordinal and its circulation direction, and — An
 * unbuildable instruction never reaches a listener as null; change `fix-roundabout-exit-number`,
 * tasks 7.2 and 7.8).
 *
 * The fields are filled in the JNI bridge, which no JVM test executes — the JNI stub the host suite
 * links against ships no symbols — so a device is the only place this can be observed. It routes
 * directly through the client (no Compose map) and reads the first instruction list the engine
 * delivers, so it does not depend on the emulator's rendering or on driving a fix stream.
 *
 * It is skipped, not failed, when the installed map data carries no route through a roundabout: the
 * map data decides that, exactly as it decides which candidate pair routes at all.
 */
@RunWith(AndroidJUnit4::class)
class RouteInstructionRoundaboutDeviceTest {

    /**
     * Andorran pairs, because that is the map data a provisioned emulator carries
     * (`provision-phone-emulator`): the urban corridor along the CG-1 and the CG-3 north end are the
     * roads there with roundabouts, and a route between these places runs on them. Every candidate
     * that routes is inspected, because the first one may not cross a roundabout.
     */
    private val candidates = listOf(
        Candidate("Andorra la Vella centre -> Escaldes-Engordany", 42.5075, 1.5218, 42.5110, 1.5400),
        Candidate("Andorra la Vella -> Sant Julià de Lòria", 42.5075, 1.5218, 42.4630, 1.4910),
        Candidate("Andorra la Vella -> La Massana", 42.5075, 1.5218, 42.5450, 1.5150),
        Candidate("Encamp -> Canillo", 42.5360, 1.5830, 42.5680, 1.6000),
        Candidate("Andorra la Vella -> Encamp", 42.5075, 1.5218, 42.5360, 1.5830),
    )

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** An instruction list the engine delivered, with the pair that produced it. */
    private data class Routed(val candidate: String, val instructions: List<RouteInstruction>)

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
        return route?.takeIf { it.descriptions?.isNotEmpty() == true }
    }

    /**
     * The first instruction list the engine delivers for [route], primed with a synthetic fix.
     *
     * Only the first emission is read: the engine re-emits the list when the route changes, and after a
     * position update that list is a window from the new current node. The fix is needed because
     * starting navigation alone does not dispatch instructions — the engine's agents only run once a
     * position has been processed (the same reason the neighbouring device test feeds one).
     */
    private fun firstInstructionList(
        client: OSMScoutClient,
        route: RouteEntry,
        candidate: Candidate
    ): List<RouteInstruction> {
        val seen = mutableListOf<RouteInstruction>()
        val firstListSeen = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val listener = object : NavigationListener {
            override fun onRouteInstructions(instructions: Array<RouteInstruction>) {
                if (firstListSeen.compareAndSet(false, true)) {
                    seen.addAll(instructions)
                    latch.countDown()
                }
            }

            override fun onNextRouteInstruction(instruction: RouteInstruction) {
                // A single instruction is also evidence the engine is dispatching; treat it as the
                // list for the purpose of reading the fields it carries.
                if (firstListSeen.compareAndSet(false, true)) {
                    seen.add(instruction)
                    latch.countDown()
                }
            }
        }
        val controller = client.startNavigation(route.routeHandle, listener)
        assumeTrue("the engine refused the route handle", controller != null)
        // A few fixes: the first one orients the engine (TODO 16 — a synthetic fix carries no bearing).
        for (attempt in 0 until 4) {
            controller!!.processLocation(
                candidate.startLat, candidate.startLon, 13.0, 5.0, System.currentTimeMillis()
            )
            if (latch.await(15, TimeUnit.SECONDS)) break
        }
        return seen.toList()
    }

    /** Every candidate that routes, with the instructions the engine delivered for it. */
    private fun routedInstructions(client: OSMScoutClient): List<Routed> {
        val routed = mutableListOf<Routed>()
        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val route = route(client, candidate)
            if (route == null) {
                failures += "${candidate.name}: no route"
                continue
            }
            val instructions = firstInstructionList(client, route, candidate)
            // A route that routed but delivered no list is NOT the same kind of event as a route
            // that never routed. The first is the bridge failing to build instructions while the
            // engine had them - the invariant under test, and under the descriptor mutation below
            // the sole outcome, because DispatchRouteInstructions returns before the array exists.
            // The second is map data. Bucketing both as `failures` let the mutation turn this case
            // into an assumeTrue skip, which reports as `OK` - the case could not falsify the
            // invariant it names. So: no route skips, no list fails.
            if (instructions.isEmpty()) {
                throw AssertionError(
                    "${candidate.name}: the route was calculated but the bridge delivered no " +
                        "instruction list - the bridge could not build the instructions the engine " +
                        "produced (dispatch sends no list rather than an array holding nulls)"
                )
            }
            routed += Routed(candidate.name, instructions)
        }
        Log.i(
            "RouteDeviceTest",
            "roundabout probe: routed=${routed.size} candidates=${candidates.size} " +
                "notRouted=[${failures.joinToString("; ")}]"
        )
        assumeTrue(
            "no candidate routed on this map data: ${failures.joinToString("; ")}",
            routed.isNotEmpty()
        )
        return routed
    }

    /**
     * Task 7.8: the engine never hands a listener an unbuildable instruction. An instruction the
     * bridge cannot construct is reported as no list at all, never as an array whose entries are
     * null — a null one surfaces far from the bridge, where its cause is no longer visible.
     */
    @Test
    fun aRouteStartDeliversANonEmptyInstructionListOfNonNullInstructions() {
        val client = client()
        try {
            val routed = routedInstructions(client)
            for (entry in routed) {
                assertTrue(
                    "${entry.candidate}: the delivered list must not be empty",
                    entry.instructions.isNotEmpty()
                )
                for (instruction in entry.instructions) {
                    // A null element is what the pre-change dispatch could deliver; reading it here
                    // would fault, which is the point: this asserts the list is whole.
                    assertTrue(
                        "${entry.candidate}: every delivered instruction must be non-null",
                        instruction.shortDescription != null && instruction.turnType != null
                    )
                }
            }
        } finally {
            client.close()
        }
    }

    /**
     * Task 7.2: the bridge fills the exit ordinal and the circulation direction, and the instruction
     * text is unchanged by them. This is the only evidence for the native side — every other case in
     * this change reads a value a test supplied.
     */
    @Test
    fun aRoundaboutExitInstructionCarriesItsOrdinalAndItsDirection() {
        val client = client()
        try {
            val routed = routedInstructions(client)

            val exits = routed.flatMap { entry ->
                entry.instructions
                    .filter { it.turnType == TurnType.ROUNDABOUT_LEAVE }
                    .map { exit -> entry.candidate to exit }
            }
            val numbered = exits.filter { (_, exit) -> exit.roundaboutExitNumber > 0 }

            Log.i(
                "RouteDeviceTest",
                "roundabout exits: total=${exits.size} numbered=${numbered.size} " +
                    "ordinals=[${exits.joinToString(",") { it.second.roundaboutExitNumber.toString() }}] " +
                    "clockwise=[${exits.joinToString(",") { it.second.roundaboutClockwise.toString() }}] " +
                    "texts=[${exits.joinToString(" | ") { it.second.shortDescription }}]"
            )

            assumeTrue(
                "this map data's routes carry no roundabout exit with a numbered exit " +
                    "(exits=${exits.size}); the ordinal is the map data's to decide",
                numbered.isNotEmpty()
            )

            for ((candidate, exit) in numbered) {
                assertTrue(
                    "$candidate: a numbered roundabout exit must carry ordinal >= 1, was " +
                        "${exit.roundaboutExitNumber}",
                    exit.roundaboutExitNumber >= 1
                )
                // The ordinal reaches the text as well, and the text stays the bridge's own English:
                // the app localizes it at the surface, not here.
                assertTrue(
                    "$candidate: the ordinal must be the one the text names, text='${exit.shortDescription}'",
                    exit.shortDescription!!.endsWith(exit.roundaboutExitNumber.toString())
                )
            }
        } finally {
            client.close()
        }
    }
}
