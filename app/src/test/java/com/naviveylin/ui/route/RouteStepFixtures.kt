package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType

/**
 * Route-step fixtures for the route panel's suites.
 *
 * A route's step list is now the only source a row can come from (spec: `route-analysis` — Rows are
 * derived from the route's steps), so every suite that renders or analyses a route builds its steps
 * here instead of writing description lines and parsing them back.
 */
internal fun step(
    kind: RouteStep.Kind,
    lat: Double = 52.5200,
    lon: Double = 13.4050,
    legDistance: Double = 0.0,
    legTime: Double = 0.0,
    name: String? = null,
    ref: String? = null,
    typeName: String? = null,
    turn: TurnType? = null,
    roundaboutExit: Int? = null,
    clockwise: Boolean? = null,
    motorwayFrom: String? = null,
    motorwayTo: String? = null,
    databaseId: Long = 0L,
    nodeIndex: Long = 0L
): RouteStep = RouteStep().apply {
    this.kind = kind
    this.lat = lat
    this.lon = lon
    this.legDistance = legDistance
    this.legTime = legTime
    distanceFromStart = 0.0
    timeFromStart = 0.0
    this.typeName = typeName
    this.databaseId = databaseId
    this.currentNodeIndex = nodeIndex
    if (name != null || ref != null) {
        this.name = RouteStep.Name(name ?: "", ref ?: "")
    }
    if (turn != null) {
        this.turn = turn
    }
    when (kind) {
        RouteStep.Kind.ROUNDABOUT_ENTER ->
            this.roundaboutEnter = RouteStep.RoundaboutEnter().apply {
                this.clockwise = clockwise ?: true
            }
        RouteStep.Kind.ROUNDABOUT_LEAVE ->
            this.roundaboutLeave = RouteStep.RoundaboutLeave().apply {
                exitCountTaken = (roundaboutExit ?: 1).toLong()
                this.clockwise = clockwise ?: true
            }
        RouteStep.Kind.MOTORWAY_ENTER ->
            this.motorwayEnter = RouteStep.MotorwayEnter().apply {
                to = RouteStep.Name("", motorwayTo ?: "")
            }
        RouteStep.Kind.MOTORWAY_CHANGE ->
            this.motorwayChange = RouteStep.MotorwayChange().apply {
                from = RouteStep.Name("", motorwayFrom ?: "")
                to = RouteStep.Name("", motorwayTo ?: "")
                move = turn ?: TurnType.STRAIGHT_ON
            }
        RouteStep.Kind.MOTORWAY_LEAVE ->
            this.motorwayLeave = RouteStep.MotorwayLeave().apply {
                from = RouteStep.Name("", motorwayFrom ?: "")
                move = turn ?: TurnType.STRAIGHT_ON
            }
        RouteStep.Kind.NAME_CHANGE ->
            this.nameChanged = RouteStep.NameChanged().apply {
                from = RouteStep.Name("", "")
                to = RouteStep.Name(name ?: "", ref ?: "")
            }
        else -> Unit
    }
}

/** A start row at the route's first vertex. */
internal fun startStep(
    lat: Double = 52.5200,
    lon: Double = 13.4050,
    name: String? = null
): RouteStep = step(RouteStep.Kind.START, lat = lat, lon = lon, name = name)

/** An arrival row at the route's last vertex. */
internal fun targetStep(
    lat: Double = 52.5300,
    lon: Double = 13.4100,
    legDistance: Double = 0.0,
    legTime: Double = 0.0,
    name: String? = null
): RouteStep = step(
    RouteStep.Kind.TARGET,
    lat = lat, lon = lon,
    legDistance = legDistance, legTime = legTime,
    name = name
)

/** A turn row. */
internal fun turnStep(
    turn: TurnType,
    lat: Double = 52.5230,
    lon: Double = 13.4080,
    legDistance: Double = 500.0,
    legTime: Double = 60.0,
    name: String? = null,
    typeName: String? = null
): RouteStep = step(
    RouteStep.Kind.TURN,
    lat = lat, lon = lon,
    legDistance = legDistance, legTime = legTime,
    name = name, turn = turn, typeName = typeName
)

/**
 * A route result carrying [steps], a polyline of the steps' own coordinates and the summed length.
 *
 * The polyline is built from the steps so a row's boundary is a vertex of the drawn line, which is
 * what the segment mapping and the tiling invariant are about.
 */
internal fun routeWithSteps(
    vararg steps: RouteStep,
    distance: Double = steps.sumOf { it.legDistance },
    duration: Double = steps.sumOf { it.legTime }
): RouteEntry = RouteEntry().apply {
    this.steps = arrayOf(*steps)
    latitudes = DoubleArray(steps.size) { steps[it].lat }
    longitudes = DoubleArray(steps.size) { steps[it].lon }
    this.distance = distance
    this.duration = duration
    routeHandle = 1L
}
