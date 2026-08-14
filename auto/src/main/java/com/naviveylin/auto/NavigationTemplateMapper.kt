package com.naviveylin.auto

import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState

/**
 * Pure mapping functions for converting navigation state to Android Auto template values.
 * Extracted for testability — no Android framework dependencies.
 */
object NavigationTemplateMapper {

    /** Map [TurnType] to [Maneuver] type constant. */
    fun maneuverTypeFromTurnType(turnType: TurnType): Int {
        return when (turnType) {
            TurnType.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
            TurnType.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            TurnType.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
            TurnType.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
            TurnType.SLIGHTLY_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            TurnType.SLIGHTLY_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            TurnType.STRAIGHT_ON -> Maneuver.TYPE_STRAIGHT
            TurnType.ROUNDABOUT_ENTER -> Maneuver.TYPE_ROUNDABOUT_ENTER_CW
            TurnType.ROUNDABOUT_LEAVE -> Maneuver.TYPE_ROUNDABOUT_EXIT_CW
            TurnType.START -> Maneuver.TYPE_DEPART
            TurnType.TARGET_REACHED -> Maneuver.TYPE_DESTINATION
            else -> Maneuver.TYPE_STRAIGHT
        }
    }

    /** Map [LaneTurn] to [LaneDirection] shape constant. */
    fun laneDirectionShapeFromLaneTurn(turn: LaneTurn?): Int {
        return when (turn) {
            LaneTurn.LEFT -> LaneDirection.SHAPE_NORMAL_LEFT
            LaneTurn.RIGHT -> LaneDirection.SHAPE_NORMAL_RIGHT
            LaneTurn.STRAIGHT_ON -> LaneDirection.SHAPE_STRAIGHT
            LaneTurn.SHARP_LEFT -> LaneDirection.SHAPE_SHARP_LEFT
            LaneTurn.SHARP_RIGHT -> LaneDirection.SHAPE_SHARP_RIGHT
            LaneTurn.SLIGHTLY_LEFT -> LaneDirection.SHAPE_SLIGHT_LEFT
            LaneTurn.SLIGHTLY_RIGHT -> LaneDirection.SHAPE_SLIGHT_RIGHT
            LaneTurn.MERGE_TO_LEFT -> LaneDirection.SHAPE_SLIGHT_LEFT
            LaneTurn.MERGE_TO_RIGHT -> LaneDirection.SHAPE_SLIGHT_RIGHT
            LaneTurn.UNKNOWN -> LaneDirection.SHAPE_UNKNOWN
            null -> LaneDirection.SHAPE_UNKNOWN
            else -> LaneDirection.SHAPE_UNKNOWN
        }
    }

    /**
     * Determine if the displayed fields changed enough to warrant a template re-render.
     * Throttles invalidations to avoid jank from 1Hz GPS updates.
     */
    fun hasStateChanged(oldState: NavigationState?, newState: NavigationState): Boolean {
        val old = oldState ?: return true
        return old.isNavigating != newState.isNavigating ||
                old.nextInstruction?.distanceTo != newState.nextInstruction?.distanceTo ||
                old.nextInstruction?.turnType != newState.nextInstruction?.turnType ||
                old.nextInstruction?.description != newState.nextInstruction?.description ||
                old.remainingDistance.toLong() != newState.remainingDistance.toLong() ||
                old.etaMillis / 1000 != newState.etaMillis / 1000 ||
                old.currentSpeedKmH.toInt() != newState.currentSpeedKmH.toInt() ||
                old.isRerouting != newState.isRerouting ||
                old.laneCount != newState.laneCount ||
                old.laneTurns != newState.laneTurns
    }
}
