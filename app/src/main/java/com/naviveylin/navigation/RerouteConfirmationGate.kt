package com.naviveylin.navigation

/**
 * Confirmation state machine for reroute triggers (spec: reroute-trigger).
 *
 * Splits the two concerns the old single 30s floor conflated:
 * - first-trigger latency: [minConfirmCount] consecutive off-route reports
 *   within [confirmWindowMs], at least [minOffRouteDurationMs] after the first
 *   report of the episode
 * - cascade protection: [cooldownMs] after a confirmed reroute before the next
 *   confirmation is accepted
 *
 * Fast path: a deviation larger than [fastPathDistanceM] on the first report of
 * an episode confirms immediately (the native engine reports every ~5s while
 * off-route, so this fires ~5s after detection).
 *
 * Pure Kotlin — no Android dependencies — so the state machine is unit-testable
 * with plain JUnit. The native engine's own guards (GPS accuracy, tunnel
 * no-signal) are applied by the caller before invoking this gate.
 */
class RerouteConfirmationGate(
    private val now: () -> Long = System::currentTimeMillis,
    private val minConfirmCount: Int = 2,
    private val minOffRouteDurationMs: Long = 10_000L,
    private val confirmWindowMs: Long = 60_000L,
    private val cooldownMs: Long = 25_000L,
    private val fastPathDistanceM: Double = 50.0
) {

    private var confirmCount = 0
    private var confirmStart = 0L
    private var lastConfirmedRerouteTime = Long.MIN_VALUE

    /** Reset all state (fresh route / navigation stop). */
    fun reset() {
        confirmCount = 0
        confirmStart = 0L
        lastConfirmedRerouteTime = Long.MIN_VALUE
    }

    /**
     * True when this report is the first of an episode and the caller should
     * compute the deviation distance for the fast path. False during cooldown
     * or after the episode has started.
     */
    fun needsDeviation(): Boolean = confirmCount == 0 && !inCooldown()

    /**
     * Process one off-route report.
     *
     * @param deviationMeters distance from the reported position to the route
     *   polyline, or null/NaN when not computed (normal path).
     * @return true when a reroute should be triggered.
     */
    fun onRequest(deviationMeters: Double?): Boolean {
        val nowMs = now()
        if (inCooldown(nowMs)) {
            confirmCount = 0
            confirmStart = 0L
            return false
        }
        // Fast path: clear deviation on the first report of an episode.
        if (deviationMeters != null && deviationMeters > fastPathDistanceM && confirmCount == 0) {
            confirmCount = 0
            confirmStart = 0L
            lastConfirmedRerouteTime = nowMs
            return true
        }
        if (confirmCount == 0) {
            // First report of an episode
            confirmCount = 1
            confirmStart = nowMs
            return false
        }
        if (nowMs - confirmStart > confirmWindowMs) {
            // Window expired, start fresh
            confirmCount = 1
            confirmStart = nowMs
            return false
        }
        confirmCount++
        if (confirmCount < minConfirmCount) {
            return false
        }
        if (nowMs - confirmStart < minOffRouteDurationMs) {
            return false
        }
        confirmCount = 0
        confirmStart = 0L
        lastConfirmedRerouteTime = nowMs
        return true
    }

    private fun inCooldown(nowMs: Long = now()): Boolean =
        lastConfirmedRerouteTime != Long.MIN_VALUE && nowMs - lastConfirmedRerouteTime < cooldownMs
}
