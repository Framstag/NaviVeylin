package com.naviveylin.ui.map
import com.naviveylin.core.BasemapReloadNotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.MainDispatcherRule
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
 * Verifies auto-zoom suspension is exposed to the UI (spec: map-recenter-button
 * — "Auto-zoom suspended while navigating (follow still on)"): a user zoom
 * suspends auto-zoom and sets uiState.autoZoomPaused; follow/auto-zoom
 * re-engagement clears it; the re-center button predicate follows the matrix.
 */
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelAutoZoomPauseTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsStorage = SettingsStorage(context),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context),
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun userZoomSuspendsAutoZoomAndSurfacesPausedState() {
        assertFalse("auto zoom starts active", viewModel.uiState.value.autoZoomPaused)

        viewModel.updateMagnification(6.0)

        assertTrue("user zoom must suspend auto-zoom", viewModel.uiState.value.autoZoomPaused)
    }

    @Test
    fun buttonZoomSuspendsAutoZoom() {
        // The on-map zoom buttons (standard and routing views) go through
        // zoomIn/zoomOut, which route into updateMagnification — the same
        // suspension path as pinch zoom.
        assertFalse("auto zoom starts active", viewModel.uiState.value.autoZoomPaused)

        viewModel.zoomIn()

        assertTrue("button zoom must suspend auto-zoom", viewModel.uiState.value.autoZoomPaused)
    }

    @Test
    fun userZoomDoesNotSuspendWhenAutoZoomDisabled() {
        viewModel.onToggleAutoZoom(false)
        assertFalse(viewModel.uiState.value.autoZoomPaused)

        viewModel.updateMagnification(6.0)

        assertFalse("disabled auto zoom must not be suspended", viewModel.uiState.value.autoZoomPaused)
    }

    @Test
    fun followToggleReengagesAndClearsPausedState() {
        viewModel.updateMagnification(6.0)
        assertTrue(viewModel.uiState.value.autoZoomPaused)

        viewModel.onToggleFollowMode(true)

        assertFalse("follow toggle must resume auto-zoom", viewModel.uiState.value.autoZoomPaused)
    }

    @Test
    fun autoZoomToggleReengagesAndClearsPausedState() {
        viewModel.updateMagnification(6.0)
        assertTrue(viewModel.uiState.value.autoZoomPaused)

        viewModel.onToggleAutoZoom(true)

        assertFalse("auto-zoom toggle on must clear suspension", viewModel.uiState.value.autoZoomPaused)
    }

    @Test
    fun reCenterButtonPredicateMatrix() {
        val p = MapCanvasViewModel::shouldShowReCenterButton
        // Follow on, auto zoom driving: no button.
        assertFalse(p(true, false, true))
        // Follow off (pan disengaged): button regardless of navigation.
        assertTrue(p(false, false, true))
        assertTrue(p(false, false, false))
        // Auto zoom suspended while navigating, follow still on: button.
        assertTrue(p(true, true, true))
        // Suspended but NOT navigating (free-form browsing): follow on, no button —
        // only navigation surfaces the paused state.
        assertFalse(p(true, true, false))
        // Both conditions: button.
        assertTrue(p(false, true, false))
    }
}
