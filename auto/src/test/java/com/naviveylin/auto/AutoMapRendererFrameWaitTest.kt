package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The frame-wait bound (spec: auto-map-renderer — A requested car frame that never lands is
 * re-requested).
 *
 * A car renderer that asks for a frame and does not get one used to leave the surface on its last
 * frame for as long as nothing else happened: the iteration cleared `pendingRender` and every
 * frame path that cannot draw (no surface, no extent, a confined fault) returned silently. Issue #25
 * is what that looks like to a driver — a black surface with host chrome and no message.
 *
 * The cases drive the extracted seam with an explicit clock (`frameWaitIteration(nowMs)`) and read
 * the request counter, so nothing waits on a timer (`guidelines/Build.md` §6c/§6e). Teardown goes
 * through [RendererTestRule], which fails the class if the watchdog job survives a test.
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererFrameWaitTest {

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
    fun aRequestTheFramePathCannotServeStaysOutstanding() {
        // Spec: the request stays outstanding while the frame path cannot draw it — the regression
        // this class of defect consists of.
        renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        renderer.requestRender()

        // No surface is held: the frame path cannot draw this request.
        renderer.frameIteration()

        assertEquals("nothing was drawn, so nothing may be counted", 0, renderer.fullRenderCount)
        assertTrue(
            "the request was consumed although no frame could be drawn",
            renderer.pendingRender
        )
    }

    @Test
    fun aFrameThatNeverLandsIsRequestedAgain() {
        // Spec: A frame that never lands is requested again — the stalled wait asks for the frame
        // once the bound expires, without the driver touching the screen.
        rendererWithSurface()
        val reports = mutableListOf<String>()
        renderer.frameWaitReport = { reports += it }

        renderer.requestRender()
        val requestsBefore = renderer.renderRequestCount
        renderer.frameWaitIteration(renderer.frameWaitArmedAtMs + AutoMapRenderer.FRAME_WAIT_BOUND_MS + 1)

        assertEquals(
            "the expired wait must request the frame again",
            requestsBefore + 1,
            renderer.renderRequestCount
        )
        assertTrue("the retried request is outstanding", renderer.pendingRender)
        assertEquals(1, renderer.frameWaitRetries)
        assertEquals(1, reports.size)
        assertTrue(reports.single().contains("retry 1/${AutoMapRenderer.FRAME_WAIT_MAX_RETRIES}"))
    }

    @Test
    fun landedFramesAreNotDuplicated() {
        // Spec: Landed frames are not duplicated — the healthy path issues exactly the requests the
        // state changes asked for, and reports nothing.
        rendererWithSurface()
        val reports = mutableListOf<String>()
        renderer.frameWaitReport = { reports += it }

        renderer.requestRender()
        val requestsBefore = renderer.renderRequestCount
        renderer.frameIteration()
        assertEquals("the requested frame was drawn", 1, renderer.fullRenderCount)

        renderer.frameWaitIteration(renderer.frameWaitArmedAtMs + AutoMapRenderer.FRAME_WAIT_BOUND_MS + 1)

        assertEquals(
            "a landed frame must not be requested a second time",
            requestsBefore,
            renderer.renderRequestCount
        )
        assertTrue("a landed frame reports nothing", reports.isEmpty())
    }

    @Test
    fun aStoppedRendererIssuesNoRetry() {
        // Spec: A stopped renderer does not retry — no request reaches the surface while the screen
        // is stopped, and a shut-down renderer is silent too.
        rendererWithSurface()
        renderer.requestRender()
        val armed = renderer.frameWaitArmedAtMs
        val requestsBefore = renderer.renderRequestCount

        renderer.pause()
        renderer.frameWaitIteration(armed + AutoMapRenderer.FRAME_WAIT_BOUND_MS + 1)
        assertEquals(
            "a stopped screen must not request frames",
            requestsBefore,
            renderer.renderRequestCount
        )

        renderer.resume() // requests once, as the started screen does
        val afterResume = renderer.renderRequestCount
        renderer.shutdown()
        renderer.frameWaitIteration(armed + AutoMapRenderer.FRAME_WAIT_BOUND_MS * 4)
        assertEquals(
            "a shut-down renderer must not request frames",
            afterResume,
            renderer.renderRequestCount
        )
    }

    @Test
    fun theExhaustedBudgetIsReportedOnce() {
        // Spec: The retry budget is finite and reported once — two retries, one exhaustion record,
        // nothing further, and a frame that lands re-arms the budget.
        rendererWithSurface()
        val reports = mutableListOf<String>()
        renderer.frameWaitReport = { reports += it }

        renderer.requestRender()
        val requestsBefore = renderer.renderRequestCount
        repeat(3) {
            renderer.frameWaitIteration(renderer.frameWaitArmedAtMs + AutoMapRenderer.FRAME_WAIT_BOUND_MS + 1)
        }

        assertEquals(
            "exactly the budget's worth of retries",
            requestsBefore + AutoMapRenderer.FRAME_WAIT_MAX_RETRIES,
            renderer.renderRequestCount
        )
        assertEquals("two retries plus one exhaustion record", 3, reports.size)
        assertTrue(
            "the exhaustion record names the spent retries: ${reports.last()}",
            reports.last().contains("after ${AutoMapRenderer.FRAME_WAIT_MAX_RETRIES} retries")
        )

        val afterExhaustion = renderer.renderRequestCount
        renderer.frameWaitIteration(renderer.frameWaitArmedAtMs + AutoMapRenderer.FRAME_WAIT_BOUND_MS * 4)
        assertEquals(
            "an exhausted wait retries no further",
            afterExhaustion,
            renderer.renderRequestCount
        )
        assertEquals("and reports no further", 3, reports.size)

        // A frame that lands re-arms the budget, so one stall does not disarm a long session.
        renderer.frameIteration()
        assertEquals("the retried request drew after all", 1, renderer.fullRenderCount)
        renderer.requestRender()
        renderer.frameWaitIteration(renderer.frameWaitArmedAtMs + AutoMapRenderer.FRAME_WAIT_BOUND_MS + 1)
        assertEquals("the re-armed budget retries again", 4, reports.size)
    }

    @Test
    fun aRequestArmsTheWaitAndAStopDisarmsIt() {
        // The production wiring: the request arms the deadline and every stop path disarms it. Without
        // this case the seam cases stay green even if the watchdog is never armed (a review finding).
        rendererWithSurface()
        renderer.requestRender()
        assertTrue("a request arms the wait", renderer.frameWaitJob?.isActive == true)

        renderer.pause()
        assertFalse("a pause disarms the wait", renderer.frameWaitJob?.isActive == true)

        renderer.resume()
        renderer.detachSurface()
        assertFalse("a surface detach disarms the wait", renderer.frameWaitJob?.isActive == true)

        renderer.onSurfaceCreated(mockSurface(), 100, 100)
        renderer.requestRender()
        renderer.shutdown()
        assertFalse("a shutdown disarms the wait", renderer.frameWaitJob?.isActive == true)
    }

    @Test
    fun theArmedWaitFiresAndRetriesTheFrame() {
        // The armed job — not only the seam — turns an expired wait into a request. The bound is
        // lowered so the case can await the job's completion instead of a deadline.
        rendererWithSurface()
        renderer.frameWaitBoundMs = 1L
        var clock = 0L
        renderer.frameWaitClock = { clock }
        val reports = mutableListOf<String>()
        renderer.frameWaitReport = { reports += it }
        renderer.requestRender()
        val requestsBefore = renderer.renderRequestCount

        val job = renderer.frameWaitJob
        assertTrue("the request armed a wait", job != null)
        clock = 5L // the bound has passed when the job runs
        runBlocking { job?.join() }
        // The chain would keep re-arming; stop it so nothing outlives the assertions.
        renderer.disarmFrameWaitWatchdog()

        assertTrue(
            "the armed wait requested the frame again",
            renderer.renderRequestCount > requestsBefore
        )
        assertTrue("and it reported the expired wait: $reports", reports.isNotEmpty())
    }
}
