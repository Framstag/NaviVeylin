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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * Verifies the bounded magnification walk wiring in [MapCanvasViewModel]
 * (spec: smooth-zoom - Eased zoom animation on discrete zoom input: "Magnification
 * change larger than the serviceable window is applied across rendered frames",
 * "Change inside the serviceable window keeps the single eased scale", "Target
 * magnification is recorded while the displayed scale still eases"; and
 * "Viewport records final magnification").
 *
 * Every case reads a frame the PRODUCTION path produced (spec: map-render — A rendered frame reaches the
 * UI state through the production collector): a real [MapRenderer] on this test's scheduler requests its
 * pixels from [FakeOSMScoutClient], its emitted frame is handed to [MapCanvasViewModel.applyRenderedFrame],
 * and only then does the walk advance. So the assertions below compare the walked step against the
 * magnification the renderer actually rendered — a frame the test invented could not disagree.
 *
 * Robolectric with the DEFAULT sandbox (AGENTS.md classloader rule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapCanvasViewModelZoomWalkTest {

    private companion object {
        /** Iteration guard: a two-level walk needs ~9 steps. */
        const val MAX_WALK_FRAMES = 40

        /** The frame in hand at the start of every walk case. */
        const val FRONT_MAG = 13.0

        /** Canvas the renderer is given; the frame is rendered at the overrun size. */
        const val CANVAS_W = 120
        const val CANVAS_H = 200
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var viewModel: MapCanvasViewModel

    /** Set by [wireRenderer]; every landed frame comes from it. */
    private lateinit var renderer: MapRenderer

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
            searchHistoryRepository = SearchHistoryRepository(context).also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = LocationService(context),
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.rendererDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private val stepMag: Double get() = viewModel.uiState.value.zoomWalkStepMag
    private val committedMag: Double get() = viewModel.uiState.value.viewport.magnification
    private val displayTarget: Double get() = viewModel.uiState.value.zoomAnimationTargetMag

    /**
     * Give the ViewModel a real renderer with a surface, on this test's scheduler. The size is
     * recorded before the renderer is injected so the injection does not itself submit a render.
     */
    private fun TestScope.wireRenderer() {
        renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = CANVAS_W
        renderer.screenHeight = CANVAS_H
        viewModel.setScreenSize(CANVAS_W, CANVAS_H)
        viewModel.setMapRendererForTest(renderer)
    }

    /**
     * Let the renderer run the queued job and hand the frame it emitted to the production
     * consumer; returns the magnification the frame was rendered at.
     */
    private suspend fun landFrame(): Double {
        val previous = viewModel.uiState.value.renderViewport?.mag
        // Past the renderer's debounce window (every request that is not a walked step goes through
        // it), then idle: both hops run on this test's scheduler, so no real time passes.
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val frame = renderer.frameFlow.value
        assertNotEquals(
            "the renderer must emit a NEW frame at the requested magnification",
            previous,
            frame.viewport.mag
        )
        viewModel.applyRenderedFrame(frame)
        return frame.viewport.mag
    }

    /** Establish the frame in hand at [mag] through the production drive. */
    private suspend fun landFrontFrame(mag: Double = FRONT_MAG): Double {
        viewModel.commitMagnification(mag, walk = false)
        viewModel.renderMap()
        val landed = landFrame()
        assertEquals("the frame in hand is the one requested", mag, landed, 1e-9)
        return landed
    }

    /**
     * Run the walk to completion through landed frames; returns the frames the walk committed,
     * including the final one (which the walk lands in one render when its remaining gap is
     * inside the window).
     */
    private suspend fun walkToCompletion(fromMag: Double): List<Double> {
        val frames = mutableListOf<Double>()
        var previous = fromMag
        var guard = 0
        while (!stepMag.isNaN() && guard++ < MAX_WALK_FRAMES) {
            val step = stepMag
            assertTrue(
                "step of ${step - previous} exceeds the window the frame in hand can serve",
                abs(step - previous) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
            )
            // The frame the renderer produced FOR THIS STEP is the step: the walk is paced by the
            // landing of the previous frame, and renderMap asks for the walked magnification.
            val rendered = landFrame()
            assertEquals("the rendered frame carries the walked step", step, rendered, 1e-9)
            frames += rendered
            previous = rendered
        }
        assertTrue("the walk must finish inside the frame guard", guard < MAX_WALK_FRAMES)
        assertTrue(
            "the landing frame of ${committedMag - previous} exceeds the window",
            abs(committedMag - previous) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
        )
        // The walk's last render lands the remainder in one frame: it is a frame like any other, so it
        // is landed and asserted rather than assumed.
        if (viewModel.uiState.value.renderViewport?.mag != committedMag) {
            assertEquals("the landing frame carries the committed magnification", committedMag, landFrame(), 1e-9)
        }
        frames += viewModel.uiState.value.renderViewport!!.mag
        return frames
    }

    @Test
    fun `a large change walks and lands exactly on the target`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRenderer()
            landFrontFrame()
            val target = FRONT_MAG + 2.0

            viewModel.updateMagnification(target)
            viewModel.renderMap()

            assertEquals(
                "the recorded viewport holds the FINAL magnification at once",
                target,
                committedMag,
                1e-9
            )
            assertEquals(
                "the render pipeline starts one window away from the frame in hand",
                FRONT_MAG + ZoomWalk.ZOOM_BLIT_WINDOW,
                stepMag,
                1e-9
            )
            assertEquals(
                "the display animation targets the walked step, not the recorded target",
                stepMag,
                displayTarget,
                1e-9
            )

            val frames = walkToCompletion(fromMag = FRONT_MAG)

            assertEquals(
                "13.0 -> 15.0 needs eight window-sized frames",
                8,
                frames.size
            )
            assertEquals("the last frame lands exactly on the target", target, frames.last(), 1e-9)
            assertTrue("the walk is finished (step cleared)", stepMag.isNaN())
            assertEquals(
                "the display target falls back to the committed magnification",
                target,
                displayTarget,
                1e-9
            )
            assertEquals(
                "the UI holds the last frame the renderer emitted",
                target,
                viewModel.uiState.value.renderViewport?.mag ?: Double.NaN,
                1e-9
            )
        }

    @Test
    fun `a change inside the window keeps the single frame path`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRenderer()
            landFrontFrame()
            val target = FRONT_MAG + ZoomWalk.ZOOM_BLIT_WINDOW

            viewModel.updateMagnification(target)
            assertTrue("an in-window change must not start a walk", stepMag.isNaN())
            assertEquals(
                "the display animation targets the committed magnification",
                target,
                displayTarget,
                1e-9
            )
            viewModel.renderMap()
            assertEquals("one frame carries the whole change", target, landFrame(), 1e-9)
        }

    @Test
    fun `a programmatic camera fit lands directly`() = runTest(mainDispatcherRule.dispatcher) {
        wireRenderer()
        landFrontFrame()
        val target = FRONT_MAG + 2.0

        viewModel.updateMagnification(target, walk = false)

        assertTrue("a camera fit must not start a walk", stepMag.isNaN())
        assertEquals(target, committedMag, 1e-9)
        assertEquals(
            "the display target is the committed magnification",
            target,
            displayTarget,
            1e-9
        )
        viewModel.renderMap()
        assertEquals("the fit's frame carries the whole change", target, landFrame(), 1e-9)
    }

    @Test
    fun `a new request mid-walk wins and ends on the newest value`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRenderer()
            landFrontFrame()
            viewModel.updateMagnification(FRONT_MAG + 2.0)
            viewModel.renderMap()
            val firstStep = stepMag
            val landedFirst = landFrame()
            assertEquals("the first step landed", firstStep, landedFirst, 1e-9)

            // A descending request arrives while the ascending leg is in flight.
            viewModel.updateMagnification(FRONT_MAG + 0.5)

            assertEquals(
                "the recorded viewport follows the newest request",
                FRONT_MAG + 0.5,
                committedMag,
                1e-9
            )
            assertTrue(
                "the walk must not jump past the newest request",
                stepMag.isNaN() || stepMag <= FRONT_MAG + 0.5 + 1e-9
            )
            val frames = walkToCompletion(fromMag = landedFirst)
            assertEquals(
                "the walk ends exactly on the newest request",
                FRONT_MAG + 0.5,
                frames.last(),
                1e-9
            )
            assertEquals(
                "and the last landed frame carries it",
                FRONT_MAG + 0.5,
                viewModel.uiState.value.renderViewport?.mag ?: Double.NaN,
                1e-9
            )
        }

    @Test
    fun `a manual commit during a walk replaces the pending target`() =
        runTest(mainDispatcherRule.dispatcher) {
            wireRenderer()
            landFrontFrame()
            viewModel.updateMagnification(FRONT_MAG + 2.0)
            viewModel.renderMap()
            assertFalse("a walk is pending", stepMag.isNaN())

            // Manual zoom back to the frame in hand: the walk's pending target is gone.
            viewModel.updateMagnification(FRONT_MAG)

            assertEquals(FRONT_MAG, committedMag, 1e-9)
            assertTrue("the superseded walk must not keep pinning a step", stepMag.isNaN())
            viewModel.renderMap()
            // The step the abandoned walk had already queued can still reach the renderer — the walk is
            // superseded, not cancelled mid-render — so the assertion is on the NEWEST frame: the last
            // render the renderer performed carries the manual magnification, not the abandoned target.
            mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_000)
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "the newest render is the manual magnification, not the abandoned target",
                FRONT_MAG,
                renderer.frameFlow.value.viewport.mag,
                1e-9
            )
        }

    @Test
    fun `a cold start with no frame lands directly`() = runTest(mainDispatcherRule.dispatcher) {
        // No renderer, hence no frame: there is nothing to transition from.
        viewModel.updateMagnification(17.0)

        assertEquals(17.0, committedMag, 1e-9)
        assertTrue("no walk without a frame to start from", stepMag.isNaN())
    }

    @Test
    fun `a descending change walks too`() = runTest(mainDispatcherRule.dispatcher) {
        wireRenderer()
        landFrontFrame()
        val target = FRONT_MAG - 1.0

        viewModel.updateMagnification(target)
        viewModel.renderMap()

        assertEquals(target, committedMag, 1e-9)
        assertEquals(
            "the descending leg starts one window below the frame in hand",
            FRONT_MAG - ZoomWalk.ZOOM_BLIT_WINDOW,
            stepMag,
            1e-9
        )
        val frames = walkToCompletion(fromMag = FRONT_MAG)
        assertEquals(target, frames.last(), 1e-9)
        assertTrue(
            "every descending frame stays inside the window",
            frames.zipWithNext().all { (a, b) -> abs(b - a) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9 }
        )
    }
}