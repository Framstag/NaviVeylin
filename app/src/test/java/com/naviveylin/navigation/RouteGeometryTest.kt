package com.naviveylin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [distanceToPolyline] (spec: reroute-trigger — Distance-based fast path).
 *
 * Route used: a 0.01° east-west segment at latitude 52.0 (≈ 685m long).
 * 1° latitude ≈ 111,320m; 1° longitude at 52° ≈ 68,540m.
 */
class RouteGeometryTest {

    private val routeLat = doubleArrayOf(52.0, 52.0)
    private val routeLon = doubleArrayOf(13.0, 13.01)

    @Test
    fun pointOnSegmentIsZero() {
        // Midpoint of the segment, exactly on the route
        val d = distanceToPolyline(52.0, 13.005, routeLat, routeLon)
        assertEquals(0.0, d, 1.0)
    }

    @Test
    fun perpendicularOffsetMatchesDistance() {
        // 0.0009° north of the segment ≈ 100m
        val d = distanceToPolyline(52.0009, 13.005, routeLat, routeLon)
        assertEquals(100.2, d, 2.0)
    }

    @Test
    fun pointBeyondRouteEndsUsesEndpoint() {
        // 0.01° east of the eastern endpoint ≈ 685m
        val d = distanceToPolyline(52.0, 13.02, routeLat, routeLon)
        assertEquals(685.4, d, 5.0)
    }

    @Test
    fun pointBeforeRouteStartUsesStartpoint() {
        // 0.01° west of the western endpoint ≈ 685m
        val d = distanceToPolyline(52.0, 12.99, routeLat, routeLon)
        assertEquals(685.4, d, 5.0)
    }

    @Test
    fun emptyPolylineIsInfinite() {
        assertTrue(distanceToPolyline(52.0, 13.0, DoubleArray(0), DoubleArray(0)).isInfinite())
    }

    @Test
    fun singlePointPolylineIsInfinite() {
        assertTrue(distanceToPolyline(52.0, 13.0, doubleArrayOf(52.0), doubleArrayOf(13.0)).isInfinite())
    }

    @Test
    fun mismatchedArraySizesIsInfinite() {
        assertTrue(distanceToPolyline(52.0, 13.0, doubleArrayOf(52.0, 52.0), doubleArrayOf(13.0)).isInfinite())
    }

    @Test
    fun multiSegmentRouteFindsNearestSegment() {
        // L-shaped route: east then north. Query near the north leg.
        val lats = doubleArrayOf(52.0, 52.0, 52.01)
        val lons = doubleArrayOf(13.0, 13.01, 13.01)
        // 0.0009° west of the north leg ≈ 0.0009 * 68540 ≈ 61.7m
        val d = distanceToPolyline(52.005, 13.0091, lats, lons)
        assertEquals(61.7, d, 2.0)
    }
}
