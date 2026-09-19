package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.auto.R
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.TurnInstructionLocalizer
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.roundDistanceMeters
import com.naviveylin.core.stringResolver
import java.util.TimeZone

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
     * Build a host [Step] for [instruction] (spec: auto/navigation-view). The
     * step carries the maneuver (type icon), the target street as road and the
     * localized instruction text as cue (spec: turn-instruction-localization);
     * [lanes] are attached to the step (with the [lanesImage] strip, which the
     * host requires alongside lane data).
     */
    fun stepForInstruction(
        instruction: RouteInstruction,
        icon: CarIcon,
        lanes: List<Lane> = emptyList(),
        lanesImage: CarIcon? = null,
        resolver: StringResolver
    ): Step {
        val maneuver = Maneuver.Builder(maneuverTypeFromTurnType(instruction.turnType))
            .setIcon(icon)
            .build()
        val builder = Step.Builder()
            .setManeuver(maneuver)
            .setCue(TurnInstructionLocalizer.shortDescription(resolver, instruction))
        instruction.streetName?.takeIf { it.isNotBlank() }?.let { builder.setRoad(it) }
        lanes.forEach { builder.addLane(it) }
        if (lanes.isNotEmpty()) {
            lanesImage?.let { builder.setLanesImage(it) }
        }
        return builder.build()
    }

    /** One host [Lane] for a lane row; recommended drives [LaneDirection.isRecommended]. */
    fun laneFromTurn(turn: LaneTurn, recommended: Boolean): Lane =
        Lane.Builder()
            .addDirection(LaneDirection.create(laneDirectionShapeFromLaneTurn(turn), recommended))
            .build()

    /**
     * Round a distance (meters) for display: exact value up to 50 m,
     * multiples of 50 m from 50 m to 1 km, multiples of 100 m (one decimal
     * km) above 1 km (spec: auto/navigation-view — distance display).
     * Shared with the phone UI (com.naviveylin.core.roundDistanceMeters).
     */
    fun roundDistanceMeters(meters: Double): Double =
        com.naviveylin.core.roundDistanceMeters(meters)

    /**
     * Distance for the host instruction panel/travel estimate: rounded, in
     * meters below 1 km and kilometers above (the host renders raw meters
     * verbosely, e.g. "16800 m", when handed UNIT_METERS for large values).
     */
    fun distanceForDisplay(meters: Double): Distance {
        val rounded = roundDistanceMeters(meters.coerceAtLeast(0.0))
        return if (rounded >= 1000) {
            Distance.create(rounded / 1000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(rounded, Distance.UNIT_METERS)
        }
    }

    /**
     * Host [RoutingInfo] for the instruction panel (spec: auto/navigation-view):
     * current step + distance to it, next-next step, lane guidance on the
     * current step. Null when not navigating or no current instruction.
     * [laneImageFor] renders the lanes strip (required by the host whenever
     * lane data is present).
     */
    fun routingInfoFromState(
        state: NavigationState,
        iconForTurn: (TurnType) -> CarIcon,
        includeLanes: Boolean,
        laneImageFor: (List<LaneTurn>, IntRange) -> CarIcon? = { _, _ -> null },
        resolver: StringResolver
    ): RoutingInfo? {
        if (!state.isNavigating) return null
        // Prefer the live next instruction: the native engine re-emits it with
        // an updated distance on every position update, while the
        // instructions list is frozen at route start. Using the list entry
        // froze the distance after the first turn.
        val current = state.nextInstruction
            ?: state.instructions.getOrNull(state.currentStepIndex)
            ?: return null
        val next = state.instructions.getOrNull(state.currentStepIndex + 1)

        val recommended = if (state.laneSuggested) {
            state.laneSuggestedFrom..state.laneSuggestedTo
        } else {
            -1..-1
        }
        val lanes = if (includeLanes && state.laneCount > 0) {
            state.laneTurns.mapIndexed { index, turn ->
                laneFromTurn(turn, recommended = index in recommended)
            }
        } else {
            emptyList()
        }

        val builder = RoutingInfo.Builder()
            .setCurrentStep(
                stepForInstruction(
                    current,
                    iconForTurn(current.turnType),
                    lanes,
                    if (lanes.isNotEmpty()) laneImageFor(state.laneTurns, recommended) else null,
                    resolver
                ),
                distanceForDisplay(current.distanceTo)
            )
        if (next != null) {
            builder.setNextStep(stepForInstruction(next, iconForTurn(next.turnType), resolver = resolver))
        }
        return builder.build()
    }

    /**
     * Route-description entries from [state] for the in-app route description
     * screen (spec: auto/navigation-view — "Route description screen"),
     * ordered from the current step on.
     */
    fun instructionsFromCurrentStep(state: NavigationState): List<RouteInstruction> {
        val start = state.currentStepIndex.coerceIn(0, state.instructions.lastIndex.coerceAtLeast(0))
        return state.instructions.drop(start)
    }

    /** One row of the route description screen. */
    data class RouteDescriptionRow(
        val turnType: TurnType,
        val title: String,
        val text: String,
        val isCurrent: Boolean
    )

    /**
     * Rows for the route description screen: distance + target street title,
     * description text, current step first and marked.
     */
    fun routeDescriptionRows(carContext: CarContext, state: NavigationState): List<RouteDescriptionRow> {
        val resolver = carContext.stringResolver()
        return instructionsFromCurrentStep(state).mapIndexed { index, instruction ->
            val distance = carContext.getString(
                if (roundDistanceMeters(instruction.distanceTo) >= 1000) R.string.distance_unit_km else R.string.distance_unit_m,
                formatDistanceNumber(instruction.distanceTo)
            )
            val target = instruction.streetName?.takeIf { it.isNotBlank() }
                ?: TurnInstructionLocalizer.shortDescription(resolver, instruction)
            RouteDescriptionRow(
                turnType = instruction.turnType,
                title = carContext.getString(R.string.distance_target, distance, target),
                text = TurnInstructionLocalizer.description(resolver, instruction),
                isCurrent = index == 0
            )
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

    /**
     * Host [Trip] for the cluster / heads-up display (spec: auto-navigation-hints
     * — "Trip metadata for cluster and heads-up display"): the current step
     * with its maneuver icon, cue and road, the step's travel estimate, the
     * destination and the current road.
     *
     * Null when navigation is not active — `NavigationManager.updateTrip`
     * rejects updates outside an active navigation session, so free driving
     * publishes nothing. While the route is recalculating, or while the arrival
     * time is not known yet, a loading trip without steps is returned (the API
     * forbids steps in the loading state), which is what the host shows as a
     * transient state.
     *
     * @param iconForTurn maneuver artwork provider (shared with the template)
     * @param nowMillis wall clock used for the step's remaining time; injectable
     *   for tests
     * @param timeZone time zone of the arrival times; injectable for tests
     */
    fun tripFromState(
        state: NavigationState,
        iconForTurn: (TurnType) -> CarIcon,
        resolver: StringResolver,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Trip? {
        if (!state.isNavigating) return null
        if (state.isRerouting) return loadingTrip()

        val current = state.nextInstruction
            ?: state.instructions.getOrNull(state.currentStepIndex)
            ?: return loadingTrip()
        if (state.etaMillis <= 0L) return loadingTrip()

        val step = stepForInstruction(current, iconForTurn(current.turnType), resolver = resolver)
        val stepArrivalMillis = if (current.timeTo > 0.0) {
            nowMillis + (current.timeTo * 1000.0).toLong()
        } else {
            state.etaMillis
        }
        val stepEstimate = TravelEstimate.Builder(
            distanceForDisplay(current.distanceTo),
            DateTimeWithZone.create(stepArrivalMillis, timeZone)
        ).apply {
            if (current.timeTo > 0.0) setRemainingTimeSeconds(current.timeTo.toLong())
        }.build()

        val builder = Trip.Builder().addStep(step, stepEstimate)
        destinationFromState(state, nowMillis, timeZone)?.let { (destination, estimate) ->
            builder.addDestination(destination, estimate)
        }
        current.streetName?.takeIf { it.isNotBlank() }?.let { builder.setCurrentRoad(it) }
        return builder.build()
    }

    /** Transient host state: recalculating, or no usable step/arrival time yet. */
    private fun loadingTrip(): Trip = Trip.Builder().setLoading(true).build()

    /**
     * Destination and its travel estimate, or null when the route carries no
     * usable destination text (the API rejects a destination without name or
     * address).
     */
    private fun destinationFromState(
        state: NavigationState,
        nowMillis: Long,
        timeZone: TimeZone
    ): Pair<Destination, TravelEstimate>? {
        val name = state.destinationName?.takeIf { it.isNotBlank() }
            ?: coordinatesText(state)
            ?: return null
        val destination = Destination.Builder().setName(name).build()
        val estimate = TravelEstimate.Builder(
            distanceForDisplay(state.remainingDistance),
            DateTimeWithZone.create(state.etaMillis, timeZone)
        ).apply {
            // Never a negative duration: the API rejects anything negative but
            // REMAINING_TIME_UNKNOWN. An ETA in the past keeps the unknown value.
            val remainingSeconds = (state.etaMillis - nowMillis) / 1000
            if (remainingSeconds > 0) setRemainingTimeSeconds(remainingSeconds)
        }.build()
        return destination to estimate
    }

    /**
     * Coordinates as the destination text when the route has no name. Locale
     * stable (like the location labels): a coordinate string is data, not
     * display text.
     */
    private fun coordinatesText(state: NavigationState): String? {
        if (state.destLat.isNaN() || state.destLon.isNaN()) return null
        return String.format(java.util.Locale.US, "%.5f, %.5f", state.destLat, state.destLon)
    }

    /**
     * Whether the host-visible trip content changed (spec: auto-navigation-hints
     * — "Trip publishing cadence"): maneuver, rounded distance, remaining time
     * or the loading state. Speed-only and position-only updates do not
     * publish, so the host is not flooded at the position update rate.
     */
    fun hasTripChanged(previous: NavigationState?, current: NavigationState): Boolean {
        val old = previous ?: return true
        return old.isNavigating != current.isNavigating ||
                old.isRerouting != current.isRerouting ||
                old.nextInstruction?.turnType != current.nextInstruction?.turnType ||
                old.nextInstruction?.shortDescription != current.nextInstruction?.shortDescription ||
                old.nextInstruction?.streetName != current.nextInstruction?.streetName ||
                roundDistanceMeters(old.nextInstruction?.distanceTo ?: 0.0) !=
                    roundDistanceMeters(current.nextInstruction?.distanceTo ?: 0.0) ||
                roundDistanceMeters(old.remainingDistance) !=
                    roundDistanceMeters(current.remainingDistance) ||
                old.etaMillis / 1000 != current.etaMillis / 1000 ||
                old.destinationName != current.destinationName
    }
}
