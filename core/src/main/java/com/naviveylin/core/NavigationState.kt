package com.naviveylin.core

import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.Vehicle

/**
 * Surface an error is attributed to, so a surface can present only the errors
 * that concern it (spec: `navigation-engine` — Errors carry the surface that
 * caused them).
 *
 * [ENGINE] is an error of the navigation engine itself (route calculation
 * failed, no GPS fix) and is visible on every surface; [PHONE] and [CAR] are
 * errors raised on behalf of one surface (e.g. a car deep link whose query
 * resolved to nothing) and are presented only there.
 */
enum class SurfaceOrigin {
    ENGINE,
    PHONE,
    CAR
}

/**
 * A route calculation that is in flight (spec: `route-calculation-feedback` —
 * In-flight route calculation is part of the shared navigation state).
 *
 * [token] identifies the request and increases with every new calculation, so the
 * end of a calculation that a newer request superseded cannot clear the live one's
 * state. [percent] is the routing engine's progress through its search, 0..99, and
 * is null until the engine has reported one — 0 is a legal value, so it must not
 * double as "unknown".
 */
data class RouteCalculation(
    val token: Long,
    val destLat: Double,
    val destLon: Double,
    val percent: Int? = null
)

/**
 * Shared navigation state consumed by both the phone UI and Android Auto.
 */
data class NavigationState(
    val isNavigating: Boolean = false,
    val currentStepIndex: Int = 0,
    val nextStep: RouteStep? = null,
    val steps: List<RouteStep> = emptyList(),
    val remainingDistance: Double = 0.0,
    val totalDistance: Double = 0.0,
    val etaMillis: Long = 0L,
    // Wall-clock time (epoch millis) when navigation started; reference point for
    // the phone navigation status card's elapsed-time progress (MapCanvasScreen).
    // 0 when not navigating.
    val navigationStartTimeMillis: Long = 0L,
    val currentSpeedKmH: Double = Double.NaN,
    val maxSpeedKmH: Double = Double.NaN,
    val position: NavigationPosition? = null,
    val currentRoadInfo: CurrentRoadInfo? = null,
    val isRerouting: Boolean = false,
    val isOffRoute: Boolean = false,
    // Whether the destination of the running session was reached (spec:
    // `navigation-engine` — Arrival is part of the shared navigation state). Set when the
    // native engine reports the target reached, kept while a reroute replaces the route,
    // and cleared by a new navigation and by a stop. In-memory only, and it carries no
    // coordinates: a surface reads the fact, never the place.
    val hasReachedDestination: Boolean = false,
    // Lane guidance
    val laneOneway: Boolean = false,
    val laneCount: Int = 0,
    val laneSuggested: Boolean = false,
    val laneSuggestedFrom: Int = 0,
    val laneSuggestedTo: Int = 0,
    val laneTurns: List<LaneTurn> = emptyList(),
    // The failure the navigation layer raised, as a typed value a surface words and acts on (spec:
    // `routing-failure-reporting` — Each reason is worded by the surface): it carries the cause, the
    // failing end, the distance to the nearest usable road and the originating surface, so a surface
    // shows the wording for the cause instead of a sentence another layer composed, and presents only
    // the failures that concern it. Null while no failure is set. Default: null.
    val failure: NavigationFailure? = null,
    // Vehicle profile the active navigation was acquired with, retained by the
    // engine so a reroute re-acquires with the same profile without a surface
    // (spec: `navigation-engine` — Route acquisition independent of a surface
    // UI). Null while not navigating. Default: null.
    val vehicle: Vehicle? = null,
    // Destination identity, retained from navigation start for display on the
    // car screen (name/address when known, otherwise coordinates only).
    val destLat: Double = Double.NaN,
    val destLon: Double = Double.NaN,
    val destinationName: String? = null,
    // Route polyline for map rendering (native renderer draws the route with
    // the stylesheet "_route" style). Null when not navigating.
    val routeLats: DoubleArray? = null,
    val routeLons: DoubleArray? = null,
    // The route calculation in flight, if any (spec: `route-calculation-feedback`).
    // Null when none is running — also while navigating on an already calculated
    // route. Default: null.
    val calculation: RouteCalculation? = null
) {
    /**
     * Whether [failure] concerns [surface]: the failure itself carries its origin, so a surface needs
     * no second field to decide whether to present it (spec: `navigation-engine` — Errors carry the
     * surface that caused them).
     */
    fun failureAppliesTo(surface: SurfaceOrigin): Boolean =
        failure?.appliesTo(surface) == true
}
