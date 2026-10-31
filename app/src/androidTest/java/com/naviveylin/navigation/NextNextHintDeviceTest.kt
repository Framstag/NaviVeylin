package com.naviveylin.navigation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.NavigationController
import com.framstag.libosmscout.client.NavigationListener
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.HINT_WINDOW_METERS
import com.naviveylin.core.guidanceStep
import com.naviveylin.core.nextNextGapMeters
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The announced roundabout exit, on real map data (spec: `next-turn-overlay` — "A roundabout exit is
 * announced before the ring is entered"; change `fix-following-manoeuvre-hint`).
 *
 * The JVM cases (`core: ManoeuvreAnnouncementTest`) prove the derivation over records this repository
 * fabricates. What they cannot prove is the *data premise* the derivation rests on: that a route's step
 * list, as live guidance publishes it from real map data, carries the roundabout leave as the manoeuvre
 * following the entry, inside the 200 m window. That premise is what this case measures — no UI, no GPS,
 * no drive: the test calculates a route from the installed map set, drives the native controller with the
 * route's own vertices, and feeds each published (step list, step ahead) pair through the app's own
 * `core/guidanceStep`.
 *
 * Both lines are logged per observation, which is also the evidence the tracker finding about the native
 * record rests on:
 *
 * - `rbt:` of the **published** step — the native record's own hint, expected to be a plain node
 *   (`hintExit=0`),
 * - `rbt:` of the **announced** step — the app-derived hint, which must name the exit (`hintExit=N`).
 */
@RunWith(AndroidJUnit4::class)
class NextNextHintDeviceTest {

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** Andorran pairs: the map data a provisioned emulator carries, with roundabouts in the corridor. */
    private val candidates = listOf(
        Candidate("Andorra la Vella -> Escaldes-Engordany", 42.5075, 1.5218, 42.5110, 1.5400),
        Candidate("Andorra la Vella -> La Massana", 42.5075, 1.5218, 42.5450, 1.5150),
        Candidate("Andorra la Vella -> Encamp", 42.5075, 1.5218, 42.5360, 1.5830)
    )

    /** One observation of an announced roundabout entry and the hint the surface would show with it. */
    private data class Observation(
        val candidate: String,
        val announced: RouteStep,
        val hintKind: RouteStep.Kind?,
        val hintExit: Int,
        val gapMeters: Double,
        val nativeKind: RouteStep.Kind?,
        val nativeHintKind: RouteStep.Kind?,
        val nativeHintExit: Int
    )

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
        if (!latch.await(240, TimeUnit.SECONDS)) return null
        return route
    }

    /** What one navigation session published; every field is written on the engine's own thread. */
    private class Published {
        val list = AtomicReference<List<RouteStep>>(emptyList())
        val next = AtomicReference<RouteStep?>(null)
        val nextCount = AtomicInteger(0)
        val firstNext = CountDownLatch(1)
    }

    private fun listenerFor(published: Published) = object : NavigationListener {
        override fun onRouteSteps(steps: Array<RouteStep>) {
            published.list.set(steps.toList())
        }

        override fun onNextRouteStep(step: RouteStep) {
            published.next.set(step)
            published.nextCount.incrementAndGet()
            published.firstNext.countDown()
        }
    }

    /** Feeds one fix at [vertex] and waits for the engine's next publication, bounded by [timeoutSeconds]. */
    private fun drive(
        controller: NavigationController,
        route: RouteEntry,
        vertex: Int,
        timestamp: Long,
        published: Published,
        timeoutSeconds: Long = 15
    ): Boolean {
        val before = published.nextCount.get()
        controller.processLocation(
            route.latitudes[vertex], route.longitudes[vertex],
            13.0, 5.0, timestamp
        )
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1_000
        while (System.currentTimeMillis() < deadline) {
            if (published.nextCount.get() > before) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun theExitIsAnnouncedBeforeTheRingIsEntered() {
        val client = client()
        try {
            val observations = mutableListOf<Observation>()
            for (candidate in candidates) {
                val route = route(client, candidate) ?: continue
                val published = Published()
                val controller = client.startNavigation(route.routeHandle, listenerFor(published))
                    ?: continue
                try {
                    // Sample the route's vertices: enough fixes to pass every manoeuvre, few enough to run
                    // in a minute. The engine needs a fresh fix to publish the next step ahead.
                    val count = route.latitudes.size
                    val stride = maxOf(1, count / 80)
                    for (vertex in 0 until count step stride) {
                        if (!drive(
                                controller, route, vertex,
                                System.currentTimeMillis() + vertex * 1_000L,
                                published
                            )
                        ) {
                            continue
                        }
                        val native = published.next.get() ?: continue
                        val steps = published.list.get()
                        val announced = guidanceStep(native, steps)
                        if (announced.kind != RouteStep.Kind.ROUNDABOUT_ENTER) continue

                        val hint = announced.nextNext
                        val observation = Observation(
                            candidate = candidate.name,
                            announced = announced,
                            hintKind = hint?.kind,
                            hintExit = hint?.roundaboutLeave?.exitCountTaken?.toInt() ?: 0,
                            gapMeters = announced.nextNextGapMeters,
                            nativeKind = native.kind,
                            nativeHintKind = native.nextNext?.kind,
                            nativeHintExit = native.nextNext?.roundaboutLeave?.exitCountTaken?.toInt() ?: 0
                        )
                        observations.add(observation)
                        Log.i(
                            TAG,
                            "approach ${candidate.name}: published ${roundaboutStepLine(native)}" +
                                " | announced ${roundaboutStepLine(announced)}" +
                                " gap=${"%.0f".format(observation.gapMeters)}m"
                        )
                    }
                } finally {
                    controller.stop()
                }
            }

            assumeTrue(
                "this map data produced no roundabout approach on a calculable route",
                observations.isNotEmpty()
            )

            // Every approach the drive produced must announce the exit, inside the window, with its
            // ordinal — and the native record's own hint must be the plain node this change bypasses.
            for (observation in observations) {
                assertEquals(
                    "the hint of the approach to ${observation.candidate} is the roundabout leave",
                    RouteStep.Kind.ROUNDABOUT_LEAVE, observation.hintKind
                )
                assertTrue(
                    "the leave carries the exit ordinal taken (${observation.hintExit})",
                    observation.hintExit >= 1
                )
                assertTrue(
                    "the gap is inside the hint window (${observation.gapMeters} m of $HINT_WINDOW_METERS)",
                    observation.gapMeters > 0.0 && observation.gapMeters <= HINT_WINDOW_METERS
                )
                assertNotNull(
                    "the announced approach is the roundabout entry",
                    observation.announced.kind
                )
            }
            assertEquals(
                "the native record's own hint is a plain route node, not the manoeuvre",
                0, observations.count { it.nativeHintKind == RouteStep.Kind.ROUNDABOUT_LEAVE }
            )
            Log.i(TAG, "approach count=${observations.size} all=${observations.map { it.hintExit }}")
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "NextNextHintDevice"
    }
}
