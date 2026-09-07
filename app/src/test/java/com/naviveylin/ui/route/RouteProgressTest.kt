package com.naviveylin.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the route progress helpers
 * (spec: route-summary-dialog — "Progress values clamped to valid range").
 */
class RouteProgressTest {

    // --- routeProgressPercent ---

    @Test
    fun `distance percent is zero at start`() {
        assertEquals(0, routeProgressPercent(total = 100.0, remaining = 100.0))
    }

    @Test
    fun `distance percent is 100 at destination`() {
        assertEquals(100, routeProgressPercent(total = 100.0, remaining = 0.0))
    }

    @Test
    fun `distance percent reflects traveled fraction`() {
        assertEquals(42, routeProgressPercent(total = 100.0, remaining = 58.0))
        assertEquals(50, routeProgressPercent(total = 10.0, remaining = 5.0))
    }

    @Test
    fun `distance percent clamps above 100`() {
        assertEquals(100, routeProgressPercent(total = 100.0, remaining = -50.0))
    }

    @Test
    fun `distance percent clamps below 0`() {
        assertEquals(0, routeProgressPercent(total = 100.0, remaining = 150.0))
    }

    @Test
    fun `distance percent is 0 for invalid total`() {
        assertEquals(0, routeProgressPercent(total = 0.0, remaining = 0.0))
        assertEquals(0, routeProgressPercent(total = -5.0, remaining = 0.0))
    }

    // --- elapsedTimePercent ---

    @Test
    fun `time percent is 0 at start`() {
        assertEquals(0, elapsedTimePercent(start = 1_000L, eta = 2_000L, now = 1_000L))
    }

    @Test
    fun `time percent is 100 at arrival`() {
        assertEquals(100, elapsedTimePercent(start = 1_000L, eta = 2_000L, now = 2_000L))
    }

    @Test
    fun `time percent reflects elapsed fraction`() {
        assertEquals(50, elapsedTimePercent(start = 1_000L, eta = 2_000L, now = 1_500L))
    }

    @Test
    fun `time percent clamps above 100`() {
        assertEquals(100, elapsedTimePercent(start = 1_000L, eta = 2_000L, now = 3_000L))
    }

    @Test
    fun `time percent clamps below 0`() {
        assertEquals(0, elapsedTimePercent(start = 1_000L, eta = 2_000L, now = 500L))
    }

    @Test
    fun `time percent is 0 for invalid inputs`() {
        assertEquals(0, elapsedTimePercent(start = 0L, eta = 2_000L, now = 1_500L))
        assertEquals(0, elapsedTimePercent(start = 1_000L, eta = 1_000L, now = 1_500L))
        assertEquals(0, elapsedTimePercent(start = 2_000L, eta = 1_000L, now = 1_500L))
    }
}
