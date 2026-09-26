package com.naviveylin.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * The location precision the user actually granted, derived from the two runtime
 * grants.
 *
 * This is the one rule both surfaces read (spec: `location-permissions` — The
 * granted accuracy class governs provider updates), so the phone map path and the
 * car session agree on what a grant allows. It is deliberately stateless and
 * uncached: the user can change the grant in system settings while the app lives,
 * so every read reflects the current state.
 */
enum class AccuracyClass {
    /** Neither grant is held — no provider updates, no position-based feature. */
    NONE,

    /** Only the approximate grant is held — the map works, turn-by-turn does not. */
    APPROXIMATE,

    /** The precise grant is held. */
    PRECISE
}

/**
 * Reads the granted location precision (spec: `location-permissions` — Runtime
 * permission request / The granted accuracy class governs provider updates).
 *
 * A granted precise permission implies usable precision on every supported API
 * level, so [AccuracyClass.PRECISE] is reported even if the coarse grant is
 * missing (possible below API 31); an approximate-only grant is
 * [AccuracyClass.APPROXIMATE] rather than a failure state.
 */
object LocationGrant {

    /** The precision class currently granted to this app. */
    fun accuracyClass(context: Context): AccuracyClass = when {
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> AccuracyClass.PRECISE
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> AccuracyClass.APPROXIMATE
        else -> AccuracyClass.NONE
    }

    /** True when any location grant is held (approximate is enough for the map). */
    fun isGranted(context: Context): Boolean = accuracyClass(context) != AccuracyClass.NONE

    /** True when the precise grant is held — the precondition for starting a route. */
    fun hasPrecise(context: Context): Boolean =
        accuracyClass(context) == AccuracyClass.PRECISE

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
