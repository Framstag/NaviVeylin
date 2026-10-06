package com.naviveylin.core

import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteInstruction
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
    val nextInstruction: RouteInstruction? = null,
    val instructions: List<RouteInstruction> = emptyList(),
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
    // Lane guidance
    val laneOneway: Boolean = false,
    val laneCount: Int = 0,
    val laneSuggested: Boolean = false,
    val laneSuggestedFrom: Int = 0,
    val laneSuggestedTo: Int = 0,
    val laneTurns: List<LaneTurn> = emptyList(),
    // Error message to display on car screen (e.g., GPS missing, route failure)
    val errorMessage: String? = null,
    // Surface the error belongs to (spec: `navigation-engine` — Errors carry the
    // surface that caused them). Null when no error is set; ENGINE means the
    // engine's own failure and is presented everywhere. Default: null.
    val errorOrigin: SurfaceOrigin? = null,
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
     * Whether the current [errorMessage] concerns [surface]: the surface's own
     * error or an engine-wide one. A surface never presents an error another
     * surface caused (spec: `navigation-engine` — Errors carry the surface that
     * caused them). An error without an origin is treated as engine-wide, so a
     * producer that does not set one is never silently hidden.
     */
    fun errorAppliesTo(surface: SurfaceOrigin): Boolean =
        errorMessage != null && (errorOrigin == null || errorOrigin == SurfaceOrigin.ENGINE ||
                errorOrigin == surface)
}
