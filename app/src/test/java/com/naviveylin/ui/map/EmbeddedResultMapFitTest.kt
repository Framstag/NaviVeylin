package com.naviveylin.ui.map

import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.ProjectionUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the embedded POI result map's fit (spec: poi-search — Embedded result
 * map fit never clips a result: Every result stays inside the embedded map /
 * Current position stays inside the embedded map).
 *
 * The map is north-locked (`MiniMap` renders at angle 0) and centered on the
 * search center, which sits at the **edge** of the result extent rather than its
 * midpoint — the off-centre camera shape that `computeAreaZoom`'s 80 %-around-the-
 * midpoint solve does not serve. Each case first asserts its premise against the
 * unverified fit, so it cannot pass vacuously.
 *
 * The premise mirrors the function's documented 30 % margin to rebuild the bbox it
 * pads; if that margin ever changes, the premise fails loudly instead of going
 * quietly vacuous.
 *
 * Robolectric with the default sandbox: the fit routes through
 * `verifiedAreaFit`, which reaches `MapCanvasViewModel`'s companion (AGENTS.md
 * classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedResultMapFitTest {

    private val mapW = 400
    private val mapH = 240
    private val dpi = 160.0

    private val centerLat = 51.5136
    private val centerLon = 7.4653

    @Test
    fun everyResultStaysInsideTheEmbeddedMap() {
        // One result 195 m north-east of the center: the search center is the
        // south-west corner of the result extent, so the padded bbox extends
        // mostly to the north-east of the camera.
        val resultLat = centerLat + 195.0 / METERS_PER_DEG_LAT
        val resultLon = centerLon + 195.0 / metersPerDegLon()
        val result = poi(resultLat, resultLon)

        val bbox = paddedResultBounds(centerLat, centerLon, listOf(result), currentPosition = null)
        val unverified = MapCanvasViewModel.computeAreaZoom(
            bbox, mapW, mapH, MapCanvasViewModel.MIN_MAG, dpi
        )
        assertFalse(
            "premise: the unverified fit puts a result outside the mini map",
            fitsVisibleArea(bbox, centerLat, centerLon, unverified, mapW, mapH, dpi)
        )

        val mag = poiFitMagnification(listOf(result), centerLat, centerLon, null, 500.0, mapW, mapH, dpi)

        assertTrue("the result must be inside the mini map", isInside(resultLat, resultLon, mag))
        assertTrue(
            "the whole padded extent must be inside the mini map",
            fitsVisibleArea(bbox, centerLat, centerLon, mag, mapW, mapH, dpi)
        )
        assertTrue("the fit may only step out, never in", mag < unverified)
    }

    @Test
    fun currentPositionStaysInsideTheEmbeddedMap() {
        // Results to the north-east, the fix to the south-west: both ends of the
        // extent must be visible, not just the results.
        val resultLat = centerLat + 150.0 / METERS_PER_DEG_LAT
        val resultLon = centerLon + 150.0 / metersPerDegLon()
        val fixLat = centerLat - 400.0 / METERS_PER_DEG_LAT
        val fixLon = centerLon - 400.0 / metersPerDegLon()
        val result = poi(resultLat, resultLon)

        val bbox = paddedResultBounds(centerLat, centerLon, listOf(result), currentPosition = fixLat to fixLon)
        val unverified = MapCanvasViewModel.computeAreaZoom(
            bbox, mapW, mapH, MapCanvasViewModel.MIN_MAG, dpi
        )
        assertFalse(
            "premise: the unverified fit puts the fix outside the mini map",
            fitsVisibleArea(bbox, centerLat, centerLon, unverified, mapW, mapH, dpi)
        )

        val mag = poiFitMagnification(
            listOf(result), centerLat, centerLon, fixLat to fixLon, 500.0, mapW, mapH, dpi
        )

        assertTrue("the current position must be inside the mini map", isInside(fixLat, fixLon, mag))
        assertTrue("the result must be inside the mini map", isInside(resultLat, resultLon, mag))
        // The fit verifies its padded extent, not just the two points: asserting the
        // bbox is what makes this case falsify when the verification is removed (it
        // passed against the unverified fit until this assertion was added).
        assertTrue(
            "the whole padded extent must be inside the mini map",
            fitsVisibleArea(bbox, centerLat, centerLon, mag, mapW, mapH, dpi)
        )
    }

    @Test
    fun noResultsFallsBackToTheRadiusAndStillFits() {
        // No results and everything at one point: the radius bbox is fitted, still
        // verified around the search center (which is its midpoint here, but the
        // rounding can clip just the same).
        val mag = poiFitMagnification(emptyList(), centerLat, centerLon, null, 500.0, mapW, mapH, dpi)

        assertTrue("a fit must have been produced", mag >= MapCanvasViewModel.MIN_MAG)
        assertTrue(
            "the radius extent must be inside the mini map",
            fitsVisibleArea(
                radiusBounds(500.0), centerLat, centerLon, mag, mapW, mapH, dpi
            )
        )
    }

    @Test
    fun unknownCanvasOrCenterFallsBackToTheRenderMinimum() {
        assertTrue(poiFitMagnification(emptyList(), centerLat, centerLon, null, 500.0, 0, mapH, dpi) == MapCanvasViewModel.MIN_MAG)
        assertTrue(poiFitMagnification(emptyList(), Double.NaN, centerLon, null, 500.0, mapW, mapH, dpi) == MapCanvasViewModel.MIN_MAG)
    }

    private fun poi(lat: Double, lon: Double): PoiEntry = PoiEntry().apply {
        label = "Result"
        this.lat = lat
        this.lon = lon
        distance = 0.0
    }

    private fun metersPerDegLon(): Double =
        METERS_PER_DEG_LAT * Math.cos(Math.toRadians(centerLat))

    /** Mirrors the fit's documented 30 % margin over the center, results and fix. */
    private fun paddedResultBounds(
        centerLat: Double,
        centerLon: Double,
        results: List<PoiEntry>,
        currentPosition: Pair<Double, Double>?
    ): DoubleArray {
        var minLat = centerLat
        var maxLat = centerLat
        var minLon = centerLon
        var maxLon = centerLon
        fun extend(lat: Double, lon: Double) {
            minLat = minOf(minLat, lat)
            maxLat = maxOf(maxLat, lat)
            minLon = minOf(minLon, lon)
            maxLon = maxOf(maxLon, lon)
        }
        currentPosition?.let { extend(it.first, it.second) }
        results.forEach { extend(it.lat, it.lon) }
        val dLat = maxLat - minLat
        val dLon = maxLon - minLon
        return doubleArrayOf(
            minLat - dLat * 0.3, maxLat + dLat * 0.3,
            minLon - dLon * 0.3, maxLon + dLon * 0.3
        )
    }

    /** Mirrors the fit's radius fallback bbox. */
    private fun radiusBounds(radiusMeters: Double): DoubleArray {
        val dLat = radiusMeters / METERS_PER_DEG_LAT
        val dLon = radiusMeters / metersPerDegLon()
        return doubleArrayOf(centerLat - dLat, centerLat + dLat, centerLon - dLon, centerLon + dLon)
    }

    private fun isInside(lat: Double, lon: Double, mag: Double): Boolean {
        val vp = ProjectionUtils.viewport(centerLat, centerLon, mag, mapW, mapH, dpi, 0.0)
        val (x, y) = vp.geoToScreenRotated(lat, lon)
        return x >= 0.0 && x <= mapW.toDouble() && y >= 0.0 && y <= mapH.toDouble()
    }

    private companion object {
        const val METERS_PER_DEG_LAT = 111320.0
    }
}
