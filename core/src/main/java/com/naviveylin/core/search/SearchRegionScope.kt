package com.naviveylin.core.search

import com.naviveylin.core.haversineDistanceMeters

/** Position reference a search region is resolved for. */
data class RegionFixReference(
    val lat: Double,
    val lon: Double,
    /** Horizontal accuracy in meters; a negative value means "unknown". */
    val accuracyMeters: Double
)

/**
 * The admin region currently scoping a search (spec: `location-search` —
 * Search scoped by current admin region; spec: `auto-search` — Search scoped by
 * the car position's admin region).
 *
 * [handle] is the backend's region handle, [lat]/[lon] the position it was
 * resolved from (kept to decide reuse) and [name] the region name a surface may
 * display. An empty scope ([NO_HANDLE], NaN position, null name) means the
 * search runs unconstrained.
 */
data class SearchRegionScope(
    val handle: Long = NO_HANDLE,
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
    val name: String? = null
) {
    companion object {
        /** Backend value meaning "no admin region" — the search is unconstrained. */
        const val NO_HANDLE = 0L
    }
}

/** Why [resolveRegionScope] returned the scope it returned — for diagnostics and tests. */
enum class RegionScopeDecision {
    /** No fix, a non-finite position, or an accuracy coarser than the gate: the scope was released. */
    FIX_UNUSABLE,

    /** The previously resolved region still applies — no backend call was made. */
    REUSED,

    /** A new region was resolved for the fix. */
    RESOLVED,

    /** The backend reported no region at the position: the search runs unconstrained. */
    RESOLVE_FAILED
}

/** The scope to use and the reason it was chosen. */
data class RegionScopeOutcome(
    val scope: SearchRegionScope,
    val decision: RegionScopeDecision
)

/**
 * Scopes a location search with the admin region containing the last known
 * position (spec: `location-search` — Search scoped by current admin region,
 * Admin region follows user movement; spec: `auto-search` — Car region scope
 * follows the car's movement).
 *
 * One rule, one behaviour: the phone search panel and the car search call this,
 * so a query naming a location without its region qualifier resolves on both
 * surfaces identically (spec: `auto-search` — Car and phone region scoping
 * parity).
 *
 * Pure and framework-free — the backend calls arrive as lambdas, so the rule is
 * host-testable and can run inside the caller's existing background search
 * block. The lambdas must not throw: callers wrap the backend calls and map a
 * failure to [SearchRegionScope.NO_HANDLE] or `null`.
 *
 * @param fix the last known position, or null when none is available
 * @param previous the scope currently in use
 * @param releaseRegion releases a handle the caller no longer needs
 * @param resolveRegion resolves the region handle containing a position (0 when none)
 * @param scopeNameOf the display name of the search scope region (parent when expanded)
 * @param regionNameOf the display name of the resolved region, the fallback for the scope name
 * @param maxAccuracyMeters coarse fixes beyond this accuracy never scope a search
 * @param movementThresholdMeters distance within which the resolved region is reused
 */
fun resolveRegionScope(
    fix: RegionFixReference?,
    previous: SearchRegionScope,
    releaseRegion: (Long) -> Unit,
    resolveRegion: (lat: Double, lon: Double) -> Long,
    scopeNameOf: (Long) -> String?,
    regionNameOf: (Long) -> String?,
    maxAccuracyMeters: Double = DEFAULT_MAX_ACCURACY_METERS,
    movementThresholdMeters: Double = DEFAULT_MOVEMENT_THRESHOLD_METERS
): RegionScopeOutcome {
    val usable = fix != null &&
        fix.lat.isFinite() &&
        fix.lon.isFinite() &&
        fix.accuracyMeters <= maxAccuracyMeters
    if (!usable) {
        releaseHeldScope(previous, releaseRegion)
        return RegionScopeOutcome(SearchRegionScope(), RegionScopeDecision.FIX_UNUSABLE)
    }

    // No age cap: the region containing a position only changes when the
    // position moves significantly, so the last known position stays valid for
    // scoping however old the fix is.
    val reusable = previous.handle != SearchRegionScope.NO_HANDLE &&
        previous.lat.isFinite() &&
        previous.lon.isFinite() &&
        haversineDistanceMeters(previous.lat, previous.lon, fix.lat, fix.lon) <= movementThresholdMeters
    if (reusable) {
        return RegionScopeOutcome(previous, RegionScopeDecision.REUSED)
    }

    releaseHeldScope(previous, releaseRegion)
    val handle = resolveRegion(fix.lat, fix.lon)
    if (handle == SearchRegionScope.NO_HANDLE) {
        return RegionScopeOutcome(SearchRegionScope(), RegionScopeDecision.RESOLVE_FAILED)
    }
    return RegionScopeOutcome(
        scope = SearchRegionScope(
            handle = handle,
            lat = fix.lat,
            lon = fix.lon,
            name = scopeNameOf(handle) ?: regionNameOf(handle)
        ),
        decision = RegionScopeDecision.RESOLVED
    )
}

/** Accuracy a fix must be at least as good as to scope a search. */
const val DEFAULT_MAX_ACCURACY_METERS: Double = 50.0

/** Movement below which the resolved region is reused instead of re-resolved. */
const val DEFAULT_MOVEMENT_THRESHOLD_METERS: Double = 500.0

private fun releaseHeldScope(previous: SearchRegionScope, releaseRegion: (Long) -> Unit) {
    if (previous.handle != SearchRegionScope.NO_HANDLE) {
        releaseRegion(previous.handle)
    }
}
