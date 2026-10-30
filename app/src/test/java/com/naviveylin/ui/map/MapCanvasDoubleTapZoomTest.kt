package com.naviveylin.ui.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ProjectionUtils
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The screen-level half of double-tap zoom (spec: smooth-zoom — Double tap zooms one level at
 * the tap point): the level the tap commits, the centre that keeps the geographic point under
 * the tap under the tap, the tap point the display animation is anchored on, and the maximum
 * magnification where the tap has no step to take.
 *
 * [fireDoubleTap] is the seam the map canvas calls, exactly as [fireLongPress] is for the
 * long press; the gesture that produces the tap is exercised by `MapGestureComposeTest`.
 * No @Config — must run in the default Robolectric sandbox so the FakeOSMScoutClient JNI stub
 * loads correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasDoubleTapZoomTest {

    private lateinit var context: Context
    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var client: FakeOSMScoutClient

    private val screenW = 1080
    private val screenH = 1920
    private val centerLat = 48.2
    private val centerLon = 16.4
    private val tap = Offset(300f, 700f)

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
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
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

    private fun dpi(): Double = context.resources.displayMetrics.densityDpi.toDouble()

    /** The geographic point the screen pixel [pos] shows at the viewport's own state. */
    private fun geoUnderTap(): Pair<Double, Double> {
        val vp = viewModel.uiState.value.viewport
        return ProjectionUtils.viewport(
            vp.centerLat, vp.centerLon, vp.magnification, screenW, screenH, dpi(), vp.angle
        ).screenToGeo(tap.x.toDouble(), tap.y.toDouble())
    }

    @Test
    fun `double tap steps one level and keeps the tap point fixed`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.updateCenter(centerLat, centerLon)
            // Fractional magnification (a pinch landed here): the discrete input steps to the
            // next whole level, exactly as the zoom button does.
            viewModel.updateMagnification(10.4)
            val before = geoUnderTap()

            val animatedAnchors = mutableListOf<Offset>()
            fireDoubleTap(viewModel, context, tap, IntSize(screenW, screenH)) { animatedAnchors.add(it) }
            advanceUntilIdle()

            assertEquals(
                "a double tap commits one level above the recorded level",
                11.0, viewModel.uiState.value.viewport.magnification, 1e-9
            )
            val after = geoUnderTap()
            assertEquals("the latitude under the tap stays under the tap", before.first, after.first, 1e-6)
            assertEquals("the longitude under the tap stays under the tap", before.second, after.second, 1e-6)
            assertEquals("the display eases around the tap point", listOf(tap), animatedAnchors)
        }

    @Test
    fun `double tap requests the frame its committed level needs`() =
        runTest(mainDispatcherRule.dispatcher) {
            // A committed magnification is a request, not a frame: the renderer is where the
            // walk's step is asked for and where the frame carrying the new level comes from.
            // Without the request the display eases toward a level no frame carries, which is
            // what review round 1, finding 1, measured. `currentMag` is what the renderer is
            // asked for, set synchronously by both request paths, so the reading is not a race
            // with the native render.
            val renderer = MapRenderer(client, dpi = dpi(), scope = backgroundScope)
            renderer.screenWidth = screenW
            renderer.screenHeight = screenH
            viewModel.setScreenSize(screenW, screenH)
            viewModel.setMapRendererForTest(renderer)
            viewModel.updateCenter(centerLat, centerLon)
            viewModel.updateMagnification(10.4)
            val before = renderer.currentMag

            fireDoubleTap(viewModel, context, tap, IntSize(screenW, screenH)) { }

            assertTrue(
                "the double tap asks the renderer for a frame, got mag=$before unchanged",
                renderer.currentMag != before
            )
        }

    @Test
    fun `double tap at the maximum magnification leaves the viewport alone`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.updateCenter(centerLat, centerLon)
            viewModel.updateMagnification(MapCanvasViewModel.MAX_MAG)

            val animatedAnchors = mutableListOf<Offset>()
            fireDoubleTap(viewModel, context, tap, IntSize(screenW, screenH)) { animatedAnchors.add(it) }
            advanceUntilIdle()

            val vp = viewModel.uiState.value.viewport
            assertEquals("no level above the maximum exists", MapCanvasViewModel.MAX_MAG, vp.magnification, 1e-9)
            assertEquals("the centre does not move without a step", centerLat, vp.centerLat, 1e-9)
            assertEquals("the centre does not move without a step", centerLon, vp.centerLon, 1e-9)
            assertTrue("no animation runs without a step, got $animatedAnchors", animatedAnchors.isEmpty())
        }
}
