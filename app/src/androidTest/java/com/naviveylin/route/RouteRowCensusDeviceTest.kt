package com.naviveylin.route

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.ui.route.routeCensus
import com.naviveylin.ui.route.routeRows
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The route-row census on real routes (change `route-panel-from-steps`, tasks 5.2 and 5.3; spec:
 * `route-analysis` — Rows are derived from the route's steps).
 *
 * It calculates one short city route and one long route on the installed map data, derives the rows
 * with the production function and records the census line the panel records — the numbers that
 * settle the row policy (`informative`/`POIAtRoute`/`Via` and the `MinRowLegMeters` floor), which no
 * unit fixture can supply. The line carries counts and metres only; a coordinate never reaches it
 * (spec: `auto-diagnostics`).
 *
 * Skipped, not failed, when the installed map data does not carry the candidate pairs.
 */
@RunWith(AndroidJUnit4::class)
class RouteRowCensusDeviceTest {

    private data class Candidate(
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val destLat: Double,
        val destLon: Double
    )

    /** One short urban route (Andorra) and one long route on the NRW motorway network. */
    private val candidates = listOf(
        Candidate("Andorra la Vella -> Escaldes-Engordany (city)", 42.5075, 1.5218, 42.5110, 1.5400),
        Candidate("Dortmund Hbf -> Essen Hbf (motorway)", 51.5177, 7.4592, 51.4510, 7.0130),
        Candidate("Dortmund Hbf -> Cologne Hbf (motorway)", 51.5177, 7.4592, 50.9430, 6.9580)
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

    @Test
    fun theCensusOfACityRouteAndALongRouteIsRecorded() {
        DiagnosticsLog.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val client = client()
        val recorded = mutableListOf<String>()
        try {
            for (candidate in candidates) {
                val entry = route(client, candidate) ?: continue
                val steps = entry.steps?.toList() ?: continue
                val rows = routeRows(steps)
                val census = routeCensus(steps, rows)
                DiagnosticsLog.log(DiagnosticsLog.ROUTE_TAG, census)
                Log.i("RouteDeviceTest", "${candidate.name}: $census")
                recorded += census
                assertTrue(
                    "${candidate.name}: the census names its steps and rows",
                    census.contains("steps=${steps.size}") && census.contains("rows=")
                )
            }
        } finally {
            client.close()
        }
        assumeTrue("no candidate routed on this map data", recorded.isNotEmpty())
        Log.i("RouteDeviceTest", "censuses recorded=${recorded.size}")
    }
}
