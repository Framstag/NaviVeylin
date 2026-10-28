package com.naviveylin.test

import com.naviveylin.test.stepOf
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType

/**
 * A live or list [RouteStep] built from the argument shape a case reads, so a case keeps its shape
 * while the record under it is the step model (change `route-steps-live-guidance`).
 *
 * The parameter list mirrors the retired prose record on purpose: positional call sites keep working
 * and a case's intent — "a left turn 300 m ahead onto Main St" — stays readable.
 *
 * `description` and `shortDescription` are accepted and **ignored**: the native layer publishes no
 * guidance text any more, and the wording is built from the step's own values
 * (`core/ManoeuvreLabels`). A case that asserted text now asserts the label builder's output for the
 * manoeuvre this fixture describes.
 *
 * @param distanceTo the distance the vehicle still has to travel (`RouteStep.remainingDistance`); for a
 *   step of the published list it is also used as the distance from the route start when
 *   [distanceFromStart] is not given
 * @param turnType the manoeuvre, mapped to the step model's kind and move
 * @param roundaboutExitNumber the exit ordinal of a roundabout leave, 0 for none
 * @param roundaboutClockwise the roundabout's circulation direction
 * @param nextNextRoundaboutExitNumber the nested hint's own exit ordinal
 */
@Suppress("UNUSED_PARAMETER", "LongParameterList")
internal fun stepOf(
    distanceTo: Double = 0.0,
    timeTo: Double = 0.0,
    legDistance: Double = 0.0,
    turnType: TurnType = TurnType.STRAIGHT_ON,
    streetName: String = "",
    description: String = "",
    shortDescription: String = "",
    nextNextDistanceTo: Double = 0.0,
    nextNextTurnType: TurnType = TurnType.STRAIGHT_ON,
    nextNextDescription: String = "",
    nextNextShortDescription: String = "",
    roundaboutExitNumber: Int = 0,
    roundaboutClockwise: Boolean = true,
    nextNextRoundaboutExitNumber: Int = 0,
    distanceFromStart: Double = distanceTo,
    hasHint: Boolean = nextNextRoundaboutExitNumber > 0 || nextNextDistanceTo > 0.0
): RouteStep {
    val step = RouteStep()
    step.kind = kindOf(turnType)
    step.distanceFromStart = distanceFromStart
    step.legDistance = legDistance
    step.legTime = timeTo
    step.remainingDistance = distanceTo
    if (timeTo > 0.0) step.remainingTime = timeTo
    if (streetName.isNotBlank()) step.name = RouteStep.Name(streetName, "")

    when (turnType) {
        TurnType.ROUNDABOUT_ENTER -> {
            step.roundaboutEnter = RouteStep.RoundaboutEnter().apply {
                clockwise = roundaboutClockwise
            }
        }
        TurnType.ROUNDABOUT_LEAVE -> {
            step.roundaboutLeave = RouteStep.RoundaboutLeave().apply {
                exitCountTaken = roundaboutExitNumber.toLong()
                clockwise = roundaboutClockwise
            }
        }
        TurnType.MOTORWAY_ENTER -> {
            step.motorwayEnter = RouteStep.MotorwayEnter().apply {
                to = RouteStep.Name("", streetName)
            }
        }
        TurnType.START, TurnType.TARGET_REACHED -> Unit
        else -> step.turn = turnType
    }

    if (hasHint) {
        val hint = RouteStep()
        hint.kind = kindOf(nextNextTurnType)
        hint.distanceFromStart = distanceFromStart + nextNextDistanceTo
        hint.remainingDistance = nextNextDistanceTo
        if (nextNextTurnType == TurnType.ROUNDABOUT_LEAVE) {
            hint.roundaboutLeave = RouteStep.RoundaboutLeave().apply {
                exitCountTaken = nextNextRoundaboutExitNumber.toLong()
                clockwise = roundaboutClockwise
            }
        } else if (nextNextTurnType != TurnType.START && nextNextTurnType != TurnType.TARGET_REACHED) {
            hint.turn = nextNextTurnType
        }
        step.nextNext = hint
    }

    return step
}

/**
 * [stepOf] for the compact shape a case usually writes: "a left turn 300 m ahead onto Main St". The
 * retired constructor's description strings are not taken at all — no surface reads native text now.
 */
internal fun stepOf(
    distanceTo: Double,
    turnType: TurnType,
    streetName: String? = null
): RouteStep = stepOf(
    distanceTo = distanceTo,
    turnType = turnType,
    streetName = streetName.orEmpty()
)

/**
 * [stepOf] for a case that still passes the retired constructor's two description strings positionally.
 * They are ignored: the wording is built from the step's values.
 */
@Suppress("UNUSED_PARAMETER")
internal fun stepOf(
    distanceTo: Double,
    turnType: TurnType,
    streetName: String?,
    description: String,
    shortDescription: String
): RouteStep = stepOf(
    distanceTo = distanceTo,
    turnType = turnType,
    streetName = streetName.orEmpty()
)

/** The street name a step names, as its manoeuvre publishes it. */
internal fun streetNameOf(step: RouteStep?): String? = step?.let {
    com.naviveylin.core.manoeuvreFromStep(it).displayName
}

/** The step kind a manoeuvre is drawn from, for a fixture that names the manoeuvre only. */
internal fun kindOf(turnType: TurnType): RouteStep.Kind = when (turnType) {
    TurnType.START -> RouteStep.Kind.START
    TurnType.TARGET_REACHED -> RouteStep.Kind.TARGET
    TurnType.ROUNDABOUT_ENTER -> RouteStep.Kind.ROUNDABOUT_ENTER
    TurnType.ROUNDABOUT_LEAVE -> RouteStep.Kind.ROUNDABOUT_LEAVE
    TurnType.MOTORWAY_ENTER -> RouteStep.Kind.MOTORWAY_ENTER
    TurnType.SHARP_LEFT, TurnType.LEFT, TurnType.SLIGHTLY_LEFT, TurnType.STRAIGHT_ON,
    TurnType.SLIGHTLY_RIGHT, TurnType.RIGHT, TurnType.SHARP_RIGHT -> RouteStep.Kind.TURN
}
