package com.naviveylin.auto

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Throttled street-name resolution for free driving (spec: auto/free-driving —
 * "Current street name shown").
 *
 * The reverse-geocode lookup (`OSMScoutClient.getAddressAt`, a native call
 * that must run off the main thread) is only triggered when the vehicle moved
 * far enough since the last lookup or when the minimum interval elapsed — it
 * never runs on every GPS tick. Pure and clock-injectable for unit tests.
 *
 * Design D4: failures keep the last label (the caller only marks the position
 * as geocoded on success); an unnamed street clears the label (no stale text).
 */
class StreetNameUpdater(
    private val minMoveMeters: Double = DEFAULT_MIN_MOVE_METERS,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastGeocodeMs = 0L

    /**
     * True when a new reverse geocode should run for [lat]/[lon]: first fix,
     * moved at least [minMoveMeters], or [minIntervalMs] elapsed since the
     * last lookup (also covers GPS drift while parked).
     */
    fun shouldGeocode(lat: Double, lon: Double): Boolean {
        val first = lastLat.isNaN()
        val moved = !first && distanceMeters(lastLat, lastLon, lat, lon) >= minMoveMeters
        val stale = now() - lastGeocodeMs >= minIntervalMs
        return first || moved || stale
    }

    /** Record that a geocode ran for [lat]/[lon] (call only on success). */
    fun markGeocoded(lat: Double, lon: Double) {
        lastLat = lat
        lastLon = lon
        lastGeocodeMs = now()
    }

    /**
     * Extract the street name from a `getAddressAt` result (index 0 is the
     * street). Blank-tolerant: null when missing or blank, so the caller can
     * clear the label.
     */
    fun streetFromAddress(address: Array<String>?): String? =
        address?.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val DEFAULT_MIN_MOVE_METERS = 25.0
        const val DEFAULT_MIN_INTERVAL_MS = 2000L

        /**
         * "ref name" display text (e.g. "B 1 Hauptstrasse"), or null when
         * neither ref nor name is present. Shared by the navigation and
         * free-driving street labels (spec: auto/navigation-view,
         * auto/free-driving — ref shown with the street name).
         */
        fun roadDisplayText(ref: String, name: String): String? {
            val parts = listOfNotNull(ref.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })
            return parts.joinToString(" ").takeIf { it.isNotBlank() }
        }

        /**
         * Equirectangular distance approximation (m), fine for the throttle
         * threshold (25 m) at map-level latitudes.
         */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val midLat = Math.toRadians((lat1 + lat2) / 2.0)
            val x = dLon * cos(midLat)
            return 6371000.0 * sqrt(dLat * dLat + x * x)
        }
    }
}
