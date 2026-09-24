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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * "Viewport records final magnification"):
 *
 * - a large change never lands in one frame - every walked step is within the window
 *   of the frame before it, and the last one lands exactly on the target;
 * - the recorded viewport holds the FINAL magnification while the display still walks;
 * - an in-window change and a programmatic camera fit keep the single-render path;
 * - a new request mid-walk wins and ends exactly on the newest value;
 * - a cold start (no frame yet) lands directly.
 *
 * Landed frames are injected through the [MapCanvasViewModel.publishRenderedFrameForTest]
 * hook (the same path the frame collector uses), so no native render pipeline is needed.
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
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var viewModel: MapCanvasViewModel

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
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
    }

    @After
    fun tearDown() {
        viewModel.cancelScopeForTest()
    }

    private val stepMag: Double get() = viewModel.uiState.value.zoomWalkStepMag
    private val committedMag: Double get() = viewModel.uiState.value.viewport.magnification
    private val displayTarget: Double get() = viewModel.uiState.value.zoomAnimationTargetMag

    /**
     * Run the walk to completion through injected landed frames; returns the frames the
     * walk committed, including the final one (which the walk lands in one render when
     * its remaining gap is inside the window).
     */
    private fun walkToCompletion(fromMag: Double = FRONT_MAG): List<Double> {
        val frames = mutableListOf<Double>()
        var previous = fromMag
        var guard = 0
        while (!stepMag.isNaN() && guard++ < MAX_WALK_FRAMES) {
            val step = stepMag
            assertTrue(
                "step of ${step - previous} exceeds the window the frame in hand can serve",
                abs(step - previous) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
            )
            frames += step
            previous = step
            viewModel.publishRenderedFrameForTest(step)
        }
        assertTrue("the walk must finish inside the frame guard", guard < MAX_WALK_FRAMES)
        assertTrue(
            "the landing frame of ${committedMag - previous} exceeds the window",
            abs(committedMag - previous) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9
        )
        frames += committedMag
        return frames
    }

    @Test
    fun `a large change walks and lands exactly on the target`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.publishRenderedFrameForTest(FRONT_MAG)
            val target = FRONT_MAG + 2.0

            viewModel.updateMagnification(target)

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

            val frames = walkToCompletion()

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
        }

    @Test
    fun `a change inside the window keeps the single frame path`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.publishRenderedFrameForTest(FRONT_MAG)
            val target = FRONT_MAG + ZoomWalk.ZOOM_BLIT_WINDOW

            viewModel.updateMagnification(target)

            assertTrue("an in-window change must not start a walk", stepMag.isNaN())
            assertEquals(
                "the display animation targets the committed magnification",
                target,
                displayTarget,
                1e-9
            )
        }

    @Test
    fun `a programmatic camera fit lands directly`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.publishRenderedFrameForTest(FRONT_MAG)
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
    }

    @Test
    fun `a new request mid-walk wins and ends on the newest value`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.publishRenderedFrameForTest(FRONT_MAG)
            viewModel.updateMagnification(FRONT_MAG + 2.0)
            val firstStep = stepMag
            viewModel.publishRenderedFrameForTest(firstStep)

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
            val frames = walkToCompletion(fromMag = firstStep)
            assertEquals(
                "the walk ends exactly on the newest request",
                FRONT_MAG + 0.5,
                frames.last(),
                1e-9
            )
        }

    @Test
    fun `a manual commit during a walk replaces the pending target`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.publishRenderedFrameForTest(FRONT_MAG)
            viewModel.updateMagnification(FRONT_MAG + 2.0)
            assertFalse("a walk is pending", stepMag.isNaN())

            // Manual zoom back to the frame in hand: the walk's pending target is gone.
            viewModel.updateMagnification(FRONT_MAG)

            assertEquals(FRONT_MAG, committedMag, 1e-9)
            assertTrue("the superseded walk must not keep pinning a step", stepMag.isNaN())
        }

    @Test
    fun `a cold start with no frame lands directly`() = runTest(mainDispatcherRule.dispatcher) {
        // No frame has been rendered yet: there is nothing to transition from.
        viewModel.updateMagnification(17.0)

        assertEquals(17.0, committedMag, 1e-9)
        assertTrue("no walk without a frame to start from", stepMag.isNaN())
    }

    @Test
    fun `a descending change walks too`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.publishRenderedFrameForTest(FRONT_MAG)
        val target = FRONT_MAG - 1.0

        viewModel.updateMagnification(target)

        assertEquals(target, committedMag, 1e-9)
        assertEquals(
            "the descending leg starts one window below the frame in hand",
            FRONT_MAG - ZoomWalk.ZOOM_BLIT_WINDOW,
            stepMag,
            1e-9
        )
        val frames = walkToCompletion()
        assertEquals(target, frames.last(), 1e-9)
        assertTrue(
            "every descending frame stays inside the window",
            frames.zipWithNext().all { (a, b) -> abs(b - a) <= ZoomWalk.ZOOM_BLIT_WINDOW + 1e-9 }
        )
    }
}
