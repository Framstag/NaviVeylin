package com.naviveylin.auto

import android.graphics.Canvas
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
 * Per-iteration confinement of the car map renderer's three loops (spec:
 * car-host-fault-isolation — No fault escapes into the host path, "A fault in one frame keeps the
 * frames coming" / "A fault in a display tick keeps the follow pipeline running"; design D1).
 *
 * The loops themselves run on `Dispatchers.Default`, so the cases drive the extracted iterations
 * directly and inject the fault through [AutoMapRenderer.testIterationFault] — no timer, no sleep
 * (`guidelines/Build.md` §6, `TODO.md` §40.31).
 *
 * Default Robolectric sandbox: the renderer loads the committed JNI stub through
 * `FakeAutoRenderClient` (per `AGENTS.md`, no `@Config` override here).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererLoopConfinementTest {

    @get:Rule
    val renderers = RendererTestRule()

    private lateinit var renderer: AutoMapRenderer

    private fun mockSurface(): Surface {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas
        every { surface.isValid } returns true
        return surface
    }

    private fun rendererWithSurface(): AutoMapRenderer {
        renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(mockSurface(), 100, 100)
        return renderer
    }

    @Test
    fun aFaultedFrameIsSkippedAndTheNextRequestStillDraws() {
        rendererWithSurface()
        renderer.requestRender()

        renderer.testIterationFault = RenderLoop.FRAME
        renderer.frameIteration()

        // The frame was skipped: nothing was drawn, and the request was consumed rather than
        // left as a permanent source of retries.
        assertEquals(0, renderer.fullRenderCount)
        assertEquals(0, renderer.blitCount)

        renderer.testIterationFault = null
        renderer.requestRender()
        renderer.frameIteration()

        // The later request is served: the frame path survived the fault (before the guard a
        // throwing frame ended the collector and every later requestRender() was dropped).
        assertEquals(1, renderer.fullRenderCount)
    }

    @Test
    fun aFaultedFrameLeavesTheSurfaceHealthyAndNothingInFlight() {
        rendererWithSurface()
        renderer.requestRender()

        renderer.testIterationFault = RenderLoop.FRAME
        renderer.frameIteration()

        // A faulting iteration is not a dead surface (that path stops the loop and asks the host
        // for a fresh surface, and must not be spent here), and it must not strand the in-flight
        // marker that throttles the next render.
        assertFalse(renderer.isSurfaceFailed())
        assertFalse(renderer.isRenderInFlight())
    }

    @Test
    fun aFaultedDisplayTickDoesNotStopTheFollowPipeline() {
        rendererWithSurface()
        val now = System.currentTimeMillis()
        // A frame must be on the surface: the following ticks update it by blitting the overrun
        // buffer (same shape as the follow-blit case in AutoMapRendererTest).
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)

        renderer.testIterationFault = RenderLoop.EXTRAPOLATION
        renderer.displayTickIteration(now + 100, 0.1)

        // The tick was skipped (no blit from it), but the pipeline is intact: the next tick
        // updates the displayed frame instead of the loop being gone.
        val blitsAfterFault = renderer.blitCount
        renderer.testIterationFault = null
        renderer.displayTickIteration(now + 200, 0.1)

        assertTrue(renderer.blitCount > blitsAfterFault)
    }

    @Test
    fun aPausedRendererRunsNoIterationAndCountsNoFault() {
        // Spec: car-host-fault-isolation — Fresh screen start re-arms the recovery budget. A stopped
        // screen's renderer is paused, so its pending request is not iterated: no frame, and no
        // fault to drive the re-creation ladder from (a backgrounded screen must not consume the
        // budget, and a fault storm of the previous period must not leak into the next).
        rendererWithSurface()
        renderer.pause()
        renderer.requestRender()
        renderer.testIterationFault = RenderLoop.FRAME

        renderer.frameIteration()

        assertEquals(0, renderer.loopFaultStreak())
        assertEquals(0, renderer.fullRenderCount)

        renderer.testIterationFault = null
        renderer.resume()
        renderer.frameIteration()
        assertEquals("a started screen draws again", 1, renderer.fullRenderCount)
    }

    @Test
    fun aFaultedZoomStepDoesNotStopTheWalk() {
        rendererWithSurface()
        renderer.renderFrame()
        val before = renderer.viewportState.value.zoomFraction
        // A far request starts a walk instead of committing in one frame.
        renderer.setViewport(51.5142273, 7.4652789, 17, 0.0, 17.0, walkZoom = true)

        renderer.testIterationFault = RenderLoop.ZOOM_WALK
        renderer.zoomWalkIteration(System.currentTimeMillis(), 0.1)
        assertEquals("the faulted step must commit nothing", before, renderer.viewportState.value.zoomFraction, 1e-9)

        renderer.testIterationFault = null
        // A four-level entry is walked in steps bounded by the blit window, so the walk needs
        // more iterations than the number of levels (`TODO.md` §71 measures ~16 renders).
        repeat(64) {
            renderer.zoomWalkIteration(System.currentTimeMillis(), 0.1)
            renderer.renderFrame()
        }

        // The walk still reaches its target: the loop was not lost to the fault.
        assertEquals(17.0, renderer.viewportState.value.zoomFraction, 1e-9)
    }
}
