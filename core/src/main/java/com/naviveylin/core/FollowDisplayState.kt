package com.naviveylin.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Monotonic forward follow-mode display position (spec: smooth-follow —
 * Correction easing, delta fix-follow-vehicle-jumps).
 *
 * Owns the eased [displayLat]/[displayLon] the phone renderer scrolls to
 * between GPS fixes. The display advances toward the predicted target every
 * display frame, but SHALL NEVER move backward along the direction of travel:
 * when the target lies behind the current display (the fix-arrival
 * overshoot case — the prediction was extrapolated ahead and the new fix
 * resets the base), the display HOLDS at its current position until the
 * extrapolation from the new fix advances beyond it.
 *
 * Without the hold, the fix-arrival target drop caused a backward correction
 * slide at every fix cadence (the "moved on map, then jumped" sawtooth): the
 * initial backward velocity of the ease is error/tau, a visible jerk.
 *
 * Teleports (GPS jump/recovery, reroute snap): when the target gap exceeds
 * [TELEPORT_SNAP_METERS] the display snaps to the target instead of gliding
 * (the ease model cannot represent a 100 m/s glide — this preserves the
 * pre-existing >500 m jump-reset behavior of the view model's center
 * smoothing).
 *
 * Shared by the phone display loop (`MapCanvasScreen`) and the Android Auto
 * extrapolation loop (`AutoMapRenderer.extrapolationTick`, delta
 * fix-aa-follow-vehicle-jumps): AA adopts the same forward-only rule instead
 * of its own easing.
 *
 * Frozen at stops: the caller stops calling [advance] while the vehicle is
 * below the movement threshold — the display position stays where it is and
 * the resume continues from it (no snap).
 */
class FollowDisplayState(
    /** Ease time constant in seconds (default 0.3, matches [FollowPrediction.easeAlpha]). */
    private val tauSec: Double = 0.3
) {

    private var displayLat = Double.NaN
    private var displayLon = Double.NaN

    /** Current displayed position; NaN until the first [advance]. */
    val lat: Double get() = displayLat

    /** Current displayed position; NaN until the first [advance]. */
    val lon: Double get() = displayLon

    /** True once [advance] has run at least once. */
    val hasPosition: Boolean get() = !displayLat.isNaN() && !displayLon.isNaN()

    /** Reset on follow-mode exit (user pan/zoom, mode switch). */
    fun reset() {
        displayLat = Double.NaN
        displayLon = Double.NaN
    }

    /**
     * Advance the displayed position toward [targetLat]/[targetLon] by one
     * display frame of [dtSec] seconds.
     *
     * [headingDeg] is the last known direction of travel (degrees clockwise
     * from north, [Double.NaN] when unknown): the travel direction used for
     * the forward-only rule. With an unknown heading the rule is skipped —
     * without a bearing the prediction does not extrapolate, so there is no
     * overshoot to absorb (the target is the real fix).
     *
     * Returns the new displayed position (which may equal the previous one
     * when the forward-only rule holds).
     */
    fun advance(
        targetLat: Double,
        targetLon: Double,
        dtSec: Double,
        headingDeg: Double
    ): Pair<Double, Double> {
        if (displayLat.isNaN() || displayLon.isNaN()) {
            displayLat = targetLat
            displayLon = targetLon
            return displayLat to displayLon
        }
        // Teleport snap: a target gap beyond the overshoot/glide scale (GPS
        // jump, reroute, resume after a long freeze) cannot be eased at a
        // physical speed — snap directly instead of gliding for seconds.
        if (haversineMeters(displayLat, displayLon, targetLat, targetLon) > TELEPORT_SNAP_METERS) {
            displayLat = targetLat
            displayLon = targetLon
            return displayLat to displayLon
        }
        val alpha = 1.0 - exp(-dtSec / maxOf(tauSec, 1e-6))
        val easedLat = displayLat + (targetLat - displayLat) * alpha
        val easedLon = displayLon + (targetLon - displayLon) * alpha
        if (!headingDeg.isNaN() &&
            forwardComponent(easedLat - displayLat, easedLon - displayLon, headingDeg) < 0.0
        ) {
            // Target is behind: hold — never slide backward along travel.
            return displayLat to displayLon
        }
        displayLat = easedLat
        displayLon = easedLon
        return displayLat to displayLon
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * FollowPrediction.EARTH_RADIUS * asin(sqrt(a))
    }

    /** Geographic forward component of the movement along [headingDeg] (deg clockwise from north). */
    private fun forwardComponent(dLat: Double, dLon: Double, headingDeg: Double): Double {
        val h = Math.toRadians(headingDeg)
        return dLat * cos(h) + dLon * sin(h)
    }

    companion object {
        /**
         * Gap (m) beyond which the display snaps to the target instead of easing
         * (GPS teleport / reroute / long-gate resume): the ease would otherwise
         * glide at a nonphysical speed for seconds.
         */
        const val TELEPORT_SNAP_METERS = 50.0
    }
}
