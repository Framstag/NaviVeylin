package com.naviveylin.core

import kotlinx.coroutines.flow.StateFlow

/** A GPS fix for the Android Auto session (framework-free, no Play Services types). */
data class AutoPosition(
    val lat: Double,
    val lon: Double,
    /** Bearing in degrees, or [Double.NaN] when unknown. */
    val bearing: Double = Double.NaN,
    /** Horizontal accuracy in meters, or negative when unknown. */
    val accuracy: Double = -1.0
)

/**
 * Provides GPS position to Android Auto screens. Implemented in the [:app]
 * module via Hilt (backed by the shared [LocationService]).
 *
 * The AA-only process has no phone UI, so nothing mirrors a phone-side
 * [NavigationViewModel]; this provider is the location source for the car
 * map (GPS marker, follow mode, re-center).
 */
interface AutoLocationProvider {

    /** Latest GPS fix, null until the first fix arrives. */
    fun position(): StateFlow<AutoPosition?>

    /** Start listening for location updates (idempotent). */
    fun start()

    /** Stop listening and release the location clients. */
    fun stop()
}
