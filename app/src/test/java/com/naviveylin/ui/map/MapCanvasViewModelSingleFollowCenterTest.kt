package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.Vehicle
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
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
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.naviveylin.test.engineUnderTest

/**
 * Verifies the single follow center (spec: smooth-follow — Prediction state
 * update, delta fix-follow-vehicle-jumps): while the display loop is active
 * ([MapCanvasViewModel.followDisplayLat]/[Lon] populated), a GPS fix does NOT
 * re-commit the follow center derived from the fix — the viewport center
 * stays anchored on the DISPLAYED position, and renders anchor there too
 * (rotation/zoom/initial-frame only; pure position movement renders nothing).
 *
 * The display loop itself lives in MapCanvasScreen (Compose); this test drives
 * its VM-side contract: the display position the screen writes is the only
 * follow center. With screenWidth == 0 in the harness, `followRenderTarget`
 * falls back to the raw lat/lon, so the committed center equals the display
 * position exactly (no anchor offset to account for).
 *
 * Classloader rule (AGENTS.md): FakeOSMScoutClient-backed tests run under
 * Robolectric with the DEFAULT sandbox — no @Config(sdk=...).
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelSingleFollowCenterTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var locationService: LocationService
    private lateinit var navViewModel: NavigationViewModel
    private lateinit var viewModel: MapCanvasViewModel

    /**
     * The time the follow-render throttle sees. It starts at the wall clock so the fix timestamps the
     * cases inject stay comparable to it (fix age is data), and is then moved by hand instead of by
     * sleeping past the 200 ms interval (spec `unit-test-suite-runtime` — Injected time instead of a real
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
        navViewModel = NavigationViewModel(
            engineUnderTest({ client }, locationService, context)
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
            timeSource = EngineTimeSource { fakeNowMs },
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setNavigationViewModel(navViewModel)
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /** Inject a fix into the shared location service (test only). */
    private fun injectFix(step: Int, bearingDeg: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<GpsFix?>
        flow.value = GpsFix(
            lat = 51.5 + step * 0.0001,
            lon = 7.4 + step * 0.0001,
            accuracy = 5.0,
            speedKmH = 75.0,
            smoothedBearing = bearingDeg,
            markerBearing = bearingDeg,
            time = System.currentTimeMillis() + step * 500L
        )
    }

    /**
     * Pump the collector. The follow render throttle reads the injected clock
     * (GPS_FOLLOW_RENDER_INTERVAL_MS = 200 ms), so consecutive fixes are spaced by moving that clock.
     */
    private fun pumpFix(scope: TestScope, step: Int, bearingDeg: Double) {
        injectFix(step, bearingDeg)
        fakeNowMs += 230L
        scope.advanceTimeBy(1)
        scope.runCurrent()
    }

    @Test
    fun `display active - fix renders are anchored on the display position not the raw fix`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.onToggleFollowMode(true)

            // The display loop writes its displayed position into the VM (the
            // single follow center); the fix positions stay elsewhere.
            viewModel.followDisplayLat = 51.51
            viewModel.followDisplayLon = 7.46
            assertTrue(viewModel.uiState.value.followMode)

            // First fix: rotation + auto-zoom commit — the committed center
            // MUST be the display position, not the raw fix (51.5, 7.4).
            pumpFix(this, 0, 90.0)
            assertEquals(51.51, viewModel.uiState.value.viewport.centerLat, 1e-9)
            assertEquals(7.46, viewModel.uiState.value.viewport.centerLon, 1e-9)
            assertNotEquals(51.5, viewModel.uiState.value.viewport.centerLat, 1e-9)

            // Bearing change with the SAME display position: still anchored on
            // the display (rotation re-render keeps the follow center).
            pumpFix(this, 1, 180.0)
            assertEquals(51.51, viewModel.uiState.value.viewport.centerLat, 1e-9)
            assertEquals(7.46, viewModel.uiState.value.viewport.centerLon, 1e-9)
        }

    @Test
    fun `display active - a pure position move renders nothing and does not re-center`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.onToggleFollowMode(true)

            // Establish: display + first fix commit (rotation/zoom) anchors on it.
            viewModel.followDisplayLat = 51.51
            viewModel.followDisplayLon = 7.46
            pumpFix(this, 0, 90.0)
            val centerBefore = viewModel.uiState.value.viewport.centerLat to
                    viewModel.uiState.value.viewport.centerLon
            assertEquals(51.51, centerBefore.first, 1e-9)
            assertEquals(7.46, centerBefore.second, 1e-9)

            // Subsequent fixes: same bearing, same speed (auto-zoom settled,
            // no rotation change) — only the position moved > 5 m. The fix
            // must NOT re-center the viewport (no render, no center commit).
            for (step in 1..3) {
                pumpFix(this, step, 90.0)
            }
            assertEquals(
                centerBefore.first, viewModel.uiState.value.viewport.centerLat, 1e-9
            )
            assertEquals(
                centerBefore.second, viewModel.uiState.value.viewport.centerLon, 1e-9
            )
        }

    @Test
    fun `display inactive - first fix still establishes an anchor frame`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.onToggleFollowMode(true)

            // No display position yet (the screen loop has not run): the initial
            // commit falls back to the marker fix so the map gets a first frame.
            pumpFix(this, 0, 90.0)
            assertEquals(51.5, viewModel.uiState.value.viewport.centerLat, 1e-9)
            assertEquals(7.4, viewModel.uiState.value.viewport.centerLon, 1e-9)

            // Once the display position arrives, the next commit re-anchors on it.
            viewModel.followDisplayLat = 51.51
            viewModel.followDisplayLon = 7.46
            pumpFix(this, 1, 180.0)
            assertEquals(51.51, viewModel.uiState.value.viewport.centerLat, 1e-9)
            assertEquals(7.46, viewModel.uiState.value.viewport.centerLon, 1e-9)
        }
}
