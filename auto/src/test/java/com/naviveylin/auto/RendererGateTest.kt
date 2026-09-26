package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [RendererGate] (spec: auto-map-renderer — "Renderer initialization
 * off the car-app main thread" scenarios "Surface arrives before the renderer
 * is ready" / "Renderer still initializing when the screen stops";
 * "No map state lost during renderer initialization").
 *
 * Uses `FakeAutoRenderClient` + the default Robolectric sandbox (per
 * AGENTS.md the stub .so loads once per JVM, so no @Config override here).
 */
@RunWith(RobolectricTestRunner::class)
class RendererGateTest {

    /** Shuts every spied renderer down after the test: the spies run the real
     *  renderer, whose loops start in `init` and would otherwise outlive the test
     *  (change `fix-auto-unit-test-heap-overflow`, `TODO.md` §33). */
    @get:Rule
    val renderers = RendererTestRule()

    private fun rendererSpy() =
        renderers.track(spyk(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0)))

    private fun surfaceMock() = mockk<Surface>(relaxed = true).also { s ->
        // The render loop draws asynchronously once the surface slot replays.
        every { s.lockCanvas(any()) } returns mockk<Canvas>(relaxed = true)
        // A usable surface: the renderer treats an invalid one as dead and never locks it.
        every { s.isValid } returns true
        every { s.release() } returns Unit
    }

    /**
     * The gate publishes the delivered surface DPI (spec: auto-map-renderer — Renderer
     * initialization off the car-app main thread; spec: car-host-fault-isolation —
     * Host callbacks answer promptly): a host callback only retains the value here, and
     * the renderer that receives it is the only thing the DPI can reach — the bridge has
     * no client-wide DPI setter any more (spec: `render-projection-dpi`).
     */
    @Test
    fun surfaceDpiIsPublishedForTheGateConsumers() {
        val gate = RendererGate()
        assertEquals("no surface delivered yet", 0.0, gate.surfaceDpi.value, 0.0)

        val surface = surfaceMock()
        gate.onSurfaceAvailable(surface, 1920, 720, 236.0)

        assertEquals(236.0, gate.surfaceDpi.value, 0.0)
    }

    @Test
    fun daylightRequestsArePublishedForTheBackgroundCollector() {
        // Spec: car-host-fault-isolation — Host callbacks answer promptly. The surface delivery
        // publishes the stylesheet request instead of setting the native flag, which reloads the
        // style variant on the DB thread.
        val gate = RendererGate()
        assertNull("nothing requested yet", gate.daylightPush.value)

        gate.requestDaylightPush(dark = true)
        assertEquals(true, gate.daylightPush.value?.dark)
        assertEquals(false, gate.daylightPush.value?.force)

        gate.requestDaylightPush(dark = false, force = true)
        assertEquals(false, gate.daylightPush.value?.dark)
        assertEquals(true, gate.daylightPush.value?.force)
    }

    @Test
    fun anIdenticalDaylightRequestStaysRetryable() {
        // A state flow suppresses an equal value, so a request the native side dropped must be
        // distinguishable from the one that was already handled — otherwise the retry after a
        // warmup race never reaches the collector.
        val gate = RendererGate()

        gate.requestDaylightPush(dark = false)
        val first = gate.daylightPush.value!!.token
        gate.requestDaylightPush(dark = false)
        val second = gate.daylightPush.value!!.token

        assertTrue("a repeated request carries a new token", second > first)
    }

    @Test
    fun destroyDropsAPendingDaylightRequest() {
        val gate = RendererGate()
        gate.requestDaylightPush(dark = true)

        gate.destroy()

        assertNull("a destroyed gate pushes nothing", gate.daylightPush.value)
    }

    @Test
    fun bufferedSlotsReplayOnPublishInDefinedOrder() {
        val gate = RendererGate()
        val renderer = rendererSpy()
        val surface = surfaceMock()

        gate.onSurfaceAvailable(surface, 640, 360, 200.0)
        gate.setDarkPresentation(true)
        gate.reCenter()
        gate.setGpsMarker(48.8566, 2.3522, 45.0, 10.0, 36.0, 1234L)
        gate.setFavoriteLocations(emptyList())
        gate.setViewport(51.5, 7.46, 12, 0.5, 12.0)

        gate.publish(renderer)

        // D5 order, surface -> dark -> viewport intents -> marker -> frames.
        // (reCenter internally issues its own setViewport — the explicit
        // viewport slot must replay AFTER it so the last intent wins.)
        verifyOrder {
            renderer.updateProjectionDpi(200.0)
            renderer.onSurfaceCreated(surface, 640, 360)
            renderer.setDarkPresentation(true)
            renderer.reCenter()
        }
        verify { renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0, 36.0, 1234L) }
        verify { renderer.setFavoriteLocations(emptyList()) }
        // The explicit viewport intent replayed after reCenter -> observable.
        assertEquals(51.5, renderer.viewportState.value.lat, 0.0001)
        assertEquals(12, renderer.viewportState.value.zoom)
        assertEquals(0.5, renderer.viewportState.value.angle, 0.0001)
        assertTrue(gate.rendererOrNull() === renderer)
    }

    @Test
    fun lastValueWinsDuringBuffering() {
        val gate = RendererGate()
        val renderer = rendererSpy()

        gate.setGpsMarker(1.0, 1.0, 10.0, 5.0, 20.0, 1L)
        gate.setGpsMarker(2.0, 2.0, 20.0, 6.0, 25.0, 2L)
        gate.setViewport(51.0, 7.0, 10, 0.0)
        gate.setViewport(52.0, 8.0, 11, 0.25, 11.0)

        gate.publish(renderer)

        verify(exactly = 1) { renderer.setGpsMarker(2.0, 2.0, 20.0, 6.0, 25.0, 2L) }
        verify(exactly = 1) { renderer.setViewport(52.0, 8.0, 11, 0.25, 11.0) }
    }

    @Test
    fun publishAppliesObservableState() {
        val gate = RendererGate()
        val renderer = renderers.track(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0))

        gate.onSurfaceAvailable(surfaceMock(), 100, 100, 200.0)
        gate.setViewport(51.5, 7.46, 12, 0.5, 12.0)
        gate.reCenter()
        gate.publish(renderer)

        // updateProjectionDpi went through (surface slot) and the last viewport
        // intent (setViewport after reCenter) disengaged follow mode.
        assertEquals(200.0, renderer.projectionDpi, 0.001)
        assertFalse(renderer.isFollowMode())
        assertEquals(51.5, renderer.viewportState.value.lat, 0.0001)
        assertEquals(12, renderer.viewportState.value.zoom)
    }

    @Test
    fun settersApplyImmediatelyOncePublished() {
        val gate = RendererGate()
        val renderer = rendererSpy()
        gate.publish(renderer)

        gate.setDarkPresentation(false)
        gate.setGpsMarker(48.8, 2.35, 45.0, 9.0, Double.NaN, 99L)
        gate.setViewport(50.0, 6.0, 9, 0.0, 9.0)

        verify { renderer.setDarkPresentation(false) }
        verify { renderer.setGpsMarker(48.8, 2.35, 45.0, 9.0, Double.NaN, 99L) }
        verify { renderer.setViewport(50.0, 6.0, 9, 0.0, 9.0) }
    }

    @Test
    fun walkZoomFlagSurvivesTheBufferedViewportPath() {
        // The car screens commit the auto-zoom magnification while the renderer may still be
        // initializing (native first-touch runs off the main thread), so the commit goes
        // through the buffered slot. The transition-eligible flag MUST survive that path —
        // dropped here, the entry would land the whole difference in one frame again
        // (spec: auto-speed-zoom — Auto-zoom entry transition; change aa-entry-zoom-animation).
        val gate = RendererGate()
        val renderer = rendererSpy()

        gate.onSurfaceAvailable(surfaceMock(), 640, 360, 240.0)
        gate.setViewport(51.5, 7.46, 17, 0.0, 17.0, walkZoom = true)
        gate.publish(renderer)

        verify { renderer.setViewport(51.5, 7.46, 17, 0.0, 17.0, true) }
    }

    @Test
    fun viewportCommitWithoutTheWalkFlagStaysDirect() {
        // The gesture/zoom-button commits (and every non-auto-zoom viewport write) keep their
        // immediate response: the flag defaults to off (spec: auto-speed-zoom — Auto-zoom
        // entry transition; design scope note).
        val gate = RendererGate()
        val renderer = rendererSpy()
        gate.publish(renderer)

        gate.setViewport(50.0, 6.0, 17, 0.0, 17.0)
        gate.setViewport(50.0, 6.0, 17, 0.0, 17.0, walkZoom = false)

        verify(exactly = 2) { renderer.setViewport(50.0, 6.0, 17, 0.0, 17.0, false) }
    }

    @Test
    fun destroyDropsPendingAndShutsDownLatePublish() {
        val gate = RendererGate()
        val renderer = rendererSpy()

        gate.setGpsMarker(1.0, 1.0, 0.0, 5.0)
        gate.destroy()
        assertNull(gate.rendererOrNull())

        gate.publish(renderer)
        verify { renderer.shutdown() }
        assertNull(gate.rendererOrNull())
    }

    @Test
    fun destroyShutsDownPublishedRenderer() {
        val gate = RendererGate()
        val renderer = rendererSpy()
        gate.publish(renderer)

        gate.destroy()

        verify { renderer.shutdown() }
        assertNull(gate.rendererOrNull())
    }

    @Test
    fun noopLifecycleCallsAreSafeBeforeReady() {
        val gate = RendererGate()

        // Must not throw and must not buffer anything replayable.
        gate.pause()
        gate.detachSurface()
        gate.onSurfaceDestroyed()
        gate.resume()

        val renderer = rendererSpy()
        gate.publish(renderer)
        // resume was buffered and applied; pause/release were no-ops.
        verify { renderer.resume() }
        verify(inverse = true) { renderer.pause() }
    }

    // ── the stop path (spec: auto-map-renderer — A stopped renderer holds no surface or frame buffer) ──

    @Test
    fun theStopPathDetachesTheRendererAndReleasesItsFrameBuffer() {
        // Screen onStop is pause() + detachSurface(): the renderer must hold neither the
        // session's surface nor the overrun frame it rendered through it, so a stopped
        // screen can never lock the surface another screen draws through — and its
        // ~3.7-8 MB buffer is not retained for as long as the screen sits in the stack.
        val gate = RendererGate()
        val renderer = renderers.track(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0))
        renderer.asyncLoopsEnabled = false
        val surface = surfaceMock()
        gate.onSurfaceAvailable(surface, 1920, 720, 240.0)
        gate.publish(renderer)

        renderer.renderFrame()
        verify(exactly = 1) { surface.lockCanvas(any()) }
        assertNotNull("a rendered frame is retained as the overrun buffer", renderer.overrunSize())

        gate.pause()
        gate.detachSurface()

        assertFalse("the stopped renderer no longer holds the session surface", renderer.isCurrentSurface(surface))
        assertNull("the overrun frame buffer is released", renderer.overrunSize())
        // Never released: the session owns the surface's lifetime.
        verify(exactly = 0) { surface.release() }
    }

    @Test
    fun aStartAfterAStopReacquiresTheSurfaceAndRendersAFullFrame() {
        // Spec: auto-map-renderer — "the first frame after the start is not blitted from a
        // buffer that predates the stop". The session hands the same retained instance to
        // the screen again on attach, so the renderer re-acquires it without a host
        // re-delivery, and its first frame is a full native render (the buffer is gone).
        val gate = RendererGate()
        val renderer = renderers.track(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0))
        renderer.asyncLoopsEnabled = false
        val surface = surfaceMock()
        gate.onSurfaceAvailable(surface, 1920, 720, 240.0)
        gate.publish(renderer)

        renderer.renderFrame()
        gate.pause()
        gate.detachSurface()

        // Screen onStart: the session re-delivers the retained surface, then resume().
        gate.onSurfaceAvailable(surface, 1920, 720, 240.0)
        gate.resume()

        assertTrue(renderer.isCurrentSurface(surface))
        assertEquals("the first frame after the start is a full render", 1, renderer.fullRenderCount)

        renderer.renderFrame()

        assertEquals("no blit of a pre-stop buffer", 2, renderer.fullRenderCount)
        verify(exactly = 2) { surface.lockCanvas(any()) }
    }

    @Test
    fun aStopClearsASurfaceFailureSoTheNextStartIsNotReportedAsFailed() {
        // Spec: auto-map-renderer — Stopped renderer reports no failure. A surface failure
        // recorded while the screen was visible must not survive the stop: it would gate
        // the renderer off at the next start and ask the host for a fresh surface (a
        // template refresh) with nothing to fetch.
        val gate = RendererGate()
        val renderer = renderers.track(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0))
        renderer.asyncLoopsEnabled = false
        // A dead surface: relaxed mock -> isValid false.
        val dead = mockk<Surface>(relaxed = true)
        gate.onSurfaceAvailable(dead, 1920, 720, 240.0)
        gate.publish(renderer)
        renderer.renderFrame()
        assertTrue(renderer.isSurfaceFailed())

        gate.pause()
        gate.detachSurface()
        assertFalse(renderer.isSurfaceFailed())

        // The next start with a usable surface renders like any other start.
        val usable = surfaceMock()
        gate.onSurfaceAvailable(usable, 1920, 720, 240.0)
        gate.resume()
        renderer.renderFrame()

        verify { usable.lockCanvas(any()) }
        assertFalse(renderer.isSurfaceFailed())
    }

    // ── the delivered surface DPI is the DPI of the car frames (spec: render-projection-dpi) ──

    @Test
    fun aDeliveredSurfaceDpiIsTheDpiOfTheCarFrames() {
        // Spec: auto-map-renderer — "Car renders carry the surface DPI". The gate holds no
        // client reference (the client-wide DPI setter is gone), so the delivered value can
        // only reach the map through the renderer that every render request reads.
        val client = FakeAutoRenderClient()
        val gate = RendererGate()
        val renderer = renderers.track(AutoMapRenderer(client, initialProjectionDpi = 240.0))
        renderer.asyncLoopsEnabled = false

        gate.publish(renderer)
        gate.onSurfaceAvailable(surfaceMock(), 1920, 720, 236.0)

        assertEquals(
            "the delivered surface DPI replaces the pre-surface fallback",
            236.0,
            renderer.projectionDpi,
            0.001
        )
        assertTrue("retaining the DPI renders nothing by itself", client.renderDpis.isEmpty())

        renderer.renderFrame()

        assertEquals(
            "the car frame's render request carries the delivered surface DPI",
            listOf(236.0),
            client.renderDpis.toList()
        )
    }

    @Test
    fun aProjectionDpiChangeReRendersAtTheNewDpi() {
        // Spec: render-projection-dpi — "Car surface replacement": a DPI change applies to the
        // car's own subsequent frames and produces a full native render at the new value (the
        // overrun buffer was projected at the old one). Called on the renderer directly: a
        // surface *re-delivery* invalidates the buffer through onSurfaceCreated anyway, which
        // would mask this rule — the gate-level test above pins the delivered value.
        //
        // A blit may still occur between the change and the landing frame: the display loop
        // keeps blitting the currently displayed frame while the new render is in flight (see
        // `renderFrame`), which is what keeps follow scrolling smooth.
        val client = FakeAutoRenderClient()
        val renderer = renderers.track(AutoMapRenderer(client, initialProjectionDpi = 240.0))
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surfaceMock(), 100, 100)
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)
        assertEquals(240.0, client.renderDpis.last(), 0.0)

        val rendersBefore = renderer.fullRenderCount
        renderer.updateProjectionDpi(200.0)

        assertEquals(200.0, renderer.projectionDpi, 0.001)
        renderer.renderFrame()

        assertTrue(
            "the frame after the change is a full native render, not the old buffer",
            renderer.fullRenderCount > rendersBefore
        )
        assertEquals(
            "the render request carries the new DPI",
            200.0,
            client.renderDpis.last(),
            0.0
        )
    }
}
