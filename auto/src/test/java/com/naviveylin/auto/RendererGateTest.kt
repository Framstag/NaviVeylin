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
        every { s.release() } returns Unit
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
        gate.releaseSurface()
        gate.onSurfaceDestroyed()
        gate.resume()

        val renderer = rendererSpy()
        gate.publish(renderer)
        // resume was buffered and applied; pause/release were no-ops.
        verify { renderer.resume() }
        verify(inverse = true) { renderer.pause() }
    }
}
