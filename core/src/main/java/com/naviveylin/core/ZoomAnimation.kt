package com.naviveylin.core

import kotlin.math.pow

/**
 * Eased front-buffer zoom animation between a start and a target scale
 * (smooth-zoom). Pure Kotlin, no Android dependencies — tested on the JVM.
 *
 * Display-only state: the class never touches the render pipeline or the
 * persisted viewport. The caller (phone renderer / MapCanvasScreen) multiplies
 * the returned scale into its per-frame graphics transform, anchored at the
 * last provided anchor point so the geographic point under the zoom focal
 * stays fixed while the animation plays.
 *
 * Scale model: 1.0 = the currently rendered front buffer's magnification
 * (no zoom applied). Discrete zoom input commits target magnification m and
 * the front buffer still shows the old magnification m0, so the target scale
 * is `2^(m - m0)`.
 *
 * Retracking: calling [retrack] mid-animation rebuilds the animation from the
 * CURRENT displayed scale (never the previous start) so rapid zoom input
 * (repeat button taps, wheel ticks, double taps) compounds smoothly without
 * snapping back.
 */
class ZoomAnimation(private val durationMs: Long = DEFAULT_DURATION_MS) {

    companion object {
        const val DEFAULT_DURATION_MS = 250L
    }

    /** Focal point of the running animation, in screen pixels. */
    var anchorX = 0f
        private set
    var anchorY = 0f
        private set

    /** True while a value-changing animation is in progress. */
    var active = false
        private set

    private var startScale = 1f
    private var targetScale = 1f
    private var startTimeMs = 0L
    private var activeDurationMs = durationMs

    /**
     * Starts an animation from [from] to [to] anchored at ([anchorX]/[anchorY])
     * lasting [durationMs] (defaults to the instance's constructor duration).
     * Interrupts any running animation (its progress is discarded — callers
     * wanting continuity should use [retrack]).
     */
    fun start(from: Float, to: Float, anchorX: Float, anchorY: Float, nowMs: Long, durationMs: Long = this.durationMs) {
        startScale = from
        targetScale = to
        this.anchorX = anchorX
        this.anchorY = anchorY
        startTimeMs = nowMs
        activeDurationMs = durationMs
        active = from != to
    }

    /**
     * Retracks toward a new [to] scale from the scale the animation currently
     * displays at [nowMs], keeping the anchor (or updating it when provided),
     * with an optional new [durationMs]. Completed/idle animations behave
     * like [start] from the rest value.
     */
    fun retrack(to: Float, anchorX: Float, anchorY: Float, nowMs: Long, durationMs: Long = this.durationMs) {
        start(currentScale(nowMs), to, anchorX, anchorY, nowMs, durationMs)
    }

    /**
     * Stops the animation at [nowMs]. The currently displayed value becomes
     * the new rest value; [currentScale] keeps returning it until restarted.
     */
    fun finish(nowMs: Long) {
        targetScale = currentScale(nowMs)
        active = false
    }

    /**
     * The eased scale at [nowMs] and "now" bookkeeping for the running
     * animation: ease-out cubic (`1 - (1 - t)^3`) between start and target
     * scale, returning the target once [durationMs] has elapsed (and
     * deactivating the animation at that point).
     */
    fun tick(nowMs: Long): Float {
        if (!active) return targetScale
        val elapsed = (nowMs - startTimeMs).coerceAtLeast(0L)
        if (elapsed >= activeDurationMs) {
            active = false
            return targetScale
        }
        val t = elapsed.toDouble() / activeDurationMs.toDouble()
        return easedScale(t)
    }

    /**
     * The scale the animation would display at [nowMs] WITHOUT changing the
     * running state — used by [retrack] and by callers that need the current
     * value before committing to a new target. Returns the rest value
     * (last target) when idle.
     */
    fun currentScale(nowMs: Long): Float {
        if (!active) return targetScale
        val elapsed = (nowMs - startTimeMs).coerceAtLeast(0L)
        if (elapsed >= activeDurationMs) return targetScale
        return easedScale(elapsed.toDouble() / activeDurationMs.toDouble())
    }

    private fun easedScale(t: Double): Float {
        val eased = (1.0 - (1.0 - t).pow(3.0)).toFloat()
        val scale = startScale + (targetScale - startScale) * eased
        // Convergence guard: exact target at full progress (float drift).
        return if (t >= 1.0) targetScale else scale
    }
}