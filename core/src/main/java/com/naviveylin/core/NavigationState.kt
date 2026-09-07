package com.naviveylin.core

import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteInstruction

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
    // Wall-clock time (epoch millis) when navigation started; reference point
    // for elapsed-time progress (routing-progress-indicator). 0 when not navigating.
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
    // Destination identity, retained from navigation start for display on the
    // car screen (name/address when known, otherwise coordinates only).
    val destLat: Double = Double.NaN,
    val destLon: Double = Double.NaN,
    val destinationName: String? = null,
    // Route polyline for map rendering (native renderer draws the route with
    // the stylesheet "_route" style). Null when not navigating.
    val routeLats: DoubleArray? = null,
    val routeLons: DoubleArray? = null
)
