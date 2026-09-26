package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import com.naviveylin.core.RenderBitmapPool
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car renderer's pooled render target: the displayed overrun frame holds one target
 * while the next render acquires a second, replaced frames go back to the pool, and a
 * stop leaves nothing held (spec: `render-performance` — Reusable render target for map
 * frames, A frame handed to the display layer is never overwritten; design D5/D7).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererPooledTargetTest {

    @get:Rule
    val renderers = RendererTestRule()

    private lateinit var client: FakeAutoRenderClient

    @Before
    fun setUp() {
        RenderBitmapPool.resetForTest()
        client = FakeAutoRenderClient()
    }

    private fun mockSurface(): Surface = mockk<Surface>(relaxed = true).apply {
        every { lockCanvas(any()) } returns mockk<Canvas>(relaxed = true)
        every { isValid } returns true
    }

    /** Attach a renderer with the async loops off, so only explicit frames render. */
    private fun attachedRenderer(width: Int = 1920, height: Int = 720): AutoMapRenderer {
        val renderer = renderers.newRenderer(client)
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(mockSurface(), width, height)
        return renderer
    }

    @Test
    fun thePairOfTargetsIsAllocatedOnceAndReusedAfterwards() {
        val renderer = attachedRenderer()

        renderer.renderFrame()
        assertEquals("the displayed frame is a pooled target", 1, RenderBitmapPool.allocatedCount)
        assertEquals("the displayed frame stays held", 1, RenderBitmapPool.inUseCount)
        assertNotNull(renderer.overrunSize())

        // A zoom change defeats the overrun blit, so this is a second full render while the
        // previous frame is still displayed: it needs its own target.
        renderer.setViewport(48.0, 11.0, 15, 0.0)
        renderer.renderFrame()
        assertEquals("the in-flight render takes a second target", 2, RenderBitmapPool.allocatedCount)
        assertEquals("the replaced frame went back to the pool", 1, RenderBitmapPool.inUseCount)

        // Every later render reuses the pair.
        renderer.setViewport(48.0, 11.0, 16, 0.0)
        renderer.renderFrame()
        renderer.setViewport(48.0, 11.0, 17, 0.0)
        renderer.renderFrame()

        assertEquals("no further allocation once the pair exists", 2, RenderBitmapPool.allocatedCount)
        assertEquals(1, RenderBitmapPool.inUseCount)
    }

    @Test
    fun aRenderInFlightNeverSharesTheDisplayedFrameTarget() {
        val renderer = attachedRenderer()
        renderer.renderFrame()

        var inUseDuringRender = -1
        client.onRender = { inUseDuringRender = RenderBitmapPool.inUseCount }
        renderer.setViewport(48.0, 11.0, 15, 0.0)
        renderer.renderFrame()
        client.onRender = null

        assertEquals(
            "the displayed frame and the in-flight render hold distinct targets",
            2,
            inUseDuringRender
        )
        assertEquals("and only the displayed one stays held", 1, RenderBitmapPool.inUseCount)
    }

    @Test
    fun aSurfaceSizeChangeReturnsTheStaleFrameToThePool() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val (oldWidth, oldHeight) = renderer.overrunSize()!!

        // A new surface of a different size drops the frame rendered for the old one.
        renderer.onSurfaceCreated(mockSurface(), 1000, 500)

        assertEquals("the stale frame's target is back in the pool", 0, RenderBitmapPool.inUseCount)
        assertEquals(
            "the old size class is retained for reuse, not leaked",
            1,
            RenderBitmapPool.freeCount(oldWidth, oldHeight)
        )

        renderer.renderFrame()
        val (newWidth, newHeight) = renderer.overrunSize()!!
        assertEquals("the new size class allocates its own target", 2, RenderBitmapPool.allocatedCount)
        assertEquals(1, RenderBitmapPool.inUseCount)
        assertEquals(0, RenderBitmapPool.freeCount(newWidth, newHeight))
    }

    @Test
    fun aStoppedRendererHoldsNoTarget() {
        val renderer = attachedRenderer()
        renderer.renderFrame()
        val (width, height) = renderer.overrunSize()!!

        renderer.detachSurface()

        assertNull("the frame buffer is released on stop", renderer.overrunSize())
        assertEquals("a stopped renderer holds no pooled target", 0, RenderBitmapPool.inUseCount)
        assertEquals("its target is available for reuse", 1, RenderBitmapPool.freeCount(width, height))
    }
}
