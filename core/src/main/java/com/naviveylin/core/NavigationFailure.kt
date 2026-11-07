package com.naviveylin.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * Which end of a route a failure concerns (spec: `routing-failure-reporting` — A reason that
 * concerns one end of the route identifies that end).
 *
 * The two ends attach to the road network differently, so a failure that concerns one of them has
 * to say which: "the destination has no connection to the road network here" is actionable, "the
 * route failed" is not.
 */
enum class RouteEndpoint {
    START,
    TARGET
}

/**
 * Why a navigation failure happened (spec: `routing-failure-reporting` — A failed route calculation
 * reports a reason).
 *
 * The set is closed so a surface can word each member from its own resource instead of showing a
 * sentence produced somewhere else. `UNKNOWN` exists for a failure whose cause the producer could
 * not name (an older native library, or a cause a newer one added) and never means "no failure".
 *
 * The route-calculation members mirror the `KIND_*` constants of the native route callback one for
 * one; the remaining members are the navigation layer's own failures.
 */
enum class FailureReason {
    /**
     * The native routing layer named a cause this layer does not know. A surface shows the
     * unspecified-failure wording for it.
     */
    UNKNOWN,

    /**
     * No routing data could be used for the calculation: no map database is loaded, or the routing
     * service could not open the loaded ones. The map data is what can fix this.
     */
    ROUTING_DATA_UNAVAILABLE,

    /**
     * The routing data was not loaded yet: the database open has been asked for but its instance list
     * is still empty, so the same calculation succeeds a moment later. Nothing about the map data is
     * wrong, so no map-manager action belongs to it.
     */
    DATABASE_NOT_LOADED,

    /** The start point could not be attached to the road network. */
    START_UNROUTABLE,

    /** The destination could not be attached to the road network. */
    TARGET_UNROUTABLE,

    /** The two points are not connected in the installed map data. */
    NO_ROUTE_FOUND,

    /** Part of the map data could not be read, so the route could not be calculated. */
    ROUTING_DATA_FAILURE,

    /** The routing work failed outside the router itself. */
    ENGINE_FAILURE,

    /** No position is available yet, so navigation cannot start. */
    GPS_SIGNAL_MISSING,

    /** Navigation needs the precise location grant. */
    PRECISE_LOCATION_REQUIRED
}

/**
 * A failure the navigation layer raises for a surface (spec: `routing-failure-reporting` — Each
 * reason is worded by the surface).
 *
 * The value carries the cause and the values a wording needs, never the wording itself: a surface
 * renders it from its own string resource, in the device language, so the same cause reads the same
 * way on the phone and on the car and neither surface shows a sentence another layer composed.
 *
 * @param reason why the failure happened
 * @param origin the surface this failure concerns; [SurfaceOrigin.ENGINE] means it is the engine's
 *   own failure and every surface presenting navigation shows it (spec: `navigation-engine` — Errors
 *   carry the surface that caused them)
 * @param endpoint the end of the route the failure concerns, when it concerns one
 * @param nearestRoadDistanceMeters the distance from that end to the nearest road the router could
 *   use, when the router reported one
 * @param surfaceMessage a message the producing surface already rendered from its own resource, for a
 *   failure whose wording differs per surface **by an existing decision** — an unreadable shared link
 *   reads differently on the phone and on the car (`guidelines/UI.md` §1, its parity table). Its reason
 *   is then [FailureReason.UNKNOWN], because a surface that owns the wording owns the cause's name too;
 *   null for every failure the rendering surface words itself from the shared resource.
 * @param detail a description of the failure for the log, never display text: a native fault's
 *   message, or the raw detail the routing layer sent. It is not shown to a user.
 */
data class NavigationFailure(
    val reason: FailureReason,
    val origin: SurfaceOrigin = SurfaceOrigin.ENGINE,
    val endpoint: RouteEndpoint? = null,
    val nearestRoadDistanceMeters: Double? = null,
    val surfaceMessage: String? = null,
    val detail: String? = null
) {
    /**
     * Whether this failure concerns [surface]: the surface's own failure or an engine-wide one. A
     * surface never presents a failure another surface caused (spec: `navigation-engine` — Errors
     * carry the surface that caused them).
     */
    fun appliesTo(surface: SurfaceOrigin): Boolean =
        origin == SurfaceOrigin.ENGINE || origin == surface
}

/**
 * The string resource that words [failure] (spec: `routing-failure-reporting` — Each reason is
 * worded by the surface).
 *
 * One rule for both variants, so the phone and the car cannot label the same cause differently
 * (`guidelines/UI.md` §1). A failure that names one end of the route and knows how far the nearest
 * usable road is gets the variant that states that distance.
 */
@StringRes
fun navigationFailureWordingRes(failure: NavigationFailure): Int {
    val hasDistance = failure.nearestRoadDistanceMeters != null

    return when (failure.reason) {
        FailureReason.ROUTING_DATA_UNAVAILABLE -> R.string.navigation_failure_routing_data_unavailable
        FailureReason.DATABASE_NOT_LOADED -> R.string.navigation_failure_database_not_loaded
        FailureReason.START_UNROUTABLE -> if (hasDistance) {
            R.string.navigation_failure_start_unroutable_near
        } else {
            R.string.navigation_failure_start_unroutable
        }

        FailureReason.TARGET_UNROUTABLE -> if (hasDistance) {
            R.string.navigation_failure_target_unroutable_near
        } else {
            R.string.navigation_failure_target_unroutable
        }

        FailureReason.NO_ROUTE_FOUND -> R.string.navigation_failure_no_route_found
        FailureReason.ROUTING_DATA_FAILURE -> R.string.navigation_failure_routing_data_failure
        FailureReason.ENGINE_FAILURE -> R.string.navigation_failure_engine_failure
        FailureReason.GPS_SIGNAL_MISSING -> R.string.navigation_failure_gps_signal_missing
        FailureReason.PRECISE_LOCATION_REQUIRED -> R.string.location_precise_required_navigation
        // A failure whose own surface words it carries that wording; this is the safety net for a
        // producer that raises the reason without one, and it never reads as a cause nobody can act on.
        FailureReason.UNKNOWN -> R.string.navigation_failure_unspecified
    }
}

/**
 * Whether [failure]'s cause is something the map manager can fix (spec: `routing-failure-reporting` — A
 * reason whose cause the user can act on offers the action).
 *
 * Missing, unopenable or unreadable routing data is what a different or re-downloaded map changes, so
 * those reasons offer the map manager on the phone; a route that simply does not exist between two
 * points has no map-manager fix. Routing data that is still loading is none of these: trying again is
 * the whole of what clears it, so it offers no action. One rule for both variants, because the car
 * states the same fact (it must not open a phone screen).
 */
fun NavigationFailure.hasMapManagerAction(): Boolean =
    reason == FailureReason.ROUTING_DATA_UNAVAILABLE ||
        reason == FailureReason.ROUTING_DATA_FAILURE ||
        reason == FailureReason.NO_ROUTE_FOUND

/**
 * The text a surface shows for [failure], in the device language.
 *
 * A failure that carries a message its own surface worded ([NavigationFailure.surfaceMessage]) is
 * shown as it is; every other failure is worded here from the shared resource, so the same cause
 * reads the same way on the phone and on the car.
 *
 * @param unitMetersRes the calling surface's own `distance_unit_m` resource
 * @param unitKilometersRes the calling surface's own `distance_unit_km` resource — each surface owns
 *   its distance units, so the shared wording takes them from the caller instead of reaching for a
 *   resource this module does not have
 */
fun navigationFailureText(context: Context,
                          failure: NavigationFailure,
                          unitMetersRes: Int,
                          unitKilometersRes: Int): String {
    failure.surfaceMessage?.let { return it }

    val wording = navigationFailureWordingRes(failure)
    val meters = failure.nearestRoadDistanceMeters
        ?: return context.getString(wording)

    val unit = if (distanceUsesKilometers(meters)) unitKilometersRes else unitMetersRes

    return context.getString(wording, context.getString(unit, formatDistanceNumber(meters)))
}
