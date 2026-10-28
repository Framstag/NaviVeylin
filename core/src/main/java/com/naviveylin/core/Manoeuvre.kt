package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType

/**
 * One manoeuvre of a calculated route, read from the route's structured step model
 * (spec: `osmscout-jni` — A calculated route publishes structured route steps; spec:
 * `route-analysis` — Rows are derived from the route's steps).
 *
 * This is the app's own projection of a [RouteStep]: it carries exactly the fields a step row
 * needs — the manoeuvre kind, its move, the roundabout and motorway records, the street name it
 * names, its position, its own leg and its node identity — and it carries nothing the app would
 * have to recover from prose.
 *
 * **An absent value stays absent.** A record the route does not carry is `null`, never a zero or an
 * empty placeholder: a plain turn has no roundabout ordinal, no circulation direction and no
 * motorway names, and a row that invented them would name an exit that does not exist.
 */
data class Manoeuvre(
    /** What kind of manoeuvre this is, as the bridge derived it. */
    val kind: RouteStep.Kind,
    /** The turn's own move, or `null` when the manoeuvre carries no turn record. */
    val move: TurnType?,
    /** 1-based ordinal of the exit taken, for a roundabout leave; `null` otherwise. */
    val roundaboutExit: Int?,
    /** Whether the roundabout circulates clockwise; `null` when this is no roundabout. */
    val clockwise: Boolean?,
    /** The motorway left at this manoeuvre, `null` when none. */
    val motorwayFrom: String?,
    /** The motorway entered at this manoeuvre, `null` when none. */
    val motorwayTo: String?,
    /** The street name this manoeuvre names, `null` when it names none. */
    val name: String?,
    /** The street's reference (for example `A40`), `null` when it has none. */
    val ref: String?,
    /** The way's type name (`highway_secondary`), `null` when absent. */
    val typeName: String?,
    /** Latitude of the manoeuvre's node in degrees. */
    val lat: Double,
    /** Longitude of the manoeuvre's node in degrees. */
    val lon: Double,
    /** Distance of the leg that ends at this manoeuvre, in metres. */
    val legDistanceMeters: Double,
    /** Travel time of that same leg, in seconds. */
    val legTimeSeconds: Double,
    /** Database the manoeuvre's node belongs to. */
    val databaseId: Long,
    /** File offset of the object travelled along from this node. */
    val pathObjectOffset: Long,
    /** Node index of [pathObjectOffset] this manoeuvre's node is. */
    val nodeIndex: Long
) {
    /** The name a row shows for this manoeuvre: its name, else its reference. */
    val displayName: String?
        get() = when {
            !name.isNullOrBlank() && !ref.isNullOrBlank() -> "$name ($ref)"
            !name.isNullOrBlank() -> name
            !ref.isNullOrBlank() -> ref
            else -> null
        }

    /** Whether this manoeuvre is a straight-on turn, the move that collapses into one row. */
    val isStraightOn: Boolean
        get() = kind == RouteStep.Kind.TURN && move == TurnType.STRAIGHT_ON
}

/** The name a [RouteStep.Name] carries, trimmed, or `null` when it carries nothing. */
private fun RouteStep.Name?.plain(): String? = this?.name?.trim()?.takeIf { it.isNotEmpty() }

/** The reference a [RouteStep.Name] carries, trimmed, or `null` when it carries nothing. */
private fun RouteStep.Name?.reference(): String? = this?.ref?.trim()?.takeIf { it.isNotEmpty() }

/**
 * The app's manoeuvre for one step of a calculated route (spec: `osmscout-jni` — A calculated route
 * publishes structured route steps).
 *
 * Every field is read from the record the bridge published for that node. Nothing is derived from
 * text, and nothing is invented for a record the route does not carry: see [Manoeuvre].
 */
fun manoeuvreFromStep(step: RouteStep): Manoeuvre {
    val kind = step.kind ?: RouteStep.Kind.UNKNOWN
    val nameChangedTo = step.nameChanged?.to
    // A name-change step names the street the route continues on: that is the name its row shows.
    val named = if (kind == RouteStep.Kind.NAME_CHANGE) nameChangedTo ?: step.name else step.name

    val move = when (kind) {
        RouteStep.Kind.TURN -> step.turn ?: step.direction?.turn
        RouteStep.Kind.MOTORWAY_CHANGE -> step.motorwayChange?.move
        RouteStep.Kind.MOTORWAY_LEAVE -> step.motorwayLeave?.move
        else -> null
    }
    val clockwise = when (kind) {
        RouteStep.Kind.ROUNDABOUT_ENTER -> step.roundaboutEnter?.clockwise
        RouteStep.Kind.ROUNDABOUT_LEAVE -> step.roundaboutLeave?.clockwise
        else -> null
    }
    val motorwayFrom = when (kind) {
        RouteStep.Kind.MOTORWAY_CHANGE -> step.motorwayChange?.from
        RouteStep.Kind.MOTORWAY_LEAVE -> step.motorwayLeave?.from
        else -> null
    }
    val motorwayTo = when (kind) {
        RouteStep.Kind.MOTORWAY_ENTER -> step.motorwayEnter?.to
        RouteStep.Kind.MOTORWAY_CHANGE -> step.motorwayChange?.to
        else -> null
    }

    return Manoeuvre(
        kind = kind,
        move = move,
        roundaboutExit = when (val leave = step.roundaboutLeave) {
            null -> null
            else -> leave.exitCountTaken.toInt()
        },
        clockwise = clockwise,
        motorwayFrom = motorwayFrom?.reference() ?: motorwayFrom?.plain(),
        motorwayTo = motorwayTo?.reference() ?: motorwayTo?.plain(),
        name = named.plain(),
        ref = named.reference(),
        typeName = step.typeName?.trim()?.takeIf { it.isNotEmpty() },
        lat = step.lat,
        lon = step.lon,
        legDistanceMeters = step.legDistance,
        legTimeSeconds = step.legTime,
        databaseId = step.databaseId,
        pathObjectOffset = step.pathObjectOffset,
        nodeIndex = step.currentNodeIndex
    )
}
