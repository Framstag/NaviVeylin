package com.naviveylin.auto

import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Full-render cadence in follow mode (spec: car-host-fault-isolation — Bounded periodic
 * render work; design D7): a slow render must not be re-queued behind itself, and the
 * base interval still applies when renders are fast.
 *
 * Measured on the automotive AVD: a full render at 1080x600 takes 2-5 s there, against
 * the 200 ms base interval — before this bound the loop kept requesting frames while the
 * previous one was still running.
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererRenderCadenceTest {

    @get:Rule
    val renderers = RendererTestRule()

    private val baseInterval = 200L

    private fun mockSurface(): Surface {
        val surface = mockk<Surface>(relaxed = true)
        every { surface.lockCanvas(any()) } returns mockk(relaxed = true)
        every { surface.isValid } returns true
        return surface
    }

    @Test
    fun theFixPathStillDrawsOneFramePerCommit() {
        // The fix path is setViewport + reengageFollow (and a pan start/release is the same
        // pair). The re-engage must not add a second request: the render loop draws one
        // frame per signal, so a duplicate request is a duplicate frame at ~1 Hz driving
        // (spec: auto-smooth-follow — A follow re-engage requests the frame it re-anchored;
        // scenario "Re-engage inside the fix path adds no frame").
        val renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(mockSurface(), 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 0.0, 10.0, speedKmH = 50.0)
        renderer.reCenter()
        renderer.frameIteration()

        val requestsBefore = renderer.renderRequestCount
        val framesBefore = renderer.fullRenderCount + renderer.blitCount

        // The commit the screen issues on a fix (or when a pan starts), then the re-engage.
        val vp = renderer.viewportState.value
        renderer.setViewport(vp.lat, vp.lon, vp.zoom, vp.angle, vp.zoom.toDouble())
        renderer.reengageFollow()

        assertEquals(
            "the commit path schedules exactly one frame",
            requestsBefore + 1, renderer.renderRequestCount
        )
        renderer.frameIteration()
        assertEquals(
            "one frame lands", framesBefore + 1, renderer.fullRenderCount + renderer.blitCount
        )
        renderer.frameIteration()
        assertEquals(
            "the re-engage scheduled no second frame",
            framesBefore + 1, renderer.fullRenderCount + renderer.blitCount
        )
    }

    @Test
    fun aFastRenderKeepsTheBaseInterval() {
        val renderer = renderers.newRenderer()

        // 100 ms since the last request, last render fast (10 ms): too early.
        assertFalse(renderer.shouldRequestFullRender(1_000L, 900L, renderInFlightNow = false, lastDurationMs = 10L))
        // 250 ms later: due.
        assertTrue(renderer.shouldRequestFullRender(1_150L, 900L, renderInFlightNow = false, lastDurationMs = 10L))
    }

    @Test
    fun aSlowRenderStretchesTheInterval() {
        val renderer = renderers.newRenderer()
        val slowRender = 2_500L

        // Past the base interval but not past the measured render duration: not due.
        assertFalse(
            renderer.shouldRequestFullRender(
                5_000L, 5_000L - (baseInterval + 50L),
                renderInFlightNow = false,
                lastDurationMs = slowRender
            )
        )
        // Past the measured duration: due.
        assertTrue(
            renderer.shouldRequestFullRender(
                5_000L, 5_000L - (slowRender + 1L),
                renderInFlightNow = false,
                lastDurationMs = slowRender
            )
        )
    }

    @Test
    fun noRequestWhileARenderIsInFlight() {
        val renderer = renderers.newRenderer()

        // Even with the interval long past, a running render blocks the next request.
        assertFalse(
            renderer.shouldRequestFullRender(
                10_000L, 0L,
                renderInFlightNow = true,
                lastDurationMs = 10L
            )
        )
    }

    @Test
    fun theInFlightFlagIsClearedAndTheDurationMeasuredAfterARender() {
        // A stuck flag would freeze follow-mode rendering for good, so the frame path
        // must always clear it (and record how long the render took).
        val renderer = renderers.newRenderer()
        val surface = mockk<Surface>(relaxed = true)
        // The assert below is about the frame this case drives: with the render loop live
        // the collector wakes after RENDER_DEBOUNCE_MS and writes the same flag
        // (spec `unit-test-suite-runtime` — A case's subject has no background work the
        // case does not drive).
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1920, 720)

        renderer.renderFrame()

        assertFalse("the in-flight flag must be cleared", renderer.isRenderInFlight())
        assertTrue("the duration must be measured", renderer.lastRenderDuration() >= 0L)
    }
}
