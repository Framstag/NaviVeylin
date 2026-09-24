package com.naviveylin.core

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * Bounded magnification walk (spec: smooth-zoom - Eased zoom animation on
 * discrete zoom input). Pure Kotlin, no Android dependencies - tested on the
 * JVM.
 *
 * A magnification change larger than the window the frame in hand can serve is
 * applied as a sequence of steps instead of scaling one rendered bitmap across
 * the whole gap: each step is within [windowMagnification] of the magnification
 * the frame was rendered at, and the last step lands exactly on the requested
 * value.
 *
 * The walk is render-synchronous: [onFrameLanded] commits the next step only
 * once the previous step's frame has landed, so the committed value never leads
 * the displayed frame by more than one window.
 *
 * The window ([ZOOM_BLIT_WINDOW]) is derived from the render pipeline's overrun
 * canvas: a frame covering `OVERRUN_FACTOR` times the surface can be scaled down
 * by `log2(OVERRUN_FACTOR)` and still cover the surface
 * (`log2(1.2) = 0.263`), so every step inside the window leaves the displayed
 * frame fully covered. [ZOOM_BLIT_MARGIN] keeps headroom below that bound.
 */
class ZoomWalk(
    private val windowMagnification: Double = ZOOM_BLIT_WINDOW,
    private val settleMagnification: Double = ZOOM_WALK_SETTLE
) {

    companion object {
        /**
         * Overrun canvas factor of the render pipeline (`MapRenderer.DEFAULT_CANVAS_OVERRUN`)
         * - the frame in hand covers this multiple of the map surface.
         */
        const val OVERRUN_FACTOR = 1.2

        /**
         * Largest magnification step the frame in hand can serve:
         * `log2(OVERRUN_FACTOR) = log2(1.2) = 0.263` minus [ZOOM_BLIT_MARGIN].
         * The same bound the Android Auto renderer uses (`ZOOM_BLIT_LIMIT`).
         */
        const val ZOOM_BLIT_WINDOW = 0.25

        /** Headroom kept below `log2(OVERRUN_FACTOR)` (0.263 - 0.25). */
        const val ZOOM_BLIT_MARGIN = 0.01

        /** Two magnifications closer than this are the same value for the walk. */
        const val ZOOM_WALK_SETTLE = 1e-3

        /** The magnification window `log2(OVERRUN_FACTOR)` actually allows. */
        val blitWindowLimit: Double get() = log2(OVERRUN_FACTOR)

        /**
         * Display scale for a frame rendered at [frontMag] while [displayMag] is shown:
         * `2^(displayMag - frontMag)`, bounded to [window]. The bound is the coverage
         * guard of the spec's "Zoom animation never exposes uncovered map area"
         * requirement - every walked step is inside the window by construction, so this
         * only bites if a magnification change ever lands un-walked.
         */
        fun displayScale(displayMag: Double, frontMag: Double, window: Double = ZOOM_BLIT_WINDOW): Float {
            if (frontMag <= 0.0 || displayMag <= 0.0) return 1f
            val delta = (displayMag - frontMag).coerceIn(-window, window)
            return 2.0.pow(delta).toFloat()
        }
    }

    /** The magnification the walk is heading for; NaN when no walk is pending. */
    var target: Double = Double.NaN
        private set

    /** The magnification the next render (and the display) is on; NaN before the first request. */
    var step: Double = Double.NaN
        private set

    /** True while the walk still has steps to commit. */
    val active: Boolean get() = !target.isNaN()

    /**
     * Request [requested] magnification, starting from the frame currently
     * displayed at [frontMag]. Returns the magnification to render now.
     *
     * With no frame on screen ([frontMag] <= 0) there is nothing to transition
     * from, so the request lands directly (spec: the displayed frame must not
     * show a magnification farther than the frame in hand can serve - there is
     * no frame). A request inside the window is a single step.
     */
    fun request(requested: Double, frontMag: Double): Double {
        if (frontMag <= 0.0 || frontMag.isNaN()) {
            step = requested
            target = Double.NaN
            return requested
        }
        val committed = step
        target = requested
        // A re-request while the previous step's frame has NOT landed keeps the
        // committed step: the auto-zoom controller re-commits on every position
        // update, and advancing the step from a frame that is still in flight would
        // lead the display by more than one window. Only the target changes.
        if (!committed.isNaN() && abs(frontMag - committed) > settleMagnification &&
            abs(committed - requested) > settleMagnification
        ) {
            return committed
        }
        step = nextStep(frontMag, requested)
        if (abs(step - requested) < settleMagnification) {
            step = requested
            target = Double.NaN
        }
        return step
    }

    /**
     * A frame rendered at [frontMag] has landed. Returns the next magnification
     * to render, or null when the walk is finished or the previous step has not
     * landed yet.
     */
    fun onFrameLanded(frontMag: Double): Double? {
        if (!active) return null
        if (abs(frontMag - step) > settleMagnification) return null
        val delta = target - frontMag
        if (abs(delta) < settleMagnification) {
            target = Double.NaN
            return null
        }
        step = nextStep(frontMag, target)
        if (abs(step - target) < settleMagnification) {
            step = target
            target = Double.NaN
        }
        return step
    }

    /**
     * The magnification a render should use now: the walk's current step, or
     * [fallback] (the committed viewport magnification) when no walk has run.
     */
    fun renderMag(fallback: Double): Double = if (step.isNaN()) fallback else step

    /**
     * The magnification to persist: the walk's target while one is pending,
     * otherwise [committed]. The displayed step is never persisted (spec:
     * smooth-zoom - Viewport records final magnification).
     */
    fun persistMag(committed: Double): Double = if (active) target else committed

    /**
     * Abandon the walk (manual zoom input, or a commit that must land directly).
     * The step is cleared so [renderMag] falls back to the committed magnification -
     * a cancelled walk must not keep pinning the render pipeline to its old step.
     */
    fun cancel() {
        target = Double.NaN
        step = Double.NaN
    }

    /** One step from [from] toward [to], never farther than the window. */
    private fun nextStep(from: Double, to: Double): Double {
        val delta = to - from
        return if (abs(delta) <= windowMagnification) {
            to
        } else {
            from + delta.coerceIn(-windowMagnification, windowMagnification)
        }
    }
}
