package com.naviveylin.core

import kotlin.math.roundToInt

/**
 * Shared distance-display rounding (phone + Android Auto, spec:
 * auto/navigation-view — distance display): exact value up to 50 m,
 * multiples of 50 m from 50 m to 1 km, multiples of 100 m (one decimal km)
 * above 1 km.
 */
fun roundDistanceMeters(meters: Double): Double {
    if (meters <= 50.0) return meters
    if (meters < 1000.0) return (meters / 50.0).roundToInt() * 50.0
    return (meters / 100.0).roundToInt() * 100.0
}
