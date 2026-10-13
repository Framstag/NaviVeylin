package com.naviveylin.core

/**
 * When the follow display must ask for a new frame (spec: `smooth-follow` — Follow frame refresh
 * before the overrun margin is exhausted; design D2).
 *
 * The displayed frame can only be blitted inside its overrun margin, so the drift accumulated since
 * that frame was rendered sets how much scrolling is left. Requesting the next frame only once the
 * offset is already clamped — the pre-2026-10-12 behaviour, `offset.clamped` was the only trigger in
 * a steady-heading drive — puts a full render on the critical path of the freeze: the emulator
 * reproduction measured the content pinned at the margin (byte-identical frames) for 1.5-2.7 s and
 * then paid the whole stall back in one frame.
 *
 * This policy requests the frame while margin remains:
 *
 * ```
 * threshold = share * margin
 *   share = MAX_SHARE                                            (latency unknown)
 *   share = min(MAX_SHARE, max(MIN_SHARE, 1 - latency/margin - SAFETY_SHARE))   (latency known)
 * ```
 *
 * A frame that offers NO overrun on an axis (a frame from the previous orientation while the canvas
 * already has the new one) has no margin to spend, so the request is unconditional there — the same
 * rule the non-negative-margin requirement states (spec: `smooth-follow` — A frame from the previous
 * orientation yields a zero offset instead of an error).
 *
 * The threshold always leaves at least `(1 - MAX_SHARE)` of the margin in reserve, so a frame is in
 * flight before the margin runs out; a known latency that would need more than that reserve
 * (a render slower than 45 % of the margin) lowers the threshold, and [MIN_SHARE] is the floor — a
 * render slower than the margin the floor leaves cannot be fixed by requesting earlier, and the
 * caller's clamp stays the last resort for it. [SAFETY_SHARE] keeps a tenth of the margin between
 * the landing and the margin, so an underestimated latency does not land exactly on the clamp.
 *
 * One request is outstanding at a time ([shouldRequest] returns true once per landing, and re-arms
 * itself after [REQUEST_TIMEOUT_MS] if no landing is observed — a request whose frame never lands
 * would otherwise pin the display at the margin forever, which the 2026-10-12 device run showed on
 * an emulator whose render is slower than the whole margin), so the render debounce is the only rate
 * limit the caller needs.
 *
 * Stateful and UI-thread-confined, like the display loop that owns it.
 */
class FollowRefreshPolicy {

    /** True while a request has been issued and its frame has not been observed yet. */
    var outstanding: Boolean = false
        private set

    private var requestedAtMs: Long = 0L

    /** The drift magnitude (px) at which the next frame must be requested. */
    fun thresholdPx(marginPx: Double, latencyPx: Double? = null): Double {
        val margin = if (marginPx.isNaN() || marginPx <= 0.0) return 0.0 else marginPx
        val share = if (latencyPx == null || latencyPx.isNaN() || latencyPx < 0.0) {
            MAX_SHARE
        } else {
            (1.0 - latencyPx / margin - SAFETY_SHARE).coerceIn(MIN_SHARE, MAX_SHARE)
        }
        return share * margin
    }

    /**
     * True when the caller must request a new follow frame now: the drift has reached the
     * threshold and no request of ours is outstanding. At most one request is ever outstanding.
     */
    /**
     * True when the caller must request a new follow frame now: the drift has reached the
     * threshold and no request of ours is still live. [nowMs] is the caller's clock; a request whose
     * frame has not been observed within [REQUEST_TIMEOUT_MS] stops blocking (it may have been
     * dropped, or the render may simply be slower than the throttle window).
     */
    fun shouldRequest(
        driftPx: Double,
        marginPx: Double,
        nowMs: Long = 0L,
        latencyPx: Double? = null
    ): Boolean {
        if (outstanding && nowMs - requestedAtMs < REQUEST_TIMEOUT_MS) return false
        // No margin to spend: the frame itself must be replaced (previous orientation, or a canvas
        // grown past the frame) — request it rather than waiting for a threshold.
        if (marginPx.isNaN() || marginPx <= 0.0) {
            markRequested(nowMs)
            return true
        }
        val limit = thresholdPx(marginPx, latencyPx)
        if (kotlin.math.abs(driftPx) < limit) return false
        markRequested(nowMs)
        return true
    }

    private fun markRequested(nowMs: Long) {
        outstanding = true
        requestedAtMs = nowMs
    }

    /** The requested frame has landed (the display loop saw a new viewport): arm the threshold. */
    fun onFrameLanded() {
        outstanding = false
    }

    /** Follow disengaged / display reset: forget the outstanding request. */
    fun reset() {
        outstanding = false
        requestedAtMs = 0L
    }

    companion object {
        /**
         * Fraction of the margin at which the next frame is requested when the render latency is
         * unknown: it leaves `(1 - MAX_SHARE)` of the margin for the render to land.
         */
        const val MAX_SHARE = 0.55

        /**
         * Fraction of the margin kept as a safety band between the landing of a requested frame and
         * the margin itself: the render latency is a p95 estimate, and a frame that lands exactly on
         * the clamp freezes the display for the frames in between.
         */
        const val SAFETY_SHARE = 0.10

        /**
         * Floor for the threshold share: a render slower than the margin it leaves cannot be helped
         * by requesting earlier — the display clamps at the margin as its last resort.
         */
        const val MIN_SHARE = 0.25

        /**
         * How long a request blocks further requests without an observed landing: a frame the
         * caller never sees is either dropped or slower than this window, and blocking on it would
         * leave the display pinned at the margin (measured on the emulator, 2026-10-12).
         */
        const val REQUEST_TIMEOUT_MS = 2_000L
    }
}
