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
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.haversineDistanceMeters
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.stringResolver
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationEngine
import com.naviveylin.navigation.VoiceGuidance
import com.naviveylin.navigation.VoiceOutput
import com.naviveylin.service.NavigationNotificationContentFormatter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The route-guidance fixture: expected instructions recorded once from a coordinate pair, then
 * compared against what navigation actually announces (design of the destination-hint diagnosis,
 * 2026-11-28).
 *
 * Two modes, selected with `-e mode=record|replay`:
 *
 * - **record** — calculate the route for a coordinate pair, derive the expected instruction list
 *   from the calculated route, write it as a fixture. This runs **once** per route; the file is
 *   committed under `app/src/androidTest/assets/route-fixtures/` and is the test setup afterwards.
 * - **replay** — calculate the same route again, drive the real [NavigationEngine] along the
 *   route's own polyline (`processLocation`, the production entry point), capture every manoeuvre
 *   it announces, and diff each against the fixture: does the announced step carry the recorded
 *   signposted destination, and does the wording each surface shows still name it.
 *
 * **The fixture is keyed on `kind` + distance from the route start, never on a node id, a file
 * offset or a database id** — an import renumbers all three, and a fixture that stored them would go
 * stale on the next map update. The pair is exactly what `StepIndexSelection.nextStepIndex` uses to
 * match a live step to the published list, so fixture and live announcement identify a manoeuvre the
 * same way the app does (window [MATCH_WINDOW_METERS]).
 *
 * It is a tool, not a gate: it prints the diff and never fails on a wording difference — what it
 * must not do is pass silently, so a route the engine never drove is reported (and skipped) rather
 * than counted as green. Driven by `tools/route-hints.sh`.
 */
@RunWith(AndroidJUnit4::class)
class RouteGuidanceFixtureDeviceTest {

    private val tag = "RouteFixture"

    /** One manoeuvre of the expected instruction list. */
    private data class Expected(
        val kind: String,
        val km: Double,
        val destination: String?,
        val leadsOnto: String?
    )

    /** One announcement the running engine produced. */
    private data class Announced(
        val fix: Int,
        val km: Double,
        val kind: String,
        val destination: String?,
        val leadsOnto: String?,
        val carHint: String?
    )

    private fun arguments() = InstrumentationRegistry.getArguments()

    // ------------------------------------------------------------------
    // Fixture I/O
    // ------------------------------------------------------------------

    private fun fixturesDir(): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "route-fixtures")

    /** The fixture written by `record`, read from the committed test assets. */
    private fun assetFixture(name: String): List<String>? = try {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("route-fixtures/$name")
            .bufferedReader()
            .readLines()
    } catch (missing: java.io.IOException) {
        null
    }

    private fun routesFromArguments(): List<List<String>> {
        // The committed test setup first, so a run needs no push; `-e routesFile` is the ad-hoc path
        // for a route that is not worth committing.
        val lines = arguments().getString("routesFile")?.let { path ->
            File(path).takeIf { it.exists() }?.readLines()
        } ?: InstrumentationRegistry.getInstrumentation().context.assets
            .open("route-fixtures/routes.txt")
            .bufferedReader()
            .readLines()
        return lines
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(Regex("\\s+")) }
            .filter { it.size >= 5 }
    }

    // ------------------------------------------------------------------
    // The calculated route
    // ------------------------------------------------------------------

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

    private fun route(
        client: OSMScoutClient,
        label: String,
        startLat: Double,
        startLon: Double,
        targetLat: Double,
        targetLon: Double
    ): RouteEntry? {
        // `openDatabases` registers a queued DB-thread job, so a calculation issued immediately can
        // outrun the open and be refused with "No databases loaded yet" (OSMScoutClient.cpp:7369).
        // Retry that one kind; any other failure is the route's own.
        var attempt = 0
        while (true) {
            val (databaseNotLoaded, entry) = routeOnce(
                client, label, startLat, startLon, targetLat, targetLon
            )
            if (entry != null) return entry
            if (!databaseNotLoaded || ++attempt >= 4) return null
            Thread.sleep(500L)
        }
    }

    private fun routeOnce(
        client: OSMScoutClient,
        label: String,
        startLat: Double,
        startLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Pair<Boolean, RouteEntry?> {
        val latch = CountDownLatch(1)
        var route: RouteEntry? = null
        var databaseNotLoaded = false
        client.calculateRouteWithProfile(
            startLat, startLon, targetLat, targetLon,
            RoutingProfile(),
            object : RouteCallback {
                override fun onProgress(percent: Int) = Unit
                override fun onSuccess(entry: RouteEntry) {
                    route = entry
                    latch.countDown()
                }

                override fun onError(message: String) {
                    databaseNotLoaded = message.contains("database", ignoreCase = true) &&
                        message.contains("loaded", ignoreCase = true)
                    if (!databaseNotLoaded) Log.w(tag, "$label: route failed: $message")
                    latch.countDown()
                }

                override fun onCancel() = latch.countDown()
            }
        )
        if (!latch.await(240, TimeUnit.SECONDS)) return Pair(true, null)
        return Pair(databaseNotLoaded, route)
    }

    // ------------------------------------------------------------------
    // The values a surface reads
    // ------------------------------------------------------------------

    private fun kilometres(step: RouteStep): Double = step.distanceFromStart / 1000.0

    /** The expected wording of a step, through the production function the surfaces call. */
    private fun leadsOntoOf(step: RouteStep, resolver: StringResolver): String? =
        ManoeuvreLabels.leadsOnto(resolver, manoeuvreFromStep(step))

    /** The raw record, first of a multi-value block, as `manoeuvreFromStep` projects it. */
    private fun destinationOf(step: RouteStep): String? =
        step.destination?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

    private fun stateAround(step: RouteStep, remaining: Double) = NavigationState(
        isNavigating = true,
        nextStep = step,
        steps = listOf(step),
        currentStepIndex = 0,
        remainingDistance = remaining,
        etaMillis = 0L
    )

    private fun carHintOf(step: RouteStep, resolver: StringResolver): String? =
        NavigationNotificationContentFormatter.carHint(
            stateAround(step, step.remainingDistance ?: Double.NaN),
            resolver
        )?.text

    /** A one-line fixture record: identity, expected record, expected wording. */
    private fun fixtureLine(step: RouteStep, resolver: StringResolver): String {
        val destination = destinationOf(step)
        val leadsOnto = leadsOntoOf(step, resolver)
        return String.format(
            java.util.Locale.ROOT,
            "%s %.1f | destination=%s | leadsOnto=%s",
            step.kind ?: RouteStep.Kind.UNKNOWN,
            kilometres(step),
            destination ?: "-",
            leadsOnto ?: "-"
        )
    }

    private fun parseFixtureLine(line: String): Expected? {
        val trimmed = line.substringBefore('#').trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('|').map { it.trim() }
        val identity = parts.firstOrNull()?.split(Regex("\\s+")) ?: return null
        if (identity.size < 2) return null
        val km = identity[1].toDoubleOrNull() ?: return null
        fun field(name: String): String? = parts
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("$name=")
            ?.takeIf { it != "-" && it.isNotEmpty() }
        return Expected(identity[0], km, field("destination"), field("leadsOnto"))
    }

    // ------------------------------------------------------------------
    // Mode: record
    // ------------------------------------------------------------------

    @Test
    fun record() {
        assumeTrue("not record mode (-e mode=record)", arguments().getString("mode") == "record")
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.stringResolver()
        val routes = routesFromArguments()
        val client = client()
        val directory = fixturesDir().apply { mkdirs() }
        var written = 0

        try {
            for (fields in routes) {
                val label = fields[0]
                val numbers = fields.drop(1).take(4).mapNotNull { it.toDoubleOrNull() }
                if (numbers.size < 4) {
                    Log.w(tag, "$label: not '<label> <startLat> <startLon> <targetLat> <targetLon>'")
                    continue
                }
                val entry = route(client, label, numbers[0], numbers[1], numbers[2], numbers[3]) ?: continue
                val steps = entry.steps?.toList() ?: continue

                val manoeuvres = steps.filter {
                    it.kind != RouteStep.Kind.INFORMATIVE && it.kind != RouteStep.Kind.UNKNOWN
                }
                val lines = buildList {
                    add("# route $label")
                    add("# start ${numbers[0]} ${numbers[1]}  target ${numbers[2]} ${numbers[3]}  vehicle car")
                    add("# identity is '<kind> <km from the route start>': node ids, file offsets and")
                    add("# database ids are deliberately absent — an import renumbers them.")
                    add("# fields: destination = the raw record's first block, leadsOnto = the wording")
                    add("# every surface composes from the step (ManoeuvreLabels.leadsOnto).")
                    manoeuvres.mapTo(this) { fixtureLine(it, resolver) }
                }
                File(directory, "$label.expected.txt").writeText(lines.joinToString("\n") + "\n")
                written++
                val withDestination = manoeuvres.count { destinationOf(it) != null }
                Log.i(
                    tag,
                    "recorded '$label' steps=${steps.size} manoeuvres=${manoeuvres.size} " +
                        "with_destination=$withDestination"
                )
            }
        } finally {
            client.close()
        }

        assumeTrue("no route could be calculated for any request", written > 0)
        Log.i(tag, "fixtures written=$written to ${directory.absolutePath}")
    }

    // ------------------------------------------------------------------
    // Mode: replay
    // ------------------------------------------------------------------

    @Test
    fun replay() {
        assumeTrue("not replay mode (-e mode=replay)", arguments().getString("mode") == "replay")
        val name = arguments().getString("fixture")
        assumeTrue("no fixture named (-e fixture=<label>.expected.txt)", !name.isNullOrBlank())
        val lines = assetFixture(name!!)
            ?: File(fixturesDir(), name).takeIf { it.exists() }?.readLines()
            ?: run {
                Log.w(tag, "no fixture '$name' in assets/route-fixtures or ${fixturesDir()}")
                null
            }
        assumeTrue("no fixture '$name'", lines != null)

        val header = lines!!.firstOrNull { it.startsWith("# start ") }
        assumeTrue("the fixture names no coordinates ($header)", header != null)
        // '# start <lat> <lon>  target <lat> <lon>  vehicle car' — the four numbers in order.
        val numbers = header!!.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }.take(4)
        assumeTrue("the fixture's coordinates do not parse ($header)", numbers.size == 4)

        val label = lines.firstOrNull { it.startsWith("# route ") }?.removePrefix("# route ")?.trim() ?: name
        val expected = lines.mapNotNull(::parseFixtureLine)

        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.stringResolver()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = client()

        try {
            val entry = route(client, label, numbers[0], numbers[1], numbers[2], numbers[3])
                ?: run {
                    Log.w(tag, "$label: the route could not be calculated again")
                    null
                }
            assumeTrue("the route could not be calculated again", entry != null)
            assumeTrue("the route carries no steps", !entry!!.steps.isNullOrEmpty())
            assumeTrue("the route carries no polyline", entry.latitudes != null && entry.longitudes != null)

            val announced = drive(entry, label, client)
            if (announced.isEmpty()) {
                Log.w(
                    tag,
                    "$label: the engine announced no manoeuvre — the synthetic fixes never left " +
                        "the off-route state (TODO.md §16: a fix without bearing), so this run " +
                        "proves nothing"
                )
            }
            report(label, expected, announced)
        } finally {
            client.close()
        }
    }

    /**
     * Drive the real engine along the route's own polyline and capture every announcement: the step
     * the phone overlay, the notification and the car template all read from `state.nextStep`.
     */
    private fun drive(entry: RouteEntry, label: String, client: OSMScoutClient): List<Announced> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.stringResolver()
        val engine = NavigationEngine(
            { client },
            LocationService(context),
            context,
            EngineTimeSource.System,
            EngineDispatchers.Production,
            SettingsStorage(context),
            VoiceGuidance(context, VoiceOutput.NoOp)
        )
        val lats = entry.latitudes!!
        val lons = entry.longitudes!!
        // Feed by distance, not by vertex count: one fix per N vertices is a jump of hundreds of
        // metres at 100 ms, which the position agent cannot follow — the route is then only driven
        // as far as the snapping keeps up, and the far half of it is never announced.
        val cumulative = DoubleArray(lats.size)
        for (index in 1 until lats.size) {
            cumulative[index] = cumulative[index - 1] +
                haversineDistanceMeters(lats[index - 1], lons[index - 1], lats[index], lons[index])
        }
        val announced = mutableListOf<Announced>()
        var lastIdentity: Pair<Any?, Long>? = null
        var fixes = 0

        try {
            engine.start(entry, Vehicle.CAR)
            await(engine, 10_000) { engine.state.value.isNavigating }
            await(engine, 10_000) {
                engine.state.value.steps.isNotEmpty() || engine.state.value.totalDistance > 0.0
            }

            var index = 0
            var budget = 0
            while (index < lats.size && !engine.state.value.hasReachedDestination && budget < maxFixes) {
                budget++
                engine.processLocation(lats[index], lons[index], 14.0, 5.0, System.currentTimeMillis())
                fixes++
                Thread.sleep(fixSettleMs)

                // Advance along the polyline to the next fix, whatever the engine published: a fix
                // that yields no step is a fix the loop still has to move past (an earlier version
                // span here forever).
                val nextDistance = cumulative[index] + feedStepMeters
                while (index < lats.size && cumulative[index] < nextDistance) index++

                val step = engine.state.value.nextStep
                if (step == null) continue

                val identity = Pair(step.kind, (step.distanceFromStart / 50.0).toLong())
                if (identity == lastIdentity) continue
                lastIdentity = identity
                announced += Announced(
                    fix = fixes,
                    km = kilometres(step),
                    kind = (step.kind ?: RouteStep.Kind.UNKNOWN).toString(),
                    destination = destinationOf(step),
                    leadsOnto = leadsOntoOf(step, resolver),
                    carHint = carHintOf(step, resolver)
                )
                // The sequence is the answer: this is what each surface shows, in the order a driver
                // met it. Nothing here re-derives a value — every field is a production function's
                // output over the step the engine published.
                Log.i(
                    tag,
                    String.format(
                        java.util.Locale.ROOT,
                        "announced '%s' fix=%d km=%.1f kind=%s destination=%s leadsOnto=%s carhint=%s",
                        label, fixes, kilometres(step),
                        step.kind ?: RouteStep.Kind.UNKNOWN,
                        destinationOf(step) ?: "-",
                        leadsOntoOf(step, resolver) ?: "-",
                        carHintOf(step, resolver) ?: "-"
                    )
                )
            }
            Log.i(
                tag,
                "$label: fixes=$fixes announcements=${announced.size} " +
                    "offRoute=${engine.state.value.isOffRoute} " +
                    "reached=${engine.state.value.hasReachedDestination}"
            )
        } finally {
            engine.stopNavigation()
        }
        return announced
    }

    /**
     * Diff the announcements against the fixture. Identity is `kind` + kilometres within
     * [MATCH_WINDOW_METERS]; a manoeuvre with no announcement is as much a finding as one whose
     * destination is missing, so all three outcomes are counted.
     */
    private fun report(label: String, expected: List<Expected>, announced: List<Announced>) {
        var matched = 0
        var destinationMissing = 0
        var wordingDiffers = 0
        var unannounced = 0
        var unexpected = 0

        for (wanted in expected) {
            val candidate = announced.firstOrNull {
                it.kind == wanted.kind && abs(it.km - wanted.km) * 1000.0 <= MATCH_WINDOW_METERS
            }
            if (candidate == null) {
                unannounced++
                Log.i(
                    tag,
                    String.format(
                        java.util.Locale.ROOT,
                        "diff '%s' %s %.1f | expected destination=%s leadsOnto=%s | NOT ANNOUNCED",
                        label, wanted.kind, wanted.km, wanted.destination ?: "-", wanted.leadsOnto ?: "-"
                    )
                )
                continue
            }

            val destinationFault = wanted.destination != null && candidate.destination != wanted.destination
            val wordingFault = wanted.leadsOnto != null && candidate.leadsOnto != wanted.leadsOnto
            if (!destinationFault && !wordingFault) {
                matched++
                continue
            }
            if (destinationFault) destinationMissing++
            if (wordingFault) wordingDiffers++

            Log.i(
                tag,
                String.format(
                    java.util.Locale.ROOT,
                    "diff '%s' %s %.1f | expected destination=%s leadsOnto=%s | " +
                        "live destination=%s leadsOnto=%s carhint=%s",
                    label, wanted.kind, wanted.km,
                    wanted.destination ?: "-", wanted.leadsOnto ?: "-",
                    candidate.destination ?: "-", candidate.leadsOnto ?: "-",
                    candidate.carHint ?: "-"
                )
            )
        }

        for (live in announced) {
            val known = expected.any {
                it.kind == live.kind && abs(it.km - live.km) * 1000.0 <= MATCH_WINDOW_METERS
            }
            if (!known) {
                unexpected++
                Log.i(tag, "diff '$label' ${live.kind} ${"%.1f".format(java.util.Locale.ROOT, live.km)} | not in the fixture (fix=${live.fix})")
            }
        }

        Log.i(
            tag,
            "summary '$label' expected=${expected.size} announced=${announced.size} " +
                "matched=$matched destination_missing=$destinationMissing " +
                "wording_differs=$wordingDiffers unannounced=$unannounced unexpected=$unexpected"
        )
    }

    /** Wait for a state predicate without blocking the engine's own dispatchers. */
    private fun await(engine: NavigationEngine, timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(25)
        }
        return predicate()
    }

    private companion object {
        /** How far a live announcement may sit from the fixture's kilometre mark. */
        const val MATCH_WINDOW_METERS = 50.0

        /** Settle time per injected fix: the native position agent runs on its own thread. */
        const val fixSettleMs = 100L

        /** Distance between two injected fixes: a 14 m/s drive sampled about every two seconds. */
        const val feedStepMeters = 30.0

        /** Hard cap on injected fixes, so a route that never reaches its target still terminates. */
        const val maxFixes = 2500
    }
}
