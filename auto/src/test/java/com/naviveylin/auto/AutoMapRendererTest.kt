package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FakeAutoRenderClient
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

    /** Shuts [renderer] down after every test: the renderer's loops start in `init`
     *  and would otherwise outlive the test (change `fix-auto-unit-test-heap-overflow`,
     *  `TODO.md` §33). */
    @get:Rule
    val renderers = RendererTestRule()

    @Before
    fun setUp() {
        client = FakeAutoRenderClient()
        renderer = renderers.newRenderer(client)
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
    fun invalidateStyleForcesFullRenderNotBlit() {
        // A style/variant change (daylight flag) must bypass the overrun blit:
        // the buffer holds pixels from the previous variant (spec:
        // auto-map-renderer — no patterns from the previous variant).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)

        // Marker-only update makes the next frame blit-eligible...
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        // ...but invalidateStyle must force a full render instead.
        renderer.invalidateStyle()
        renderer.renderFrame()

        assertEquals(2, renderer.fullRenderCount)
    }

    @Test
    fun invalidateStyleAfterShutdownIsNoOp() {
        renderer.shutdown()
        renderer.invalidateStyle() // must not throw
    }

    @Test
    fun invalidateDataForcesFullRenderNotBlit() {
        // A basemap data change (download/update/delete while running) must
        // bypass the overrun blit: the buffer holds pixels rendered without
        // the new data.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)

        // Marker-only update makes the next frame blit-eligible...
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        // ...but invalidateData must force a full render instead.
        renderer.invalidateData()
        renderer.renderFrame()

        assertEquals(2, renderer.fullRenderCount)
    }

    @Test
    fun invalidateDataAfterShutdownIsNoOp() {
        renderer.shutdown()
        renderer.invalidateData() // must not throw
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

        // An UNKNOWN speed (the derivation's sentinel, as reported when no speed is known and the
        // vehicle has not moved) keeps the gate CLOSED: the auto-zoom default-speed seed (20 km/h)
        // is a target-computation default and must never reach this feed, or a parked car would
        // glide as if it moved (spec: auto-smooth-follow — "Fix feed from follow-mode screens";
        // change task 7.2).
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = -1.0, timeMs = now)
        assertFalse("an unknown speed is not the 20 km/h seed", renderer.extrapolationGateActive(now + 100))

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
    fun staleFixHoldsDisplayInsteadOfEasingBack() {
        // Delta fix-aa-follow-vehicle-jumps: forward-only displayed position —
        // with a stale fix the prediction holds at the fix and the display
        // HOLDS ahead of it (never slides backward along the direction of
        // travel). The pre-delta behavior eased the display back toward the
        // stale fix — a backward correction slide, the exact jerk the
        // forward-only rule forbids. The held lead is bounded by the eased
        // lead (≤ v·tau); the next moving fix's prediction advances past it.
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

        // Now the fix is stale (10 s old): the prediction holds at the fix.
        // The display must NOT ease back — it holds exactly at its position.
        renderer.extrapolationTick(now + 10_000, 0.1)
        val after = renderer.markerPosition()
        assertEquals(ahead.first, after.first, 0.0)
        assertEquals(ahead.second, after.second, 0.0)
        // And it stays ahead of the (stale) fix, bounded by the eased lead.
        assertTrue(abs(after.first - 51.5142273) >= 1e-7)
    }

    @Test
    fun forwardOnlyHoldStopsBackwardSlideAtFixArrival() {
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Fix arrival behind the display holds"/"Forward-only across
        // consecutive fixes"): a fix arriving with the prediction overshooting
        // (curve/deceleration) puts the target behind the display — the
        // display HOLDS instead of sliding backward, and resumes once the
        // extrapolation from the new fix passes the held position.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        // Eastbound at 14 m/s (50.4 km/h).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)
        val (_, lonAhead) = renderer.markerPosition()
        // The display advanced east of the fix.
        assertTrue(lonAhead > 7.4652789)

        // Fix arrives with the vehicle having barely advanced (deceleration):
        // the target line now lies BEHIND the displayed position -> hold.
        renderer.setGpsMarker(51.5142273, 7.4652789 + 1e-6, 90.0, 10.0, speedKmH = 50.4, timeMs = now + 300)
        renderer.extrapolationTick(now + 305, 0.005)
        assertEquals(lonAhead, renderer.markerPosition().second, 0.0)

        // Extrapolation from the new fix advances past the held position ->
        // the display resumes forward.
        renderer.extrapolationTick(now + 800, 0.2)
        assertTrue(renderer.markerPosition().second > lonAhead)
    }

    @Test
    fun gateFreezeKeepsDisplayAndMarkerAtStop() {
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Vehicle stops"/"Vehicle resumes after a stop"): the stopped gate
        // freezes the displayed position and marker — no NaN reset, no
        // raw-fix fallback, no ease back toward the fix; the resume continues
        // from the frozen position.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)
        val (frozenLat, frozenLon) = renderer.markerPosition()
        assertTrue(frozenLon > 7.4652789)

        // Stop (same fix, speed 0): the gate closes; a stationary tick must
        // not mutate the display. The marker stays on the frozen displayed
        // position (east of the fix), NOT the raw fix (lon 7.4652789).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 0.0, timeMs = now + 1000)
        renderer.extrapolationTick(now + 1_100, 0.1)
        assertEquals(frozenLat, renderer.markerPosition().first, 0.0)
        assertEquals(frozenLon, renderer.markerPosition().second, 0.0)
        assertTrue(renderer.markerPosition().second > 7.4652789)

        // Resume: the display advances from the frozen position (no snap).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now + 2000)
        renderer.extrapolationTick(now + 2_300, 0.3)
        assertTrue(renderer.markerPosition().second > frozenLon)
    }

    @Test
    fun stationaryJitterFixesDoNotReAnchorViewport() {
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Vehicle stops"): the stationary branch seeds the display + viewport
        // to the fix ONCE per stop episode; GPS-jitter fixes after that leave
        // the viewport and the displayed position untouched.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        // First stationary fix (no previous fix): seed once.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis())
        val vpAfterSeed = renderer.viewportState.value
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        // Center anchor: the seeded viewport center IS the fix.
        assertEquals(51.5142273, vpAfterSeed.lat, 1e-9)
        assertEquals(7.4652789, vpAfterSeed.lon, 1e-9)

        // Jitter fixes while stopped: nothing re-anchors, the display stays on
        // the seeded fix.
        renderer.setGpsMarker(51.5142273 + 1e-6, 7.4652789 + 1e-6, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis() + 500)
        assertEquals(vpAfterSeed.lat, renderer.viewportState.value.lat, 0.0)
        assertEquals(vpAfterSeed.lon, renderer.viewportState.value.lon, 0.0)
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)

        renderer.setGpsMarker(51.5142273 - 2e-6, 7.4652789 - 1e-6, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis() + 1000)
        assertEquals(vpAfterSeed.lat, renderer.viewportState.value.lat, 0.0)
        assertEquals(vpAfterSeed.lon, renderer.viewportState.value.lon, 0.0)
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
    }

    @Test
    fun marginRenderRequestHonoursThrottleParity() {
        // Delta fix-aa-follow-vehicle-jumps (design D3): the margin full-render
        // request is throttled at RENDER_REQUEST_INTERVAL_MS = 200 ms (phone
        // parity). A clamped tick inside the window does not re-request (the
        // viewport stays put); one past the boundary does (the viewport
        // re-anchors on the advanced display).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        // Jump ~1.4 km east + moving at 100 km/h: at zoom 12 that is ~36 px —
        // beyond the overrun margin (10 px), so the display clamps on the
        // first tick and requests the margin render (t0).
        renderer.setGpsMarker(51.5142273, 7.4852, 90.0, 10.0, speedKmH = 100.0, timeMs = now)
        renderer.extrapolationTick(now + 50, 0.05)
        val vp1 = renderer.viewportState.value

        // Inside the 200 ms window: no new request -> viewport unchanged.
        renderer.extrapolationTick(now + 190, 0.14)
        assertEquals(vp1.lat, renderer.viewportState.value.lat, 1e-12)
        assertEquals(vp1.lon, renderer.viewportState.value.lon, 1e-12)

        // Past the boundary: the request fires -> the viewport re-anchors on
        // the (advanced) display.
        renderer.extrapolationTick(now + 350, 0.16)
        val vp3 = renderer.viewportState.value
        assertTrue(
            "vp1=" + vp1.lat + "," + vp1.lon +
                " vp3=" + vp3.lat + "," + vp3.lon +
                " marker=" + renderer.markerPosition(),
            vp1.lat != vp3.lat || vp1.lon != vp3.lon
        )
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

    @Test
    fun defaultFollowAnchorKeepsCenterFraming() {
        // Default anchor (center/center) must reproduce the pre-feature
        // framing: the follow render target equals the vehicle position.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.reCenter()
        assertEquals(51.5142273, renderer.markerViewport().first, 1e-9)
        assertEquals(7.4652789, renderer.markerViewport().second, 1e-9)
    }

    @Test
    fun offCenterAnchorShiftsFollowRenderTarget() {
        // Bottom-right anchor: after reCenter the render target must be the
        // anchor center of the fix — projecting the fix against it lands on
        // the 70%/90% fractions (spec: auto/free-driving — Follow mode
        // activated; auto/navigation-view — Vehicle anchor during navigation).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.reCenter()

        val vp = renderer.markerViewport()
        assertFalse(vp.first == 51.5142273 && vp.second == 7.4652789)
        val proj = ProjectionUtils.viewport(
            vp.first, vp.second, renderer.fractionalZoom(), 100, 100, 240.0,
            renderer.viewportState.value.angle
        )
        val (x, y) = proj.geoToScreen(51.5142273, 7.4652789)
        assertEquals(0.7 * 100, x, 0.5)
        assertEquals(0.9 * 100, y, 0.5)
    }

    @Test
    fun reengageFollowAnchorsViewportToFix() {
        // Re-engage after a pan must return the marker to the anchor, not the
        // surface center (spec: auto/navigation-view — Anchor restored after
        // manual pan).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0)
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        renderer.reengageFollow()

        val expected = anchorCenter(
            51.5142273, 7.4652789, VehicleAnchorPosition.BOTTOM_CENTER,
            renderer.fractionalZoom(), 100, 100, 240.0, renderer.viewportState.value.angle
        )
        assertEquals(expected.first, renderer.markerViewport().first, 1e-9)
        assertEquals(expected.second, renderer.markerViewport().second, 1e-9)
    }

    // --- Host pane insets + marker/content alignment -------------------------
    // (spec: auto/navigation-view — "Panel clearance via side anchor"; change
    // anchor-per-surface-visible-area)

    @Test
    fun hostPaneClampsTheLeadingEdgeAnchorOnly() {
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        // Default (center) keeps its exact fraction on both pane sides: the AA default
        // framing contract is unchanged.
        assertEquals(0.5, renderer.resolvedFollowAnchor().fx, 1e-9)
        renderer.setHostPaneRtl(true)
        assertEquals(0.5, renderer.resolvedFollowAnchor().fx, 1e-9)
        renderer.setHostPaneRtl(false)

        // A preset inside the host's 40% leading band moves to the nearest free
        // position (band edge + the grid's own 10% margin), not behind the panel.
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT)
        val clamped = renderer.resolvedFollowAnchor()
        assertEquals(VehicleAnchorPosition.TOP_FAR_LEFT.fy, clamped.fy, 1e-9)
        assertTrue("far-left must move out of the 40% band (was ${clamped.fx})", clamped.fx >= 0.4)

        // A preset already clear of the band is untouched.
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_RIGHT)
        assertEquals(VehicleAnchorPosition.BOTTOM_RIGHT.fx, renderer.resolvedFollowAnchor().fx, 1e-9)

        // RTL hosts mirror the panel: the far-RIGHT preset is the clamped one.
        renderer.setHostPaneRtl(true)
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_RIGHT)
        val rtlClamped = renderer.resolvedFollowAnchor()
        assertTrue("far-right must move out of the band in RTL", rtlClamped.fx <= 0.6 + 1e-9)
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT)
        assertEquals(
            VehicleAnchorPosition.TOP_FAR_LEFT.fx,
            renderer.resolvedFollowAnchor().fx, 1e-9
        )
    }

    @Test
    fun bottomRowAnchorClampsAboveHostBottomChrome() {
        // AAOS bottom bar (host chrome over the surface bottom, not part of
        // the granted surface): a bottom-row preset must move up out of the
        // covered band (design: anchor-per-surface-visible-area, AA vertical
        // clamp — the AA counterpart of the phone overlay-inset rule). Center
        // stays exact; no inset = identity.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        renderer.setHostBottomInset(30)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        val clamped = renderer.resolvedFollowAnchor()
        assertTrue(
            "bottom-center must move above the bottom band (was ${clamped.fy})",
            clamped.fy < VehicleAnchorPosition.BOTTOM_CENTER.fy
        )
        assertTrue("clamped anchor must stay inside the visible band", clamped.fy >= 0.5)

        renderer.setFollowAnchor(VehicleAnchorPosition.CENTER)
        assertEquals(0.5, renderer.resolvedFollowAnchor().fy, 1e-9)

        renderer.setHostBottomInset(0)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER.fy, renderer.resolvedFollowAnchor().fy, 1e-9)
    }

    @Test
    fun hostTopInsetStoredClampedAndNoopsOnRepeat() {
        // Mirror of setHostBottomInset (design D8, street-name-host-views):
        // the renderer stores the host-covered top band (the AAOS status bar)
        // for the street-pill band anchoring; it must clamp negatives, guard
        // repeats, and leave the follow-anchor resolution untouched (the top
        // anchor clamp is a recorded follow-up).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        assertEquals(0, renderer.hostTopInset())
        renderer.setHostTopInset(56)
        assertEquals(56, renderer.hostTopInset())
        renderer.setHostTopInset(-10) // negative clamped to 0
        assertEquals(0, renderer.hostTopInset())
        renderer.setHostTopInset(56)
        renderer.setHostTopInset(56) // repeat: no-op guard, no render churn
        assertEquals(56, renderer.hostTopInset())

        // The top band does not shift bottom/clamped anchors (follow-up note).
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        assertEquals(VehicleAnchorPosition.BOTTOM_CENTER.fy, renderer.resolvedFollowAnchor().fy, 1e-9)
    }

    @Test
    fun viewportStateCarriesTheFractionalMagnification() {
        // The emitted viewport state must carry the FRACTIONAL magnification, not just
        // the integer model level: a consumer that commits a viewport without a new
        // zoom target (a fix with no auto-zoom step) reads it back, and the integer
        // level would round the committed magnification to the whole level — the map
        // then breathes in and out once per fix (spec: auto-speed-zoom — Fractional
        // target is committed, not rounded; change aa-follow-framing-and-zoom-parity).
        renderer.setViewport(51.5142273, 7.4652789, 13, 0.0, 13.08)

        val state = renderer.viewportState.value
        assertEquals("the integer model level", 13, state.zoom)
        assertEquals("the fractional magnification must survive", 13.08, state.zoomFraction, 1e-9)
    }

    @Test
    fun markerRidesTheBlittedContentWithABlitOffset() {
        // The displayed frame is blitted by the display drift; the marker must move by
        // the same offset (else it leads the map content between commits and snaps back
        // on the next commit — the AA counterpart of the phone fix).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setGpsMarker(51.5142273, 7.4652789, 0.0, 10.0)
        renderer.reCenter()

        val (x0, y0) = renderer.markerScreenPosition(100, 100)
        renderer.setBlitOffsetForTest(7.0, -3.0)
        val (x1, y1) = renderer.markerScreenPosition(100, 100)
        assertEquals("marker x must follow the blit offset", x0 - 7.0, x1, 1e-9)
        assertEquals("marker y must follow the blit offset", y0 + 3.0, y1, 1e-9)

        // With the default center anchor and no display drift the marker sits at the
        // anchor it was framed for (center), and lands on the content position.
        renderer.setBlitOffsetForTest(0.0, 0.0)
        val (x2, y2) = renderer.markerScreenPosition(100, 100)
        assertEquals(50.0, x2, 0.5)
        assertEquals(50.0, y2, 0.5)
    }

    // --- AA follow blit anchor: raw preset vs resolved fraction ------------------
    // (spec: auto-smooth-follow — "Single resolved anchor in the AA follow blit";
    // change fix-aa-follow-blit-anchor-mismatch)

    @Test
    fun paneBandBlitOffsetUsesTheResolvedAnchor() {
        // The AA blit offset uses the RESOLVED anchor (clampAnchorOutOfPane against the
        // host's 40% leading band), the same fraction anchorCenterFor commits the frame
        // on. The raw preset differs for pane-band presets (far-left in LTR) and a
        // mismatch keeps the offset permanently outside the overrun margin — every
        // tick becomes a full native render instead of a sub-region blit.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100) // dpi 240, default mag
        renderer.setHostPaneRtl(false) // LTR host
        renderer.setFollowAnchor(VehicleAnchorPosition.TOP_FAR_LEFT)
        val resolved = renderer.resolvedFollowAnchor()
        assertTrue(
            "far-left must resolve out of the 40% band (was ${resolved.fx})",
            resolved.fx >= 0.4 + 1e-9
        )
        assertTrue("resolved fx must differ from the raw preset", resolved.fx < 0.5)

        val mag = 12.0
        val dpi = 240.0
        val canvasW = 100
        val canvasH = 100
        val bitmapW = 120 // 1.2x overrun
        val bitmapH = 120
        val marginX = (bitmapW - canvasW) / 2.0
        val marginY = (bitmapH - canvasH) / 2.0
        val lat = 51.5142273
        val lon = 7.4652789
        // Display ~30 m north of the frame position — within the overrun margin.
        val (dispLat, dispLon) = (lat + 30.0 / 111320.0) to lon

        // The AA frame is committed anchor-centered on the RESOLVED fraction.
        val (fLat, fLon) = anchorCenter(lat, lon, resolved.fx, resolved.fy, mag, canvasW, canvasH, dpi)

        val offResolved = FollowPrediction.displayOffsetPx(
            dispLat, dispLon, fLat, fLon, mag, 0.0,
            bitmapW, bitmapH, canvasW, canvasH, dpi,
            resolved.fx, resolved.fy
        )

        // The buggy blit: the raw preset while the frame is rendered at the resolved
        // fraction (what AutoMapRenderer did before the fix).
        val offRaw = FollowPrediction.displayOffsetPx(
            dispLat, dispLon, fLat, fLon, mag, 0.0,
            bitmapW, bitmapH, canvasW, canvasH, dpi,
            VehicleAnchorPosition.TOP_FAR_LEFT.fx, VehicleAnchorPosition.TOP_FAR_LEFT.fy
        )
        assertFalse(
            "resolved-anchor blit stays inside the margin (resolved=" + resolved +
                " off=" + offResolved.rawX + "," + offResolved.rawY + " clamped=" + offResolved.clamped +
                " rawOff=" + offRaw.rawX + "," + offRaw.rawY + ")",
            offResolved.clamped
        )
        assertTrue("raw-anchor blit saturates the margin (render churn)", offRaw.clamped)
        assertEquals("raw-anchor blit clamps to the margin", marginX, kotlin.math.abs(offRaw.clampedX), 1e-9)

        // Content position of the displayed point under the resolved blit lands on the
        // resolved anchor fraction (the frame-viewport projection minus the blit offset
        // — the same construction the renderer's markerScreenPosition uses).
        val frameVp = ProjectionUtils.viewport(fLat, fLon, mag, bitmapW, bitmapH, dpi)
        val (bx, by) = frameVp.geoToScreenRotated(dispLat, dispLon)
        val contentX = canvasW / 2.0 - offResolved.clampedX + (bx - bitmapW / 2.0)
        val contentY = canvasH / 2.0 - offResolved.clampedY + (by - bitmapH / 2.0)
        assertEquals("content x at the resolved fraction", resolved.fx * canvasW, contentX, 1e-6)
        assertEquals("content y at the resolved fraction", resolved.fy * canvasH, contentY, 1e-6)
    }

    @Test
    fun browseBlitKeepsTheSurfaceCenterAnchorContract() {
        // Non-follow (browse) blit keeps the pre-anchor surface-center semantics: with
        // the 0.5/0.5 anchor an aligned display needs no shift, and the follow branch
        // selects the resolved anchor (asserted by paneBandBlitOffsetUsesTheResolvedAnchor)
        // while browse always uses the center fraction (spec: single resolved anchor —
        // browse/browse-mode surfaces that measure no pane are unaffected).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        val mag = 12.0
        val dpi = 240.0
        val lat = 52.0
        val lon = 13.4
        val aligned = FollowPrediction.displayOffsetPx(
            lat, lon, lat, lon, mag, 0.0,
            120, 120, 100, 100, dpi,
            0.5, 0.5
        )
        assertEquals("aligned browse blit needs no shift x", 0.0, aligned.clampedX, 1e-9)
        assertEquals("aligned browse blit needs no shift y", 0.0, aligned.clampedY, 1e-9)
        assertFalse("aligned browse blit must not be clamped", aligned.clamped)
    }

    // --- Overlays project against the displayed frame ----------------------------
    // (spec: gps-location-marker — Marker projects against displayed bitmap viewport
    // / Destination pin shares the displayed frame; auto-map-renderer — Map
    // re-renders on viewport change; change overlay-projects-against-displayed-frame)

    private val fixLat = 51.5142273
    private val fixLon = 7.4652789

    @Test
    fun markerStaysOnItsContentWhileAFollowReanchorIsPending() {
        // The fix path (setViewport + reengageFollow) re-anchors the PENDING target
        // while the frame on the surface is still the previous one. The marker must
        // keep riding the displayed frame instead of jumping onto the pending target
        // and snapping back when the re-anchored frame commits.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        assertEquals("the displayed frame is committed", 1, renderer.fullRenderCount)

        // reCenter snaps the display onto the fix, so the marker sits on the anchor
        // fraction of the committed frame.
        val before = renderer.markerScreenPosition(100, 100)
        assertEquals(
            "marker x rides the anchor of the displayed frame",
            VehicleAnchorPosition.BOTTOM_CENTER.fx * 100, before.first, 0.5
        )
        assertEquals(
            "marker y rides the anchor of the displayed frame",
            VehicleAnchorPosition.BOTTOM_CENTER.fy * 100, before.second, 0.5
        )

        // Next fix (~13.7 m on, 50 km/h): setViewport + reengageFollow re-anchor the
        // pending target — no render has committed it yet.
        renderer.setGpsMarker(fixLat + 0.000123, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now + 1000)
        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.0)
        renderer.reengageFollow()

        val after = renderer.markerScreenPosition(100, 100)
        assertEquals("no render may commit in the pending window", 1, renderer.fullRenderCount)
        assertEquals("marker x must not move before the re-anchor commits", before.first, after.first, 1e-9)
        assertEquals("marker y must not move before the re-anchor commits", before.second, after.second, 1e-9)
    }

    @Test
    fun followBlitServedForANonCenterAnchor() {
        // A follow-mode frame-center change inside the overrun region must be served
        // by a blit for every anchor preset. The offset is measured on the DISPLAYED
        // position: a frame center is not a point of the rendered bitmap, so passing
        // it adds the anchor displacement (bottom row: 0.4 x height = 40 px on a
        // 100 px surface, margin 10 px), which saturates the margin and turns every
        // GPS fix into a full native render.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        assertEquals(1, renderer.fullRenderCount)

        renderer.setGpsMarker(fixLat + 0.000123, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now + 1000)
        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.0)
        renderer.reengageFollow()
        renderer.renderFrame()

        assertEquals("a follow re-anchor must be blitted, not re-rendered", 1, renderer.fullRenderCount)
        assertTrue("the frame must have been served by a blit", renderer.blitCount >= 1)
    }

    @Test
    fun markerUsesTheDisplayedFrameRotation() {
        // A pending rotation must not move the marker before the frame carrying it is
        // committed: the overlay projects at the DISPLAYED frame's rotation, not the
        // pending one (bottom anchor = 40 px lever arm from the frame center).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0)
        renderer.reCenter()
        renderer.renderFrame()
        val before = renderer.markerScreenPosition(100, 100)

        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.5)
        val after = renderer.markerScreenPosition(100, 100)

        assertEquals("the pending rotation must not have committed", 1, renderer.fullRenderCount)
        assertEquals("marker x must not follow a pending rotation", before.first, after.first, 1e-9)
        assertEquals("marker y must not follow a pending rotation", before.second, after.second, 1e-9)
    }

    @Test
    fun committedFrameIsLabeledWithTheRenderedParameters() {
        // The native render runs OUTSIDE surfaceLock (so the extrapolation loop can
        // keep blitting), so the render target can be written while it is in flight:
        // a fix re-anchor, the loop's clamp branch, an auto-zoom commit. The frame
        // that becomes the DISPLAYED frame must be described by the parameters the
        // pixels were rendered with — the overlays, the blit offset and the
        // diagnostic are all derived from that label.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)

        renderer.setViewport(fixLat, fixLon, 12, 0.0)
        // Race the render: the pending target moves mid-render.
        client.onRender = {
            renderer.setViewport(fixLat + 0.004, fixLon + 0.004, 12, 0.0)
        }
        renderer.renderFrame()

        val rendered = client.renderCalls.last()
        assertEquals("the render was issued at the committed target", fixLat, rendered[0], 1e-9)
        assertEquals(fixLon, rendered[1], 1e-9)
        val frame = renderer.markerViewport()
        assertEquals(
            "the committed frame must carry the rendered center, not the pending one",
            rendered[0], frame.first, 1e-9
        )
        assertEquals(rendered[1], frame.second, 1e-9)
    }

    // --- Follow render target on the displayed position --------------------------
    // (spec: auto-smooth-follow — Display center extrapolation; auto/navigation-view —
    // Smooth follow-mode scrolling during navigation; change aa-follow-framing-and-zoom-parity, P1)

    @Test
    fun followReanchorUsesTheDisplayedPosition() {
        // The fix path (setViewport + reengageFollow) must anchor the follow render
        // target on the DISPLAYED (eased predicted) position, never on the raw fix:
        // anchoring on the fix lands the committed frame (display - fix) px away from
        // where the shown scene sat, so the map and the marker jump by that lead at
        // the commit and the next blit pulls them back (~1 Hz excursion).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()

        // One display second: the eased display moves ahead of the fix.
        renderer.extrapolationTick(now + 1000, 1.0)
        val display = renderer.markerPosition()
        assertTrue("the display must have advanced past the fix", display.first > fixLat)

        // The fix path, as the car screens run it.
        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.0)
        renderer.reengageFollow()

        val expected = anchorCenter(
            display.first, display.second,
            VehicleAnchorPosition.BOTTOM_CENTER.fx, VehicleAnchorPosition.BOTTOM_CENTER.fy,
            12.0, 100, 100, 240.0, 0.0
        )
        val target = renderer.viewportState.value
        assertEquals("follow target must be anchored on the display", expected.first, target.lat, 1e-9)
        assertEquals(expected.second, target.lon, 1e-9)
        assertTrue(
            "the target must NOT be the raw fix's anchor center",
            abs(target.lat - fixLat) > 1e-6
        )
        // The display itself is left untouched (no snap).
        assertEquals(display.first, renderer.markerPosition().first, 1e-12)
    }

    @Test
    fun followReanchorFallsBackToTheFixBeforeTheFirstDisplayFrame() {
        // No display position exists yet (no render, no tick): the raw fix stays the
        // anchor target.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0)
        renderer.setViewport(fixLat, fixLon, 12, 0.0)
        renderer.reengageFollow()

        val expected = anchorCenter(
            fixLat, fixLon,
            VehicleAnchorPosition.BOTTOM_CENTER.fx, VehicleAnchorPosition.BOTTOM_CENTER.fy,
            12.0, 100, 100, 240.0, 0.0
        )
        val target = renderer.viewportState.value
        assertEquals(expected.first, target.lat, 1e-9)
        assertEquals(expected.second, target.lon, 1e-9)
    }

    // --- Follow commit lands the display on the anchor ---------------------------
    // (spec: auto-smooth-follow — Display center extrapolation / Anchor-centered follow
    // framing; change aa-follow-framing-and-zoom-parity)

    @Test
    fun followCommitLandsTheDisplayOnTheAnchor() {
        // After a follow commit the display must sit exactly on the resolved anchor: the
        // next tick's blit offset has to be ~0 (the frame was anchored on the position the
        // display was at), otherwise the content jumps at every commit — the "map moved
        // and came back" excursion the user reported on device. Uses the real car surface
        // (1080x600) with a host bottom inset, so the resolved anchor is off-center.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setHostBottomInset(120)

        var now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()

        // One fix interval of ticks (the vehicle advances ~14 m).
        for (i in 1..10) {
            now += 90
            renderer.extrapolationTick(now, 0.09)
        }
        val advancePx = run {
            val dLat = (renderer.markerPosition().first - fixLat) * 111320.0
            kotlin.math.abs(dLat) / 0.955
        }
        assertTrue("the display must have advanced", advancePx > 5.0)

        // The next fix, as the car screens commit it: a new heading AND a new fractional
        // magnification (auto-zoom step) with a re-anchor.
        renderer.setGpsMarker(fixLat + 0.00013, fixLon, 5.0, 10.0, speedKmH = 52.0, timeMs = now)
        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            16, -0.1, 16.05
        )
        renderer.reengageFollow()
        renderer.renderFrame()

        // The very next tick: the display is (almost) where the frame was anchored, so
        // the offset that places it on the anchor must be ~0.
        renderer.extrapolationTick(now + 5, 0.005)
        val (ox, oy) = renderer.blitOffset()
        assertEquals("offset x after a commit (~0)", 0.0, ox, 3.0)
        assertEquals("offset y after a commit (~0)", 0.0, oy, 3.0)
    }

    @Test
    fun freshRenderPutsTheDisplayOnTheAnchor() {
        // A full render must land the displayed position on the resolved anchor exactly
        // like a blit does. Drawing a fresh frame unshifted is only correct while the
        // display still sits on the frame's own anchor position; the native render takes
        // 25-300 ms, during which the display advances — an unshifted draw then leaves the
        // whole scene (map AND marker) that advance away from the previous frame and the
        // next blit tick moves it back: the sub-second "map jumps up and comes back"
        // excursion measured on device (drawn `dy -55 -> -60` for an unchanged frame
        // center). Observed through the marker, which rides the same offset.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setHostBottomInset(120)

        var now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        // A render that is NOT served by a blit (an angle change forces a full render).
        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            16, -0.05, renderer.fractionalZoom()
        )
        renderer.reengageFollow()
        // The native render takes 25-300 ms: the display keeps advancing while it is in
        // flight (the extrapolation loop runs on another coroutine).
        val renderStart = now
        client.onRender = {
            now = renderStart + 1000
            renderer.extrapolationTick(now, 1.0)
        }
        renderer.renderFrame()

        val advancedM = kotlin.math.abs(renderer.markerPosition().first - fixLat) * 111320.0
        assertTrue(
            "precondition: the display must have advanced during the render (was " +
                "%.1f m)".format(advancedM),
            advancedM > 10.0
        )
        val (mx, my) = renderer.markerScreenPosition(1080, 600)
        val resolved = renderer.resolvedFollowAnchor()
        assertEquals("marker x on the resolved anchor after a render", resolved.fx * 1080, mx, 2.5)
        assertEquals("marker y on the resolved anchor after a render", resolved.fy * 600, my, 2.5)
    }

    // --- P3: zoom transition across display frames ---------------------------------
    // (spec: auto-speed-zoom — Smooth zoom transitions / Committed magnification is reached
    // across frames; change aa-follow-framing-and-zoom-parity)

    @Test
    fun zoomStepIsAppliedAcrossFrames() {
        // A committed magnification change must reach the eye ACROSS display frames: the
        // displayed magnification eases toward the target (applied as a scale of the overrun
        // blit about the follow anchor), instead of the whole map scaling in one frame.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setHostBottomInset(120)

        var now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        val frameMag = renderer.viewportState.value.zoomFraction

        // A small auto-zoom step lands in the pending target (as the screens commit it).
        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            16, 0.0, frameMag + 0.15
        )
        renderer.reengageFollow()
        now += 90
        renderer.extrapolationTick(now, 0.09)

        val shown = renderer.displayedMagnification()
        assertTrue(
            "displayed magnification must move toward the target (frame=$frameMag shown=$shown)",
            shown > frameMag + 0.01
        )
        assertTrue("and must not overshoot the target", shown < frameMag + 0.15)

        // The vehicle stays anchored while the scale moves.
        val resolved = renderer.resolvedFollowAnchor()
        val (mx, my) = renderer.markerScreenPosition(1080, 600)
        assertEquals("marker x during a zoom transition", resolved.fx * 1080, mx, 2.5)
        assertEquals("marker y during a zoom transition", resolved.fy * 600, my, 2.5)
    }

    @Test
    fun zoomStepBeyondTheBlitLimitFallsBackToAFullRender() {
        // A step larger than a scaled overrun blit can cover has no pixels to scale (the
        // buffer would no longer cover the surface): the blit is refused and a full native
        // render lands the committed magnification.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)
        renderer.setHostBottomInset(120)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        val frameMag = renderer.viewportState.value.zoomFraction
        val before = renderer.fullRenderCount

        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            16, 0.0, frameMag + AutoMapRenderer.ZOOM_BLIT_LIMIT + 0.25
        )
        renderer.reengageFollow()
        renderer.renderFrame()

        assertEquals(
            "a step the blit cannot cover must be rendered",
            before + 1, renderer.fullRenderCount
        )
    }

    @Test
    fun destinationPinProjectsAgainstTheDisplayedFrame() {
        // The pin is an overlay like the marker: it must project against the frame on
        // the surface, so a pending rotation cannot move it off the map content. The
        // pin's inner dot is the only drawCircle on the surface, so it is the pin's
        // observed screen position.
        val (surface, canvas) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.setFollowAnchor(VehicleAnchorPosition.BOTTOM_CENTER)

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.renderFrame()
        renderer.setDestinationMarker(fixLat + 0.0004, fixLon + 0.0004, "Ziel")

        val pinX = slot<Float>()
        val pinY = slot<Float>()
        every { canvas.drawCircle(capture(pinX), capture(pinY), any(), any()) } returns Unit

        // A display frame (no commit): dt = 0, so the display still sits on the
        // committed anchor and the blit offset is zero.
        renderer.extrapolationTick(now, 0.0)
        val before = pinX.captured.toDouble() to pinY.captured.toDouble()

        // A pending rotation, re-engaged like the car screens do — no commit.
        renderer.setViewport(renderer.viewportState.value.lat, renderer.viewportState.value.lon, 12, 0.5)
        renderer.reengageFollow()
        renderer.extrapolationTick(now, 0.0)

        assertEquals("the pin must be projected at the displayed rotation", before.first, pinX.captured.toDouble(), 1e-9)
        assertEquals("the pin must be projected at the displayed rotation", before.second, pinY.captured.toDouble(), 1e-9)
    }

    // --- aa-entry-zoom-animation: zoom transition walk -------------------------------
    // (spec: auto-speed-zoom — Auto-zoom entry transition starts from the displayed
    // magnification / Entry transition is bounded in time and render requests;
    // change aa-entry-zoom-animation)

    /**
     * A renderer with one frame on the surface at [mag] and the background loops
     * disabled, so the test drives the walk and the renders itself.
     */
    private fun rendererWithFrame(mag: Double = 13.0) {
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        renderer.setViewport(
            renderer.viewportState.value.lat, renderer.viewportState.value.lon,
            mag.toInt(), 0.0, mag
        )
        renderer.renderFrame()
    }

    /** One magnification request through the auto-zoom path (transition-eligible). */
    private fun requestAutoZoom(mag: Double, walkZoom: Boolean = true) {
        val vp = renderer.viewportState.value
        renderer.setViewport(vp.lat, vp.lon, mag.toInt(), vp.angle, mag, walkZoom = walkZoom)
    }

    /** Drive the walk to completion, rendering one frame per committed step. */
    private fun walkToCompletion(limit: Int = 64): List<Double> {
        val steps = mutableListOf<Double>()
        repeat(limit) {
            val stepped = renderer.advanceZoomWalk() ?: return steps
            steps += stepped
            renderer.renderFrame()
        }
        return steps
    }

    @Test
    fun farZoomCommitStartsAWalk() {
        // The reported defect: entering free driving snapped 13.0 -> 17.0 in ONE frame.
        // A request farther than the blit window must not be committed in one frame.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        assertEquals(
            "a far auto-zoom request must not be committed in one frame",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        val first = renderer.advanceZoomWalk()
        assertNotNull("the walk commits the first step", first)
        assertTrue(
            "the first step stays inside the blit window",
            abs(first!! - 13.0) <= AutoMapRenderer.ZOOM_BLIT_LIMIT + 1e-9
        )
    }

    @Test
    fun walkStepsStayInsideTheBlitWindow() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        val steps = walkToCompletion()
        assertTrue("a 4-level entry walks in several steps (got ${steps.size})", steps.size >= 16)
        var previous = 13.0
        steps.forEach { step ->
            assertTrue(
                "step $step must stay inside the blit window (from $previous)",
                abs(step - previous) <= AutoMapRenderer.ZOOM_BLIT_LIMIT + 1e-9
            )
            previous = step
        }
        assertTrue(
            "the sequence must be monotonic toward the target (spec: no overshoot), got $steps",
            steps.zipWithNext().all { (a, b) -> b >= a }
        )
    }

    @Test
    fun speedUnknownTargetWalksFromTheDisplayedMagnification() {
        // spec: auto-speed-zoom — "Speed unknown while a magnification is displayed": the target
        // is computed from the default speed of 20 km/h and the displayed magnification moves to
        // it across display frames, not in one frame. 20 km/h is the spec's DEFAULT value; the
        // car derivation has no seed of its own (`unknownSpeedCommitsNoAutoZoomTarget`), so the
        // value is fed explicitly here.
        rendererWithFrame(13.0)
        val target = SpeedZoomTable.compute(20.0)
        assertEquals("20 km/h is the slow-city level", 16.0, target, 1e-9)

        requestAutoZoom(target)

        assertEquals(
            "the target must not land in one frame while a magnification is displayed",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        val steps = walkToCompletion()
        assertTrue("the 3-level difference is walked (got ${steps.size})", steps.size >= 12)
        assertEquals("and it ends exactly on the target", 16.0, steps.last(), 0.0)
    }

    @Test
    fun followLoopKeepsTickingWhileTheWalkPlays() {
        // spec: auto-speed-zoom — Entry transition is bounded in time and render requests:
        // "the follow display loop SHALL keep running while the transition plays". The walk owns
        // its own loop, so a pending walk must neither stall the follow tick nor be stalled by
        // it.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame()
        requestAutoZoom(17.0)

        renderer.advanceZoomWalk()
        renderer.renderFrame()
        renderer.reengageFollow()
        val walkMag = renderer.viewportState.value.zoomFraction
        val (beforeLat, beforeLon) = renderer.markerPosition()

        // The follow loop ticks while the transition is pending.
        renderer.extrapolationTick(now + 200, 0.2)

        val (afterLat, afterLon) = renderer.markerPosition()
        assertTrue(
            "the follow display keeps gliding during the transition",
            afterLat != beforeLat || afterLon != beforeLon
        )
        assertEquals(
            "the follow tick commits no zoom step",
            walkMag, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertNotNull(
            "and the walk continues — it is not paced by the follow loop",
            renderer.advanceZoomWalk()
        )
    }

    @Test
    fun walkEndsOnAnExactTargetRender() {
        // The transition ends on a full native render at the EXACT requested value, not on an
        // accumulated arithmetic sum (spec: auto-speed-zoom — Entering free driving from a
        // browse magnification: "SHALL end on a frame rendered at exactly 17.0").
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        val rendersBefore = renderer.fullRenderCount

        val steps = walkToCompletion()

        assertEquals("the last step lands on the requested magnitude", 17.0, steps.last(), 0.0)
        assertEquals("and it is the committed one", 17.0, renderer.viewportState.value.zoomFraction, 0.0)
        assertEquals(
            "every step, the target included, was rendered",
            rendersBefore + steps.size, renderer.fullRenderCount
        )
    }

    @Test
    fun walkIsRenderSynchronous() {
        // A step is committed only once the previous step's frame has LANDED: otherwise the
        // walk outruns the renders and the committed value leads the displayed frame by more
        // than the blit window, which snaps the display.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        assertNotNull("first step", renderer.advanceZoomWalk())
        assertNull("no second step before the frame landed", renderer.advanceZoomWalk())
        renderer.renderFrame()
        assertNotNull("the walk continues once the frame landed", renderer.advanceZoomWalk())
    }

    @Test
    fun walkWithNoDisplayedFrameLandsDirectlyInsteadOfStepping() {
        // Cold start (no frame on the surface): there is nothing to transition from, so the
        // request lands directly — the spec's "jumps directly to the target instead of
        // smoothing from the default map zoom".
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)

        requestAutoZoom(17.0)

        assertEquals("no frame -> the request lands directly", 17.0, renderer.advanceZoomWalk() ?: Double.NaN, 0.0)
        assertEquals(17.0, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun noRenderRequestAfterSurfaceLoss() {
        // The walk advances only while the surface is usable (design D2): with the surface gone
        // the loop must neither lock it, nor commit a step, nor request a render — a lost surface
        // does not get to snap the map to the target behind the driver's back. (The overrun buffer
        // is dropped with the surface, so the walk's cold-start branch would land the far target
        // in ONE step if the gate were missing.)
        //
        // Deterministic by construction: the loops are OFF while the frame and the pending walk
        // are set up — no render is in flight, no step can be taken — and are switched ON only
        // after the surface is gone, which is exactly the window under test.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 50.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame() // one frame on the surface at 13.0
        requestAutoZoom(19.0)
        assertEquals(
            "the far request starts a walk, it does not land in one frame",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )

        renderer.onSurfaceDestroyed()
        val renders = renderer.fullRenderCount
        renderer.asyncLoopsEnabled = true // the walk loop now runs with the surface gone
        Thread.sleep(200)

        assertEquals(
            "the walk must not land the target without a surface",
            13.0, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertEquals("and no render is requested", renders, renderer.fullRenderCount)
        // This is the one walk case that runs with the background loops ENABLED: shut the
        // renderer down so its three loops do not outlive the test (the unit-test fork has a
        // small heap).
        renderer.shutdown()
    }

    @Test
    fun walkCompletesWhileParked() {
        // The extrapolation loop is movement-gated; the walk is not, otherwise entering free
        // driving at standstill would keep the old magnification until the vehicle moves.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 1080, 600)
        val now = System.currentTimeMillis()
        renderer.setGpsMarker(fixLat, fixLon, 0.0, 10.0, speedKmH = 0.0, timeMs = now)
        renderer.reCenter()
        requestAutoZoom(13.0, walkZoom = false)
        renderer.renderFrame()

        assertFalse(
            "the extrapolation loop is closed while the vehicle stands",
            renderer.extrapolationGateActive(now + 1000)
        )

        requestAutoZoom(17.0)
        val steps = walkToCompletion()
        assertTrue("the walk runs with the follow loop closed", steps.isNotEmpty())
        assertEquals(17.0, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun walkKeepsTheVehicleOnTheAnchor() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        renderer.reengageFollow()

        repeat(3) {
            renderer.advanceZoomWalk()
            renderer.renderFrame()
        }

        val resolved = renderer.resolvedFollowAnchor()
        val (mx, my) = renderer.markerScreenPosition(1080, 600)
        assertEquals("marker x during the walk", resolved.fx * 1080, mx, 2.5)
        assertEquals("marker y during the walk", resolved.fy * 600, my, 2.5)
    }

    @Test
    fun walkRetargetsWithoutPassingTheRequestedMagnification() {
        // Mid-transition requests (the auto-zoom convergence steps down while the display is
        // still walking up) must be taken from the current value and never passed.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        repeat(2) {
            renderer.advanceZoomWalk()
            renderer.renderFrame()
        }
        assertTrue(
            "the upward transition is in progress before the re-target",
            renderer.viewportState.value.zoomFraction < 17.0
        )

        requestAutoZoom(16.5)
        val rest = walkToCompletion()

        assertTrue(
            "the sequence must never pass the newest request",
            rest.all { it <= 16.5 + 1e-9 }
        )
        assertEquals("and it ends exactly on it", 16.5, renderer.viewportState.value.zoomFraction, 0.0)
    }

    @Test
    fun smallZoomDeltaNeedsNoExtraRender() {
        rendererWithFrame(15.0)
        val renders = renderer.fullRenderCount

        requestAutoZoom(15.2)

        assertEquals(
            "a delta inside the blit window commits directly",
            15.2, renderer.viewportState.value.zoomFraction, 1e-9
        )
        assertNull("and starts no walk", renderer.advanceZoomWalk())
        assertEquals(
            "and the transition initiates no full native render of its own",
            renders, renderer.fullRenderCount
        )
        renderer.renderFrame()
        assertEquals("the single commit frame is the only render", renders + 1, renderer.fullRenderCount)
    }

    @Test
    fun identicalRecommitKeepsTheWalk() {
        // The car screens re-commit the fraction they read back from the viewport state on
        // every fix; a fix with no new zoom target must not cancel a pending walk.
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)
        val committed = renderer.viewportState.value.zoomFraction

        requestAutoZoom(committed)

        assertNotNull("a re-commit must not cancel the walk", renderer.advanceZoomWalk())
    }

    @Test
    fun nearZoomRequestSupersedesTheWalk() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        requestAutoZoom(13.2, walkZoom = false)

        assertEquals("a near request commits directly", 13.2, renderer.viewportState.value.zoomFraction, 1e-9)
        assertNull("and clears the walk target", renderer.advanceZoomWalk())
    }

    @Test
    fun shutdownClearsTheWalk() {
        rendererWithFrame(13.0)
        requestAutoZoom(17.0)

        renderer.shutdown()
        val renders = renderer.fullRenderCount

        assertNull(renderer.advanceZoomWalk())
        assertEquals("no render is requested after shutdown", renders, renderer.fullRenderCount)
    }
}
