package com.naviveylin.ui.map

import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Follow framing math (spec: smooth-follow — Anchor-centered follow framing;
 * gps-location-marker — Marker projects against displayed bitmap viewport).
 *
 * Pins the contract that makes the whole follow pipeline consistent:
 *
 *  - the FRAME is rendered anchor-centered on its own position (render target
 *    = `anchorCenter(vehicle)`), so the vehicle sits at the anchor *inside* the
 *    bitmap and `viewport.center` stays the geo position at the screen center;
 *  - the DRAW offset is the prediction drift only (`displayOffsetPx`), which is
 *    therefore always inside the overrun margin — no uncovered strip and no
 *    content pushed off-screen (the pre-fix code subtracted the anchor a second
 *    time and saturated the clamp for every non-center preset);
 *  - the MARKER is projected against the anchor center of the DISPLAYED
 *    position, which is the one viewport that reproduces the content position
 *    of the displayed position (the anchor fraction) — projecting against the
 *    frame's own center would place the marker ahead of the content by the
 *    blit offset.
 *
 * Plain JUnit on purpose: no Android classes, no JNI (see AGENTS.md classloader rule).
 */
class FollowAnchorFramingTest {

    private val screenW = 1080
    private val screenH = 1920
    private val dpi = 320.0
    private val mag = 14.0

    /** Bitmap size of the overrun frame at the renderer's default 1.2x factor. */
    private val bitmapW = (screenW * 1.2).toInt()
    private val bitmapH = (screenH * 1.2).toInt()

    /** Overrun margin: how far the drawn frame may be shifted before a strip shows. */
    private val marginX = (bitmapW - screenW) / 2.0
    private val marginY = (bitmapH - screenH) / 2.0

    private val lat = 52.51
    private val lon = 13.40

    /** Shifted position ~[meters] north/east of the fix, for drift cases. */
    private fun offset(lat: Double, lon: Double, metersNorth: Double, metersEast: Double) =
        (lat + metersNorth / FollowPrediction.METERS_PER_DEG_LAT) to
            (lon + metersEast / (FollowPrediction.METERS_PER_DEG_LON * Math.cos(Math.toRadians(lat))))

    private fun drift(
        displayLat: Double, displayLon: Double,
        frameLat: Double, frameLon: Double,
        anchor: VehicleAnchorPosition,
        angle: Double = 0.0
    ) = driftResolved(displayLat, displayLon, frameLat, frameLon, anchor.fx, anchor.fy, angle)

    /** Drift for an anchor given as fractions (the visible-area resolved case). */
    private fun driftResolved(
        displayLat: Double, displayLon: Double,
        frameLat: Double, frameLon: Double,
        fx: Double, fy: Double,
        angle: Double = 0.0
    ) = FollowPrediction.displayOffsetPx(
        displayLat, displayLon, frameLat, frameLon, mag, angle,
        bitmapW, bitmapH, screenW, screenH, dpi, fx, fy
    )

    @Test
    fun `center anchor reproduces the pre-anchor framing`() {
        // The default preset must be bit-identical to the old center framing: the
        // render target is the vehicle itself and the drift is the plain offset.
        val (aLat, aLon) = anchorCenter(
            lat, lon, VehicleAnchorPosition.CENTER, mag, screenW, screenH, dpi
        )
        assertEquals(lat, aLat, 1e-12)
        assertEquals(lon, aLon, 1e-12)
    }

    @Test
    fun `every preset keeps the drawn frame inside the overrun margin`() {
        // Band regression: with the frame rendered anchor-centered on the
        // vehicle, the draw offset is the drift from the anchor. It must stay
        // inside the margin for every preset at a realistic prediction drift
        // (50 m ahead) — otherwise the surface background shows through.
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = 50.0, metersEast = 0.0)
        VehicleAnchorPosition.entries.forEach { anchor ->
            val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi)
            val off = drift(dispLat, dispLon, fLat, fLon, anchor)
            assertTrue(
                "preset ${anchor.id} drift $off leaves the margin",
                kotlin.math.abs(off.rawX) <= marginX && kotlin.math.abs(off.rawY) <= marginY
            )
            assertFalse("preset ${anchor.id} must not be clamped while aligned", off.clamped)
        }
    }

    @Test
    fun `aligned frame has zero draw offset for every preset`() {
        // The frame already contains the vehicle at the anchor pixel, so an
        // aligned display needs no shift at all. The pre-fix code shifted by
        // the anchor offset here (up to 0.4 * screen) — the reported band.
        VehicleAnchorPosition.entries.forEach { anchor ->
            val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi)
            val off = drift(lat, lon, fLat, fLon, anchor)
            assertEquals("preset ${anchor.id} x", 0.0, off.clampedX, 1e-6)
            assertEquals("preset ${anchor.id} y", 0.0, off.clampedY, 1e-6)
        }
    }

    @Test
    fun `vehicle projects to the active anchor for every preset and rotation`() {
        // Render-target contract under rotation: with the viewport centered on
        // anchorCenter(vehicle, anchor, angle), the vehicle projects to the
        // anchor fraction of the surface.
        val angles = listOf(0.0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, Math.PI)
        VehicleAnchorPosition.entries.forEach { anchor ->
            angles.forEach { angle ->
                val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi, angle)
                val vp = ProjectionUtils.viewport(fLat, fLon, mag, screenW, screenH, dpi, angle)
                val (x, y) = vp.geoToScreenRotated(lat, lon)
                assertEquals(
                    "preset ${anchor.id} angle $angle x",
                    anchor.fx * screenW, x, 1.0
                )
                assertEquals(
                    "preset ${anchor.id} angle $angle y",
                    anchor.fy * screenH, y, 1.0
                )
            }
        }
    }

    @Test
    fun `marker lands on the map content of the displayed position`() {
        // Marker projection: against the anchor center of the DISPLAYED
        // position the displayed position projects to the content position the
        // blit produces (the anchor fraction + the clamped drift remainder).
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = 30.0, metersEast = 12.0)
        VehicleAnchorPosition.entries.forEach { anchor ->
            val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi)
            val off = drift(dispLat, dispLon, fLat, fLon, anchor)

            // Frame projection of the displayed position, in bitmap pixels.
            val frameVp = ProjectionUtils.viewport(fLat, fLon, mag, bitmapW, bitmapH, dpi)
            val (bx, by) = frameVp.geoToScreenRotated(dispLat, dispLon)
            // Bitmap pixel -> surface pixel (drawFrontFrame: dx = center - offset).
            val contentX = screenW / 2.0 - off.clampedX + (bx - bitmapW / 2.0)
            val contentY = screenH / 2.0 - off.clampedY + (by - bitmapH / 2.0)

            // Marker projection: anchor center of the displayed position.
            val (mLat, mLon) = anchorCenter(dispLat, dispLon, anchor, mag, screenW, screenH, dpi)
            val markerVp = ProjectionUtils.viewport(mLat, mLon, mag, screenW, screenH, dpi)
            val (mx, my) = markerVp.geoToScreenRotated(dispLat, dispLon)

            assertEquals("preset ${anchor.id} marker x", contentX, mx, 1e-6)
            assertEquals("preset ${anchor.id} marker y", contentY, my, 1e-6)
            // ... which is the anchor fraction of the surface.
            assertEquals("preset ${anchor.id} anchor x", anchor.fx * screenW, mx, 1e-6)
            assertEquals("preset ${anchor.id} anchor y", anchor.fy * screenH, my, 1e-6)
        }
    }

    @Test
    fun `marker stays on the content while the frame lags the prediction`() {
        // The lagging-frame case is where the pre-fix marker left the road: the
        // drift the blit applies must be exactly the term the marker projection
        // absorbs, for a drift right at the margin.
        val anchor = VehicleAnchorPosition.BOTTOM_CENTER
        // ~90% of the overrun margin in metres at this magnification/dpi and
        // latitude (Mercator shrinks ground metres per pixel by cos(lat)).
        val metersPerPixel = ProjectionUtils.EARTH_RADIUS /
            ProjectionUtils.computeScale(mag, screenW.toDouble(), dpi).scale *
            Math.cos(Math.toRadians(lat))
        val marginMeters = 0.9 * marginY * metersPerPixel
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = marginMeters, metersEast = 0.0)

        val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi)
        val off = drift(dispLat, dispLon, fLat, fLon, anchor)
        assertFalse("drift at the margin boundary must still be unclamped", off.clamped)

        val frameVp = ProjectionUtils.viewport(fLat, fLon, mag, bitmapW, bitmapH, dpi)
        val (bx, by) = frameVp.geoToScreenRotated(dispLat, dispLon)
        val contentX = screenW / 2.0 - off.clampedX + (bx - bitmapW / 2.0)
        val contentY = screenH / 2.0 - off.clampedY + (by - bitmapH / 2.0)

        val (mLat, mLon) = anchorCenter(dispLat, dispLon, anchor, mag, screenW, screenH, dpi)
        val (mx, my) = ProjectionUtils
            .viewport(mLat, mLon, mag, screenW, screenH, dpi)
            .geoToScreenRotated(dispLat, dispLon)

        assertEquals(contentX, mx, 1e-6)
        assertEquals(contentY, my, 1e-6)
    }

    @Test
    fun `resolved anchor keeps the marker on the content with overlays`() {
        // Phone navigation geometry: turn card 0.19H, routing status 0.18H, widget
        // column 0.07W. The bottom-center preset resolves into the visible area, the
        // frame is rendered anchor-centered on THAT fraction, and the marker (which
        // the screen projects against anchorCenter(display, resolved)) lands on the
        // map content — still inside the overrun margin, so no strip is uncovered.
        val anchor = VehicleAnchorPosition.BOTTOM_CENTER
        val resolved = com.naviveylin.core.resolveAnchorFraction(
            anchor, 0, (screenH * 0.19).toInt(), (screenW * 0.07).toInt(), (screenH * 0.18).toInt(),
            screenW, screenH
        )
        assertTrue("resolved fy must move up into the visible area", resolved.fy < anchor.fy)

        val (fLat, fLon) = anchorCenter(lat, lon, resolved.fx, resolved.fy, mag, screenW, screenH, dpi)
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = 40.0, metersEast = 15.0)
        val off = driftResolved(dispLat, dispLon, fLat, fLon, resolved.fx, resolved.fy)
        assertFalse("drift must stay inside the margin", off.clamped)

        // Content position of the displayed point on the surface.
        val frameVp = ProjectionUtils.viewport(fLat, fLon, mag, bitmapW, bitmapH, dpi)
        val (bx, by) = frameVp.geoToScreenRotated(dispLat, dispLon)
        val contentX = screenW / 2.0 - off.clampedX + (bx - bitmapW / 2.0)
        val contentY = screenH / 2.0 - off.clampedY + (by - bitmapH / 2.0)

        // Marker projection used by the phone: the RESOLVED fraction of the display.
        val (mLat, mLon) = anchorCenter(dispLat, dispLon, resolved.fx, resolved.fy, mag, screenW, screenH, dpi)
        val (mx, my) = ProjectionUtils.viewport(mLat, mLon, mag, screenW, screenH, dpi)
            .geoToScreenRotated(dispLat, dispLon)

        assertEquals("marker x", contentX, mx, 1e-6)
        assertEquals("marker y", contentY, my, 1e-6)
        assertEquals("marker at the resolved anchor x", resolved.fx * screenW, mx, 1e-6)
        assertEquals("marker at the resolved anchor y", resolved.fy * screenH, my, 1e-6)
        // And the resolved anchor is inside the visible map area (below the turn card).
        assertTrue(my > screenH * 0.19 - 1e-6)
        assertTrue(my < screenH * 0.82 + 1e-6)
    }

    @Test
    fun `projecting the marker against the frame center would leave the content`() {
        // Documents the defect this change removes: the frame's own center only
        // reproduces the content when the frame is not lagging, so the marker
        // must not use it (kept as a guard against re-introducing it).
        val anchor = VehicleAnchorPosition.BOTTOM_CENTER
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = 0.0, metersEast = 200.0)
        val (fLat, fLon) = anchorCenter(lat, lon, anchor, mag, screenW, screenH, dpi)
        val off = drift(dispLat, dispLon, fLat, fLon, anchor)

        val (wrongX, wrongY) = ProjectionUtils
            .viewport(fLat, fLon, mag, screenW, screenH, dpi)
            .geoToScreenRotated(dispLat, dispLon)
        val (rightX, rightY) = ProjectionUtils
            .viewport(
                anchorCenter(dispLat, dispLon, anchor, mag, screenW, screenH, dpi).first,
                anchorCenter(dispLat, dispLon, anchor, mag, screenW, screenH, dpi).second,
                mag, screenW, screenH, dpi
            )
            .geoToScreenRotated(dispLat, dispLon)
        assertTrue(
            "frame-center projection must differ from the content position by the drift",
            kotlin.math.abs(wrongX - rightX) > 1.0 || kotlin.math.abs(wrongY - rightY) > 1.0
        )
        assertEquals(kotlin.math.abs(off.rawY), kotlin.math.abs(wrongY - rightY), 1.0)
    }

    @Test
    fun `blit against the raw preset while frame and marker use the resolved anchor leaves the marker off the content`() {
        // Regression: the production call site previously passed the RAW preset to
        // displayOffsetPx while the render target (followRenderTarget) and the marker
        // projection used the RESOLVED fraction (spec: smooth-follow — Single
        // resolved anchor across render, blit and marker). The frame is rendered
        // anchor-centered on the resolved fraction, so the raw-anchor blit places the
        // displayed content at the raw fraction while the marker draws at the resolved
        // one: the marker rides ahead of the road by the anchor delta, and the blit is
        // permanently outside the overrun margin (500 ms render churn).
        val anchor = VehicleAnchorPosition.BOTTOM_CENTER
        val resolved = com.naviveylin.core.resolveAnchorFraction(
            anchor, 0, (screenH * 0.19).toInt(), (screenW * 0.07).toInt(), (screenH * 0.18).toInt(),
            screenW, screenH
        )
        assertTrue("resolved fy must move up into the visible area", resolved.fy < anchor.fy)

        // Frame rendered for the fix, anchor-centered on the RESOLVED fraction (VM
        // followRenderTarget); display drifts within the margin (spec scenario).
        val (fLat, fLon) = anchorCenter(lat, lon, resolved.fx, resolved.fy, mag, screenW, screenH, dpi)
        val (dispLat, dispLon) = offset(lat, lon, metersNorth = 40.0, metersEast = 15.0)

        // The buggy blit: anchor args = raw preset (what the screen passed before).
        val offRaw = drift(dispLat, dispLon, fLat, fLon, anchor)
        val offResolved = driftResolved(dispLat, dispLon, fLat, fLon, resolved.fx, resolved.fy)
        assertTrue("raw-anchor blit sits outside the margin (render churn)", offRaw.clamped)
        assertFalse("resolved-anchor blit stays inside the margin", offResolved.clamped)

        // Content position of the displayed point under the raw-anchor (buggy) blit.
        val frameVp = ProjectionUtils.viewport(fLat, fLon, mag, bitmapW, bitmapH, dpi)
        val (bx, by) = frameVp.geoToScreenRotated(dispLat, dispLon)
        val contentX = screenW / 2.0 - offRaw.clampedX + (bx - bitmapW / 2.0)
        val contentY = screenH / 2.0 - offRaw.clampedY + (by - bitmapH / 2.0)

        // Marker projection used by the phone: the RESOLVED fraction of the display.
        val (mLat, mLon) = anchorCenter(dispLat, dispLon, resolved.fx, resolved.fy, mag, screenW, screenH, dpi)
        val (mx, my) = ProjectionUtils.viewport(mLat, mLon, mag, screenW, screenH, dpi)
            .geoToScreenRotated(dispLat, dispLon)
        assertEquals("marker sits at the resolved anchor x", resolved.fx * screenW, mx, 1e-6)
        assertEquals("marker sits at the resolved anchor y", resolved.fy * screenH, my, 1e-6)

        // The marker is displaced from its own map content by the anchor delta (the
        // reported "vehicle ahead of the track in the driving direction"): on a
        // y-down screen the resolved anchor lies above the raw anchor, so the marker
        // ends up ABOVE the content it represents. Compare against the aligned blit:
        // with the resolved anchor everywhere the two coincide.
        val contentXAligned = screenW / 2.0 - offResolved.clampedX + (bx - bitmapW / 2.0)
        val contentYAligned = screenH / 2.0 - offResolved.clampedY + (by - bitmapH / 2.0)
        assertEquals("aligned blit puts the content on the marker x", mx, contentXAligned, 1e-6)
        assertEquals("aligned blit puts the content on the marker y", my, contentYAligned, 1e-6)
        assertTrue(
            "raw-anchor blit displaces the content from the marker by the anchor delta",
            kotlin.math.abs(contentX - mx) + kotlin.math.abs(contentY - my) > 50.0
        )
    }
}
