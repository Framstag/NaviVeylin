package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep

/**
 * Whether a step describes **facts only** — a max speed, lanes, a way type, a POI, a via section — rather
 * than a driving decision. The engine's walk publishes one step per described route node, so such a node
 * reaches the live path too; a guidance surface must not announce it (spec: `osmscout-jni` — Live guidance
 * publishes route steps; the same rule the route list applies).
 */
fun isDataOnly(step: RouteStep): Boolean = when (step.kind) {
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN -> true
    else -> false
}

/**
 * The step a guidance surface should announce, resolved from the step the engine published
 * (spec: `next-turn-overlay` — Turn type icon, "Next next" hint; change `route-steps-live-guidance`).
 *
 * The engine publishes the next *described route node*, which is often a facts-only node: measured on
 * device 2026-10-28, the overlay then read "0 m" plus a street name with a straight-ahead arrow while the
 * real turn was 31 m away and demoted to the hint row. When [published] is such a node, this returns the
 * route's next manoeuvre **with the distance recomputed from the vehicle's progress**, so the wording, the
 * glyph and the number all describe the manoeuvre ahead.
 *
 * The returned record is a copy carrying only the fields a guidance surface reads (its manoeuvre, name,
 * identity and position); the route's own step list is never mutated.
 */
fun guidanceStep(published: RouteStep, route: List<RouteStep>): RouteStep {
    if (!isDataOnly(published)) return published

    // The progress the published step reports, or the route start when it carries none (a route list
    // entry has no progress: its remaining distance is the whole way from the start).
    val positionMeters =
        published.distanceFromStart - (published.remainingMeters ?: published.distanceFromStart)
    val manoeuvre = route.firstOrNull {
        !isDataOnly(it) && it.distanceFromStart >= published.distanceFromStart
    } ?: return published

    val announced = copyForGuidance(manoeuvre)
    announced.remainingDistance = (manoeuvre.distanceFromStart - positionMeters).coerceAtLeast(0.0)
    announced.remainingTime = null
    return announced
}

/** A fresh record carrying the fields a guidance surface reads; the rest stay at their defaults. */
private fun copyForGuidance(step: RouteStep): RouteStep = RouteStep().also { copy ->
    copy.kind = step.kind
    copy.databaseId = step.databaseId
    copy.currentNodeIndex = step.currentNodeIndex
    copy.pathObjectOffset = step.pathObjectOffset
    copy.lat = step.lat
    copy.lon = step.lon
    copy.distanceFromStart = step.distanceFromStart
    copy.timeFromStart = step.timeFromStart
    copy.legDistance = step.legDistance
    copy.legTime = step.legTime
    copy.name = step.name
    copy.typeName = step.typeName
    copy.turn = step.turn
    copy.direction = step.direction
    copy.roundaboutEnter = step.roundaboutEnter
    copy.roundaboutLeave = step.roundaboutLeave
    copy.motorwayEnter = step.motorwayEnter
    copy.motorwayChange = step.motorwayChange
    copy.motorwayLeave = step.motorwayLeave
    copy.junction = step.junction
    copy.destination = step.destination
    copy.nextNext = step.nextNext
}
