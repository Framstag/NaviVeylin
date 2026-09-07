package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for shared duration formatting (spec: move-routing-summary —
 * per-step time in the navigation details step list).
 */
class DurationFormatTest {

    @Test
    fun minutesOnly() {
        assertEquals("5 min", formatDurationText(300.0))
        assertEquals("45 min", formatDurationText(2700.0))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("1h 20min", formatDurationText(4800.0))
        assertEquals("2h 5min", formatDurationText(7500.0))
    }

    @Test
    fun zeroAndSubMinute() {
        assertEquals("0 min", formatDurationText(0.0))
        assertEquals("0 min", formatDurationText(30.0))
    }

    @Test
    fun exactHourBoundary() {
        assertEquals("1h 0min", formatDurationText(3600.0))
    }
}
