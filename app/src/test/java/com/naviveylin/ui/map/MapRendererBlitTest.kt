package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the sub-region blit fast-path never discards a pending FORCED render
 * (spec: map-render — "Forced overlay renders are never dropped by the blit
 * fast-path"). Regression: after `setRoute` submits a forced render with an
 * unmoved camera, a covering blit request must keep the pending forced job so
 * the new route renders without any gesture.
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

            // Small pan strictly inside the overrun buffer: blit covers it, no
            // full render — the optimization must stay intact.
            renderer.requestRender(51.5001, 7.4001, 5.0)

            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                "in-overrun pan must be served by the blit, no new render " +
                    "(before=$totalBefore after=" + client.renderCountTotal() + ")",
                totalBefore,
                client.renderCountTotal()
            )
        } finally {
            renderer.shutdown()
        }
    }
}
