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
 * [isDataOnly] over a derived manoeuvre, so a surface that reads [Manoeuvre]s applies the same rule as
 * one that reads the published steps (spec: `route-analysis` — an informative step is data, never a
 * row).
 */
fun isDataOnly(manoeuvre: Manoeuvre): Boolean = when (manoeuvre.kind) {
    RouteStep.Kind.INFORMATIVE, RouteStep.Kind.UNKNOWN -> true
    else -> false
}

/**
 * Distance between two manoeuvres up to which the following one is announced as the "next next" hint
 * (spec: `next-turn-overlay` — "Next next" hint).
 *
 * The bound is the one live guidance was built with: the pre-step-model instruction builder carried the
 * same 200 m as `CloseHintDistance`, and `osmscout::client::RouteStepBuilder::NextNextWindowMeters` still
 * names it. This constant is the *announcement's* bound, read by the surface that shows the row; the
 * native record's own `nextNext` is not read by a guidance surface (see [guidanceStep]).
 */
const val HINT_WINDOW_METERS = 200.0

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
 * [nextManoeuvreFrom], bounded by [HINT_WINDOW_METERS]: the manoeuvre the overlay announces as its second
 * row, or `null` when the following one is further away than the window
 * (spec: `next-turn-overlay` — "Next next" hint).
 */
fun nextManoeuvreAfter(route: List<RouteStep>, step: RouteStep): RouteStep? =
    nextManoeuvreFrom(route, step)
        ?.takeIf { it.distanceFromStart - step.distanceFromStart <= HINT_WINDOW_METERS }

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
 * The announced record's second row is the **following manoeuvre of [route]**, bounded by
 * [HINT_WINDOW_METERS] — never `published.nextNext`, which live guidance fills with the next collected
 * route node (a plain node a few metres ahead, carrying no roundabout exit). Without this, a roundabout's
 * exit ordinal reached the display only after the ring had been entered (change
 * `fix-following-manoeuvre-hint`).
 *
 * The returned record is always a copy, so a surface can never mutate the engine's published state, and its
 * `nextNext` is always this function's result (or `null`, when no manoeuvre follows within the window) — a
 * node hint cannot reach a display, not even on the fallback path where no manoeuvre of the list is known.
 */
fun guidanceStep(published: RouteStep, route: List<RouteStep>): RouteStep {
    val manoeuvre = if (!isDataOnly(published)) published else route.firstOrNull {
        !isDataOnly(it) && it.distanceFromStart >= published.distanceFromStart
    } ?: return copyForGuidance(published).also { it.nextNext = null }

    val announced = copyForGuidance(manoeuvre)

    if (manoeuvre === published) {
        // A live manoeuvre keeps the progress it arrived with; only the hint is re-derived.
        announced.nextNext = nextManoeuvreAfter(route, announced)
        return announced
    }

    // The progress the published step reports, or the route start when it carries none (a route list
    // entry has no progress: its remaining distance is the whole way from the start).
    val positionMeters =
        published.distanceFromStart - (published.remainingMeters ?: published.distanceFromStart)
    announced.remainingDistance = (manoeuvre.distanceFromStart - positionMeters).coerceAtLeast(0.0)
    announced.remainingTime = null
    announced.nextNext = nextManoeuvreAfter(route, announced)
    return announced
}

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
