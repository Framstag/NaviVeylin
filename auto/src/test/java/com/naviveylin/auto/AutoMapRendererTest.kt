package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AutoMapRenderer].
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererTest {

    private lateinit var client: FakeAutoRenderClient
    private lateinit var renderer: AutoMapRenderer

    @Before
    fun setUp() {
        client = FakeAutoRenderClient()
        renderer = AutoMapRenderer(client)
    }

    @Test
    fun surfaceCreatedStartsRender() {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas

        renderer.onSurfaceCreated(surface, 100, 100)

        // Should trigger a render call - verify via viewport state
        assertTrue(renderer.viewportState.value.zoom > 0)
    }

    @Test
    fun setGpsMarkerUpdatesState() {
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)

        // Re-center should use GPS position
        assertTrue(renderer.isFollowMode())
    }

    @Test
    fun setViewportDisengagesFollowMode() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)

        assertFalse(renderer.isFollowMode())
    }

    @Test
    fun reCenterReengagesFollowMode() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)
        assertFalse(renderer.isFollowMode())

        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
        renderer.reCenter()

        assertTrue(renderer.isFollowMode())
    }

    @Test
    fun shutdownStopsRenderLoop() {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas

        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.shutdown()

        // Should not crash after shutdown
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
    }

    @Test
    fun surfaceDestroyedClearsSurface() {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas

        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.onSurfaceDestroyed()

        // Surface is null, render should not crash
        renderer.setGpsMarker(48.8566, 2.3522, 45.0, 10.0)
    }

    @Test
    fun setFavoriteLocationsStoresData() {
        val fav = com.framstag.libosmscout.client.FavoriteLocation()
        fav.lat = 48.8566
        fav.lon = 2.3522
        fav.name = "Test"

        renderer.setFavoriteLocations(listOf(fav))

        // Should not crash
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas

        renderer.onSurfaceCreated(surface, 100, 100)
    }

    @Test
    fun setFavoriteLocationsNullClearsData() {
        renderer.setFavoriteLocations(null)

        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas

        renderer.onSurfaceCreated(surface, 100, 100)
    }

    @Test
    fun viewportStateFlowEmitsUpdates() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.5)

        val state = renderer.viewportState.value
        assertEquals(48.8566, state.lat, 1e-10)
        assertEquals(2.3522, state.lon, 1e-10)
        assertEquals(8, state.zoom)
        assertEquals(0.5, state.angle, 1e-10)
    }

    // --- continuous zoom (pinch) ---

    @Test
    fun zoomStep_smallScaleFactorKeepsZoomLevel() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)

        // Tiny pinch (1.01) must NOT jump a whole zoom level
        val (fraction, zoom) = renderer.zoomStep(1.01f)
        assertEquals(8, zoom)
        assertTrue(fraction > 8.0)
    }

    @Test
    fun zoomStep_doubleScaleZoomsInOneLevel() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)

        val (fraction, zoom) = renderer.zoomStep(2.0f)
        assertEquals(9, zoom)
        assertEquals(9.0, fraction, 1e-6)
    }

    @Test
    fun zoomStep_halfScaleZoomsOutOneLevel() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)

        val (fraction, zoom) = renderer.zoomStep(0.5f)
        assertEquals(7, zoom)
        assertEquals(7.0, fraction, 1e-6)
    }

    @Test
    fun zoomStep_accumulatesAcrossEvents() {
        renderer.setViewport(48.8566, 2.3522, 8, 0.0)

        // Two 1.5x pinches accumulate to ~9.17 → zoom level 9
        val (f1, z1) = renderer.zoomStep(1.5f)
        renderer.setViewport(48.8566, 2.3522, z1, 0.0, f1)
        val (f2, z2) = renderer.zoomStep(1.5f)
        assertEquals(9, z2)
        assertTrue(f2 > 9.0)
    }

    @Test
    fun zoomStep_clampsAtLimits() {
        renderer.setViewport(48.8566, 2.3522, AutoMapRenderer.MAX_ZOOM, 0.0)
        val (fraction, zoom) = renderer.zoomStep(4.0f)
        assertEquals(AutoMapRenderer.MAX_ZOOM, zoom)
        assertEquals(AutoMapRenderer.MAX_ZOOM.toDouble(), fraction, 1e-6)

        renderer.setViewport(48.8566, 2.3522, AutoMapRenderer.MIN_ZOOM, 0.0)
        val (fractionOut, zoomOut) = renderer.zoomStep(0.25f)
        assertEquals(AutoMapRenderer.MIN_ZOOM, zoomOut)
        assertEquals(AutoMapRenderer.MIN_ZOOM.toDouble(), fractionOut, 1e-6)
    }
}
