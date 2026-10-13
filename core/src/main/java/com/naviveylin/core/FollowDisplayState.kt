package com.naviveylin.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bounded-rate follow-mode display position (spec: `smooth-follow` — Correction easing; design D1).
 *
 * Owns the displayed position the phone renderer scrolls the map to between GPS fixes; shared with
 * the Android Auto renderer (`AutoMapRenderer.extrapolationTick`, spec: `auto-smooth-follow` —
 * Correction easing, Shared follow bounds on the car surface).
 *
 * The displayed position advances at the extrapolation's effective speed along the direction of
 * travel plus a position correction toward the current fix, and that correction is clamped to a
 * bounded fraction of the effective speed ([CORR_FRACTION], floored at [CORR_FLOOR_M_S]). The
 * resulting displayed rate stays inside 0.75x..1.25x of the effective speed: the display never
 * holds still and never closes a gap faster than that, whatever the fix position does.
 *
 * The position error is measured against the fix (the jittery reference) while the velocity comes
 * from the filtered estimate — that split is what turns a backward fix jump into a rate dip instead
 * of a halt. A proportional term on the error nulls a speed-estimate bias without an integral term:
 * 5 % of the speed needs only `0.05 * v * tau` ~ 0.2 m of error at 50 km/h, so the residual error is
 * bounded and cannot accumulate.
 *
 * The former forward-only hold is gone (spec: `smooth-follow` — Correction easing, "the earlier
 * monotonic-forward rule is replaced by this bound"): it stopped the display for the whole
 * correction whenever the fix track lay behind the extrapolation, and at 50 km/h that took only
 * `v * tau` = 4.2 m of along-track error — the scale of real GPS jitter and of an engine junction
 * snap. Measured on the emulator (2026-10-12): a full stop, then a payback at up to 2.9x the
 * vehicle speed.
 *
 * Teleports (GPS jump/recovery, reroute snap, resume after a long freeze) snap instead of gliding:
 * the bounded rate cannot represent a 100 m/s glide. Frozen at stops: the caller stops calling
 * [advance] below the movement threshold (spec: `smooth-follow` — Extrapolation loop gating), so
 * the display position stays where it is and the resume continues from it.
 */
class FollowDisplayState(
    /** Correction time constant in seconds (default 0.3, matches [FollowPrediction.easeAlpha]). */
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

    /** Reset on follow-mode exit (user pan/zoom, mode switch, car session end). */
    fun reset() {
        displayLat = Double.NaN
        displayLon = Double.NaN
    }

    /**
     * Advance the displayed position by one display frame of [dtSec] seconds.
     *
     * [headingDeg] is the direction of travel in degrees clockwise from north, or [Double.NaN] when
     * unknown: with it the frame advances at [effectiveSpeedMs] along that direction, without it
     * there is no feed-forward term and only the clamped correction runs (the display then settles
     * on the fix as the pre-feed-forward implementation did).
     *
     * [effectiveSpeedMs] is the prediction's effective speed
     * ([FollowPrediction.effectiveSpeedMs]) — the same estimate the rendered frame is anchored on.
     *
     * [referenceAgeSec] is the age of the fix [targetLat]/[targetLon] came from. While it is fresh
     * (≤ [STALE_REFERENCE_SEC]) the correction and the teleport snap apply. A stale reference is a
     * lost feed, not a moved vehicle (spec: `smooth-follow` — Stop decision against a current base):
     * the display then advances by the feed-forward alone, so it neither gets pulled back onto a fix
     * the vehicle has long left nor yanked by a snap in the wrong direction; the first fresh fix
     * afterwards re-applies both rules normally (that is the GPS-recovery snap).
     *
     * Returns the new displayed position.
     */
    fun advance(
        targetLat: Double,
        targetLon: Double,
        dtSec: Double,
        headingDeg: Double,
        effectiveSpeedMs: Double,
        referenceAgeSec: Double = 0.0
    ): Pair<Double, Double> {
        if (displayLat.isNaN() || displayLon.isNaN()) {
            displayLat = targetLat
            displayLon = targetLon
            return displayLat to displayLon
        }
        val referenceFresh = referenceAgeSec <= STALE_REFERENCE_SEC
        // Teleport snap: a target gap beyond the overshoot/glide scale (GPS jump, reroute, resume
        // after a long freeze) cannot be corrected at a physical rate — snap directly instead of
        // gliding for seconds.
        if (referenceFresh &&
            haversineMeters(displayLat, displayLon, targetLat, targetLon) > TELEPORT_SNAP_METERS
        ) {
            displayLat = targetLat
            displayLon = targetLon
            return displayLat to displayLon
        }

        val vEff = if (effectiveSpeedMs.isNaN() || effectiveSpeedMs < 0.0) 0.0 else effectiveSpeedMs
        val correctionLimit = maxOf(CORR_FRACTION * vEff, CORR_FLOOR_M_S)

        // Position error to the fix, in local metres (north / east). A stale reference contributes no
        // error: the display is dead-reckoning and must not be dragged back toward an old fix.
        val errNorth = if (referenceFresh) {
            (targetLat - displayLat) * FollowPrediction.METERS_PER_DEG_LAT
        } else 0.0
        val errEast = if (referenceFresh) {
            (targetLon - displayLon) * FollowPrediction.METERS_PER_DEG_LON *
                cos(Math.toRadians(displayLat))
        } else 0.0

        // Feed-forward: the vehicle's own motion this frame, along the direction of travel.
        var vNorth = 0.0
        var vEast = 0.0
        if (!headingDeg.isNaN()) {
            val h = Math.toRadians(headingDeg)
            vNorth = vEff * cos(h)
            vEast = vEff * sin(h)
        }

        // Correction: proportional to the error, its RATE clamped to the bounded fraction — the
        // clamp is on the correction VECTOR's magnitude, so a diagonal error cannot buy a larger
        // correction than the band allows by acting on both axes at once.
        var corrNorth = errNorth / tauSec
        var corrEast = errEast / tauSec
        val corrMagnitude = sqrt(corrNorth * corrNorth + corrEast * corrEast)
        if (corrMagnitude > correctionLimit) {
            val scale = correctionLimit / corrMagnitude
            corrNorth *= scale
            corrEast *= scale
        }

        val stepNorth = (vNorth + corrNorth) * dtSec
        val stepEast = (vEast + corrEast) * dtSec
        displayLat += stepNorth / FollowPrediction.METERS_PER_DEG_LAT
        displayLon += stepEast / (FollowPrediction.METERS_PER_DEG_LON * cos(Math.toRadians(displayLat)))
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

    companion object {
        /**
         * Gap (m) beyond which the display snaps to the target instead of correcting (GPS teleport
         * / reroute / long-gate resume): the bounded rate would otherwise glide for seconds. A
         * reference older than [STALE_REFERENCE_SEC] never snaps — see [advance].
         */
        const val TELEPORT_SNAP_METERS = 50.0

        /** Reference age above which the correction and the snap are skipped (seconds). */
        const val STALE_REFERENCE_SEC = 3.0

        /**
         * Fraction of the effective speed the correction may add to or take from the advance
         * (design D1): the displayed rate band is `1 ± CORR_FRACTION`.
         */
        const val CORR_FRACTION = 0.25

        /**
         * Floor for the correction bound (m/s): with a near-zero effective speed the proportional
         * term still has to settle the display onto the fix.
         */
        const val CORR_FLOOR_M_S = 1.0
    }
}
