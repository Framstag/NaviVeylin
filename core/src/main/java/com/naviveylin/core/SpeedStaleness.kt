package com.naviveylin.core

/**
 * Detects a stale speed feed (spec: gps-speed-priority / speed-spike-filtering).
 *
 * The location provider goes silent at standstill (min-distance throttling),
 * so the consumers' last-fix timestamp ages. Once it exceeds [STALE_SPEED_MS]
 * the displayed speed SHALL read 0 instead of the last delivered value.
 *
 * Framework-free so the Android Auto screens ([:auto], which depends only on
 * [:core]) can share the same rule as the phone state holders ([app]).
 */
object SpeedStaleness {

    /** Speed values this old are stale — display decays to 0 km/h. */
    const val STALE_SPEED_MS = 3_000L

    /** True when [lastFixTimeMs] is beyond the staleness window. */
    fun isStale(lastFixTimeMs: Long, nowMs: Long): Boolean =
        lastFixTimeMs > 0L && nowMs - lastFixTimeMs > STALE_SPEED_MS
}
