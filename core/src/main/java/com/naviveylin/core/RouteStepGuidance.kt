package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep

/**
 * Whether a step describes **facts only** — a max speed, lanes, a way type, a POI, a via section — rather
 * than a driving decision. The route's step list publishes one step per described route node, so such a
 * node reaches a list consumer; a guidance surface must not announce it (spec: `osmscout-jni` — Live
 * guidance publishes route steps; the same rule the route list applies). Live guidance itself publishes
 * the next manoeuvre, never a facts-only node.
 */
fun isDataOnly(step: RouteStep): Boolean = when (step.kind) {
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN -> true
    else -> false
}

/**
 * [isDataOnly] over a derived manoeuvre, so a surface that reads [Manoeuvre]s applies the same rule as
 * one that reads the published steps (spec: `route-analysis` — an informative step is data, never a
 * row).
 */
fun isDataOnly(manoeuvre: Manoeuvre): Boolean = when (manoeuvre.kind) {
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN -> true
    else -> false
}

/**
 * The first manoeuvre of [route] that lies strictly after [step], or `null` when none does
 * (spec: `auto/navigation-view` — Current and next step in host instruction panel).
 *
 * The route's step list carries **one entry per described route node**, and almost every node carries a
 * name and a way type (`RouteStepCollector` builds a step for each described node; census of one 17.3 km
 * route on device: `steps=501 rows=34 info=466`, `leg_m median=10`). So the entry after a manoeuvre is
 * normally a facts-only node, never the following manoeuvre — which is what this helper steps over.
 *
 * The comparison is strictly greater because two manoeuvres can be described at one node (a turn and a
 * motorway entry): a co-located record is not "the following" one, and two guidance rows at one spot say
 * nothing a driver can act on.
 */
fun nextManoeuvreFrom(route: List<RouteStep>, step: RouteStep): RouteStep? =
    route.firstOrNull {
        !isDataOnly(it) && it.distanceFromStart > step.distanceFromStart
    }

/**
 * The step a guidance surface announces: a copy of the step live guidance published
 * (spec: `next-turn-overlay` — Turn type icon, "Next next" hint).
 *
 * The engine publishes the manoeuvre ahead itself (spec: `osmscout-jni` — Live guidance publishes route
 * steps): the step is the first manoeuvre after the position, never a described route node, and its
 * `nextNext` is the following manoeuvre when that lies within the close-manoeuvre window. Both rows a
 * surface shows therefore come from the record, and no Kotlin code re-derives either — the window has
 * one owner, `osmscout::client::RouteStepBuilder::NextNextWindowMeters`.
 *
 * The returned record is always a copy, so a surface can never mutate the engine's published state.
 */
fun guidanceStep(published: RouteStep): RouteStep = copyForGuidance(published)

/**
 * A fresh record with every field of [step], because the copy replaces the published record on the live
 * path: the announced step is what the overlay, the notification, the car panel and the HUD all read, so a
 * field left behind here would be a value a surface silently lost.
 */
private fun copyForGuidance(step: RouteStep): RouteStep = RouteStep().also { copy ->
    copy.kind = step.kind
    copy.databaseId = step.databaseId
    copy.currentNodeIndex = step.currentNodeIndex
    copy.pathObjectOffset = step.pathObjectOffset
    copy.pathObjectType = step.pathObjectType
    copy.targetNodeIndex = step.targetNodeIndex
    copy.lat = step.lat
    copy.lon = step.lon
    copy.distanceFromStart = step.distanceFromStart
    copy.timeFromStart = step.timeFromStart
    copy.objectOffsets = step.objectOffsets
    copy.objectTypes = step.objectTypes
    copy.legDistance = step.legDistance
    copy.legTime = step.legTime
    copy.name = step.name
    copy.nameChanged = step.nameChanged
    copy.typeName = step.typeName
    copy.crossingWays = step.crossingWays
    copy.direction = step.direction
    copy.turn = step.turn
    copy.roundaboutEnter = step.roundaboutEnter
    copy.roundaboutLeave = step.roundaboutLeave
    copy.motorwayEnter = step.motorwayEnter
    copy.motorwayChange = step.motorwayChange
    copy.motorwayLeave = step.motorwayLeave
    copy.junction = step.junction
    copy.destination = step.destination
    copy.maxSpeed = step.maxSpeed
    copy.lanes = step.lanes
    copy.suggestedLanes = step.suggestedLanes
    copy.poi = step.poi
    copy.via = step.via
    copy.start = step.start
    copy.target = step.target
    copy.remainingDistance = step.remainingDistance
    copy.remainingTime = step.remainingTime
    copy.nextNext = step.nextNext
}
