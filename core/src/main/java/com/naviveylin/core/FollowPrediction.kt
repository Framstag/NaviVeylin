package com.naviveylin.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Predicts the vehicle position between GPS fixes by linear extrapolation
 * along the last known speed and heading.
 *
 * Display-only: predicted positions are used to scroll the map viewport
 * smoothly between fixes and are NEVER fed to the navigation engine.
 *
 * Used by the phone renderer (smooth-follow) and the Android Auto renderer
 * (auto-smooth-follow).
 *
 * Speed model (rev 2, from the emulator GPX log analysis):
 *  - The receiver GPS speed (Doppler or receiver-filtered position-diff) is
 *    smooth and is the PRIMARY speed estimate. The raw position-diff average
 *    over the last fix interval is jitter-inflated (±2-5 m per fix) and must
 *    NOT drive the prediction in steady state — it made the displayed speed
 *    oscillate ("stop and go").
 *  - While the GPS speed is DECREASING (>5% per fix) the prediction is capped
 *    at 70% of the smoothed position-diff average. A position-diff based GPS
 *    speed lags the actual speed by one fix interval, so without the cap the
 *    map would run past a braking vehicle. The smoothed average (EMA, tau=1s)
 *    removes the jitter inflation from the cap value.
 *  - When the fix has not moved for 1 s the vehicle is considered stopped and
 *    the prediction holds — some receivers (and the emulator) report a
 *    non-zero speed while stationary.
 */
class FollowPrediction {

    private var fixLat = Double.NaN
    private var fixLon = Double.NaN
    private var speedMs = 0.0
    private var headingRad = Double.NaN
    private var fixTimeMs = 0L
    private var hasValidFix = false

    // Previous fix, used to compute the average (position-difference) speed.
    private var avgSpeedMs = 0.0

    // Previous fix's GPS speed, used to detect deceleration.
    private var prevSpeedMs = Double.NaN

    // EMA of the position-diff speed (tau=1s): removes the jitter inflation
    // from the deceleration cap value.
    private var smoothAvgMs = 0.0

    // True while the GPS speed is decreasing (5% hysteresis): the prediction
    // is capped to 70% of the smoothed average so a lagging position-diff
    // speed cannot run the map past a braking vehicle.
    private var decelerating = false

    // Last receipt time the fix moved more than [STOP_MOVE_METERS]. When the
    // fix has not moved for [STOP_HOLD_MS] the vehicle is considered stopped
    // and the prediction holds (the emulator reports a non-zero speed at a
    // stop, so a zero GPS speed cannot be relied on).
    private var lastMoveTimeMs = 0L

    /**
     * Debug: expose the last speed-estimate components for logcat diagnosis
     * (fix cadence, speed source quality, prediction behavior).
     */
    data class DebugState(
        val avgSpeedMs: Double,
        val gpsSpeedMs: Double,
        val effectiveSpeedMs: Double,
        val smoothAvgMs: Double,
        val decelerating: Boolean,
        val stopped: Boolean
    )

    /**
     * Debug snapshot at [nowMs] (ms since epoch).
     */
    fun debugState(nowMs: Long = fixTimeMs): DebugState {
        val gpsValid = !speedMs.isNaN()
        val stopped = isStopped(nowMs, gpsValid)
        val effectiveSpeed = effectiveSpeed(gpsValid, stopped)
        return DebugState(
            avgSpeedMs,
            if (gpsValid) speedMs else Double.NaN,
            effectiveSpeed,
            smoothAvgMs,
            decelerating,
            stopped
        )
    }

    /**
     * Maximum extrapolation window in seconds. Beyond this the position is
     * held at the last fix instead of extrapolating indefinitely (a lost fix
     * must not drift the map). Must exceed the longest fix gap — the emulator
     * sends fixes at irregular 1-2.4 s intervals, and a window shorter than
     * the gap makes the prediction hold mid-gap, easing the display backward
     * (a visible "overshoot" slide).
     */
    var maxExtrapolationSec: Double = 3.0

    /**
     * Update the prediction state from a GPS fix.
     *
     * @param speedMs ground speed in m/s, or [Double.NaN] when unknown
     * @param headingDeg direction of travel in degrees [0,360) clockwise from
     *   north, or [Double.NaN] when unknown
     * @param timeMs fix timestamp in ms since epoch (receipt time is fine —
     *   GPS fix timestamps can be ahead of the system clock)
     */
    fun update(lat: Double, lon: Double, speedMs: Double, headingDeg: Double, timeMs: Long) {
        if (hasValidFix && !fixLat.isNaN() && !lat.isNaN() && !lon.isNaN()) {
            val dtSec = (timeMs - fixTimeMs) / 1000.0
            if (dtSec > 0.1) {
                val distM = haversineMeters(fixLat, fixLon, lat, lon)
                avgSpeedMs = distM / dtSec
                if (distM > STOP_MOVE_METERS) lastMoveTimeMs = timeMs
                // EMA of the position-diff speed (tau=1s): removes the jitter
                // inflation from the deceleration cap value.
                if (smoothAvgMs <= 0.0) {
                    smoothAvgMs = avgSpeedMs
                } else {
                    smoothAvgMs += (avgSpeedMs - smoothAvgMs) * (1.0 - exp(-dtSec / SMOOTH_AVG_TAU_SEC))
                }
            }
        }
        // Deceleration detection on the smooth receiver speed (5% hysteresis).
        // The receiver speed is filtered, so a single-fix drop of >5% reliably
        // indicates real deceleration, not jitter.
        val gpsValid = !speedMs.isNaN()
        decelerating = gpsValid && !prevSpeedMs.isNaN() && speedMs < prevSpeedMs * DECEL_HYSTERESIS
        prevSpeedMs = speedMs
        fixLat = lat
        fixLon = lon
        // Keep NaN as NaN (unknown speed) — only negative values are invalid.
        // A zero GPS speed means "stopped" and must win over a stale average
        // in predictedPosition, otherwise the map extrapolates past a stop.
        this.speedMs = if (speedMs < 0.0) 0.0 else speedMs
        this.headingRad = if (headingDeg.isNaN() || headingDeg < 0.0) Double.NaN else Math.toRadians(headingDeg)
        fixTimeMs = timeMs
        hasValidFix = !lat.isNaN() && !lon.isNaN()
        if (hasValidFix && lastMoveTimeMs == 0L) lastMoveTimeMs = timeMs
    }

    /**
     * Predicted position at [nowMs] (ms since epoch).
     *
     * Holds the last fix position when there is no valid fix, no speed, no
     * heading, the fix is older than [maxExtrapolationSec], or the vehicle is
     * considered stopped.
     */
    fun predictedPosition(nowMs: Long): Pair<Double, Double> {
        if (!hasValidFix) return fixLat to fixLon
        val gpsValid = !speedMs.isNaN()
        val stopped = isStopped(nowMs, gpsValid)
        val effectiveSpeed = effectiveSpeed(gpsValid, stopped)
        if (effectiveSpeed <= 0.0 || headingRad.isNaN()) return fixLat to fixLon
        val elapsedSec = (nowMs - fixTimeMs) / 1000.0
        if (elapsedSec < 0.0 || elapsedSec > maxExtrapolationSec) return fixLat to fixLon
        val distM = effectiveSpeed * elapsedSec
        val latRad = Math.toRadians(fixLat)
        val dLat = distM * cos(headingRad) / METERS_PER_DEG_LAT
        val dLon = distM * sin(headingRad) / (METERS_PER_DEG_LON * cos(latRad))
        return (fixLat + dLat) to (fixLon + dLon)
    }

    /**
     * Stopped detection. The fix-movement check is authoritative (some
     * receivers — and the emulator — report a non-zero speed while
     * stationary). The base threshold must exceed the longest fix gap so a
     * slow fix stream does not look like a stop; the fast path (low speed +
     * 1 s without movement) catches a real stop quickly so the prediction
     * does not drift past it.
     */
    private fun isStopped(nowMs: Long, gpsValid: Boolean): Boolean {
        val sinceMove = nowMs - lastMoveTimeMs
        return sinceMove > STOP_HOLD_MS ||
            (gpsValid && speedMs < STOP_SPEED_MS && sinceMove > STOP_HOLD_FAST_MS)
    }

    private fun effectiveSpeed(gpsValid: Boolean, stopped: Boolean): Double = when {
        // Stopped: hold. Some receivers (and the emulator) report a non-zero
        // speed while stationary, so the fix-movement check is authoritative.
        stopped -> 0.0
        // Decelerating: cap the prediction to 70% of the smoothed position-
        // diff average. A position-diff based GPS speed lags the actual speed
        // by one fix interval; without the cap the map runs past a braking
        // vehicle. The smoothed average removes the jitter inflation.
        gpsValid && decelerating && smoothAvgMs > 0.0 -> minOf(speedMs, smoothAvgMs * DECEL_CAP)
        // Steady state: the receiver speed is smooth and accurate — use it
        // directly (no 30% deficit, no "stop and go").
        gpsValid -> speedMs
        // No GPS speed: fall back to the conservative position-diff average.
        avgSpeedMs > 0.0 -> avgSpeedMs * DECEL_CAP
        else -> 0.0
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * EARTH_RADIUS * asin(sqrt(a))
    }

    companion object {
        /** Earth radius in meters (WGS84). */
        const val EARTH_RADIUS = 6378137.0

        /** Meters per degree of latitude (WGS84). */
        const val METERS_PER_DEG_LAT = 111320.0

        /** Meters per degree of longitude at the equator (WGS84). */
        const val METERS_PER_DEG_LON = 111320.0

        /** Deceleration detection: GPS speed must drop below 95% of the previous fix. */
        const val DECEL_HYSTERESIS = 0.95

        /** Cap applied to the prediction speed while decelerating. */
        const val DECEL_CAP = 0.7

        /** EMA time constant for the smoothed position-diff speed (seconds). */
        const val SMOOTH_AVG_TAU_SEC = 1.0

        /** Fix must move more than this (m) to count as "moving". */
        const val STOP_MOVE_METERS = 5.0

        /** Fix not moving for this long (ms) → vehicle considered stopped. */
        const val STOP_HOLD_MS = 3_000L

        /**
         * Fast stop path: with a very low GPS speed, a 1 s pause is enough to
         * declare a stop (the emulator reports 6.3 km/h while stationary, so
         * the speed alone cannot be trusted, but a low speed + no movement is
         * a reliable stop signal).
         */
        const val STOP_HOLD_FAST_MS = 1_000L

        /** GPS speed below this (m/s, ≈10 km/h) counts as "very low". */
        const val STOP_SPEED_MS = 2.78

        /**
         * Exponential ease factor for a frame delta: `1 - exp(-dt/tau)`.
         * With the default tau of 0.3 s the eased value lags the target by
         * `v*tau` and absorbs the fix-arrival correction. In steady state the
         * prediction speed equals the receiver speed, so the correction is
         * small and a short tau keeps the display lag (and the stop catch-up)
         * small. The lag is invisible: the marker stays at screen center and
         * the map content is simply a fraction of a second "old".
         */
        fun easeAlpha(dtSec: Double, tauSec: Double = 0.3): Double =
            1.0 - exp(-dtSec / tauSec)

        /**
         * Result of [displayOffsetPx]: the raw (unclamped) and clamped offsets in
         * the viewport (rotated) frame.
         */
        data class DisplayOffset(
            val rawX: Double, val rawY: Double,
            val clampedX: Double, val clampedY: Double
        ) {
            val clamped: Boolean get() = clampedX != rawX || clampedY != rawY
        }

        /**
         * Compute the display offset (bitmap px) for a displayed position against
         * the current frame's viewport, clamped to the overrun margin.
         *
         * The bitmap has the viewport rotation baked in, so the north-up geo delta
         * is rotated by the viewport angle first (same rotation the renderer's blit
         * applies) — otherwise the map scrolls in the wrong direction in a rotated
         * (heading-up) viewport. The clamp keeps the visible window inside the
         * overrun buffer so no edge strip is revealed.
         */
        fun displayOffsetPx(
            displayLat: Double, displayLon: Double,
            frameLat: Double, frameLon: Double,
            mag: Int, angle: Double,
            bitmapW: Int, bitmapH: Int,
            canvasW: Int, canvasH: Int, dpi: Double
        ): DisplayOffset {
            val (sx, sy) = ProjectionUtils.geoToScreen(
                displayLat, displayLon, bitmapW, bitmapH, mag, frameLat, frameLon, dpi
            )
            val ox = sx - bitmapW / 2.0
            val oy = sy - bitmapH / 2.0
            val cosA = cos(angle)
            val sinA = sin(angle)
            val oxR = ox * cosA - oy * sinA
            val oyR = ox * sinA + oy * cosA
            val marginX = (bitmapW - canvasW) / 2.0
            val marginY = (bitmapH - canvasH) / 2.0
            return DisplayOffset(
                oxR, oyR,
                oxR.coerceIn(-marginX, marginX), oyR.coerceIn(-marginY, marginY)
            )
        }
    }
}
