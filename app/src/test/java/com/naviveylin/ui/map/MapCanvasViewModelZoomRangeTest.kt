package com.naviveylin.ui.map

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the zoom range: the zoom control and the pinch/rotation gesture
 * clamp share the same floor of 4 (MIN_MAG == GESTURE_MIN_MAG). A lower
 * floor would allow world-zoom renders that hang the native render worker.
 * No @Config — must run in the default Robolectric
 * sandbox so the FakeOSMScoutClient JNI stub loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelZoomRangeTest {

    private lateinit var context: Context
    private lateinit var viewModel: MapCanvasViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val client = FakeOSMScoutClient()
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
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    @Test
    fun zoomOutStopsAtFourFloor() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.updateMagnification(6.0)
        assertEquals(6.0, viewModel.uiState.value.viewport.magnification, 1e-9)

        viewModel.zoomOut()
        assertEquals(5.0, viewModel.uiState.value.viewport.magnification, 1e-9)

        // Floor: further zoom-out is a no-op.
        viewModel.zoomOut()
        assertEquals(4.0, viewModel.uiState.value.viewport.magnification, 1e-9)
        viewModel.zoomOut()
        assertEquals(4.0, viewModel.uiState.value.viewport.magnification, 1e-9)
    }

    @Test
    fun updateMagnificationClampsToControlRange() = runTest(mainDispatcherRule.dispatcher) {
        // Below the floor clamps up to 4.
        viewModel.updateMagnification(1.0)
        assertEquals(4.0, viewModel.uiState.value.viewport.magnification, 1e-9)

        // Above the max clamps down to 20.
        viewModel.updateMagnification(21.0)
        assertEquals(20.0, viewModel.uiState.value.viewport.magnification, 1e-9)
    }

    @Test
    fun zoomInAtMaxIsNoOp() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.updateMagnification(20.0)
        viewModel.zoomIn()
        assertEquals(20.0, viewModel.uiState.value.viewport.magnification, 1e-9)
    }

    @Test
    fun gestureClampKeepsFourFloor() {
        // Pinch/rotation commit can never go below 4 — same floor as the
        // zoom control.
        for (magInt in 0..4) {
            assertEquals(4.0, MapCanvasViewModel.clampGestureMagnification(magInt.toDouble()), 1e-9)
        }
        assertEquals(5.0, MapCanvasViewModel.clampGestureMagnification(5.0), 1e-9)
        assertEquals(20.0, MapCanvasViewModel.clampGestureMagnification(20.0), 1e-9)
        assertEquals(20.0, MapCanvasViewModel.clampGestureMagnification(21.0), 1e-9)
    }

    @Test
    fun zoomButtonsSnapFromFractionalMagnification() = runTest(mainDispatcherRule.dispatcher) {
        // continuous-pinch-zoom: after a continuous pinch commit (fractional mag),
        // the discrete controls move to whole levels (spec: zoom-controls unchanged
        // behavior on a fractional starting magnification).
        viewModel.updateMagnification(15.3)
        viewModel.zoomIn()
        assertEquals(16.0, viewModel.uiState.value.viewport.magnification, 1e-9)

        viewModel.updateMagnification(15.3)
        viewModel.zoomOut()
        assertEquals(14.0, viewModel.uiState.value.viewport.magnification, 1e-9)
    }

    @Test
    fun updateMagnificationKeepsFractionalValues() = runTest(mainDispatcherRule.dispatcher) {
        // Continuous pinch commits store the fractional magnification verbatim.
        viewModel.updateMagnification(15.34)
        assertEquals(15.34, viewModel.uiState.value.viewport.magnification, 1e-12)
    }
}
