package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the shared fit-verification seam (spec: fav-auto-zoom — Bounding box
 * zoom calculation; poi-search — Details via single click / Embedded result map
 * fit never clips a result).
 *
 * The cases pin the *invariants* a fit must satisfy, not the arithmetic that
 * happens to produce them: whatever magnification comes back, the projected bbox
 * must be inside the visible band around the camera that will actually be used —
 * and each case first asserts its own **premise**, that the unverified
 * `computeAreaZoom` result really does leave the bbox outside. Without that
 * premise a case could pass vacuously against a fit that never needed stepping
 * out (the shape `TODO.md` §111 records for a geometry test that proved nothing
 * about the call site).
 *
 * The three ways a fit can clip have their own premise:
 *  1. whole-level rounding moving the exact fit *up* to the next level
 *     (2^0.5 ≈ 1.41x more content than the 80 % target — past the viewport);
 *  2. an **off-centre camera** (the favourite coordinate / the POI is not the
 *     bbox midpoint), which clips even at an exact fit;
 *  3. a **rotated** viewport, which needs a larger screen hull than north-up.
 *
 * Canvas 400x800 px at 160 dpi (Robolectric mdpi) around Dortmund, so the
 * formula's inputs are the ones the production fits use. Runs under Robolectric
 * with the default sandbox: `verifiedAreaFit` calls into `MapCanvasViewModel`'s
 * companion, which is an Android `ViewModel` (AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class FitVerificationTest {

    private val dpi = 160.0
    private val width = 400
    private val height = 800
    private val lat = 51.5136
    private val lon = 7.4653

    /** The documented area-favorites floor (`MapCanvasViewModel.MIN_AREA_ZOOM`, private). */
    private val areaFloor = 14.0

    private val minMag = MapCanvasViewModel.MIN_MAG

    /** A bbox spanning [latMeters] x [lonMeters] ground metres, centred on ([centerLat], [centerLon]). */
    private fun bboxMeters(centerLat: Double, centerLon: Double, latMeters: Double, lonMeters: Double): DoubleArray {
        val dLat = latMeters / METERS_PER_DEG_LAT
        val dLon = lonMeters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(centerLat)))
        return doubleArrayOf(centerLat - dLat / 2, centerLat + dLat / 2, centerLon - dLon / 2, centerLon + dLon / 2)
    }

    private fun rawFit(bbox: DoubleArray, minZoom: Double = areaFloor, visibleHeight: Int = height): Double =
        MapCanvasViewModel.computeAreaZoom(bbox, width, visibleHeight, minZoom, dpi)

    @Test
    fun roundedUpFitIsSteppedOutUntilTheBboxFits() {
        // 195 m square in ground metres: the exact fit lands ~16.55, which rounds
        // UP to 17 — the fitted content then needs ~437 px of the 400 px canvas.
        val bbox = bboxMeters(lat, lon, 195.0, 195.0)
        val raw = rawFit(bbox)

        assertFalse(
            "premise: the rounded fit must actually leave the bbox outside",
            fitsVisibleArea(bbox, lat, lon, raw, width, height, dpi)
        )

        val verified = verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor)

        assertTrue(
            "the applied magnification must keep the whole bbox visible",
            fitsVisibleArea(bbox, lat, lon, verified, width, height, dpi)
        )
        assertTrue("verification only ever steps out, never in", verified < raw)
    }

    @Test
    fun alreadyFittingFitIsReturnedUnchanged() {
        // 232 m square: the exact fit is ~16.3, which rounds DOWN to 16 — the
        // content is smaller than the 80 % target and already fits.
        val bbox = bboxMeters(lat, lon, 232.0, 232.0)
        val raw = rawFit(bbox)

        assertTrue(
            "premise: this fit already satisfies the band",
            fitsVisibleArea(bbox, lat, lon, raw, width, height, dpi)
        )

        assertEquals(raw, verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor), 0.0)
    }

    @Test
    fun offCentreCameraIsSteppedOutUntilTheBboxFits() {
        // Same bbox, but the camera sits on its south-west corner — the shape the
        // area-favorite fit has (the camera is the favourite coordinate, not the
        // bbox midpoint), which clips at an exact fit too.
        val bbox = bboxMeters(lat, lon, 195.0, 195.0)
        val cameraLat = bbox[0]
        val cameraLon = bbox[2]
        val raw = rawFit(bbox)

        assertFalse(
            "premise: an off-centre camera clips at this fit",
            fitsVisibleArea(bbox, cameraLat, cameraLon, raw, width, height, dpi)
        )

        val verified = verifiedAreaFit(bbox, cameraLat, cameraLon, width, height, dpi, areaFloor)

        assertTrue(fitsVisibleArea(bbox, cameraLat, cameraLon, verified, width, height, dpi))
        assertTrue("an off-centre camera needs more than the rounding step", verified < raw)
    }

    @Test
    fun rotatedViewportIsSteppedOutUntilTheBboxFits() {
        // 285 m square: fits north-up at the rounded level, and clips once the
        // viewport is rotated 45 degrees (the square's screen hull grows by 2^0.5).
        val bbox = bboxMeters(lat, lon, 285.0, 285.0)
        val raw = rawFit(bbox)
        val angle = Math.toRadians(45.0)

        assertTrue(
            "premise: this fit passes north-up",
            fitsVisibleArea(bbox, lat, lon, raw, width, height, dpi)
        )
        assertFalse(
            "premise: the same fit clips on a rotated viewport",
            fitsVisibleArea(bbox, lat, lon, raw, width, height, dpi, angleRad = angle)
        )

        val verified = verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor, angleRad = angle)

        assertTrue(fitsVisibleArea(bbox, lat, lon, verified, width, height, dpi, angleRad = angle))
        assertTrue(verified < raw)
    }

    @Test
    fun fitStopsAtTheFloorWhenEvenTheFloorDoesNotFit() {
        // A 40x60 degree bbox can never fit at the area-favorites floor, and the
        // floor is where stepping stops — callers must not read that as verified.
        val bbox = doubleArrayOf(lat - 20.0, lat + 20.0, lon - 30.0, lon + 30.0)

        val verified = verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor)

        assertEquals(areaFloor, verified, 0.0)
        assertFalse(
            "a floor-reached fit is not a fit that shows the extent",
            fitsVisibleArea(bbox, lat, lon, verified, width, height, dpi)
        )
    }

    @Test
    fun fitReachesBelowTheAreaFloorWhenTheCallerPassesTheRenderMinimum() {
        // The POI fit's floor (option B2): the same bbox that is stuck at 14 for
        // the area-favorite fit can be shown in full at the render-stability floor.
        val bbox = doubleArrayOf(lat - 0.4, lat + 0.4, lon - 0.4, lon + 0.4)

        val atAreaFloor = verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor)
        val atRenderMinimum = verifiedAreaFit(bbox, lat, lon, width, height, dpi, minMag)

        assertEquals(areaFloor, atAreaFloor, 0.0)
        assertFalse(fitsVisibleArea(bbox, lat, lon, atAreaFloor, width, height, dpi))
        assertTrue("the POI fit must be able to go below the area floor", atRenderMinimum < areaFloor)
        assertTrue(fitsVisibleArea(bbox, lat, lon, atRenderMinimum, width, height, dpi))
    }

    @Test
    fun coveredBottomIsRespected() {
        // The route panel covers the lower canvas; the fit is computed against
        // the visible height and verified against the band above the sheet.
        val bbox = bboxMeters(lat, lon, 232.0, 232.0)
        val covered = 700
        val fullCanvasFit = rawFit(bbox)

        assertFalse(
            "premise: the full-canvas fit lands inside the covered area",
            fitsVisibleArea(bbox, lat, lon, fullCanvasFit, width, height, dpi, coveredPx = covered)
        )

        val verified = verifiedAreaFit(bbox, lat, lon, width, height, dpi, areaFloor, coveredPx = covered)

        assertTrue(
            "the bbox must stay in the band above the sheet",
            fitsVisibleArea(bbox, lat, lon, verified, width, height, dpi, coveredPx = covered)
        )
        assertTrue("covering the canvas can only shrink the fit", verified <= fullCanvasFit)
    }

    @Test
    fun unknownCameraOrCanvasIsNotAFit() {
        val bbox = bboxMeters(lat, lon, 232.0, 232.0)

        assertFalse("no canvas, nothing can be inside it", fitsVisibleArea(bbox, lat, lon, 16.0, 0, 800, dpi))
        assertFalse(fitsVisibleArea(bbox, Double.NaN, lon, 16.0, width, height, dpi))
        assertFalse(fitsVisibleArea(bbox, lat, Double.NaN, 16.0, width, height, dpi))
    }

    private companion object {
        const val METERS_PER_DEG_LAT = 111320.0
    }
}
