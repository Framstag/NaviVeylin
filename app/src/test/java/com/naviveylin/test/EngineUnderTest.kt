package com.naviveylin.test

import android.content.Context
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationEngine
import java.util.Collections

/**
 * Build a [NavigationEngine] for a test with its two seams explicit.
 *
 * The engine's timing and threading are injected (spec: `navigation-engine` — Engine lifecycle and
 * threading), so a test states which clock and which dispatchers it uses instead of inheriting them
 * from a hidden default: a case that must not depend on real time passes a mutable [EngineTimeSource]
 * and a test-dispatcher [EngineDispatchers], and a case that only needs the engine to exist omits both
 * and gets the production pair.
 *
 * The engine is registered in [EngineUnderTestRegistry], because every engine is a process-lifetime scope
 * that dispatches through `Dispatchers.Main` (spec `navigation-engine` — Engine lifecycle and threading) and
 * a case must not leave one running into the next case (spec `unit-test-suite-runtime` — A case leaves no
 * process-wide dispatcher state to race a neighbouring case).
 */
internal fun engineUnderTest(
    client: () -> OSMScoutClient,
    locationService: LocationService,
    context: Context,
    timeSource: EngineTimeSource = EngineTimeSource.System,
    dispatchers: EngineDispatchers = EngineDispatchers.Production
): NavigationEngine =
    NavigationEngine({ client() }, locationService, context, timeSource, dispatchers)
        .also { EngineUnderTestRegistry.register(it) }

/**
 * The engines the running case built, so the rule that owns `Dispatchers.setMain` can stop them before it
 * restores Main ([MainDispatcherRule.finished]).
 *
 * Production needs no registry: the app has one engine and the process owns it. A test, by contrast, builds a
 * fresh engine per case (25 classes do), and each one brings a supervisor scope with a live stale-speed ticker
 * and up to a dozen `launch(Dispatchers.Main)` call sites — with the scope never cancelled, the previous case's
 * dispatch can land while the next case's `Dispatchers.setMain` runs, which is the
 * `Dispatchers.Main is used concurrently with setting it` error of `TODO.md` §148 case 4.
 *
 * Engines registered by a class that does not use [MainDispatcherRule] are stopped by the next rule-bearing
 * class instead of never; that is the pre-existing behaviour (no teardown) improved, not a new requirement.
 */
internal object EngineUnderTestRegistry {

    private val engines = Collections.synchronizedList(mutableListOf<NavigationEngine>())

    /**
     * How many engines the last [shutdownAll] could not stop within the seam's bound. The rule's own report,
     * asserted by the guard pair: a teardown that cancels without waiting leaves this above zero, which is what
     * the delta's scenario "a cancel that returns immediately is not enough" means in practice.
     */
    @Volatile
    private var unfinishedByTest = 0

    fun register(engine: NavigationEngine) {
        engines.add(engine)
    }

    /**
     * Cancel every registered engine, run [drainScheduler] (the case's scheduler completes the work the
     * engines queued on it), then await each scope's completion (bounded — see
     * [NavigationEngine.shutdownForTest]), recording how many did not stop in time.
     */
    fun shutdownAll(drainScheduler: () -> Unit) {
        val finished = synchronized(engines) {
            val copy = engines.toList()
            engines.clear()
            copy
        }
        if (finished.isEmpty()) {
            unfinishedByTest = 0
            return
        }
        unfinishedByTest = finished.count { !it.shutdownForTest(drainScheduler) }
    }

    /** Engines of the last finished case whose scope was still alive when its teardown's bound expired. */
    fun enginesThatOutlivedTheirTeardownForTest(): Int = unfinishedByTest
}
