package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        renderer = AutoMapRenderer(client, initialProjectionDpi = 240.0)
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

    // --- auto-smooth-follow (spec: auto-smooth-follow) ---

    private fun mockSurface(): Pair<Surface, Canvas> {
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas
        every { surface.isValid } returns true
        return surface to canvas
    }

    @Test
    fun fullRenderRendersAtOverrunSize() {
        // Task 1.1/1.2: a full render is at ~1.2x the surface size and the
        // visible region (viewport center) is extracted for display.
        val (surface, canvas) = mockSurface()
        val dxSlot = slot<Float>()
        val dySlot = slot<Float>()
        every { canvas.drawBitmap(any(), capture(dxSlot), capture(dySlot), any()) } returns Unit
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        renderer.renderFrame()

        val size = renderer.overrunSize()
        assertNotNull(size)
        assertEquals(120, size!!.first)
        assertEquals(120, size.second)
        assertEquals(1, renderer.fullRenderCount)
        // The overrun bitmap is drawn centered: dx = (100 - 120) / 2 = -10.
        assertEquals(-10f, dxSlot.captured, 0.01f)
        assertEquals(-10f, dySlot.captured, 0.01f)
    }

    @Test
    fun smallGpsMoveServedByBlitNoFullRender() {
        // Task 2.1: a small viewport move within the overrun region is served
        // by a blit — no full native render (render-count assertion).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        renderer.extrapolationTick(now + 100, 0.1)

        assertEquals(1, renderer.fullRenderCount)
        assertTrue(renderer.blitCount >= 1)
    }

    @Test
    fun largeViewportMoveFallsBackToFullRender() {
        // Task 2.2: when the shift exceeds the overrun margin, fall back to a
        // full render at the new center.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)

        // Dortmund -> Paris: far beyond the overrun margin.
        renderer.setViewport(48.8566, 2.3522, 12, 0.0)
        renderer.renderFrame()

        assertEquals(2, renderer.fullRenderCount)
        assertEquals(48.8566, renderer.viewportState.value.lat, 1e-9)
        assertEquals(2.3522, renderer.viewportState.value.lon, 1e-9)
    }

    @Test
    fun blitRespectsSurfaceFailurePath() {
        // Task 2.3: a dead surface goes through the existing failure path — no
        // lockCanvas exception, no blit counted, loop gated off.
        val surface = mockk<Surface>(relaxed = true)
        val canvas = mockk<Canvas>(relaxed = true)
        every { surface.lockCanvas(any()) } returns canvas
        every { surface.isValid } returns false
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        renderer.extrapolationTick(now + 100, 0.1)

        assertEquals(0, renderer.blitCount)
        assertFalse(renderer.extrapolationGateActive(now + 200))
    }

    @Test
    fun extrapolationGateStopsWhenStationaryPausedOrDisengaged() {
        // Task 3.1: the loop runs only when resumed + follow mode + moving +
        // fresh fix + valid surface.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        val now = System.currentTimeMillis()

        // No fix yet -> not moving -> gate off.
        assertFalse(renderer.extrapolationGateActive(now))

        // Moving fix -> gate on.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        assertTrue(renderer.extrapolationGateActive(now + 100))

        // Vehicle stops -> gate off.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 0.0, timeMs = now + 1000)
        assertFalse(renderer.extrapolationGateActive(now + 1100))

        // Moving again, then paused -> gate off.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now + 2000)
        renderer.pause()
        assertFalse(renderer.extrapolationGateActive(now + 2100))
        renderer.resume()
        assertTrue(renderer.extrapolationGateActive(now + 2200))

        // Follow disengaged (pan) -> gate off.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        assertFalse(renderer.extrapolationGateActive(now + 2300))
    }

    @Test
    fun markerRidesPredictedPositionBetweenFixes() {
        // Task 3.3: the GPS marker is drawn at the predicted (displayed)
        // position each frame so it glides with the blitted map.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        // Before the first tick the display is not initialized -> raw fix.
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)

        renderer.extrapolationTick(now + 100, 0.1)
        val (mlat, mlon) = renderer.markerPosition()
        // The marker moved off the raw fix along the prediction.
        assertTrue(mlat != 51.5142273 || mlon != 7.4652789)
    }

    @Test
    fun staleFixHoldsPositionInsteadOfExtrapolating() {
        // Task 4.1/4.2: the prediction uses the fix time — a stale fix holds
        // the position (display-only; the nav engine never sees it).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(
            51.5142273, 7.4652789, 45.0, 10.0,
            speedKmH = 10.0, timeMs = now - 10_000
        )
        renderer.extrapolationTick(now, 0.1)

        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        assertEquals(7.4652789, renderer.markerPosition().second, 1e-9)
    }

    @Test
    fun staleFixEasesDisplayBackToFix() {
        // Phone-matching behavior: a stale fix (beyond the extrapolation
        // window) holds the position and the display eases BACK to it instead
        // of freezing ahead — the loop keeps running and FollowPrediction
        // holds. This is what prevents the map from overshooting during a GPS
        // gap (the emulator GPX has a 14.4 s gap).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        // Let the display run ahead of the fix (fresh fix, extrapolating).
        renderer.extrapolationTick(now + 500, 0.1)
        val ahead = renderer.markerPosition()
        assertTrue(abs(ahead.first - 51.5142273) > 1e-7)

        // Now the fix is stale (10 s old): the prediction holds at the fix and
        // the display eases back toward it.
        renderer.extrapolationTick(now + 10_000, 0.1)
        val back = renderer.markerPosition()
        assertTrue(abs(back.first - 51.5142273) < abs(ahead.first - 51.5142273))
    }

    @Test
    fun reengageFollowReengagesWithoutSnapping() {
        // reengageFollow re-engages follow mode without snapping the display
        // to the fix (the extrapolation loop eases there); reCenter snaps.
        // Screens that disengage follow transiently (heading-up rotation) use
        // reengageFollow so the map does not jump back per fix.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0)

        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        assertFalse(renderer.isFollowMode())
        renderer.reengageFollow()
        assertTrue(renderer.isFollowMode())

        // reCenter snaps the display to the fix.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reCenter()
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        assertEquals(7.4652789, renderer.markerPosition().second, 1e-9)
    }

    @Test
    fun fullRenderPreservesDisplayInFollowMode() {
        // Regression test for the "pumping" bug: a render triggered while
        // follow mode stays engaged (heading-up setViewport + reengageFollow)
        // must NOT yank the loop-owned eased display back to the render
        // target — that made the vehicle marker oscillate every fix.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        // The extrapolation loop eased the display ahead of the fix.
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        renderer.extrapolationTick(now + 500, 0.1)
        val displayBefore = renderer.markerPosition()
        assertTrue(abs(displayBefore.first - 51.5142273) > 1e-7)

        // Simulate the heading-up flow: disengage + re-engage, then the
        // pending render completes in follow mode.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reengageFollow()
        renderer.renderFrame()

        val displayAfter = renderer.markerPosition()
        assertEquals(displayBefore.first, displayAfter.first, 1e-12)
        assertEquals(displayBefore.second, displayAfter.second, 1e-12)
    }
}
