package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
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
 * SpeedZoomTable.stepToward, epsilon no-op at constant speed, first-commit
 * direct jump (spec's "Speed unknown" scenario), and the autoZoomCommitTick
 * bumped only on real commits (spec: smooth-zoom).
 *
 * Classloader rule (AGENTS.md): FakeOSMScoutClient-backed tests run under
 * Robolectric with the DEFAULT sandbox — no @Config(sdk=...).
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAutoZoomCommitTest {

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

    @Test
    fun `auto zoom commits fractional magnifications stepping half a level per update`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.enterFreeDrive()
        // First fix at city speed: first-commit exception jumps to target 16.0.
        pumpFix(this, 1, 30.0)
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("first commit bumps the tick", 1, viewModel.uiState.value.autoZoomCommitTick)

        // Steady city speed: the epsilon no-op holds mag and tick.
        pumpFix(this, 2, 30.0)
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("constant speed must not re-commit", 1, viewModel.uiState.value.autoZoomCommitTick)

        // Accelerate to 75 km/h → target 14.5: converge at most 0.5/update.
        pumpFix(this, 3, 75.0)
        assertEquals("step 1 of 3", 15.5, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(2, viewModel.uiState.value.autoZoomCommitTick)

        pumpFix(this, 4, 75.0)
        assertEquals("step 2 of 3", 15.0, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(3, viewModel.uiState.value.autoZoomCommitTick)

        pumpFix(this, 5, 75.0)
        assertEquals("settles at the fractional target, never rounded", 14.5, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals(4, viewModel.uiState.value.autoZoomCommitTick)

        // Speed unchanged: epsilon no-op, no further commits.
        pumpFix(this, 6, 75.0)
        assertEquals(14.5, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("stable target must not churn", 4, viewModel.uiState.value.autoZoomCommitTick)
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
    fun `manual zoom suspends auto zoom and a band change re-engages with fractional convergence`() = runTest(mainDispatcherRule.dispatcher) {
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
        // toward the 14.5 target at 75 km/h.
        pumpFix(this, 3, 75.0)
        assertTrue("band-crossing clears the suspension", !viewModel.uiState.value.driveSuspended)
        assertEquals("re-engage converges, no snap", 11.5, viewModel.uiState.value.viewport.magnification, 0.001)
        assertEquals("re-engage commits and bumps the tick", tickBefore + 1, viewModel.uiState.value.autoZoomCommitTick)
        pumpFix(this, 4, 75.0)
        assertEquals(12.0, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 5, 75.0)
        assertEquals(12.5, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 6, 75.0)
        assertEquals(13.0, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 7, 75.0)
        assertEquals(13.5, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 8, 75.0)
        assertEquals(14.0, viewModel.uiState.value.viewport.magnification, 0.001)
        pumpFix(this, 9, 75.0)
        assertEquals("settles at the suburban target", 14.5, viewModel.uiState.value.viewport.magnification, 0.001)
    }
}
