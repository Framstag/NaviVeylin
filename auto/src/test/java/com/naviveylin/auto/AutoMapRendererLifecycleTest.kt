package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AutoMapRenderer]'s lifecycle and published state: surfaces, shutdown, the marker and favourite
 * setters, single-step pinch zoom, the coordinate-free render diagnostic lines, and the
 * route-without-geometry entry — one group of the cases that were a single 2 099-line
 * `AutoMapRendererTest` until the file crossed the `source-size` row's bound (item #300).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererLifecycleTest : AutoMapRendererTestSupport() {

    @Test
    fun committedViewportCenterIsNullBeforeTheFirstFrame() {
        // Spec: search-result-ranking — distance reference for ordering and display: a car map screen
        // publishes the center of the frame it drew, so a renderer that has drawn nothing offers no
        // center. `markerViewport()` (the overlay projection) answers with the pending target, which
        // no frame ever displayed — the two must not be interchangeable for the publish.
        assertNull(renderer.committedViewportCenter())
        assertNotNull(
            "the pending target is still the overlay reference before the first frame",
            renderer.markerViewport()
        )
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

        renderer.onSurfaceCreated(surface, 100, 100)    }

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

    @Test
    fun setDestinationMarkerStoresPositionAndName() {
        renderer.setDestinationMarker(48.8566, 2.3522, "Eiffel Tower")
        val state = renderer.destinationMarkerState()
        assertTrue(state.visible)
        assertEquals(48.8566, state.lat, 1e-6)
        assertEquals(2.3522, state.lon, 1e-6)
        assertEquals("Eiffel Tower", state.name)
    }

    @Test
    fun setDestinationMarkerNaNclearsMarker() {
        renderer.setDestinationMarker(48.8566, 2.3522, "Eiffel Tower")
        renderer.setDestinationMarker(Double.NaN, Double.NaN, null)
        val state = renderer.destinationMarkerState()
        assertFalse(state.visible)
    }

    // ── the MAP render entry (spec: auto-diagnostics — A car map-render diagnostic
    //    identifies the frame without a position; change
    //    `fix-car-render-coordinate-redaction`, TODO.md §98) ──

    /** A coordinate shape, the thing the exported diagnostics file must not contain. */
    private val coordinateShape = Regex("""[0-9]{1,3}\.[0-9]{4,}""")

    @Test
    fun renderLogMessageCarriesMagnificationAndFrameSizeWithoutAPosition() {
        val message = renderLogMessage(
            frameMag = 17.0,
            bitmapWidth = 1296,
            bitmapHeight = 720,
            surfaceDpi = 420.0
        )

        assertTrue("magnification is identity: $message", message.contains("mag=17.00"))
        assertTrue("frame size is identity: $message", message.contains("bitmap 1296x720"))
        assertTrue("the projection DPI is identity: $message", message.contains("dpi=420"))
        assertFalse("the frame centre is gone: $message", message.contains("center"))
        assertFalse("no coordinate shape: $message", coordinateShape.containsMatchIn(message))
    }

    @Test
    fun renderLogMessageStaysPositionFreeWhenTheRenderFailed() {
        val message = renderLogMessage(
            frameMag = 13.25,
            bitmapWidth = null,
            bitmapHeight = null,
            surfaceDpi = 420.0
        )

        assertTrue("a NULL render is still marked: $message", message.contains("NULL"))
        assertFalse("no coordinate shape: $message", coordinateShape.containsMatchIn(message))
        assertFalse("no decimal-comma form of the magnification", message.contains("mag=13,25"))
    }

    @Test
    fun setRouteWithoutGeometryStillSchedulesAndDrawsTheFrame() {
        // The car's route view is fed the shared navigation state's route geometry, which is absent
        // for a route the engine acquired without a polyline (spec: `navigation-engine` —
        // Acquisition without usable polyline geometry; spec: `route-map-overview` — absent counts
        // as an empty polyline). Absent must be accepted, not dereferenced.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.frameIteration()
        val requestsBefore = renderer.renderRequestCount
        val framesBefore = renderer.fullRenderCount + renderer.blitCount

        renderer.setRoute(null, null)
        renderer.frameIteration()

        assertEquals(
            "clearing the route schedules its own frame",
            requestsBefore + 1, renderer.renderRequestCount
        )
        assertEquals(
            "the scheduled frame is drawn",
            framesBefore + 1, renderer.fullRenderCount + renderer.blitCount
        )
    }
}
