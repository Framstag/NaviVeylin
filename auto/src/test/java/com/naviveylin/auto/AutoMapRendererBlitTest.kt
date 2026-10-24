package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AutoMapRenderer]'s frame production: the overrun bitmap, a full render versus a blit,
 * invalidation, the blit offset a marker and a pane band ride, and the frame a display commit lands
 * on — one group of the cases that were a single 2 099-line `AutoMapRendererTest` until the file
 * crossed the `source-size` row's bound (item #300).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererBlitTest : AutoMapRendererTestSupport() {

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
}
