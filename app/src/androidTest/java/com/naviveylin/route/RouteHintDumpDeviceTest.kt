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
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.stringResolver
import com.naviveylin.service.NavigationNotificationContentFormatter
import com.naviveylin.ui.navigation.instructionLines
import com.naviveylin.core.ManoeuvreLabels
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The route-hint dump: what every surface would *say* for one calculated route
 * (spec: `auto-navigation-hints` — The car hint names the signposted destination; spec:
 * `next-turn-overlay` — The second line carries the signposted destination).
 *
 * This is a diagnostic tool, not a gate. It calculates a route through the production path —
 * `OSMScoutClient.calculateRouteWithProfile` over the app's own installed maps — and then, for one
 * step, prints the values the surfaces are built from, computed by the production functions:
 *
 * - `step`, `kind`, `destination` — what the native step model published, before any wording;
 * - `short` / `leadsOnto` — `ManoeuvreLabels`, i.e. the car `Step`'s cue and road and the phone
 *   overlay's two lines;
 * - `phone2` — `instructionLines(...).destination`, the overlay's second line;
 * - `carhint` — `NavigationNotificationContentFormatter.carHint(...)`'s text, the notification's
 *   car secondary text, worded from real `:core`/`:app` resources in the device language.
 *
 * The fork it settles: a motorway enter/change that shows no signposted destination on a screen is
 * either a step that carries no record (`destination=-`) — a defect below the bridge — or a step
 * that carries one and a surface that does not word it (`destination='...'` beside an empty
 * `carhint`) — a defect above it. Nothing in this file re-implements a production decision, so a
 * green result cannot be an artefact of the tool disagreeing with the app.
 *
 * Driven by `tools/route-hints.sh`, which pushes a routes file and reads the `RouteHints` tag:
 *
 * ```
 * adb shell am instrument -w -e class com.naviveylin.route.RouteHintDumpDeviceTest \
 *   -e routesFile /data/local/tmp/route-hints.txt \
 *   com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Skipped, not failed, when no map data is installed on the device.
 */
@RunWith(AndroidJUnit4::class)
class RouteHintDumpDeviceTest {

    private val tag = "RouteHints"

    /** One requested route, read from the routes file. */
    private data class Request(
        val label: String,
        val startLat: Double,
        val startLon: Double,
        val targetLat: Double,
        val targetLon: Double
    )

    private fun routesFile(): List<Request> {
        // The committed test setup first (no push needed), then `-e routesFile` for an ad-hoc route.
        val lines = InstrumentationRegistry.getArguments().getString("routesFile")?.let { path ->
            File(path).takeIf { it.exists() }?.readLines()
        } ?: InstrumentationRegistry.getInstrumentation().context.assets
            .open("route-fixtures/routes.txt")
            .bufferedReader()
            .readLines()
        val requests = lines
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val fields = line.split(Regex("\\s+"))
                if (fields.size < 5) {
                    Log.w(tag, "skipped a line that is not '<label> <lat> <lon> <lat> <lon>': $line")
                    return@mapNotNull null
                }
                val numbers = fields.drop(1).take(4).map { it.toDoubleOrNull() ?: return@mapNotNull null }
                Request(fields[0], numbers[0], numbers[1], numbers[2], numbers[3])
            }
        assumeTrue("no route request in the routes source", requests.isNotEmpty())
        return requests
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

    private fun route(client: OSMScoutClient, request: Request): RouteEntry? {
        val latch = CountDownLatch(1)
        var route: RouteEntry? = null
        client.calculateRouteWithProfile(
            request.startLat, request.startLon, request.targetLat, request.targetLon,
            RoutingProfile(),
            object : RouteCallback {
                override fun onProgress(percent: Int) = Unit
                override fun onSuccess(entry: RouteEntry) {
                    route = entry
                    latch.countDown()
                }

                override fun onError(message: String) {
                    Log.w(tag, "${request.label}: route failed: $message")
                    latch.countDown()
                }

                override fun onCancel() = latch.countDown()
            }
        )
        if (!latch.await(240, TimeUnit.SECONDS)) return null
        return route
    }

    /** One step's line: the model's own values beside every surface's wording of them. */
    private fun line(index: Int, step: RouteStep, resolver: StringResolver): String {
        val manoeuvre = manoeuvreFromStep(step)
        // The state a surface reads: the step ahead is the live one, and the frozen list entry is
        // the fallback `carHint` uses when the engine has published no live step.
        val state = NavigationState(
            isNavigating = true,
            nextStep = step,
            steps = listOf(step),
            currentStepIndex = 0,
            remainingDistance = step.remainingDistance?.takeIf { !it.isNaN() } ?: 0.0,
            etaMillis = 0L
        )
        val hint = NavigationNotificationContentFormatter.carHint(state, resolver)
        return listOf(
            "step=$index",
            "km=" + String.format(java.util.Locale.ROOT, "%.1f", step.distanceFromStart / 1000.0),
            "kind=${step.kind ?: RouteStep.Kind.UNKNOWN}",
            "leg=${step.legDistance.toInt()}",
            "destination=${step.destination?.let { "'$it'" } ?: "-"}",
            "manDest=${manoeuvre.destination?.let { "'$it'" } ?: "-"}",
            "short=${quote(ManoeuvreLabels.short(resolver, manoeuvre))}",
            "leadsOnto=${quote(ManoeuvreLabels.leadsOnto(resolver, manoeuvre))}",
            "phone2=${quote(instructionLines(resolver, manoeuvre).destination)}",
            "carhint=${quote(hint?.text)}"
        ).joinToString(" ")
    }

    private fun quote(text: String?): String = text?.let { "'$it'" } ?: "-"

    @Test
    fun everyStepOfARouteIsDumpedWithTheWordingOfEverySurface() {
        val routes = routesFile()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.stringResolver()
        val client = client()
        var reported = 0

        try {
            for (request in routes) {
                val entry = route(client, request) ?: continue
                val steps = entry.steps?.toList() ?: continue
                reported++

                Log.i(tag, "=== route '${request.label}' steps=${steps.size} " +
                    "length_m=${entry.distance.toInt()}")

                steps.forEachIndexed { index, step ->
                    Log.i(tag, line(index, step, resolver))
                }

                // The fork, per motorway enter/change: the step carries a record, or it does not.
                // A manoeuvre whose step carries one *and* whose car hint is blank is a surface
                // defect; a manoeuvre whose step carries none is a defect below the bridge.
                val motorway = steps.withIndex().filter {
                    it.value.kind == RouteStep.Kind.MOTORWAY_ENTER ||
                        it.value.kind == RouteStep.Kind.MOTORWAY_CHANGE
                }
                val withRecord = motorway.count { it.value.destination != null }
                Log.i(
                    tag,
                    "summary '${request.label}' motorway_enter_or_change=${motorway.size} " +
                        "with_destination=$withRecord " +
                        "without_destination=${motorway.size - withRecord} " +
                        "steps=${steps.size}"
                )
                motorway.forEach { (index, step) ->
                    val manoeuvre = manoeuvreFromStep(step)
                    val hint = NavigationNotificationContentFormatter.carHint(
                        NavigationState(
                            isNavigating = true,
                            nextStep = step,
                            steps = listOf(step),
                            remainingDistance = step.remainingDistance?.takeIf { !it.isNaN() } ?: 0.0
                        ),
                        resolver
                    )
                    Log.i(
                        tag,
                        "verdict '${request.label}' step=$index kind=${step.kind} " +
                            "destination=${step.destination?.let { "'$it'" } ?: "-"} " +
                            "carriesDestination=${ManoeuvreLabels.carriesDestination(manoeuvre)} " +
                            "carhintNamesIt=${hint?.text?.contains(manoeuvre.destination ?: "\u0000") ?: false}"
                    )
                }
            }
        } finally {
            client.close()
        }

        assumeTrue("no candidate routed on this map data", reported > 0)
        Log.i(tag, "routes reported=$reported of ${routes.size}")
    }
}
