package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteCallback
import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.NavigationState
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.core.SurfaceOrigin

/**
 * The failure the phone must present, out of the shared navigation state (spec: `navigation-engine` —
 * Errors carry the surface that caused them, and *Engine-wide error reaches the phone*).
 *
 * A failure that names another surface is not the phone's to show: a car-raised failure appears on the car
 * only, which is the other half of the same rule. Extracted from the carrier's composition so the rule is
 * asserted without standing up a map screen, whose unit harness needs three Hilt view models and a
 * renderer initialised.
 */
internal fun phoneFailureOf(state: NavigationState): NavigationFailure? =
    state.failure?.takeIf { it.appliesTo(SurfaceOrigin.PHONE) }

/**
 * The failure a native route-callback kind names (spec: `routing-failure-reporting` — A failed route
 * calculation reports a reason).
 *
 * The kind is an `int` on the JNI boundary and is one of [RouteCallback]'s `KIND_*` constants; this
 * turns it into the typed failure a surface words. A kind this build does not know (a newer native
 * library, or a cause it added) becomes [FailureReason.UNKNOWN], so an unknown cause is shown as an
 * unspecified failure rather than as nothing.
 *
 * @param kind one of [RouteCallback]'s `KIND_*` constants
 * @param detail the routing layer's description of the failure; for the log, never display text
 * @param nearestRoadDistanceMeters how far the failing end is from the nearest road the router could
 *   use, or `NaN` when the router did not find one
 * @param origin the surface the failure concerns; [SurfaceOrigin.ENGINE] for the routing layer's own
 *   failures, which every surface presenting navigation shows
 */
fun navigationFailureOf(
    kind: Int,
    detail: String? = null,
    nearestRoadDistanceMeters: Double = Double.NaN,
    origin: SurfaceOrigin = SurfaceOrigin.ENGINE
): NavigationFailure {
    val reason = when (kind) {
        RouteCallback.KIND_ROUTING_DATA_UNAVAILABLE -> FailureReason.ROUTING_DATA_UNAVAILABLE
        RouteCallback.KIND_DATABASE_NOT_LOADED -> FailureReason.DATABASE_NOT_LOADED
        RouteCallback.KIND_START_UNROUTABLE -> FailureReason.START_UNROUTABLE
        RouteCallback.KIND_TARGET_UNROUTABLE -> FailureReason.TARGET_UNROUTABLE
        RouteCallback.KIND_NO_ROUTE_FOUND -> FailureReason.NO_ROUTE_FOUND
        RouteCallback.KIND_ROUTING_DATA_FAILURE -> FailureReason.ROUTING_DATA_FAILURE
        RouteCallback.KIND_ENGINE_FAILURE -> FailureReason.ENGINE_FAILURE
        else -> FailureReason.UNKNOWN
    }

    return NavigationFailure(
        reason = reason,
        origin = origin,
        endpoint = when (reason) {
            FailureReason.START_UNROUTABLE -> RouteEndpoint.START
            FailureReason.TARGET_UNROUTABLE -> RouteEndpoint.TARGET
            else -> null
        },
        // A distance of zero means the endpoint is on the road, so the router would have attached
        // it: a non-positive or non-finite value is "unknown", never "the nearest road is 0 m away".
        nearestRoadDistanceMeters = nearestRoadDistanceMeters
            .takeIf { it.isFinite() && it > 0.0 },
        detail = detail
    )
}
