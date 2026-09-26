package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the overrun fast-path never discards a pending FORCED render
 * (spec: map-render — "Forced overlay renders are never dropped by the blit
 * fast-path") and that a covered pan window does not render or emit a frame at all:
 * the display shifts the frame in hand (spec: canvas-overrun — Overrun window shift
 * for pan).
 *
 * Runs under Robolectric with the default sandbox: FakeOSMScoutClient triggers
 * OSMScoutClient's static System.loadLibrary (stubbed .so), which requires the
 * default classloader (AGENTS.md classloader rule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapRendererBlitTest {

    private val client = FakeOSMScoutClient()

    private fun TestScope.createRenderer(): MapRenderer {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        return renderer
    }

    /**
     * Build the front buffer with one initial render (tile path). Subsequent
     * zero/small-shift requests then exercise the sub-region blit path, which
     * requires a non-null front buffer.
     */
    private fun TestScope.renderInitialFrame(renderer: MapRenderer) {
        renderer.requestRender(51.5, 7.4, 5.0)
        // The debounce loop parks on the CONFLATED channel and only wakes with
        // the signal; advance the virtual clock past both debounce windows.
        advanceTimeBy(1_000)
        advanceUntilIdle()
    }

    private fun FakeOSMScoutClient.renderCountTotal() = renderCount.get() + renderWithRouteAndPoisCount.get()

    @Test
    fun forcedRouteRenderSurvivesCoveringBlit() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val totalBefore = client.renderCountTotal()

            // Reroute-style sequence: setRoute submits a forced render, then the
            // collector's renderMap() submits a non-force request at the SAME
            // viewport. The blit covers it — the pending forced render must NOT
            // be discarded.
            val routeLats = doubleArrayOf(51.49, 51.5, 51.51)
            val routeLons = doubleArrayOf(7.38, 7.4, 7.42)
            renderer.setRoute(routeLats, routeLons, 51.49, 7.38, 51.51, 7.42)
            renderer.requestRender(51.5, 7.4, 5.0)

            // Debounce window (panDebounceMs) + render execution.
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(
                "forced route render must execute despite the covering blit",
                client.renderCountTotal() > totalBefore
            )
            assertNotNull("route must reach the render call", client.lastRouteLats)
            assertEquals(routeLats[0], client.lastRouteLats!![0], 1e-9)
            assertEquals(routeLats[1], client.lastRouteLats!![1], 1e-9)
            assertEquals(routeLats[2], client.lastRouteLats!![2], 1e-9)
        } finally {
            renderer.shutdown()
        }
    }

    @Test
    fun clearRouteForcedRenderSurvivesCoveringBlit() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val totalBefore = client.renderCountTotal()

            renderer.clearRoute()
            renderer.requestRender(51.5, 7.4, 5.0)

            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(
                "forced clear render must execute despite the covering blit",
                client.renderCountTotal() > totalBefore
            )
        } finally {
            renderer.shutdown()
        }
    }

    @Test
    fun coveringBlitWithoutForcedPendingSchedulesNoRender() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val totalBefore = client.renderCountTotal()

            assertTrue(
                "initial frame must render first (baseline renders=$totalBefore)",
                totalBefore > 0
            )

            // Small pan strictly inside the overrun buffer: covered by the frame in
            // hand, no full render — the optimization must stay intact.
            renderer.requestRender(51.5001, 7.4001, 5.0)

            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                "in-overrun pan must be served by the overrun frame, no new render " +
                    "(before=$totalBefore after=" + client.renderCountTotal() + ")",
                totalBefore,
                client.renderCountTotal()
            )
        } finally {
            renderer.shutdown()
        }
    }

    /**
     * A covered pan window is served by the DISPLAY (the frame is drawn at its display
     * offset), so the renderer must neither render nor emit a new frame: emitting the
     * same content again would be a per-event frame allocation for nothing
     * (spec: render-performance — Pan hot path stays off the frame budget). The
     * published FrameState is compared by identity — a re-emission creates a new
     * instance even when the bitmap and viewport are equal.
     */
    @Test
    fun coveredPanEmitsNoFrame() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val frameBefore = renderer.frameFlow.value
            val rendersBefore = client.renderCountTotal()

            renderer.requestRender(51.5001, 7.4001, 5.0)
            advanceTimeBy(500)
            advanceUntilIdle()

            assertSame(
                "the frame in hand must stay displayed for a covered pan",
                frameBefore,
                renderer.frameFlow.value
            )
            assertEquals(
                "a covered pan must not render",
                rendersBefore,
                client.renderCountTotal()
            )
        } finally {
            renderer.shutdown()
        }
    }

    /**
     * The displayed frame is the OVERRUN buffer, not a screen-sized crop: the pan
     * window and the follow-mode scroll both need the margin around the visible area
     * (spec: canvas-overrun — Configurable canvas overrun).
     */
    @Test
    fun emittedFrameIsTheOverrunBuffer() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val bitmap = renderer.frameFlow.value.bitmap

            assertNotNull("an initial render must emit a frame", bitmap)
            assertEquals(
                (renderer.screenWidth * MapRenderer.DEFAULT_CANVAS_OVERRUN).toInt(),
                bitmap!!.width
            )
            assertEquals(
                (renderer.screenHeight * MapRenderer.DEFAULT_CANVAS_OVERRUN).toInt(),
                bitmap.height
            )
            assertTrue(
                "the displayed frame must carry the overrun margin",
                bitmap.width > renderer.screenWidth && bitmap.height > renderer.screenHeight
            )
        } finally {
            renderer.shutdown()
        }
    }

    /**
     * The window coverage contract: a shift beyond the overrun buffer is NOT covered,
     * so a render is scheduled and the frame is re-centered there (spec: map-pan-zoom
     * — Pan beyond overrun buffer triggers re-render). At the test magnification (5,
     * dpi 160) a degree is only a few pixels, so the tested shift is well beyond the
     * 40 px horizontal margin of a 400×800 screen with 1.2× overrun.
     */
    @Test
    fun panBeyondTheOverrunMarginIsNotCovered() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)
            val covered = renderer.overrunWindowCovers(56.5, 12.4, 5.0, 0.0)

            assertEquals("a far pan must not be covered by the frame in hand", false, covered)
        } finally {
            renderer.shutdown()
        }
    }

    /**
     * A zoom or an angle change can never be served by the frame in hand (wrong
     * magnification/rotation), however small the camera shift is.
     */
    @Test
    fun zoomAndRotationAreNeverCovered() = runTest {
        val renderer = createRenderer()
        try {
            renderInitialFrame(renderer)

            assertEquals(
                "a magnification change must not be covered",
                false,
                renderer.overrunWindowCovers(51.5, 7.4, 5.25, 0.0)
            )
            assertEquals(
                "an angle change must not be covered",
                false,
                renderer.overrunWindowCovers(51.5, 7.4, 5.0, 0.2)
            )
        } finally {
            renderer.shutdown()
        }
    }
}
