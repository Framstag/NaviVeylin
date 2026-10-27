package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the derived BROWSE re-center rule (spec: map-modes — Browse
 * re-center): the button follows the measured offset between the map center and
 * the vehicle — not a remembered interaction — so a viewport that is not
 * centered on the vehicle (a persisted start viewport), vehicle movement, a pan
 * and a zoom all count, while rotation does not, GPS noise does not toggle it,
 * and a missing fix hides it. Re-centering centers the vehicle and hides the
 * button without applying the vehicle anchor presets.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapCanvasViewModelBrowseReCenterTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: MapCanvasViewModel

    /**
     * The time the dwell decisions see. It starts at the wall clock so the fix timestamps the cases
     * deliver stay comparable to it (fix age is data, not a wait); the dwell window is then moved by
     * hand instead of by sleeping (spec: `unit-test-suite-runtime` — Injected time instead of a real
     * clock).
     */
    private var fakeNowMs: Long = System.currentTimeMillis()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        settingsStorage = SettingsStorage(context)
        settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher
        createViewModel()
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private fun createViewModel() {
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = locationService,
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            timeSource = EngineTimeSource { fakeNowMs },
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setScreenSize(SCREEN_W, SCREEN_H)
        viewModel.updateMagnification(MAG)
        // A device that delivers fixes has its location services on; without this the platform read
        // (Robolectric starts with them off) would report no fix for every case here
        // (spec: gps-fix-quality — Fix availability and quality tiers).
        locationService.setLocationSourceReadForTest { true }
    }

    private val dpi: Double
        get() = context.resources.displayMetrics.densityDpi.toDouble()

    /**
     * The viewport center that shows [VEHICLE_LAT]/[VEHICLE_LON] [dxPx] right and
     * [dyPx] down from the center of the canvas.
     */
    private fun centerForVehicleOffset(
        dxPx: Double,
        dyPx: Double,
        mag: Double = MAG,
        angle: Double = 0.0
    ): Pair<Double, Double> = ProjectionUtils
        .viewport(VEHICLE_LAT, VEHICLE_LON, mag, SCREEN_W, SCREEN_H, dpi, angle)
        .screenToGeoRotated(SCREEN_W / 2.0 - dxPx, SCREEN_H / 2.0 - dyPx)

    /** Move the committed viewport so the vehicle sits [dxPx]/[dyPx] off center. */
    private fun moveViewportBy(dxPx: Double, dyPx: Double, mag: Double = MAG) {
        val (lat, lon) = centerForVehicleOffset(dxPx, dyPx, mag)
        viewModel.updateMagnification(mag)
        viewModel.updateCenter(lat, lon)
    }

    private fun emitFix(lat: Double = VEHICLE_LAT, lon: Double = VEHICLE_LON) {
        locationService.setGpsFixForTest(
            GpsFix(
                lat = lat, lon = lon,
                accuracy = 5.0, speedKmH = 30.0,
                smoothedBearing = 45.0, markerBearing = 45.0,
                time = fakeNowMs
            )
        )
    }

    /** The committed offset of the vehicle from the canvas center, in screen px. */
    private fun committedOffsetPx(): Double {
        val vp = viewModel.uiState.value.viewport
        return MapCanvasViewModel.browseCenterOffsetPx(
            vp.centerLat, vp.centerLon, vp.magnification, vp.angle,
            SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
    }

    /**
     * Wait out the appear dwell, then deliver a fix so the rule re-evaluates.
     * The fix repeats the position the case is about — in BROWSE the map does not
     * follow, so a test that moved the *vehicle* must repeat the moved position.
     *
     * The dwell itself no longer needs this second fix (the rule completes it on the clock, see
     * [awaitReveal]); the cases below still deliver the fix because the fix is what moves the vehicle
     * they are about.
     */
    private fun awaitDwellAndRefresh(
        lat: Double = VEHICLE_LAT,
        lon: Double = VEHICLE_LON
    ) {
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        emitFix(lat, lon)
    }

    /**
     * Pump this test's scheduler from a real dispatcher until [condition] holds. The shared
     * re-evaluation tick runs on a real dispatcher (it may not feed the virtual scheduler), so a case
     * has to give it a chance to happen while awaiting the observable state — never a clock: a
     * wall-clock deadline in test sources is refused by `checkNoWallClockWaits` (spec
     * `unit-test-suite-runtime`). Bounded by a spin budget so a missing condition fails the assertion
     * instead of hanging the case.
     */
    private fun pumpUntil(maxSpins: Int = REVEAL_PUMP_SPIN_BUDGET, condition: () -> Boolean) = runBlocking {
        withContext(Dispatchers.Default) {
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            var spins = 0
            while (!condition() && spins < maxSpins) {
                scheduler.advanceUntilIdle()
                yield()
                spins++
            }
            condition()
        }
    }

    /** Await the reveal of the re-center button (the dwell completing on the clock). */
    private fun awaitReveal(): Boolean = pumpUntil { viewModel.uiState.value.browseReCenterVisible }

    /**
     * Like [pumpUntil], but drains only the tasks that are already runnable (`runCurrent`) and never
     * advances the test scheduler's virtual time. A case that must observe a state *before* a debounced
     * publication arrives uses this: `advanceUntilIdle` would fire the debounce first and close the very
     * window under test.
     */
    private fun pumpCurrentUntil(
        maxSpins: Int = REVEAL_PUMP_SPIN_BUDGET,
        condition: () -> Boolean
    ) = runBlocking {
        withContext(Dispatchers.Default) {
            val scheduler = mainDispatcherRule.dispatcher.scheduler
            var spins = 0
            while (!condition() && spins < maxSpins) {
                scheduler.runCurrent()
                yield()
                spins++
            }
            condition()
        }
    }

    /**
     * Await [count] further shared-tick heartbeats. The tick loop counts **every** tick in
     * [MapCanvasViewModel.fixQualityTickCount], so waiting for heartbeats proves the loop ran — the
     * bounded way to assert that nothing was published for the dwell, without reading a clock.
     */
    private fun awaitTickHeartbeats(count: Int) {
        val from = viewModel.fixQualityTickCount.value
        pumpUntil { viewModel.fixQualityTickCount.value >= from + count }
        assertTrue(
            "the shared re-evaluation tick must still be running for this case to mean anything",
            viewModel.fixQualityTickCount.value >= from + count
        )
    }

    /**
     * Put the map center on the vehicle and deliver a fix, waited out past the
     * dwell — the baseline the movement/pan/zoom cases start from.
     */
    private fun startCenteredOnVehicle() {
        moveViewportBy(0.0, 0.0)
        emitFix()
        awaitDwellAndRefresh()
    }

    // --- projection helper (task 2.2) ---

    @Test
    fun offsetHelperMeasuresTheDistanceFromTheCanvasCenter() = runTest(mainDispatcherRule.dispatcher) {
        val (lat, lon) = centerForVehicleOffset(120.0, 0.0)
        val offset = MapCanvasViewModel.browseCenterOffsetPx(
            lat, lon, MAG, 0.0, SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
        assertEquals("120 px to the right of the center", 120.0, offset, 1.0)

        // A rotation about the center preserves the distance (spec: map-modes —
        // Browse re-center, "Rotating the map does not change visibility").
        val (rLat, rLon) = centerForVehicleOffset(0.0, 200.0, angle = 0.0)
        val rotated = MapCanvasViewModel.browseCenterOffsetPx(
            rLat, rLon, MAG, ROTATION_RAD, SCREEN_W, SCREEN_H, dpi, VEHICLE_LAT, VEHICLE_LON
        )
        assertEquals("rotation preserves the offset", 200.0, rotated, 1.0)
    }

    @Test
    fun offsetHelperReportsUnknownInputsAsNaN() {
        assertTrue(
            "unknown position",
            MapCanvasViewModel.browseCenterOffsetPx(
                52.51, 13.40, MAG, 0.0, SCREEN_W, SCREEN_H, dpi, Double.NaN, 13.40
            ).isNaN()
        )
        assertTrue(
            "unknown canvas",
            MapCanvasViewModel.browseCenterOffsetPx(
                52.51, 13.40, MAG, 0.0, 0, 0, dpi, 52.52, 13.41
            ).isNaN()
        )
    }

    // --- the rule (spec scenarios) ---

    @Test
    fun offCenterViewportShowsTheButtonWithoutAnyMapInteraction() =
        runTest(mainDispatcherRule.dispatcher) {
            // Stands in for the persisted start viewport: the viewport is set
            // before the first fix ever arrives, and no pan/zoom happens at all.
            // The fix is repeated below because this case is about a *fix* arriving on such a viewport;
            // the reveal no longer needs it — `offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent`
            // covers the dwell completing on its own.
            moveViewportBy(400.0, 0.0)
            advanceUntilIdle()
            assertFalse("no fix yet", viewModel.uiState.value.browseReCenterVisible)

            emitFix()
            advanceUntilIdle()
            assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

            awaitDwellAndRefresh()
            advanceUntilIdle()

            assertTrue(
                "a fix arriving on a viewport that is not centered on the vehicle must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
            assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
        }

    @Test
    fun offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent() =
        runTest(mainDispatcherRule.dispatcher) {
            // The reported defect: the app is started in phone mode with a persisted viewport that is
            // away from the vehicle, and the vehicle stands still — one fix, then the location provider
            // goes silent (min-distance throttling). The dwell must complete on elapsed time, so the
            // button has to appear with NO further fix, NO pan and NO other state change.
            moveViewportBy(400.0, 0.0)
            advanceUntilIdle()
            assertFalse("no fix yet", viewModel.uiState.value.browseReCenterVisible)

            emitFix()
            advanceUntilIdle()
            assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

            // Only the clock moves from here on.
            fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L

            assertTrue(
                "the dwell elapsing on its own must show the button without a further fix or interaction",
                awaitReveal()
            )
            assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
        }

    @Test
    fun aSingleFlickPanRevealsTheButtonWithoutAFollowUpEvent() = runTest(mainDispatcherRule.dispatcher) {
        startCenteredOnVehicle()
        advanceUntilIdle()
        assertFalse(
            "a centered viewport stays hidden past the dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        // One flick: exactly one viewport write, and then no further event of any kind.
        moveViewportBy(-500.0, 0.0)
        advanceUntilIdle()
        assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L

        assertTrue(
            "a flick pan must reveal the button once the dwell elapses, with no further event",
            awaitReveal()
        )
    }

    @Test
    fun theDwellBoundaryIsDecidedByElapsedTime() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()

        // One millisecond short of the dwell, with the tick loop proven to have run: the deadline is
        // exceeded strictly, so this must stay hidden.
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS - 1
        awaitTickHeartbeats(2)
        assertFalse(
            "one millisecond short of the dwell must stay hidden",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals(
            "no dwell re-evaluation before the deadline",
            0L.toLong(),
            viewModel.recenterDwellTickCount.value
        )

        fakeNowMs += 1
        assertTrue("at the dwell boundary the button must appear", awaitReveal())
    }

    @Test
    fun noDwellEvaluationWhileTheViewportIsCentered() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(0.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()
        assertFalse("centered", viewModel.uiState.value.browseReCenterVisible)

        awaitTickHeartbeats(2)

        assertEquals(
            "a centered viewport must not publish a dwell re-evaluation",
            0L.toLong(),
            viewModel.recenterDwellTickCount.value
        )
    }

    @Test
    fun pressingReCenterDiscardsThePendingDwell() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        assertTrue(awaitReveal())

        viewModel.recenterInBrowse()
        advanceUntilIdle()
        assertFalse("pressing re-center hides the button", viewModel.uiState.value.browseReCenterVisible)

        val published = viewModel.recenterDwellTickCount.value
        awaitTickHeartbeats(2)
        assertEquals(
            "a re-centered viewport leaves no dwell pending",
            published,
            viewModel.recenterDwellTickCount.value
        )
    }

    @Test
    fun leavingBrowseDiscardsTheDwellAndReturningReArmsIt() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()

        // Leave BROWSE with the dwell pending, then let it elapse. FREE_DRIVE owns its own visibility
        // rule, so the browse dwell must neither fire nor leak into it.
        viewModel.enterFreeDrive()
        advanceUntilIdle()
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        awaitTickHeartbeats(2)
        assertFalse(
            "the browse dwell must not fire outside BROWSE",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals(
            "the browse dwell must not publish anything outside BROWSE",
            0L.toLong(),
            viewModel.recenterDwellTickCount.value
        )

        // Back in BROWSE. Entering FREE_DRIVE re-engaged follow on the vehicle, so the map is centered
        // again; pan away to get an off-center viewport and prove the discarded dwell needs a full
        // fresh one — the elapsing clock alone must not reveal the button.
        viewModel.exitFreeDrive()
        advanceUntilIdle()
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        assertFalse(
            "returning to BROWSE must not reveal the button without a fresh dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        assertTrue(awaitReveal())
    }

    @Test
    fun agedOutFixSuppressesThePendingDwell() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()

        // The source is switched off, so the quality gate reports NONE and the rule must not reveal
        // anything however long the dwell is left to run. The tier is republished by the real-clock
        // tick, so wait for it (not for a clock) before the dwell is left to elapse.
        locationService.setLocationSourceReadForTest { false }
        pumpUntil { viewModel.uiState.value.gpsFixQuality == GpsFixQuality.NONE }
        assertEquals(
            "the source-off tier must have reached the state before the dwell is left to run",
            GpsFixQuality.NONE,
            viewModel.uiState.value.gpsFixQuality
        )
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        awaitTickHeartbeats(2)
        assertFalse(
            "a fix that aged out must not reveal the button",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals(
            "a suppressed dwell must not publish anything",
            0L.toLong(),
            viewModel.recenterDwellTickCount.value
        )

        // A usable fix again needs a fresh dwell.
        locationService.setLocationSourceReadForTest { true }
        pumpUntil { viewModel.uiState.value.gpsFixQuality != GpsFixQuality.NONE }
        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        assertTrue(
            "with a usable fix the button must appear after a fresh dwell",
            awaitReveal()
        )
    }

    @Test
    fun anAgingFixDoesNotLetTheClockRevealTheButton() = runTest(mainDispatcherRule.dispatcher) {
        // The age route, not the source-off route of `agedOutFixSuppressesThePendingDwell`: the fix is
        // still present but no longer usable, and the *published* tier lags the derived one by the
        // quality pipeline's debounce. The clock path must read the tier it derives itself, otherwise it
        // reveals the button inside that window — which the requirement's "A fix that ages out suppresses
        // the pending dwell" scenario forbids (independent review 2026-10-11, finding P2-1).
        moveViewportBy(400.0, 0.0)
        advanceUntilIdle()
        emitFix()
        advanceUntilIdle()
        assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)
        assertEquals(
            "the fix starts usable",
            GpsFixQuality.GOOD,
            viewModel.uiState.value.gpsFixQuality
        )

        // Only the clock moves: the fix ages past the limit, and nothing is emitted.
        fakeNowMs += viewModel.fixAgeLimitMs + 1L

        val heartbeats = viewModel.fixQualityTickCount.value
        pumpCurrentUntil { viewModel.fixQualityTickCount.value >= heartbeats + 3 }
        assertTrue(
            "the shared tick loop must have run for this case to mean anything",
            viewModel.fixQualityTickCount.value >= heartbeats + 3
        )
        assertEquals(
            "the clock path must not publish a dwell re-evaluation for an unusable fix",
            0L.toLong(),
            viewModel.recenterDwellTickCount.value
        )
        assertFalse(
            "an aged-out fix must not reveal the button, not even briefly",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun theDwellBoundaryIsDecidedByInjectedTimeNotByElapsedRealTime() =
        runTest(mainDispatcherRule.dispatcher) {
            // Both sides of the window, one millisecond apart: a case that advanced nothing must stay
            // hidden, and the boundary itself must be the trigger — so a conversion that silently
            // stopped evaluating the window cannot pass (change `speed-up-test-iteration`, task 2.1).
            moveViewportBy(400.0, 0.0)
            advanceUntilIdle()
            emitFix()
            advanceUntilIdle()
            assertFalse(
                "no time moved, so the dwell cannot have elapsed",
                viewModel.uiState.value.browseReCenterVisible
            )

            fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS - 1
            emitFix()
            advanceUntilIdle()
            assertFalse(
                "one millisecond short of the dwell must stay hidden",
                viewModel.uiState.value.browseReCenterVisible
            )

            fakeNowMs += 1
            emitFix()
            advanceUntilIdle()
            assertTrue(
                "at the dwell boundary the button must appear",
                viewModel.uiState.value.browseReCenterVisible
            )
        }

    @Test
    fun vehicleMovingAwayFromTheCenterShowsTheButton() = runTest(mainDispatcherRule.dispatcher) {
        // Start centered on the vehicle — the map does not follow in BROWSE, so
        // it stays put while the vehicle drives.
        startCenteredOnVehicle()
        advanceUntilIdle()
        assertFalse(
            "a centered viewport stays hidden past the dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        val (movedLat, movedLon) = ProjectionUtils
            .viewport(VEHICLE_LAT, VEHICLE_LON, MAG, SCREEN_W, SCREEN_H, dpi, 0.0)
            .screenToGeoRotated(SCREEN_W / 2.0 + 400.0, SCREEN_H / 2.0)
        emitFix(movedLat, movedLon)
        advanceUntilIdle()
        assertFalse("dwell not elapsed yet", viewModel.uiState.value.browseReCenterVisible)

        awaitDwellAndRefresh(movedLat, movedLon)
        advanceUntilIdle()

        assertTrue(
            "vehicle movement away from the center must show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun panningAwayFromTheVehicleShowsTheButton() = runTest(mainDispatcherRule.dispatcher) {
        startCenteredOnVehicle()
        advanceUntilIdle()
        assertFalse(
            "a centered viewport stays hidden past the dwell",
            viewModel.uiState.value.browseReCenterVisible
        )

        moveViewportBy(-500.0, 0.0)
        advanceUntilIdle()

        awaitDwellAndRefresh()
        advanceUntilIdle()

        assertTrue(
            "panning away from the vehicle must show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun zoomingAboutAPointOtherThanTheCenterShowsTheButton() =
        runTest(mainDispatcherRule.dispatcher) {
            startCenteredOnVehicle()
            advanceUntilIdle()
            assertFalse(
                "a centered viewport stays hidden past the dwell",
                viewModel.uiState.value.browseReCenterVisible
            )

            // A pinch zooms about the gesture centroid: the center moves.
            moveViewportBy(300.0, 150.0, mag = MAG + 1.0)
            advanceUntilIdle()

            awaitDwellAndRefresh()
            advanceUntilIdle()

            assertTrue(
                "a zoom that moves the center away from the vehicle must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
        }

    @Test
    fun rotatingTheMapDoesNotChangeVisibility() = runTest(mainDispatcherRule.dispatcher) {
        // Far off center: visible.
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)
        assertEquals("the vehicle starts 400 px off center", 400.0, committedOffsetPx(), 1.0)

        val offsetBefore = committedOffsetPx()
        viewModel.onManualRotation(ROTATION_RAD)
        advanceUntilIdle()

        assertTrue(
            "rotation about the center keeps the offset, so the button stays visible",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals(
            "the offset from the center is unchanged by the rotation",
            offsetBefore, committedOffsetPx(), 1.0
        )
    }

    @Test
    fun centeredVehicleHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        startCenteredOnVehicle()
        advanceUntilIdle()

        assertFalse(
            "a centered viewport never shows the button",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertTrue("the vehicle is centered", committedOffsetPx() < 1.0)
    }

    @Test
    fun noisyPositionAroundTheCenterDoesNotToggleTheButton() =
        runTest(mainDispatcherRule.dispatcher) {
            // High magnification is where the pixel offset is dominated by GPS
            // noise (one screen pixel is ~0.03 m at magnif 20), so the noise band
            // straddles the appear threshold. The dwell must keep it hidden.
            val noisyMag = 19.0
            moveViewportBy(0.0, 0.0, mag = noisyMag)
            advanceUntilIdle()

            val offsets = listOf(
                0.0,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 1.1,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 0.8,
                0.0,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 1.3,
                MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX * 0.9
            )
            for (dx in offsets) {
                val (lat, lon) = centerForVehicleOffset(dx, 0.0, mag = noisyMag)
                emitFix(lat, lon)
                advanceUntilIdle()
                assertFalse(
                    "noise at dx=$dx px must not reveal the button",
                    viewModel.uiState.value.browseReCenterVisible
                )
            }

            // A sustained offset does reveal it — the dwell must not deadlock.
            val (sLat, sLon) = centerForVehicleOffset(200.0, 0.0, mag = noisyMag)
            emitFix(sLat, sLon)
            advanceUntilIdle()
            fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
            emitFix(sLat, sLon)
            advanceUntilIdle()

            assertTrue(
                "a sustained off-center position must show the button",
                viewModel.uiState.value.browseReCenterVisible
            )
        }

    @Test
    fun hysteresisHoldsTheButtonUntilTheHideThreshold() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        // Inside the hysteresis band (below the appear threshold, above the hide
        // threshold): an already visible button must not be restarted.
        moveViewportBy(MapCanvasViewModel.RECENTER_HIDE_OFFSET_PX + 4.0, 0.0)
        advanceUntilIdle()
        assertTrue(
            "inside the band a shown button stays visible",
            viewModel.uiState.value.browseReCenterVisible
        )

        // Below the hide threshold: hidden immediately, no dwell.
        moveViewportBy(MapCanvasViewModel.RECENTER_HIDE_OFFSET_PX / 2.0, 0.0)
        advanceUntilIdle()
        assertFalse(
            "below the hide threshold the button hides immediately",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun noGpsFixHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport with a fix", viewModel.uiState.value.browseReCenterVisible)

        locationService.setGpsFixForTest(null)
        advanceUntilIdle()
        assertFalse(
            "without a fix the button must be hidden",
            viewModel.uiState.value.browseReCenterVisible
        )
        assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
    }

    // --- the action and the anchor presets ---

    @Test
    fun reCenterCentersTheVehicleAndHidesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        viewModel.recenterInBrowse()
        advanceUntilIdle()

        assertTrue("the vehicle must be centered", committedOffsetPx() < 1.0)
        assertEquals("mode stays BROWSE", MapMode.BROWSE, viewModel.mode)
        assertFalse(
            "re-centering hides the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    @Test
    fun reCenterIgnoresANonCenterAnchorPreset() = runTest(mainDispatcherRule.dispatcher) {
        val current = settingsStorage.load()
        settingsStorage.save(
            current.copy(freeDrivingAnchorId = VehicleAnchorPosition.BOTTOM_CENTER.id)
        )
        createViewModel()
        advanceUntilIdle()

        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        awaitDwellAndRefresh()
        advanceUntilIdle()
        assertTrue("off-center viewport", viewModel.uiState.value.browseReCenterVisible)

        viewModel.recenterInBrowse()
        advanceUntilIdle()

        val vp = viewModel.uiState.value.viewport
        val (x, y) = ProjectionUtils
            .viewport(vp.centerLat, vp.centerLon, vp.magnification, SCREEN_W, SCREEN_H, dpi, vp.angle)
            .geoToScreenRotated(VEHICLE_LAT, VEHICLE_LON)
        assertEquals("vehicle at the canvas center x", SCREEN_W / 2.0, x, 1.0)
        assertEquals(
            "vehicle at the canvas center y — the BOTTOM_CENTER preset is not applied",
            SCREEN_H / 2.0, y, 1.0
        )
        assertFalse(
            "an off-center anchor target would re-show the button",
            viewModel.uiState.value.browseReCenterVisible
        )
    }

    // --- driving modes are untouched by the browse rule ---

    @Test
    fun freeDriveSuspensionStillDrivesTheButton() = runTest(mainDispatcherRule.dispatcher) {
        emitFix()
        advanceUntilIdle()
        viewModel.enterFreeDrive()
        advanceUntilIdle()

        assertFalse(
            "following free drive hides the button",
            MapCanvasViewModel.shouldShowReCenterButton(
                viewModel.mode,
                viewModel.uiState.value.driveSuspended,
                viewModel.uiState.value.browseReCenterVisible
            )
        )

        viewModel.updateMagnification(6.0)
        advanceUntilIdle()

        assertTrue(
            "a zoom in free drive suspends the preset and shows the button",
            MapCanvasViewModel.shouldShowReCenterButton(
                viewModel.mode,
                viewModel.uiState.value.driveSuspended,
                viewModel.uiState.value.browseReCenterVisible
            )
        )
    }

    /**
     * The canvas metrics are an input of the derivation, so a change that moves
     * only them must re-derive it (spec: map-modes — Browse re-center, "A canvas
     * size change re-derives the offset"). The reachable path is the first
     * layout: the viewport was restored and the fix is already in state while the
     * canvas has not been measured, so the offset is unmeasurable (NaN) and no
     * dwell is armed; nothing else emits afterwards at a standstill.
     */
    @Test
    fun aCanvasSizeChangeAloneReDerivesTheOffset() = runTest(mainDispatcherRule.dispatcher) {
        // Before the first onSizeChanged: the VM holds the unmeasured canvas.
        viewModel.setScreenSize(0, 0)
        moveViewportBy(400.0, 0.0)
        emitFix()
        advanceUntilIdle()
        assertFalse(
            "the offset is unmeasurable while the canvas is not measured",
            viewModel.uiState.value.browseReCenterVisible
        )

        // The first layout, and nothing else: only the canvas metrics change.
        viewModel.setScreenSize(SCREEN_W, SCREEN_H)
        advanceUntilIdle()

        assertTrue(
            "a canvas size change alone must re-derive the offset and arm the dwell",
            pumpUntil(maxSpins = 2_000) { viewModel.browseReCenterBeyondSinceMs != 0L }
        )

        fakeNowMs += MapCanvasViewModel.RECENTER_DWELL_MS + 150L
        assertTrue("the armed dwell must complete", awaitReveal())
    }

    private companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 1920

        /**
         * Spin budget for [awaitReveal]: each spin advances the test scheduler and yields, so the
         * budget covers many real-clock tick periods while still turning a missing completion into a
         * bounded failure rather than a hang.
         */
        const val REVEAL_PUMP_SPIN_BUDGET = 5_000_000
        const val MAG = 15.0
        const val ROTATION_RAD = 0.7
        const val VEHICLE_LAT = 52.51
        const val VEHICLE_LON = 13.40
    }
}
