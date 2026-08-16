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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
            context = context
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun zoomOutStopsAtFourFloor() = runTest {
        viewModel.updateMagnification(6)
        assertEquals(6, viewModel.uiState.value.viewport.magnification)

        viewModel.zoomOut()
        assertEquals(5, viewModel.uiState.value.viewport.magnification)

        // Floor: further zoom-out is a no-op.
        viewModel.zoomOut()
        assertEquals(4, viewModel.uiState.value.viewport.magnification)
        viewModel.zoomOut()
        assertEquals(4, viewModel.uiState.value.viewport.magnification)
    }

    @Test
    fun updateMagnificationClampsToControlRange() = runTest {
        // Below the floor clamps up to 4.
        viewModel.updateMagnification(1)
        assertEquals(4, viewModel.uiState.value.viewport.magnification)

        // Above the max clamps down to 20.
        viewModel.updateMagnification(21)
        assertEquals(20, viewModel.uiState.value.viewport.magnification)
    }

    @Test
    fun zoomInAtMaxIsNoOp() = runTest {
        viewModel.updateMagnification(20)
        viewModel.zoomIn()
        assertEquals(20, viewModel.uiState.value.viewport.magnification)
    }

    @Test
    fun gestureClampKeepsFourFloor() {
        // Pinch/rotation commit can never go below 4 — same floor as the
        // zoom control.
        for (mag in 0..4) {
            assertEquals(4, MapCanvasViewModel.clampGestureMagnification(mag))
        }
        assertEquals(5, MapCanvasViewModel.clampGestureMagnification(5))
        assertEquals(20, MapCanvasViewModel.clampGestureMagnification(20))
        assertEquals(20, MapCanvasViewModel.clampGestureMagnification(21))
    }
}
