package com.naviveylin.ui.route

import kotlin.math.roundToInt

/**
 * Pure progress helpers for the route summary dialog progress lines
 * (spec: route-summary-dialog — "Route progress visualization during active
 * navigation"). All functions return a percent clamped to 0–100 and never
 * divide by zero.
 */

/**
 * Percent of the route distance traveled, given the total route distance and
 * the remaining distance. Returns 0 when the inputs are invalid (total <= 0).
 */
fun routeProgressPercent(total: Double, remaining: Double): Int {
    if (total <= 0.0) return 0
    val fraction = (total - remaining) / total
    return clampPercent(fraction)
}

/**
 * Percent of the estimated travel time elapsed, given the navigation start
 * time, the arrival estimate (both epoch millis) and the current time.
 * Returns 0 when the inputs are invalid (start <= 0 or eta <= start).
 */
fun elapsedTimePercent(start: Long, eta: Long, now: Long): Int {
    if (start <= 0L || eta <= start) return 0
    val fraction = (now - start).toDouble() / (eta - start).toDouble()
    return clampPercent(fraction)
}

private fun clampPercent(fraction: Double): Int {
    if (fraction.isNaN()) return 0
    return (fraction * 100.0).roundToInt().coerceIn(0, 100)
}
