package com.naviveylin.core

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Movement-derived GPS quality helpers for follow-mode car screens (spec:
 * auto-smooth-follow — "Fix feed from follow-mode screens"). Many receivers
 * (and GPX replay tracks) omit GPS speed or bearing; the effective value is
 * computed from the movement between consecutive fixes instead, so display
 * extrapolation (smooth scrolling) keeps running.
 *
 * Ported verbatim from the free-driving screen (previously private to
 * `FreeDrivingScreen`) so free driving, the browse map and the navigation
 * view share one implementation with identical behavior.
 */
object AutoPositionUtil {

    /** Mean earth radius in meters (flat-earth approximation, matches the
     *  phone renderer's distance model). */
    const val EARTH_RADIUS_M = 6371000.0

    /** Minimum movement (m) before a derived bearing is trusted. */
    const val MIN_BEARING_MOVE_M = 3.0

    /** Minimum time (ms) between fixes before a derived speed is trusted. */
    const val MIN_SPEED_DT_MS = 500L

    /** Minimum movement (m) before a derived speed is trusted. */
    const val MIN_SPEED_MOVE_M = 1.0

    /**
     * Bearing (degrees 0..360, clockwise from north) of the movement from
     * (lat1,lon1) to (lat2,lon2); null when the movement is below
     * [MIN_BEARING_MOVE_M] (too noisy to trust). Used for GPX replay tracks
     * without a GPS bearing.
     */
    fun movementBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double? {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val midLat = Math.toRadians((lat1 + lat2) / 2.0)
        val x = dLon * cos(midLat)
        val dist = EARTH_RADIUS_M * sqrt(dLat * dLat + x * x)
        if (dist < MIN_BEARING_MOVE_M) return null
        var deg = Math.toDegrees(Math.atan2(x, dLat))
        if (deg < 0.0) deg += 360.0
        return deg
    }

    /**
     * Speed (km/h) implied by the movement from (lat1,lon1) to (lat2,lon2)
     * over [dtMs]; null when the time delta or movement is too small to
     * trust. Used for GPX replay tracks without a GPS speed.
     */
    fun movementSpeedKmH(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        dtMs: Long
    ): Double? {
        if (dtMs < MIN_SPEED_DT_MS) return null
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val midLat = Math.toRadians((lat1 + lat2) / 2.0)
        val x = dLon * cos(midLat)
        val dist = EARTH_RADIUS_M * sqrt(dLat * dLat + x * x)
        if (dist < MIN_SPEED_MOVE_M) return null
        return dist / (dtMs / 1000.0) * 3.6
    }
}

/**
 * Stateful per-screen derivation of effective GPS speed/bearing.
 *
 * Holds the previous fix and the last effective values so a fix without a
 * GPS speed/bearing falls back to the movement between fixes, and a
 * too-small movement keeps the last effective value (no flicker). Consume
 * exactly once per fix via [derive] — it internally reads the previous fix
 * position for the speed FIRST, then overwrites it for the bearing (order
 * matters: speed must see the previous fix).
 */
class AutoFixDerivation {

    private var lastFixLat = Double.NaN
    private var lastFixLon = Double.NaN
    private var lastFixTimeMs = 0L
    private var lastEffectiveSpeed = -1.0
    private var lastEffectiveBearing = -1.0

    /**
     * Effective speed (km/h) then bearing (degrees) for [pos]: the GPS value
     * when valid (>= 0), else the movement-derived value, else the last
     * effective value.
     */
    fun derive(pos: AutoPosition, nowMs: Long): Pair<Double, Double> {
        // Speed first: it reads the previous fix position, which the bearing
        // step overwrites below.
        val speed = if (pos.speedKmH >= 0.0) {
            pos.speedKmH
        } else {
            AutoPositionUtil.movementSpeedKmH(lastFixLat, lastFixLon, pos.lat, pos.lon, nowMs - lastFixTimeMs)
                ?: lastEffectiveSpeed
        }
        if (speed >= 0.0) lastEffectiveSpeed = speed
        lastFixTimeMs = nowMs

        val bearing = if (pos.bearing >= 0.0) {
            pos.bearing
        } else {
            AutoPositionUtil.movementBearing(lastFixLat, lastFixLon, pos.lat, pos.lon) ?: lastEffectiveBearing
        }
        if (bearing >= 0.0) lastEffectiveBearing = bearing
        lastFixLat = pos.lat
        lastFixLon = pos.lon
        return speed to bearing
    }
}
