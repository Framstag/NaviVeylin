package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.navigation.NavigationStateProvider
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the fractional auto-zoom commit path (spec: auto-speed-zoom —
 * Smooth zoom transitions): fractional magnifications committed via
 * SpeedZoomTable.stepToward with the distance-proportional step (gain 0.3,
 * capped at 0.5 levels per update, settling inside the ZOOM_EPSILON deadband),
 * epsilon no-op at constant speed, first-commit direct jump (spec's "Speed
 * unknown" scenario), and the autoZoomCommitTick bumped only on real commits
 * (spec: smooth-zoom).
 *
 * Classloader rule (AGENTS.md): FakeOSMScoutClient-backed tests run under
 * Robolectric with the DEFAULT sandbox — no @Config(sdk=...).
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAutoZoomCommitTest {

    private companion object {
        /** Speed whose interpolated target is 14.5 (table: 60 km/h → 16, 90 km/h → 13). */
        const val HIGHWAY_SPEED_KMH = 75.0

        /** Interpolated target magnification at [HIGHWAY_SPEED_KMH]. */
        const val HIGHWAY_TARGET_MAG = 14.5

        /** Iteration guard for the convergence helper (settles in ~10 updates). */
        const val MAX_SETTLE_PUMPS = 30
    }

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var navViewModel: NavigationViewModel
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        locationService = LocationService(context)
        navViewModel = NavigationViewModel(
            client, NavigationStateProvider(), locationService, context
        )
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setNavigationViewModel(navViewModel)
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Drive the navigation state's speed directly (deterministic — the real
     *  speed path goes through the native engine, unavailable in unit tests). */
    private fun setNavSpeed(speedKmH: Double) {
        val field = NavigationViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(navViewModel) as MutableStateFlow<NavigationState>
        flow.value = flow.value.copy(currentSpeedKmH = speedKmH)
    }

    /** Switch the derived map mode (navigation active vs not). */
    private fun setNavMode(isNavigating: Boolean) {
        val field = NavigationViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(navViewModel) as MutableStateFlow<NavigationState>
        flow.value = flow.value.copy(isNavigating = isNavigating)
    }

    /** Inject a fix into the shared location service (test only). */
    private fun injectFix(step: Int, speedKmH: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<GpsFix?>
        // Distinct positions (> 5 m apart per step) so the follow collector sees
        // positionChanged; distinct timestamps bypass the 100 ms dedupe window.
        flow.value = GpsFix(
            lat = 51.5 + step * 0.001,
            lon = 7.4 + step * 0.001,
            accuracy = 5.0,
            speedKmH = speedKmH,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis() + step * 500L
        )
    }

    /**
     * Pump the collector: the follow render throttle compares REAL
     * System.currentTimeMillis (GPS_FOLLOW_RENDER_INTERVAL_MS = 200 ms), so
     * consecutive fixes must be spaced in real time; the collector itself runs
     * on the virtual test scheduler.
     */
    private fun pumpFix(scope: TestScope, step: Int, speedKmH: Double) {
        setNavSpeed(speedKmH)
        injectFix(step, speedKmH)
        Thread.sleep(230)
        scope.advanceTimeBy(1)
        scope.runCurrent()
    }

    /**
     * Pump [HIGHWAY_SPEED_KMH] fixes until the proportional auto-zoom stops
     * committing (the remaining gap fell inside [SpeedZoomTable.ZOOM_EPSILON]),
     * asserting the documented curve: monotonic approach, no overshoot, never
     * rounded, one `autoZoomCommitTick` bump per commit. An exact landing on the
     * target is asymptotically unreachable under the gain, so the settle check
     * asserts the deadband instead. Returns the settled magnification.
     */
    private fun TestScope.pumpUntilAutoZoomSettles(firstStep: Int): Double {
        var previous = viewModel.uiState.value.viewport.magnification
        var commits = viewModel.uiState.value.autoZoomCommitTick
        val zoomingOut = HIGHWAY_TARGET_MAG < previous
        var step = firstStep
        while (step < firstStep + MAX_SETTLE_PUMPS) {
            pumpFix(this, step, HIGHWAY_SPEED_KMH)
            val current = viewModel.uiState.value.viewport.magnification
            if (current == previous) break
            if (zoomingOut) {
                assertTrue("must approach the target ($current after $previous)", current < previous)
                assertTrue("must not overshoot the target ($current)", current >= HIGHWAY_TARGET_MAG)
            } else {
                assertTrue("must approach the target ($current after $previous)", current > previous)
                assertTrue("must not overshoot the target ($current)", current <= HIGHWAY_TARGET_MAG)
            }
            assertTrue("must stay fractional, never rounded ($current)", current % 1.0 != 0.0)
            commits += 1
            assertEquals("each commit bumps the tick", commits, viewModel.uiState.value.autoZoomCommitTick)
            previous = current
            step += 1
        }
        assertTrue(
            "auto-zoom must settle before the iteration guard ($previous)",
            step < firstStep + MAX_SETTLE_PUMPS
        )
        assertTrue(
            "settled inside the deadband ($previous)",
            kotlin.math.abs(previous - HIGHWAY_TARGET_MAG) <= SpeedZoomTable.ZOOM_EPSILON
        )
        return previous
    }

    @Test
    fun `auto zoom converges proportionally and settles inside the deadband`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.enterFreeDrive()
        // First fix at city speed: first-commit exception jumps to target 16.0.
        pumpFix(this, 1, 30.0)
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("first commit bumps the tick", 1, viewModel.uiState.value.autoZoomCommitTick)

        // Steady city speed: the epsilon no-op holds mag and tick.
        pumpFix(this, 2, 30.0)
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("constant speed must not re-commit", 1, viewModel.uiState.value.autoZoomCommitTick)

        // Accelerate to 75 km/h → target 14.5: the step is the proportional
        // fraction (gain 0.3) of the remaining gap, capped at 0.5 per update.
        // A 1.5-level gap therefore steps 0.45, NOT a fixed 0.5 (design D1/D5),
        // so the sequence is 16.0 → 15.55 → 15.235 → 15.0145 …
        pumpFix(this, 3, HIGHWAY_SPEED_KMH)
        assertEquals("proportional step 1 (0.3 × 1.5)", 15.55, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(2, viewModel.uiState.value.autoZoomCommitTick)

        pumpFix(this, 4, HIGHWAY_SPEED_KMH)
        assertEquals("proportional step 2 (0.3 × 1.05)", 15.235, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(3, viewModel.uiState.value.autoZoomCommitTick)

        pumpFix(this, 5, HIGHWAY_SPEED_KMH)
        assertEquals("proportional step 3 (0.3 × 0.735)", 15.0145, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(4, viewModel.uiState.value.autoZoomCommitTick)

        // The exponential approach continues (sub-epsilon steps floored at
        // 0.05) and settles inside the ±0.05 deadband around 14.5; the loop
        // asserts monotonicity, no overshoot and the per-commit tick bump.
        val settled = pumpUntilAutoZoomSettles(firstStep = 6)

        // Speed unchanged: epsilon no-op, no further commits, no churn.
        val settledTick = viewModel.uiState.value.autoZoomCommitTick
        pumpFix(this, 6 + MAX_SETTLE_PUMPS, HIGHWAY_SPEED_KMH)
        assertEquals("stable target must not churn", settled, viewModel.uiState.value.viewport.magnification, 0.0)
        assertEquals("stable target must not re-commit", settledTick, viewModel.uiState.value.autoZoomCommitTick)
    }

    @Test
    fun `first commit jumps directly to the target instead of stepping`() = runTest(mainDispatcherRule.dispatcher) {
        // Speed unknown (NaN): filterSpeed falls back to the 20 km/h default,
        // targeting 16.0; the first commit must NOT ease from the 15.0 init.
        viewModel.enterFreeDrive()
        viewModel.updateMagnification(16.0) // start already at a city level
        runCurrent()

        pumpFix(this, 1, Double.NaN)
        assertEquals(
            "first auto-zoom commit goes straight to the target",
            16.0, viewModel.uiState.value.viewport.magnification, 0.001
        )
    }

    @Test
    fun `manual zoom suspends auto zoom and a band change re-engages with proportional convergence`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.enterFreeDrive()
        pumpFix(this, 1, 30.0)
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.001)

        // Band-change re-engage is reachable in NAVIGATION mode: while
        // suspended in FREE_DRIVE the follow collector returns early (the
        // drive-suspension gate), so the threshold-crossing resume lives in the
        // NAVIGATION path (existing behavior, spec scenario "Speed crosses
        // threshold boundary").
        setNavMode(true)
        assertEquals(MapMode.NAVIGATION, viewModel.mode)

        // Manual zoom suspends auto-zoom (spec: auto-speed-zoom — Manual zoom
        // temporarily suspends auto-zoom).
        viewModel.updateMagnification(11.0)
        assertTrue(viewModel.uiState.value.driveSuspended)
        val tickBefore = viewModel.uiState.value.autoZoomCommitTick

        // Speed changes within the same band: still suspended, no commit.
        pumpFix(this, 2, 28.0)
        assertEquals("suspended auto-zoom must not move the mag", 11.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(tickBefore, viewModel.uiState.value.autoZoomCommitTick)

        // Speed crosses a band boundary (28 km/h band 2 → 75 km/h band 4):
        // re-engage, converge fractionally from the suspended level (11.0)
        // toward the 14.5 target at 75 km/h. A 3.5-level gap exceeds
        // 0.5 / gain = 1.667 levels, so the first four updates run at the 0.5
        // cap; from a 1.5-level gap on, the proportional fraction takes over
        // (0.45, not 0.5) and the approach settles inside the deadband.
        pumpFix(this, 3, HIGHWAY_SPEED_KMH)
        assertTrue("band-crossing clears the suspension", !viewModel.uiState.value.driveSuspended)
        assertEquals("re-engage converges at the cap, no snap", 11.5, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("re-engage commits and bumps the tick", tickBefore + 1, viewModel.uiState.value.autoZoomCommitTick)

        pumpFix(this, 4, HIGHWAY_SPEED_KMH)
        assertEquals("capped step (0.3 × 3.0 clamps at 0.5)", 12.0, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 5, HIGHWAY_SPEED_KMH)
        assertEquals("capped step (0.3 × 2.5 clamps at 0.5)", 12.5, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 6, HIGHWAY_SPEED_KMH)
        assertEquals("capped step (0.3 × 2.0 clamps at 0.5)", 13.0, viewModel.uiState.value.viewport.magnification, 0.001)

        pumpFix(this, 7, HIGHWAY_SPEED_KMH)
        assertEquals("proportional step at a 1.5-level gap", 13.45, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 8, HIGHWAY_SPEED_KMH)
        assertEquals("proportional step at a 1.05-level gap", 13.765, viewModel.uiState.value.viewport.magnification, 0.001)

        // Settles inside the deadband, still never rounding to a whole level.
        pumpUntilAutoZoomSettles(firstStep = 9)
    }
}
