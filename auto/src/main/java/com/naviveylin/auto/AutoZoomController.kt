package com.naviveylin.auto

import com.naviveylin.core.SpeedZoomTable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Speed-driven auto-zoom for free driving (spec: auto/free-driving — "Current
 * driving speed shown" driving the magnification; spec: auto-speed-zoom
 * mapping). Pure and clock-injectable for unit tests.
 *
 * Mirrors the phone's auto-zoom semantics (MapCanvasViewModel) without the
 * route-specific parts (turn boost, curve boost, post-turn hold):
 * - linear-interpolated speed → magnification table ([SpeedZoomTable]);
 * - stability sampling + cooldown so jittery fixes never thrash the zoom;
 * - hysteresis (only commits when the target moved a full level);
 * - manual zoom suspends auto-zoom; a speed-band change re-engages it.
 */
class AutoZoomController(
    private val minMag: Int = AutoMapRenderer.MIN_ZOOM,
    private val maxMag: Int = AutoMapRenderer.MAX_ZOOM,
    private val cooldownMs: Long = ZOOM_COOLDOWN_MS,
    private val hysteresisMag: Double = ZOOM_HYSTERESIS_MAG,
    private val commitSamples: Int = ZOOM_COMMIT_SAMPLES,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private var lastValidSpeedKmH = Double.NaN
    private var suspended = false
    private var lastBand = -1
    private var lastCommitMs = 0L
    // Sentinel below any real target (min magnification ≥ 1) so the first
    // fix never matches a "stable" sample.
    private var pendingTarget = -1.0
    private var stableSamples = 0
    private var committedTarget = Double.NaN

    /** Manual zoom: suspend auto-zoom until the speed crosses a band boundary. */
    fun suspend() {
        suspended = true
    }

    /** True after [suspend] until a speed-band change re-engages auto-zoom. */
    fun isSuspended(): Boolean = suspended

    /**
     * Feed a speed fix. Returns the magnification to commit, or null when
     * nothing should change (speed invalid, suspended, cooldown, stability,
     * or hysteresis).
     */
    fun onSpeed(rawSpeedKmH: Double): Int? {
        val speed = filterSpeed(rawSpeedKmH)
        if (speed.isNaN()) return null

        val target = SpeedZoomTable.compute(speed).roundToInt().coerceIn(minMag, maxMag)
        val band = SpeedZoomTable.bandIndex(speed)

        if (suspended) {
            // Speed crossed a band boundary → re-engage after a manual zoom.
            if (band != lastBand) {
                suspended = false
                lastBand = band
            } else {
                return null
            }
        } else {
            lastBand = band
        }

        val nowMs = now()
        val cooldownElapsed = nowMs - lastCommitMs >= cooldownMs
        if (target == pendingTarget.roundToInt()) {
            stableSamples++
        } else {
            pendingTarget = target.toDouble()
            stableSamples = 0
        }

        val canCommit = cooldownElapsed && stableSamples >= commitSamples
        val diffMag = if (committedTarget.isNaN()) hysteresisMag else abs(target - committedTarget)
        if (canCommit && diffMag >= hysteresisMag) {
            committedTarget = target.toDouble()
            lastCommitMs = nowMs
            stableSamples = 0
            return target
        }
        return null
    }

    /** Reject speed spikes (> 150 km/h) and NaN; keep the last good speed. */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0.0 && rawSpeedKmH <= MAX_SPEED_KMH) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    companion object {
        const val ZOOM_COOLDOWN_MS = 2500L
        const val ZOOM_COMMIT_SAMPLES = 3
        const val ZOOM_HYSTERESIS_MAG = 1.0 // only commit when target differs by a full level
        const val MAX_SPEED_KMH = 150.0
    }
}
