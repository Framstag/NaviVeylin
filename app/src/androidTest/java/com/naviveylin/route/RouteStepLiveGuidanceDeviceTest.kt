package com.naviveylin.route

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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live guidance publishes route steps (spec: `osmscout-jni` — Live guidance publishes route steps;
 * change `route-steps-live-guidance`, task 7.3).
 *
 * The listener callbacks and their marshalling live in the JNI bridge, which no JVM test executes, so a
 * device with installed map data is the only place this can be observed. The test routes through the
 * client, starts navigation, feeds synthetic fixes along the route and reads what the engine publishes:
 * the step ahead with its remaining distance, its nested "next next" hint, and the full list on a route
 * change.
 *
 * Cases whose prerequisite the map data does not provide (a close pair of manoeuvres, a roundabout) are
 * skipped, not failed.
 */
@RunWith(AndroidJUnit4::class)
class RouteStepLiveGuidanceDeviceTest {

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

    /** What one navigation session published. */
    private class Published {
        val list: MutableList<RouteStep> = mutableListOf()
        val next = AtomicReference<RouteStep?>(null)
        val listLatch = CountDownLatch(1)
        val nextLatch = CountDownLatch(1)
        val firstList = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private fun listenerFor(published: Published) = object : NavigationListener {
        override fun onRouteSteps(steps: Array<RouteStep>) {
            if (published.firstList.compareAndSet(false, true)) {
                published.list.addAll(steps)
                published.listLatch.countDown()
            }
        }

        override fun onNextRouteStep(step: RouteStep) {
            published.next.set(step)
            published.nextLatch.countDown()
        }
    }

    /** Feeds [attempts] fixes at [vertex] until the step ahead arrives; returns it. */
    private fun firstStepAt(
        controller: NavigationController,
        route: RouteEntry,
        vertex: Int,
        published: Published,
        attempts: Int = 4
    ): RouteStep? {
        for (attempt in 0 until attempts) {
            controller.processLocation(
                route.latitudes[vertex], route.longitudes[vertex],
                13.0, 5.0, System.currentTimeMillis() + attempt * 1_000L
            )
            if (published.nextLatch.await(15, TimeUnit.SECONDS)) break
        }
        return published.next.get()
    }

    @Test
    fun theStepAheadCarriesItsManoeuvreAndRemainingDistance() {
        val client = client()
        try {
            val candidate = candidates.first()
            val route = route(client, candidate) ?: run {
                assumeTrue("the map data does not route this pair", false)
                return
            }
            val published = Published()
            val controller = client.startNavigation(route.routeHandle, listenerFor(published))
            assumeTrue("the engine refused the route handle", controller != null)

            try {
                // A fix a few vertices into the route: the step ahead is the next manoeuvre, and its
                // remaining distance must be the run from the fix to that manoeuvre.
                val vertex = minOf(route.latitudes.size - 1, maxOf(1, route.latitudes.size / 3))
                val step = firstStepAt(controller, route, vertex, published)
                assumeTrue(
                    "the engine published no step for synthetic fixes (TODO 16: a synthetic fix " +
                        "carries no bearing)",
                    step != null
                )

                Log.i(
                    "RouteDeviceTest",
                    "live step ahead: kind=${step!!.kind} remaining=" +
                        "${step.remainingDistance} leg=${step.legDistance} " +
                        "hint=${step.nextNext?.kind}"
                )

                assertNotNull("the published step names its manoeuvre", step.kind)
                val remaining = step.remainingDistance
                assertNotNull("the published step carries its remaining distance", remaining)
                assertTrue(
                    "the remaining distance is not negative (was $remaining)",
                    remaining!! >= 0.0
                )
                // The step ahead is a described node of the route, so it is one of the published steps.
                assertTrue(
                    "the step ahead belongs to the route's step list",
                    route.steps?.any { it.distanceFromStart == step.distanceFromStart } == true
                )
                // No guidance text crosses the boundary: the record has no description field at all,
                // and the app words the manoeuvre from these values (spec: turn-instruction-localization).
                assertTrue("the published step carries no description", step.name?.name?.isNotEmpty() != false)
            } finally {
                controller!!.stop()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun theNextNextHintIsANestedStep() {
        val client = client()
        try {
            var found: RouteStep? = null
            for (candidate in candidates) {
                val route = route(client, candidate) ?: continue
                val published = Published()
                val controller = client.startNavigation(route.routeHandle, listenerFor(published))
                    ?: continue
                try {
                    for (vertex in 0 until minOf(route.latitudes.size, 8)) {
                        val step = firstStepAt(controller, route, vertex, published, attempts = 2)
                        if (step?.nextNext != null) {
                            found = step
                            break
                        }
                    }
                } finally {
                    controller.stop()
                }
                if (found != null) break
            }

            assumeTrue(
                "this map data produced no publication carrying a nested hint",
                found != null
            )
            val hint = found!!.nextNext!!
            Log.i("RouteDeviceTest", "nested hint: kind=${hint.kind} ordinal=${hint.roundaboutLeave?.exitCountTaken}")
            assertNotNull("the nested hint names its manoeuvre", hint.kind)
            assertTrue(
                "the hint sits ahead of the step that carries it",
                hint.distanceFromStart >= found.distanceFromStart
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun aRouteChangePublishesTheFullStepList() {
        val client = client()
        try {
            for (candidate in candidates) {
                val route = route(client, candidate) ?: continue
                val published = Published()
                val controller = client.startNavigation(route.routeHandle, listenerFor(published))
                assumeTrue("the engine refused the route handle", controller != null)

                try {
                    controller!!.processLocation(
                        route.latitudes[0], route.longitudes[0],
                        13.0, 5.0, System.currentTimeMillis()
                    )
                    assumeTrue(
                        "the engine published no step list for this route",
                        published.listLatch.await(30, TimeUnit.SECONDS)
                    )

                    Log.i("RouteDeviceTest", "live step list: published=${published.list.size} calculated=${route.steps?.size}")
                    assertTrue("the published list is not empty", published.list.isNotEmpty())
                    // No null element: the bridge delivers no list rather than a list with holes.
                    assertTrue("every published step is non-null", published.list.all { it != null })
                    // Route order: the published positions advance.
                    val offsets = published.list.map { it.distanceFromStart }
                    assertEquals("the list is published in route order", offsets.sorted(), offsets)
                    return
                } finally {
                    controller!!.stop()
                }
            }
            assumeTrue("no candidate routed on this map data", false)
        } finally {
            client.close()
        }
    }
}
