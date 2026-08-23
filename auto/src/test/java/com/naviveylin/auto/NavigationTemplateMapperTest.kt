package com.naviveylin.auto

import android.graphics.Bitmap
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.core.graphics.drawable.IconCompat
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationTemplateMapperTest {

    private val testIcon: CarIcon by lazy {
        CarIcon.Builder(
            IconCompat.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        ).build()
    }

    private fun instr(distance: Double, type: TurnType, street: String) =
        RouteInstruction(distance, type, street, "desc", "short")

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

    // --- roundDistanceMeters (spec: auto/navigation-view — distance display) ---

    @Test
    fun roundDistanceMeters_showsActualValueUpTo50() {
        assertEquals(0.0, NavigationTemplateMapper.roundDistanceMeters(0.0), 0.01)
        assertEquals(45.0, NavigationTemplateMapper.roundDistanceMeters(45.0), 0.01)
        assertEquals(50.0, NavigationTemplateMapper.roundDistanceMeters(50.0), 0.01)
    }

    @Test
    fun roundDistanceMeters_multiplesOf50Between50And1km() {
        assertEquals(150.0, NavigationTemplateMapper.roundDistanceMeters(137.0), 0.01)
        assertEquals(100.0, NavigationTemplateMapper.roundDistanceMeters(124.0), 0.01)
        assertEquals(200.0, NavigationTemplateMapper.roundDistanceMeters(180.0), 0.01)
        assertEquals(1000.0, NavigationTemplateMapper.roundDistanceMeters(999.0), 0.01)
    }

    @Test
    fun roundDistanceMeters_oneDecimalAbove1km() {
        assertEquals(1200.0, NavigationTemplateMapper.roundDistanceMeters(1234.0), 0.01)
        assertEquals(1400.0, NavigationTemplateMapper.roundDistanceMeters(1350.0), 0.01)
        assertEquals(4300.0, NavigationTemplateMapper.roundDistanceMeters(4321.0), 0.01)
    }

    @Test
    fun distanceForDisplay_usesMetersBelow1kmAndKmAbove() {
        val meters = NavigationTemplateMapper.distanceForDisplay(350.0)
        assertEquals(350.0, meters.displayDistance, 0.01)
        assertEquals(Distance.UNIT_METERS, meters.displayUnit)

        val km = NavigationTemplateMapper.distanceForDisplay(16800.0)
        assertEquals(16.8, km.displayDistance, 0.01)
        assertEquals(Distance.UNIT_KILOMETERS, km.displayUnit)

        val edge = NavigationTemplateMapper.distanceForDisplay(999.0)
        assertEquals(1.0, edge.displayDistance, 0.01)
        assertEquals(Distance.UNIT_KILOMETERS, edge.displayUnit)
    }

    // --- stepForInstruction / lanes / routingInfo (spec: auto/navigation-view) ---

    @Test
    fun stepForInstruction_setsManeuverRoadAndCue() {
        val step = NavigationTemplateMapper.stepForInstruction(
            instr(350.0, TurnType.LEFT, "Main St"), testIcon
        )
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, step.maneuver!!.type)
        assertEquals("Main St", step.road.toString())
        assertEquals("short", step.cue.toString())
    }

    @Test
    fun stepForInstruction_hidesBlankStreet() {
        val step = NavigationTemplateMapper.stepForInstruction(
            instr(100.0, TurnType.RIGHT, ""), testIcon
        )
        assertNull(step.road)
        assertEquals("short", step.cue.toString())
    }

    @Test
    fun laneFromTurn_marksRecommended() {
        val recommended = NavigationTemplateMapper.laneFromTurn(LaneTurn.LEFT, true)
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT, recommended.directions[0].shape)
        assertTrue(recommended.directions[0].isRecommended)

        val plain = NavigationTemplateMapper.laneFromTurn(LaneTurn.RIGHT, false)
        assertFalse(plain.directions[0].isRecommended)
    }

    @Test
    fun routingInfoFromState_setsCurrentAndNextStep() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 1,
            instructions = listOf(
                instr(1000.0, TurnType.START, "Start St"),
                instr(800.0, TurnType.LEFT, "Main St"),
                instr(200.0, TurnType.RIGHT, "Elm St")
            )
        )
        val info = NavigationTemplateMapper.routingInfoFromState(
            state, { testIcon }, includeLanes = false
        )!!
        assertEquals(800.0, info.currentDistance!!.displayDistance, 0.01)
        assertEquals("Main St", info.currentStep!!.road.toString())
        // Next-next turn is the step after the current one.
        assertEquals("Elm St", info.nextStep!!.road.toString())
    }

    @Test
    fun routingInfoFromState_prefersLiveNextInstructionDistance() {
        // The native engine re-emits the current instruction with an updated
        // distance on every position update; the instructions list is frozen
        // at route start. The live value must win, or the displayed distance
        // freezes after the first turn.
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            instructions = listOf(
                instr(1000.0, TurnType.START, "Start St"),
                instr(800.0, TurnType.LEFT, "Main St")
            ),
            nextInstruction = instr(350.0, TurnType.LEFT, "Main St")
        )
        val info = NavigationTemplateMapper.routingInfoFromState(
            state, { testIcon }, includeLanes = false
        )!!
        assertEquals(350.0, info.currentDistance!!.displayDistance, 0.01)
        assertEquals("Main St", info.currentStep!!.road.toString())
    }

    @Test
    fun routingInfoFromState_noNextStepWhenLast() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            instructions = listOf(instr(100.0, TurnType.LEFT, "Main St"))
        )
        val info = NavigationTemplateMapper.routingInfoFromState(
            state, { testIcon }, includeLanes = false
        )!!
        assertNull(info.nextStep)
    }

    @Test
    fun routingInfoFromState_nullWhenNotNavigating() {
        assertNull(
            NavigationTemplateMapper.routingInfoFromState(
                NavigationState(), { testIcon }, includeLanes = true
            )
        )
    }

    @Test
    fun routingInfoFromState_attachesLanesWhenEnabled() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            instructions = listOf(instr(350.0, TurnType.LEFT, "Main St")),
            laneCount = 2,
            laneSuggested = true,
            laneSuggestedFrom = 0,
            laneSuggestedTo = 0,
            laneTurns = listOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON)
        )
        val withLanes = NavigationTemplateMapper.routingInfoFromState(
            state, { testIcon }, includeLanes = true, laneImageFor = { _, _ -> testIcon }
        )!!
        assertEquals(2, withLanes.currentStep!!.lanes.size)
        assertTrue(withLanes.currentStep!!.lanes[0].directions[0].isRecommended)
        assertFalse(withLanes.currentStep!!.lanes[1].directions[0].isRecommended)
        assertNotNull(withLanes.currentStep!!.lanesImage)

        val withoutLanes = NavigationTemplateMapper.routingInfoFromState(
            state, { testIcon }, includeLanes = false
        )!!
        assertTrue(withoutLanes.currentStep!!.lanes.isEmpty())
    }

    @Test
    fun instructionsFromCurrentStep_dropsStepsBeforeCurrent() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 1,
            instructions = listOf(
                instr(1000.0, TurnType.START, "Start St"),
                instr(800.0, TurnType.LEFT, "Main St"),
                instr(200.0, TurnType.RIGHT, "Elm St")
            )
        )
        val remaining = NavigationTemplateMapper.instructionsFromCurrentStep(state)
        assertEquals(listOf("Main St", "Elm St"), remaining.map { it.streetName })
    }

    @Test
    fun routeDescriptionRows_orderTitleAndCurrentMark() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 1,
            instructions = listOf(
                instr(1000.0, TurnType.START, "Start St"),
                instr(800.0, TurnType.LEFT, "Main St"),
                instr(200.0, TurnType.RIGHT, "Elm St")
            )
        )
        val rows = NavigationTemplateMapper.routeDescriptionRows(state)
        assertEquals(2, rows.size)
        assertTrue(rows[0].isCurrent)
        assertFalse(rows[1].isCurrent)
        assertEquals("800 m · Main St", rows[0].title)
        assertEquals("200 m · Elm St", rows[1].title)
        assertEquals(TurnType.RIGHT, rows[1].turnType)
    }

    @Test
    fun routeDescriptionRows_fallsBackToShortDescription() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            instructions = listOf(instr(500.0, TurnType.LEFT, ""))
        )
        val rows = NavigationTemplateMapper.routeDescriptionRows(state)
        assertEquals("500 m · short", rows[0].title)
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
