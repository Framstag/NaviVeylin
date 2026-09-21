package com.naviveylin.auto

import android.view.Surface
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
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
}
