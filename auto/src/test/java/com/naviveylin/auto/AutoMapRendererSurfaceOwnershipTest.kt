package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Surface lifetime on the renderer side (spec: car-host-fault-isolation —
 * Single-owner car surface; design D1): a renderer draws on a surface the
 * session owns and never releases it — not on replace, not on detach, not on
 * shutdown. Releasing it (as before) disconnected the buffer queue the host
 * still owned, which made the host re-deliver a surface whose queue it had.
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererSurfaceOwnershipTest {

    /** Shuts the renderer down after every test (see RendererTestRule). */
    @get:Rule
    val renderers = RendererTestRule()

    @Test
    fun rendererNeverReleasesASurfaceItDrawsOn() {
        val renderer = renderers.newRenderer()
        val surface = mockk<Surface>(relaxed = true)

        renderer.onSurfaceCreated(surface, 1920, 720)
        renderer.onSurfaceDestroyed()
        renderer.shutdown()

        verify(exactly = 0) { surface.release() }
    }

    @Test
    fun replacingASurfaceDoesNotReleaseThePreviousOne() {
        val renderer = renderers.newRenderer()
        val first = mockk<Surface>(relaxed = true)
        val second = mockk<Surface>(relaxed = true)

        renderer.onSurfaceCreated(first, 1920, 720)
        renderer.onSurfaceCreated(second, 1080, 600)

        verify(exactly = 0) { first.release() }
        verify(exactly = 0) { second.release() }
    }

    @Test
    fun detachSurfaceStopsDrawingWithoutReleasing() {
        val renderer = renderers.newRenderer()
        val surface = mockk<Surface>(relaxed = true)
        renderer.onSurfaceCreated(surface, 1920, 720)

        renderer.detachSurface()
        renderer.shutdown()

        verify(exactly = 0) { surface.release() }
    }

    @Test
    fun aReplacedSurfaceIsNoLongerDrawable() {
        // The stale-surface guard (spec: car-host-fault-isolation — "After release the
        // system SHALL NOT lock or draw the released surface"): a native render that
        // outlives its surface must not draw the frame, and must not report the stale
        // surface as a failure of the live one (observed on the automotive AVD: a
        // background round trip replaced the surface and the map stayed frozen).
        val renderer = renderers.newRenderer()
        val first = mockk<Surface>(relaxed = true)
        val second = mockk<Surface>(relaxed = true)

        renderer.onSurfaceCreated(first, 1920, 720)
        assertTrue(renderer.isCurrentSurface(first))

        renderer.onSurfaceCreated(second, 1920, 720)
        assertFalse("a replaced surface is not drawable", renderer.isCurrentSurface(first))
        assertTrue(renderer.isCurrentSurface(second))

        renderer.onSurfaceDestroyed()
        assertFalse("a destroyed surface is not drawable", renderer.isCurrentSurface(second))
    }

    /** A surface the renderer can lock, so a "does it lock?" assertion is meaningful. */
    private fun mockSurface(): Surface = mockk<Surface>(relaxed = true).apply {
        every { lockCanvas(any()) } returns mockk<Canvas>(relaxed = true)
        every { isValid } returns true
    }

    @Test
    fun aDetachedRendererLocksNoSurface() {
        // Spec: auto-map-renderer — A stopped renderer holds no surface or frame buffer.
        // A stopped screen's renderer must not lock the one session surface another screen
        // is drawing through, and it must not keep a frame buffer either.
        val renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        val surface = mockSurface()
        renderer.onSurfaceCreated(surface, 1920, 720)

        // While attached it does lock (otherwise the assertion below proves nothing).
        renderer.renderFrame()
        verify(exactly = 1) { surface.lockCanvas(any()) }
        assertNotNull("a rendered frame is retained as the overrun buffer", renderer.overrunSize())

        renderer.detachSurface()
        assertFalse(renderer.isCurrentSurface(surface))
        assertNull("the overrun frame buffer is released on stop", renderer.overrunSize())

        renderer.renderFrame()
        verify(exactly = 1) { surface.lockCanvas(any()) }
        assertFalse("a detached renderer is not in a failed-surface state", renderer.isSurfaceFailed())
    }

    @Test
    fun detachingClearsASurfaceFailure() {
        // Spec: auto-map-renderer — Stopped renderer reports no failure: a surface failure
        // recorded while the screen was visible must not make the next start look failed
        // (it would request a host template refresh with no surface to fetch).
        val renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        val dead = mockk<Surface>(relaxed = true).apply { every { isValid } returns false }
        renderer.onSurfaceCreated(dead, 1920, 720)

        renderer.renderFrame()
        assertTrue(renderer.isSurfaceFailed())

        renderer.detachSurface()
        assertFalse(renderer.isSurfaceFailed())
    }

    @Test
    fun aStoppedRendererIsNotDrawableThroughItsOldSurface() {
        // The stop path is pause() + detachSurface(): after both, a render requested by
        // anything still running on this screen finds no surface to draw on.
        val renderer = renderers.newRenderer()
        renderer.asyncLoopsEnabled = false
        val surface = mockSurface()
        renderer.onSurfaceCreated(surface, 1920, 720)

        renderer.pause()
        renderer.detachSurface()
        renderer.renderFrame()

        verify(exactly = 0) { surface.lockCanvas(any()) }
    }
}
