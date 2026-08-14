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
    val errorMessage: String? = null
)
