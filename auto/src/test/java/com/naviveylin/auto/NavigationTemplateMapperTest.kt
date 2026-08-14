package com.naviveylin.auto

import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTemplateMapperTest {

    // --- maneuverTypeFromTurnType ---

    @Test
    fun maneuverType_left() {
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.LEFT))
    }

    @Test
    fun maneuverType_right() {
        assertEquals(Maneuver.TYPE_TURN_NORMAL_RIGHT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.RIGHT))
    }

    @Test
    fun maneuverType_sharpLeft() {
        assertEquals(Maneuver.TYPE_TURN_SHARP_LEFT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.SHARP_LEFT))
    }

    @Test
    fun maneuverType_sharpRight() {
        assertEquals(Maneuver.TYPE_TURN_SHARP_RIGHT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.SHARP_RIGHT))
    }

    @Test
    fun maneuverType_slightLeft() {
        assertEquals(Maneuver.TYPE_TURN_SLIGHT_LEFT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.SLIGHTLY_LEFT))
    }

    @Test
    fun maneuverType_slightRight() {
        assertEquals(Maneuver.TYPE_TURN_SLIGHT_RIGHT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.SLIGHTLY_RIGHT))
    }

    @Test
    fun maneuverType_straight() {
        assertEquals(Maneuver.TYPE_STRAIGHT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.STRAIGHT_ON))
    }

    @Test
    fun maneuverType_roundaboutEnter() {
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_CW,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.ROUNDABOUT_ENTER))
    }

    @Test
    fun maneuverType_roundaboutLeave() {
        assertEquals(Maneuver.TYPE_ROUNDABOUT_EXIT_CW,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.ROUNDABOUT_LEAVE))
    }

    @Test
    fun maneuverType_depart() {
        assertEquals(Maneuver.TYPE_DEPART,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.START))
    }

    @Test
    fun maneuverType_destination() {
        assertEquals(Maneuver.TYPE_DESTINATION,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.TARGET_REACHED))
    }

    @Test
    fun maneuverType_unknownDefaultsToStraight() {
        assertEquals(Maneuver.TYPE_STRAIGHT,
            NavigationTemplateMapper.maneuverTypeFromTurnType(TurnType.MOTORWAY_ENTER))
    }

    // --- laneDirectionShapeFromLaneTurn ---

    @Test
    fun laneShape_left() {
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.LEFT))
    }

    @Test
    fun laneShape_right() {
        assertEquals(LaneDirection.SHAPE_NORMAL_RIGHT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.RIGHT))
    }

    @Test
    fun laneShape_straight() {
        assertEquals(LaneDirection.SHAPE_STRAIGHT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.STRAIGHT_ON))
    }

    @Test
    fun laneShape_sharpLeft() {
        assertEquals(LaneDirection.SHAPE_SHARP_LEFT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.SHARP_LEFT))
    }

    @Test
    fun laneShape_sharpRight() {
        assertEquals(LaneDirection.SHAPE_SHARP_RIGHT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.SHARP_RIGHT))
    }

    @Test
    fun laneShape_slightLeft() {
        assertEquals(LaneDirection.SHAPE_SLIGHT_LEFT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.SLIGHTLY_LEFT))
    }

    @Test
    fun laneShape_slightRight() {
        assertEquals(LaneDirection.SHAPE_SLIGHT_RIGHT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.SLIGHTLY_RIGHT))
    }

    @Test
    fun laneShape_mergeLeft() {
        assertEquals(LaneDirection.SHAPE_SLIGHT_LEFT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.MERGE_TO_LEFT))
    }

    @Test
    fun laneShape_mergeRight() {
        assertEquals(LaneDirection.SHAPE_SLIGHT_RIGHT,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.MERGE_TO_RIGHT))
    }

    @Test
    fun laneShape_nullDefaultsToUnknown() {
        assertEquals(LaneDirection.SHAPE_UNKNOWN,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(null))
    }

    @Test
    fun laneShape_unknownDefaultsToUnknown() {
        assertEquals(LaneDirection.SHAPE_UNKNOWN,
            NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(LaneTurn.UNKNOWN))
    }

    // --- hasStateChanged ---

    @Test
    fun hasStateChanged_returnsTrueForNullOldState() {
        assertTrue(NavigationTemplateMapper.hasStateChanged(null, NavigationState()))
    }

    @Test
    fun hasStateChanged_returnsFalseForIdenticalState() {
        val state = NavigationState(isNavigating = true, remainingDistance = 1000.0)
        assertFalse(NavigationTemplateMapper.hasStateChanged(state, state))
    }

    @Test
    fun hasStateChanged_detectsNavigatingChange() {
        val old = NavigationState(isNavigating = false)
        val new = NavigationState(isNavigating = true)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsDistanceChange() {
        val old = NavigationState(remainingDistance = 1000.0)
        val new = NavigationState(remainingDistance = 500.0)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsMeterLevelDistanceChange() {
        val old = NavigationState(remainingDistance = 1000.0)
        val new = NavigationState(remainingDistance = 999.4)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsReroutingChange() {
        val old = NavigationState(isRerouting = false)
        val new = NavigationState(isRerouting = true)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsLaneCountChange() {
        val old = NavigationState(laneCount = 2)
        val new = NavigationState(laneCount = 3)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_ignoresSameEtaWithinSecond() {
        val old = NavigationState(etaMillis = 1000)
        val new = NavigationState(etaMillis = 1500)
        assertFalse(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsEtaChangeAcrossSecond() {
        val old = NavigationState(etaMillis = 1000)
        val new = NavigationState(etaMillis = 2500)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsNextInstructionBecomingNull() {
        val old = NavigationState(
            nextInstruction = com.framstag.libosmscout.client.RouteInstruction(
                100.0, com.framstag.libosmscout.client.TurnType.LEFT,
                "Main St", "Turn left into Main St", "Turn left"
            )
        )
        val new = NavigationState(nextInstruction = null)
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsNextInstructionAppearing() {
        val old = NavigationState(nextInstruction = null)
        val new = NavigationState(
            nextInstruction = com.framstag.libosmscout.client.RouteInstruction(
                100.0, com.framstag.libosmscout.client.TurnType.LEFT,
                "Main St", "Turn left into Main St", "Turn left"
            )
        )
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }

    @Test
    fun hasStateChanged_detectsNextInstructionDescriptionChange() {
        val old = NavigationState(
            nextInstruction = com.framstag.libosmscout.client.RouteInstruction(
                100.0, com.framstag.libosmscout.client.TurnType.LEFT,
                "Main St", "Turn left into Main St", "Turn left"
            )
        )
        val new = NavigationState(
            nextInstruction = com.framstag.libosmscout.client.RouteInstruction(
                100.0, com.framstag.libosmscout.client.TurnType.RIGHT,
                "Elm St", "Turn right into Elm St", "Turn right"
            )
        )
        assertTrue(NavigationTemplateMapper.hasStateChanged(old, new))
    }
}
