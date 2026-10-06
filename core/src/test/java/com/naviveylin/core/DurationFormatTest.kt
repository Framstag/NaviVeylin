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

    /**
     * A step's duration keeps the seconds (spec: `routing-summary` — the summary formats its own
     * values). A leg of 45 s must not read "0 min", which is what every step of a city route showed
     * (owner finding, 2026-10-03), and an unknown duration must produce nothing at all instead of a
     * zero.
     */
    @Test
    fun stepDurationKeepsSeconds() {
        assertEquals("45 s", formatStepDurationText(45.0))
        assertEquals("2 s", formatStepDurationText(2.0))
        assertEquals("59 s", formatStepDurationText(59.9))
    }

    @Test
    fun stepDurationMinutesAndHours() {
        assertEquals("1 min", formatStepDurationText(60.0))
        assertEquals("1 min", formatStepDurationText(119.0))
        assertEquals("1 min", formatStepDurationText(90.0))
        assertEquals("5 min", formatStepDurationText(300.0))
        assertEquals("1h 5min", formatStepDurationText(3900.0))
    }

    @Test
    fun stepDurationUnknownIsEmpty() {
        assertEquals("", formatStepDurationText(0.0))
        assertEquals("", formatStepDurationText(0.5))
        assertEquals("", formatStepDurationText(-1.0))
    }
}
