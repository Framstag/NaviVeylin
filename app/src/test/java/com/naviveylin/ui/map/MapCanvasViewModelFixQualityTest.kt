package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the GPS fix quality of [MapCanvasViewModel] (spec: `gps-fix-quality`).
 *
 * The quality used to be computed only when a fix arrived, so it stayed latched at its last tier
 * forever while the location source was silent — location services switched off, or the
 * minimum-distance throttling at standstill. These cases age a fix out, disable the source and
 * restore a fix **without delivering a new fix in between**, which is exactly what the old code
 * could not see.
 *
 * Timing: the fix-quality pipeline (derivation and debounce) runs on the ViewModel's own scope, which
 * the [MainDispatcherRule] points at this test's scheduler — so a fix-driven or debounce-driven
 * transition is advanced to virtually ([advanceTimeBy], [awaitQuality]'s pumping). The *tick* that
 * notices an aged-out fix or a disabled source runs on the production dispatcher with the real clock
 * (spec: `gps-fix-quality` — Fix-quality re-evaluation stays off the main thread; the rule the
 * stale-speed ticker documents, `TODO.md` §40.C.16), so a tick-driven transition is waited for with a
 * real-clock poll — the pattern of `awaitSpeedCondition` in the stale-speed cases.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelFixQualityTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var viewModel: MapCanvasViewModel

    /** The clock the quality reads; moved by a case to age the last fix out. */
    private var fakeNow = 1_700_000_000_000L

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        val settingsStorage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        val searchHistoryRepository = SearchHistoryRepository(context)
            .also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
        val favoriteRepository = FavoriteRepository(client)
            .also { it.defaultDispatcher = mainDispatcherRule.dispatcher }
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = favoriteRepository,
            searchHistoryRepository = searchHistoryRepository,
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            timeSource = EngineTimeSource { fakeNow },
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
        viewModel.fixQualityTickMs = SETUP_TICK_MS
        viewModel.fixAgeLimitMs = 200L
        // Robolectric starts with location services off; the platform read itself is covered by
        // LocationServiceTest — a case flips this to false for the disabled-source case.
        locationService.setLocationSourceReadForTest { true }
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Delivers a fix with the given accuracy, timestamped at the test clock. */
    private fun installFix(accuracy: Double) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = 51.5136,
                lon = 7.4653,
                accuracy = accuracy,
                speedKmH = 0.0,
                smoothedBearing = Double.NaN,
                markerBearing = Double.NaN,
                time = fakeNow
            )
        )
    }

    private fun quality(): GpsFixQuality = viewModel.uiState.value.gpsFixQuality

    /**
     * Runs a case and cancels the ViewModel's scope before `runTest` returns: the fix-quality tick is
     * an endless delay loop, and a test releases what it starts (`guidelines/Build.md` §6 — teardown
     * rule). The loop itself runs on the production dispatcher, so it does not keep the test scheduler
     * non-idle (the rule the stale-speed ticker documents, `TODO.md` §40.C.16).
     */
    private fun fixQualityTest(body: suspend TestScope.() -> Unit): Unit =
        runTest(mainDispatcherRule.dispatcher) {
            try {
                body()
            } finally {
                viewModel.cancelScopeForTest()
            }
        }

    /**
     * Pump this test's scheduler from a real dispatcher until [condition] holds. The fix-quality tick runs
     * on a real dispatcher with the real clock **on purpose** (it must not feed the virtual scheduler), so
     * the case drives its own scheduler while that thread works and awaits the observable state instead of
     * reading a clock or sleeping (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
     */
    private fun pumpUntil(condition: () -> Boolean) = runBlocking {
        withContext(Dispatchers.Default) {
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            while (!condition()) {
                scheduler.advanceUntilIdle()
                yield()
            }
        }
    }

    /**
     * Awaits the **published** quality, driving the ViewModel's scheduler (and with it the debounce) while
     * the real-clock tick detects the change.
     */
    private fun awaitQuality(expected: GpsFixQuality) = pumpUntil { quality() == expected }

    /** Waits for [TICK_WATCH_TICKS] completed ticks — a tick window, not a wall-clock window. */
    private fun watchTicks() {
        val from = viewModel.fixQualityTickCount.value
        pumpUntil { viewModel.fixQualityTickCount.value >= from + TICK_WATCH_TICKS }
    }

    /** The collaborators are pinned to this test's scheduler, so draining it settles the construction. */
    private fun settleConstruction() {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun aGoodFixReportsGood() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.GOOD, quality())
    }

    @Test
    fun aPoorAccuracyReportsPoor() = fixQualityTest {
        installFix(accuracy = 80.0)
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.POOR, quality())
    }

    @Test
    fun noFixAtAllIsNone() = fixQualityTest {
        advanceTimeBy(2_500)

        assertEquals(GpsFixQuality.NONE, quality())
    }

    @Test
    fun anAgedOutFixBecomesNoneWithoutANewFix() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        // The fix ages out. No new fix is delivered — the old code could not see this at all.
        fakeNow += 1_000L
        awaitQuality(GpsFixQuality.NONE)
    }

    @Test
    fun aDisabledSourceIsNoneWithoutANewFix() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        // The platform reports that location services are switched off; the fix itself stays young.
        locationService.setLocationSourceReadForTest { false }
        awaitQuality(GpsFixQuality.NONE)
    }

    @Test
    fun aNewFixRestoresTheTier() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        fakeNow += 1_000L
        awaitQuality(GpsFixQuality.NONE)

        installFix(accuracy = 10.0)
        awaitQuality(GpsFixQuality.GOOD)
    }

    @Test
    fun anUnchangedQualityPublishesNoNewValue() = fixQualityTest {
        val published = mutableListOf<GpsFixQuality>()
        val job = backgroundScope.launch {
            viewModel.uiState
                .map { it.gpsFixQuality }
                .distinctUntilChanged()
                .collect { published += it }
        }

        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(listOf(GpsFixQuality.NONE, GpsFixQuality.GOOD), published)

        // Several real ticks with nothing changing must not republish the quality.
        watchTicks()
        assertEquals(listOf(GpsFixQuality.NONE, GpsFixQuality.GOOD), published)

        job.cancel()
    }

    /**
     * Every fix-quality consumer resolves the same value (spec: `gps-fix-quality` — One definition for
     * every fix-quality consumer). The inventory (grep of `gpsFixQuality`) is: compass fill
     * (`CompassButton.compassFillColor`), the re-center gates (`MapCanvasScreenChromeBand.kt:224`,
     * `:380`, `:583`, each `shouldShowReCenterButton(...) && quality != NONE`), the speed widget's
     * `gpsAvailable` (`MapCanvasScreenModalBands.kt:114`), and the browse re-center derivation inside
     * the ViewModel (`MapCanvasViewModel.kt`).
     */
    @Test
    fun everyConsumerReadsTheSharedQuality() = fixQualityTest {
        installFix(accuracy = 10.0)
        advanceTimeBy(2_500)
        assertEquals(GpsFixQuality.GOOD, quality())

        locationService.setLocationSourceReadForTest { false }
        awaitQuality(GpsFixQuality.NONE)

        val state = viewModel.uiState.value
        assertEquals(GpsFixQuality.NONE, state.gpsFixQuality)

        // The ViewModel's own browse re-center derivation reads the quality, not a fix age.
        assertFalse(state.browseReCenterVisible)

        // The screen's gate is `shouldShowReCenterButton(...) && quality != NONE`: NONE hides the
        // button in every mode, whatever the helper returns.
        assertFalse(
            MapCanvasViewModel.shouldShowReCenterButton(
                MapMode.BROWSE,
                driveSuspended = true,
                browseReCenterVisible = true
            ) && state.gpsFixQuality != GpsFixQuality.NONE
        )

        // Compass fill: the no-fix hue family is not the GOOD tone (the tone itself is pinned by
        // CompassPaletteTest) and the speed widget's `gpsAvailable` is the same comparison.
        assertNotEquals(
            compassFillColor(GpsFixQuality.GOOD, isDarkPresentation = false),
            compassFillColor(GpsFixQuality.NONE, isDarkPresentation = false)
        )
        assertFalse(state.gpsFixQuality != GpsFixQuality.NONE)
    }

    /**
     * The re-evaluation must not dispatch onto the main dispatcher while the quality is unchanged (spec:
     * `gps-fix-quality` — Re-evaluation stays off the main dispatcher while the quality is unchanged).
     * It used to hop back onto the main dispatcher once per tick
     * (`withContext(fixQualityTickDispatcher) { delay(...) }` returns into the dispatcher that owns the
     * loop), so a ViewModel that outlives its test — every case that replaces its instance, every class
     * that builds one without cancelling it — kept reading the main dispatcher every second from a real
     * thread pool, and a *later* test then failed in `MainDispatcherRule` with `Dispatchers.Main is used
     * concurrently with setting it` (TODO.md §121).
     *
     * Real-time case on purpose: the tick runs on the production dispatcher with the real clock while the
     * main dispatcher is instrumented, and the case pumps the main dispatcher the way a following test
     * would — so every tick is evaluated.
     *
     * **What a counted dispatch is attributed to.** The dispatcher this case instruments is the main
     * dispatcher of the whole process, so a count of "dispatches from another thread" is not the same
     * thing as "main-dispatcher traffic of the re-evaluation": any other subsystem that resumes a
     * main-collector from a background thread lands in the same count. It did, twice on 2026-10-06, in a
     * focused run of this class's neighbours: the failing stack was `NavigationEngine.tickStaleness` →
     * `runStaleSpeedTicker` — an unrelated ticker — while the message blamed the fix-quality tick, which
     * is what issue #87 records. [CountingMainDispatcher] therefore attributes every counted dispatch by
     * the stack that issued it: the frames of `MapCanvasViewModel`'s own code are the re-evaluation's
     * traffic, everything else is another subsystem's and is reported, not asserted. A dispatch the
     * ViewModel's own coroutine issues from a real thread carries its `invokeSuspend` frame, so the guard
     * the case exists for — a per-tick hop onto the main dispatcher — is still caught, while a loaded
     * runner's unrelated traffic can no longer turn the case red.
     *
     * The case provokes the unrelated traffic itself, one dispatch per window from a thread that is
     * neither the test thread nor the re-evaluation's, and asserts the counter attributed it to another
     * subsystem. That control closes two failure directions on every run: an attribution that counted
     * everything would trip the zero assertion instead, and one that counted nothing would trip the
     * control. It does not close a third — a predicate that never matches the ViewModel passes both —
     * which the recorded falsification run exercises instead (a per-tick main-dispatcher hop attributed
     * to the ViewModel's own `invokeSuspend`), not a passing run.
     *
     * Asserted over [TICK_WINDOWS] windows, not over one (a rate needs N ≥ 3): the tick period is real
     * time, so a single window is a sample, and a per-tick pattern is a property of all of them.
     */
    @Test
    fun aTickDoesNotDispatchOnTheMainDispatcher() {
        val countingMain = CountingMainDispatcher(mainDispatcherRule.dispatcher)
        Dispatchers.setMain(countingMain)
        viewModel.fixQualityTickMs = TICK_MS
        settleConstruction()

        // Stand-in for the unrelated main-dispatcher traffic of a loaded run: one dispatch per window,
        // issued from a thread that is neither the test thread nor the re-evaluation's.
        val noiseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val windows = mutableListOf<Pair<Int, Int>>()
        try {
            repeat(TICK_WINDOWS) {
                countingMain.reset()
                // Awaited, so the window is known to contain the unrelated dispatch: the noise block runs
                // on the pumped main dispatcher, and its coroutine completes on the IO thread after it.
                val noiseIssued = CompletableDeferred<Unit>()
                noiseScope.launch {
                    withContext(Dispatchers.Main) { }
                    noiseIssued.complete(Unit)
                }
                pumpUntil { noiseIssued.isCompleted }
                watchTicks()
                windows += countingMain.fromTheViewModel to countingMain.fromElsewhere
            }
        } finally {
            runBlocking { noiseScope.coroutineContext[Job]?.cancelAndJoin() }
        }

        val fromTheViewModel = windows.sumOf { it.first }
        assertEquals(
            "the fix-quality tick dispatched on the main dispatcher from another thread " +
                "$fromTheViewModel times over $TICK_WINDOWS windows of $TICK_WATCH_TICKS ticks " +
                "(${TICK_MS}ms each) while nothing changed " +
                "(${windows.mapIndexed { index, counts -> "window ${index + 1}: ${counts.first}" }})" +
                " — it must stay off the main dispatcher; first dispatch from the ViewModel:\n" +
                countingMain.firstViewModelDispatchStack(),
            0,
            fromTheViewModel
        )
        // The control: an unrelated dispatch really reached this instrumented dispatcher in every window
        // (issued and awaited before that window's ticks) — the counter is not blind, it attributes by
        // stack.
        assertTrue(
            "no main dispatch from another thread was attributed to another subsystem at all, so this " +
                "case cannot show that it tells the re-evaluation's traffic from the rest: " +
                "${windows.mapIndexed { index, counts -> "window ${index + 1}: ${counts.second}" }}; " +
                "counted stacks:\n${countingMain.elsewhereDispatchStacks()}",
            windows.all { it.second >= 1 }
        )
    }

    /**
     * The main dispatcher under test. Counts the dispatches **another thread** sends to it and
     * attributes each one by the stack that issued it: a dispatch the ViewModel's own code makes from a
     * real thread is the re-evaluation's traffic ([fromTheViewModel]), anything else is another
     * subsystem's ([fromElsewhere]) and is reported, never blamed on the re-evaluation. Dispatches made
     * by the thread that owns this wrapper (the case's own scheduler pumping) are passed through without
     * counting. Stacks are kept because the failure message is the diagnosis (`guidelines/Build.md` §6).
     */
    private class CountingMainDispatcher(
        private val delegate: CoroutineDispatcher
    ) : CoroutineDispatcher() {

        private val ownerThread = Thread.currentThread()
        private val dispatches = ConcurrentLinkedQueue<CountedDispatch>()

        val fromTheViewModel: Int get() = dispatches.count { it.fromTheViewModel }

        val fromElsewhere: Int get() = dispatches.count { !it.fromTheViewModel }

        private class CountedDispatch(val fromTheViewModel: Boolean, val stack: Throwable)

        fun reset() {
            dispatches.clear()
        }

        /** Where the first counted dispatch the ViewModel's own code issued came from, for a failure. */
        fun firstViewModelDispatchStack(): String = dispatches
            .firstOrNull { it.fromTheViewModel }
            ?.stack?.stackTrace?.joinToString("\n") { "\tat $it" } ?: "(none)"

        /** Where the counted dispatches of other subsystems came from, for the control's failure. */
        fun elsewhereDispatchStacks(): String = dispatches
            .filterNot { it.fromTheViewModel }
            .joinToString("\n---\n") { dispatch ->
                dispatch.stack.stackTrace.joinToString("\n") { "\tat $it" }
            }.ifEmpty { "(none)" }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (Thread.currentThread() !== ownerThread) {
                val issuer = Throwable("main dispatch from another thread")
                dispatches.add(CountedDispatch(issuer.issuedByTheViewModel(), issuer))
            }
            delegate.dispatch(context, block)
        }

        /**
         * Whether the stack that issued a dispatch runs through the ViewModel's own code — the
         * re-evaluation loop, the collectors it resumes, the state it publishes. `Thread` frames carry the
         * class name of the enclosing class, so the ViewModel's own coroutines appear as
         * `MapCanvasViewModel$…` and this test's frames as `MapCanvasViewModelFixQualityTest…`.
         */
        private fun Throwable.issuedByTheViewModel(): Boolean =
            stackTrace.any { frame ->
                frame.className == VIEW_MODEL_CLASS ||
                    frame.className.startsWith("$VIEW_MODEL_CLASS\$")
            }
    }

    private companion object {
        /** Tick period of the setUp instance: several ticks fit into a real-clock watch. */
        const val SETUP_TICK_MS = 50L

        /** Tick period of the main-dispatcher case. */
        const val TICK_MS = 20L

        /**
         * A tick window, in completed ticks: six at [SETUP_TICK_MS] (≈ 300 ms), six at [TICK_MS]
         * (≈ 120 ms) — the unit each case counts in, so no case asserts on a wall-clock span.
         */
        const val TICK_WATCH_TICKS = 6L

        /** How many windows the main-dispatcher case asserts; a rate needs N ≥ 3, not one sample. */
        const val TICK_WINDOWS = 3

        /** The subject of [CountingMainDispatcher]'s attribution. */
        const val VIEW_MODEL_CLASS = "com.naviveylin.ui.map.MapCanvasViewModel"

        /** Time the main-dispatcher case gives the construction-time work to settle. */
        const val SETTLE_MS = 600L

        /** Poll interval of the real-clock waits. */
        const val POLL_MS = 10L

        /** Deadline of [awaitQuality]: generous, the wait is one to three real ticks. */
        const val QUALITY_DEADLINE_MS = 5_000L
    }
}
