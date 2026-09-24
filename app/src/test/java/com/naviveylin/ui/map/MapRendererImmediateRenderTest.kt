package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the walked-step render path (spec: smooth-zoom - Eased zoom animation on
 * discrete zoom input; design D3): [MapRenderer.requestRenderImmediate] enqueues a
 * magnification render WITHOUT the pan/zoom debounce, while the debounced path keeps
 * its wait so pan/rotate/gesture/follow commits are unaffected.
 *
 * Runs under Robolectric with the default sandbox: FakeOSMScoutClient triggers
 * OSMScoutClient's static System.loadLibrary (stubbed .so), which requires the default
 * classloader (AGENTS.md classloader rule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapRendererImmediateRenderTest {

    private val client = FakeOSMScoutClient()

    private fun TestScope.createRenderer(): MapRenderer {
        val renderer = MapRenderer(client, dpi = 160.0, scope = backgroundScope)
        renderer.screenWidth = 400
        renderer.screenHeight = 800
        return renderer
    }

    /** Build the front buffer with one initial (debounced) render at mag 5. */
    private fun TestScope.renderInitialFrame(renderer: MapRenderer) {
        renderer.requestRender(51.5, 7.4, 5.0)
        advanceTimeBy(1_000)
        advanceUntilIdle()
        check(renderer.frameFlow.value.bitmap != null) { "no initial frame rendered" }
    }

    private fun FakeOSMScoutClient.renderCountTotal() =
        renderCount.get() + renderWithRouteAndPoisCount.get()

    @Test
    fun walkStepRendersWithoutWaitingForTheZoomDebounce() = runTest {
        val renderer = createRenderer()
        renderInitialFrame(renderer)
        assertEquals(5.0, renderer.frameFlow.value.viewport.mag, 1e-9)

        // One walked magnification step (well inside the 200 ms zoom debounce).
        renderer.requestRenderImmediate(51.5, 7.4, 5.25, 0.0)
        advanceTimeBy(50)

        assertEquals(
            "a walk step must land immediately, not after zoomDebounceMs",
            5.25,
            renderer.frameFlow.value.viewport.mag,
            1e-9
        )
        assertEquals(5.25, renderer.currentViewportFlow.value.mag, 1e-9)
    }

    @Test
    fun debouncedZoomStillWaitsForItsWindow() = runTest {
        val renderer = createRenderer()
        renderInitialFrame(renderer)
        val before = client.renderCountTotal()

        // A regular zoom request keeps the debounce window.
        renderer.requestRender(51.5, 7.4, 6.0, 0.0)
        advanceTimeBy(100)
        assertEquals(
            "the debounced zoom must not land inside its window",
            5.0,
            renderer.frameFlow.value.viewport.mag,
            1e-9
        )

        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals(6.0, renderer.frameFlow.value.viewport.mag, 1e-9)
        assertTrue("the debounced zoom renders after its window", client.renderCountTotal() > before)
    }

    @Test
    fun immediateStepRetargetsAPendingDebouncedRequest() = runTest {
        val renderer = createRenderer()
        renderInitialFrame(renderer)

        // A debounced zoom is pending (not yet fired) when the walk commits its step.
        renderer.requestRender(51.5, 7.4, 6.0, 0.0)
        advanceTimeBy(50)
        renderer.requestRenderImmediate(51.5, 7.4, 5.25, 0.0)
        advanceTimeBy(1_000)
        advanceUntilIdle()

        // The debounced request must not land its (older, larger) magnification after
        // the step — that would step the displayed magnification backward.
        assertEquals(
            "the pending request is re-targeted, never replayed at its old magnification",
            5.25,
            renderer.frameFlow.value.viewport.mag,
            1e-9
        )
        assertEquals(5.25, renderer.renderedMag, 1e-9)
    }
}
