package com.naviveylin.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.ZoomWalk
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Drives a frame through the PRODUCTION path: `initMap` builds the renderer, `setScreenSize` gives it a
 * surface, the renderer's own dispatcher runs the queued job, and the frame reaches the UI state through
 * the collector that consumes `MapRenderer.frameFlow` (spec: map-render — A rendered frame reaches the UI
 * state through the production collector).
 *
 * Before this case class no ViewModel case had a frame reach `MapCanvasUiState` through the collector: the
 * classes that needed one (`MapCanvasViewModelZoomWalkTest`, `MapCanvasViewModelRouteFitTest`) injected a
 * magnification through a test-only setter (`publishRenderedFrameForTest`, deleted by this change), so the
 * collector, the bitmap the frame carries and the viewport that rides with it were never exercised. The two
 * seams that made that necessary are asserted here: without a size the job is skipped
 * (`MapRenderer.enqueueRenderJob`), and without `initMap` there is no renderer at all.
 *
 * Robolectric with the DEFAULT sandbox (AGENTS.md classloader rule); the fake client renders the pixels
 * (`FakeOSMScoutClient.render*`), so no JNI and no device are involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelFrameHarnessTest {

    private companion object {
        const val MAP_PATH = "/data/maps/harness"

        /** Surface the case reports; the frame is rendered at the overrun size, not this one. */
        const val CANVAS_W = 120
        const val CANVAS_H = 200

        /** A four-level walk needs ~16 steps; the guard is the case's own bound, not a timeout. */
        const val MAX_WALK_FRAMES = 40
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        val settingsStorage = SettingsStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context).also { it.ioDispatcher = mainDispatcherRule.dispatcher },
            settingsStorage = settingsStorage,
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            darkModeController = DarkModeController(settingsStorage),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        // One scheduler for every hop the render path takes: the ViewModel's own work, the renderer's
        // scope (which owns the queue and the debounce) and the frame collector's resumptions.
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    /**
     * Run the production initialization and, when [reportSize] is set, the production size report; returns
     * the frames that landed. No state is injected: the scheduler drives the renderer's queue.
     */
    private fun TestScope.landFrames(reportSize: Boolean = true): MutableList<MapRenderer.FrameState> {
        val frames = mutableListOf<MapRenderer.FrameState>()
        viewModel.initMap(MAP_PATH)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val renderer = viewModel.mapRenderer
        assertNotNull("initMap must build the renderer off the main thread", renderer)
        backgroundScope.launch {
            renderer!!.frameFlow.collect { frame -> if (frame.bitmap != null) frames += frame }
        }
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        if (reportSize) {
            viewModel.setScreenSize(CANVAS_W, CANVAS_H)
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        }
        return frames
    }

    @Test
    fun `a frame lands through the frame collector`() = runTest(mainDispatcherRule.dispatcher) {
        val frames = landFrames()

        assertEquals("exactly one frame for one size-triggered render", 1, frames.size)
        val frame = frames.single()
        val state = viewModel.uiState.value
        assertNotNull("the collector must publish the viewport of the frame it applied", state.renderViewport)
        assertEquals("the published viewport is the rendered one", frame.viewport.mag, state.renderViewport!!.mag, 1e-9)
        assertNotNull("the collector must publish the bitmap the frame carried", state.renderedBitmap)
        // The frame is the renderer's own overrun buffer, not a canvas-sized bitmap a test invented.
        val expectedW = (CANVAS_W * MapRenderer.DEFAULT_CANVAS_OVERRUN).toInt()
        val expectedH = (CANVAS_H * MapRenderer.DEFAULT_CANVAS_OVERRUN).toInt()
        assertEquals(expectedW, state.renderedBitmap!!.width)
        assertEquals(expectedH, state.renderedBitmap!!.height)
        assertEquals("the frame's bitmap is the published one", expectedW, frame.bitmap!!.width)
    }

    @Test
    fun `a render without a reported size is skipped`() = runTest(mainDispatcherRule.dispatcher) {
        // The finding's own condition: nothing arrives, because the surface is still 0x0.
        val frames = landFrames(reportSize = false)

        assertTrue("no frame can land while the surface size is zero", frames.isEmpty())
        assertNull("no viewport is published either", viewModel.uiState.value.renderViewport)
        assertEquals(
            "and the renderer asked for no native render",
            0,
            client.renderCount.get() + client.renderWithRouteAndPoisCount.get()
        )
    }

    @Test
    fun `a walk step is the magnification the renderer rendered`() =
        runTest(mainDispatcherRule.dispatcher) {
            val all = landFrames()
            val first = all.single()
            val from = first.viewport.mag
            val target = from + 2.0
            // The one collector from landFrames keeps running: the walk's frames append to `all`, so
            // the slice after the first frame is exactly what the walk produced.
            val mark = all.size

            viewModel.updateMagnification(target)
            // The production order (MapCanvasScreenChromeBand, MapCanvasMapBand,
            // MapCanvasScreenParts): the commit records the level, the render request is what asks
            // the renderer for the walk's first step and produces the frame that carries it.
            viewModel.renderMap()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            val walkFrames = all.drop(mark)

            assertTrue("the walk must finish inside the frame guard", walkFrames.size < MAX_WALK_FRAMES)
            val expectedSteps = ceil((target - from) / ZoomWalk.ZOOM_BLIT_WINDOW - 1e-9).toInt()
            assertEquals(
                "one landed frame per walked step (renders=${client.renderCount.get()}+" +
                    "${client.renderWithRouteAndPoisCount.get()}, frames=${all.map { it.viewport.mag }}, " +
                    "state=${viewModel.uiState.value.renderViewport?.mag}, step=" +
                    "${viewModel.uiState.value.zoomWalkStepMag})",
                expectedSteps,
                walkFrames.size
            )
            // Every frame the UI was handed is the step the walk published, one window at a time.
            walkFrames.forEachIndexed { index, frame ->
                val expected = minOf(from + ZoomWalk.ZOOM_BLIT_WINDOW * (index + 1), target)
                assertEquals(
                    "frame ${index + 1} of the walk rendered the walked step",
                    expected,
                    frame.viewport.mag,
                    1e-9
                )
            }
            assertTrue(
                "no frame exceeds the window of the frame before it",
                walkFrames.zipWithNext().all { (a, b) ->
                    abs(b.viewport.mag - a.viewport.mag) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
                }
            )
            assertEquals(
                "the walk lands exactly on the committed target",
                target,
                walkFrames.last().viewport.mag,
                1e-9
            )
            val state = viewModel.uiState.value
            assertEquals("the newest frame is the one the UI holds", target, state.renderViewport!!.mag, 1e-9)
            assertEquals("and the viewport already recorded the target", target, state.viewport.magnification, 1e-9)
            assertTrue("the walk is finished", state.zoomWalkStepMag.isNaN())
        }
}