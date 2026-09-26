package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.RenderBitmapPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [MapRenderUtil.renderToBitmap] and the pooled [MapRenderUtil.renderInto].
 */
@RunWith(RobolectricTestRunner::class)
class MapRenderUtilTest {

    private val client = FakeOSMScoutClient()

    /** The DPI the pre-DPI cases render with. */
    private val dpi = 420.0

    @Before
    fun setUp() {
        RenderBitmapPool.resetForTest()
        client.renderReturnsNull = false
    }

    @Test
    fun renderToBitmapReturnsBitmap() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi
        )
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun renderToBitmapWithOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            routeLats = doubleArrayOf(48.85, 48.86),
            routeLons = doubleArrayOf(2.35, 2.36),
            favoriteLats = doubleArrayOf(48.86),
            favoriteLons = doubleArrayOf(2.35)
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapWithSearchSelected() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            searchSelLat = 48.86,
            searchSelLon = 2.35
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapWithTrack() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            trackLats = doubleArrayOf(48.85, 48.86),
            trackLons = doubleArrayOf(2.35, 2.36)
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapNullOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            routeLats = null,
            routeLons = null,
            favoriteLats = null,
            favoriteLons = null
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapEmptyOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            routeLats = doubleArrayOf(),
            routeLons = doubleArrayOf(),
            favoriteLats = doubleArrayOf(),
            favoriteLons = doubleArrayOf()
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapReturnsCorrectSize() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 200,
            height = 150,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi
        )
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width == 200)
        assertTrue(bitmap.height == 150)
    }

    // ---- Pooled form (spec: render-performance — Reusable render target for map frames) ----

    @Test
    fun renderIntoWritesTheRenderedPixelsIntoTheTarget() {
        val target = RenderBitmapPool.acquire(120, 90)

        val result = MapRenderUtil.renderInto(
            client = client,
            target = target,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi
        )

        assertSame("the pooled form returns the caller's target", target, result)
        assertEquals(120, target.width)
        assertEquals(90, target.height)
        assertEquals(
            "the rendered pixels land in the target",
            FakeOSMScoutClient.TEST_PIXEL_COLOR,
            target.getPixel(0, 0)
        )
        RenderBitmapPool.release(target)
    }

    @Test
    fun renderIntoUsesTheOverlayPathWhenOverlaysArePresent() {
        val before = client.renderWithRouteAndPoisCount.get()
        val target = RenderBitmapPool.acquire(80, 80)

        MapRenderUtil.renderInto(
            client = client,
            target = target,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi,
            routeLats = doubleArrayOf(48.85, 48.86),
            routeLons = doubleArrayOf(2.35, 2.36),
            favoriteLats = doubleArrayOf(48.86),
            favoriteLons = doubleArrayOf(2.35)
        )

        assertEquals(
            "overlays still take the overlay-aware native entry point",
            before + 1,
            client.renderWithRouteAndPoisCount.get()
        )
        RenderBitmapPool.release(target)
    }

    @Test
    fun consecutivePooledRendersAllocateOneTarget() {
        val first = RenderBitmapPool.acquire(100, 100)
        MapRenderUtil.renderInto(client, first, 48.8566, 2.3522, 0.0, 5.0, dpi)
        RenderBitmapPool.release(first)

        val second = RenderBitmapPool.acquire(100, 100)
        MapRenderUtil.renderInto(client, second, 48.8566, 2.3522, 0.0, 5.0, dpi)

        assertSame("the second render reuses the released target", first, second)
        assertEquals("no allocation for the second render", 1, RenderBitmapPool.allocatedCount)
        RenderBitmapPool.release(second)
    }

    @Test
    fun renderIntoLeavesTheTargetIntactWhenTheNativeRenderFails() {
        val target = RenderBitmapPool.acquire(60, 60)
        MapRenderUtil.renderInto(client, target, 48.8566, 2.3522, 0.0, 5.0, dpi)
        val pixelsAfterSuccess = target.getPixel(3, 3)

        client.renderReturnsNull = true
        val result = MapRenderUtil.renderInto(client, target, 48.8566, 2.3522, 0.0, 5.0, dpi)

        assertNull("a failed render reports null", result)
        assertEquals(
            "the target's pixels survive a failed render for the next attempt",
            pixelsAfterSuccess,
            target.getPixel(3, 3)
        )
        assertTrue("the target stays usable", target.width == 60 && target.height == 60)
        RenderBitmapPool.release(target)
    }

    @Test
    fun renderToBitmapStillAllocatesItsOwnBitmapOutsideThePool() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 64,
            height = 64,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5.0,
            dpi = dpi
        )

        assertNotNull(bitmap)
        assertEquals("the legacy signature does not touch the pool", 0, RenderBitmapPool.allocatedCount)
        assertEquals(0, RenderBitmapPool.inUseCount)
    }

    // ---- Projection DPI travels with the request (spec: render-projection-dpi) ----

    @Test
    fun renderIntoPassesTheRenderDpiToThePlainEntryPoint() {
        val before = client.renderCount.get()
        val target = RenderBitmapPool.acquire(100, 100)

        MapRenderUtil.renderInto(client, target, 48.8566, 2.3522, 0.0, 5.0, dpi)

        assertEquals("no overlays → the plain render entry point", before + 1, client.renderCount.get())
        assertEquals(
            "the request carries the caller's DPI",
            listOf(dpi),
            client.renderDpis.toList()
        )
        RenderBitmapPool.release(target)
    }

    @Test
    fun renderIntoPassesTheRenderDpiToTheOverlayEntryPoint() {
        val target = RenderBitmapPool.acquire(100, 100)

        MapRenderUtil.renderInto(
            client, target, 48.8566, 2.3522, 0.0, 5.0, dpi,
            routeLats = doubleArrayOf(48.85, 48.86),
            routeLons = doubleArrayOf(2.35, 2.36)
        )

        assertEquals(
            "the overlay request carries the caller's DPI",
            listOf(dpi),
            client.renderDpis.toList()
        )
        RenderBitmapPool.release(target)
    }

    /**
     * The reported defect, at the seam: two surfaces render through the same client in one
     * process (a phone canvas and a car surface). Each request carries the DPI of the surface
     * it draws, so neither render is projected with the other's value — and the DPI adds no
     * extra native render (spec: render-projection-dpi — "No process-global projection DPI",
     * "Carrying the DPI adds no render cost").
     */
    @Test
    fun eachSurfaceRendersAtItsOwnDpiThroughOneClient() {
        val carDpi = 236.0
        val phoneTarget = RenderBitmapPool.acquire(100, 100)
        val carTarget = RenderBitmapPool.acquire(100, 100)

        val rendersBefore = client.renderCount.get()
        MapRenderUtil.renderInto(client, phoneTarget, 48.8566, 2.3522, 0.0, 5.0, dpi)
        MapRenderUtil.renderInto(client, carTarget, 48.8566, 2.3522, 0.0, 5.0, carDpi)
        MapRenderUtil.renderInto(client, phoneTarget, 48.8566, 2.3522, 0.0, 5.0, dpi)

        assertEquals(
            "each request carries its own surface's DPI, in order",
            listOf(dpi, carDpi, dpi),
            client.renderDpis.toList()
        )
        assertEquals("no render beyond one per frame", rendersBefore + 3, client.renderCount.get())
        RenderBitmapPool.release(phoneTarget)
        RenderBitmapPool.release(carTarget)
    }
}
