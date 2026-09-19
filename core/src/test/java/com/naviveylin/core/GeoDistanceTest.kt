package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared haversine implementation (task 2.1: one source of
 * truth for distances, used both for display and for the search ranking).
 */
class GeoDistanceTest {

    @Test
    fun `same point is zero meters`() {
        assertEquals(0.0, haversineDistanceMeters(51.5136, 7.4653, 51.5136, 7.4653), 0.001)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val meters = haversineDistanceMeters(51.0, 7.0, 52.0, 7.0)
        assertEquals(111_195.0, meters, 200.0)
    }

    @Test
    fun `distance is symmetric`() {
        val there = haversineDistanceMeters(51.5136, 7.4653, 51.2601, 7.4641)
        val back = haversineDistanceMeters(51.2601, 7.4641, 51.5136, 7.4653)
        assertEquals(there, back, 0.001)
    }

    @Test
    fun `nan coordinate yields infinity so callers can treat it as unmeasurable`() {
        assertTrue(haversineDistanceMeters(Double.NaN, 7.0, 51.0, 7.0).isInfinite())
    }
}
