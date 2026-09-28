package com.naviveylin.core

/**
 * Detects a fix that has aged out of usefulness (spec: `gps-fix-quality` — Fix availability and
 * quality tiers).
 *
 * A missing update does not by itself mean a missing fix: the provider goes silent at standstill
 * (minimum-distance throttling, `LocationService.MIN_DISTANCE_M = 5.0f`), so a fix's timestamp ages
 * while the position stays perfectly usable. The limit is therefore long — at least the standstill
 * silence a fix must survive (30 s), at most the point where a live-but-silent source (tunnel,
 * garage) must stop being presented as a good fix (60 s).
 *
 * Framework-free, like [SpeedStaleness]: the app's state holders share one rule instead of each
 * deciding for itself when a fix stops counting. The "no fix was ever received" case is the
 * caller's `null` branch, expressed here as `fixTimeMs <= 0L` never being aged out.
 */
object FixFreshness {

    /** Fixes this old are no longer a current position — quality falls to NONE. */
    const val FIX_AGE_LIMIT_MS = 60_000L

    /** True when [fixTimeMs] lies beyond the age limit. `fixTimeMs <= 0` (never had a fix) is false. */
    fun isAgedOut(fixTimeMs: Long, nowMs: Long, limitMs: Long = FIX_AGE_LIMIT_MS): Boolean =
        fixTimeMs > 0L && nowMs - fixTimeMs > limitMs
}
