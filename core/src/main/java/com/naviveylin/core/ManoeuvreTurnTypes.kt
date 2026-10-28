package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType

/**
 * The turn type an icon is drawn from, resolved from a manoeuvre's **kind and move**
 * (spec: `next-turn-overlay` — Turn type icon; spec: `turn-instruction-localization` — All native
 * instruction kinds are covered).
 *
 * A motorway change is drawn with the motorway glyph, never as a plain turn: the bridge publishes no
 * turn type for it, which is exactly what made the retired prose matcher unable to tell the two
 * apart. The function lives in `:core` so the phone overlay, the notification, the HUD and the car
 * panel cannot drift apart.
 */
fun turnTypeFor(manoeuvre: Manoeuvre): TurnType = when (manoeuvre.kind) {
    RouteStep.Kind.START -> TurnType.START
    RouteStep.Kind.TARGET -> TurnType.TARGET_REACHED
    RouteStep.Kind.ROUNDABOUT_ENTER -> TurnType.ROUNDABOUT_ENTER
    RouteStep.Kind.ROUNDABOUT_LEAVE -> TurnType.ROUNDABOUT_LEAVE
    RouteStep.Kind.MOTORWAY_ENTER, RouteStep.Kind.MOTORWAY_CHANGE -> TurnType.MOTORWAY_ENTER
    RouteStep.Kind.MOTORWAY_LEAVE, RouteStep.Kind.TURN, RouteStep.Kind.NAME_CHANGE,
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN ->
        manoeuvre.move ?: TurnType.STRAIGHT_ON
}

/** [turnTypeFor] for a step the engine published. */
fun turnTypeFor(step: RouteStep): TurnType = turnTypeFor(manoeuvreFromStep(step))

/**
 * The distance a live step still has to travel before its manoeuvre, in metres
 * (spec: `osmscout-jni` — Live guidance publishes route steps).
 *
 * `null` on the published record means the engine has not reported a progress value yet — a step of
 * a route that is not being navigated, or the very first publication — and is reported as `0.0` so a
 * surface shows "now" rather than a distance it invented.
 */
val RouteStep.remainingMeters: Double
    get() = remainingDistance ?: 0.0

/**
 * The time a live step still needs before its manoeuvre, in seconds; `0.0` when the engine has not
 * reported one (spec: `osmscout-jni` — Live guidance publishes route steps).
 */
val RouteStep.remainingSeconds: Double
    get() = remainingTime ?: 0.0

/** The roundabout exit ordinal this step takes, or `0` when it is no roundabout leave. */
val RouteStep.exitOrdinal: Int
    get() = roundaboutLeave?.exitCountTaken?.toInt() ?: 0

/** The nested "next next" hint's own exit ordinal, or `0` when it carries none. */
val RouteStep.nextNextExitOrdinal: Int
    get() = nextNext?.roundaboutLeave?.exitCountTaken?.toInt() ?: 0

/**
 * The distance between the step ahead and the manoeuvre after it, in metres — what a "next next" row
 * says ("then, in 120 m"). Both records carry their node's distance from the route start, so the gap
 * is their difference; a step without a hint reports zero.
 */
val RouteStep.nextNextGapMeters: Double
    get() {
        val hint = nextNext ?: return 0.0
        return (hint.distanceFromStart - distanceFromStart).coerceAtLeast(0.0)
    }
