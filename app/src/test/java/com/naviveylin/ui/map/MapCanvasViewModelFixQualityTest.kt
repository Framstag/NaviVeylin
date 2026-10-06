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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
     * (`CompassButton.compassFillColor`), the re-center gates (`MapCanvasScreen.kt:1686`, `:1823`,
     * `:2318`, each `shouldShowReCenterButton(...) && quality != NONE`), the speed widget's
     * `gpsAvailable` (`:1891`), and the browse re-center derivation inside the ViewModel (`:3095`).
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
     * The tick must not read the main dispatcher while the quality is unchanged (spec: `gps-fix-quality`
     * — Fix-quality re-evaluation stays off the main thread). It used to hop back onto the main
     * dispatcher once per tick (`withContext(fixQualityTickDispatcher) { delay(...) }` returns into the
     * dispatcher that owns the loop), so a ViewModel that outlives its test — every case that replaces
     * its instance, every class that builds one without cancelling it — kept reading the main dispatcher
     * every second from a real thread pool, and a *later* test then failed in `MainDispatcherRule` with
     * `Dispatchers.Main is used concurrently with setting it` (TODO.md §121).
     *
     * Real-time case on purpose: the tick runs on the production dispatcher with the real clock while
     * the main dispatcher is instrumented, and the case pumps the main dispatcher the way a following
     * test would — so every tick is evaluated. Counted are only dispatches from **another** thread:
     * what this case's own pumping queues comes from the test thread, and the construction-time I/O of
     * the ViewModel's collaborators settles before the counted window. Asserted is the **second** window
     * of two: a single late arrival of that construction work is tolerated, a per-tick pattern is not.
     */
    @Test
    fun aTickDoesNotDispatchOnTheMainDispatcher() {
        val countingMain = CountingMainDispatcher(mainDispatcherRule.dispatcher)
        Dispatchers.setMain(countingMain)
        viewModel.fixQualityTickMs = TICK_MS
        settleConstruction()

        countingMain.reset()
        watchTicks()
        val firstWindow = countingMain.count

        countingMain.reset()
        watchTicks()
        val secondWindow = countingMain.count

        assertEquals(
            "the fix-quality tick dispatched on the main dispatcher from another thread " +
                "${secondWindow} times in the second ${TICK_WATCH_MS}ms window while nothing changed " +
                "(first window: $firstWindow) — it must stay off the main dispatcher; " +
                "first dispatch:\n${countingMain.firstDispatchStack()}",
            0,
            secondWindow
        )
    }

    /**
     * The main dispatcher under test. Counts the dispatches **another thread** sends to it, with the
     * stack of the first one for the failure message; dispatches made by the thread that owns this
     * wrapper (the case's own scheduler pumping) are passed through without counting.
     */
    private class CountingMainDispatcher(
        private val delegate: CoroutineDispatcher
    ) : CoroutineDispatcher() {

        private val ownerThread = Thread.currentThread()
        private val dispatches = AtomicInteger(0)
        private val firstDispatch = AtomicReference<Throwable?>(null)

        val count: Int get() = dispatches.get()

        fun reset() {
            dispatches.set(0)
            firstDispatch.set(null)
        }

        /** Where the first counted dispatch came from, for the failure message. */
        fun firstDispatchStack(): String =
            firstDispatch.get()?.stackTrace?.joinToString("\n") { "\tat $it" } ?: "(none)"

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (Thread.currentThread() !== ownerThread) {
                firstDispatch.compareAndSet(null, Throwable("main dispatch from another thread"))
                dispatches.incrementAndGet()
            }
            delegate.dispatch(context, block)
        }
    }

    private companion object {
        /** Tick period of the setUp instance: several ticks fit into a real-clock watch. */
        const val SETUP_TICK_MS = 50L

        /** Tick period of the main-dispatcher case. */
        const val TICK_MS = 20L

        /** How long a case watches real ticks. */
        const val TICK_WATCH_MS = 300L

        /** [TICK_WATCH_MS] expressed in ticks of [SETUP_TICK_MS] — the window a case actually needs. */
        const val TICK_WATCH_TICKS = 6L

        /** Time the main-dispatcher case gives the construction-time work to settle. */
        const val SETTLE_MS = 600L

        /** Poll interval of the real-clock waits. */
        const val POLL_MS = 10L

        /** Deadline of [awaitQuality]: generous, the wait is one to three real ticks. */
        const val QUALITY_DEADLINE_MS = 5_000L
    }
}
