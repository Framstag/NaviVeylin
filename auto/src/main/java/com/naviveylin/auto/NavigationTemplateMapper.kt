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
import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.auto.R
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import com.naviveylin.core.exitOrdinal
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.nextManoeuvreFrom
import com.naviveylin.core.remainingMeters
import com.naviveylin.core.remainingSeconds
import com.naviveylin.core.roundDistanceMeters
import com.naviveylin.core.routeRows
import com.naviveylin.core.stringResolver
import com.naviveylin.core.turnTypeFor
import java.util.TimeZone

/**
 * Pure mapping functions for converting navigation state to Android Auto template values.
 * Extracted for testability — no Android framework dependencies.
 */
object NavigationTemplateMapper {

    /** [displayedDistanceBucket] value for "no current step" — no real distance produces it. */
    private const val NO_DISTANCE = -1L

    /**
     * Map [TurnType] to [Maneuver] type constant, for a caller that has no instruction.
     *
     * The roundabout entries are the fallback: [TurnType] carries neither the circulation direction
     * nor the exit ordinal, so a roundabout manoeuvre goes through [maneuverTypeForInstruction]
     * instead, which is direction-aware and picks the number-bearing type when the exit is known.
     */
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

    /**
     * The [Maneuver] type for [instruction] (spec: auto/navigation-view — A roundabout exit uses the
     * number-bearing host maneuver).
     *
     * A roundabout needs this per-instruction path: a [TurnType] cannot say which way the ring
     * circulates, so both roundabout types used to be built clockwise, silently wrong on a
     * counter-clockwise roundabout. A known exit ordinal selects the number-bearing type, so the car
     * host draws its own numbered roundabout; an unknown ordinal stays on the numberless type with the
     * app's own glyph. The number-bearing type requires its number, so [stepForInstruction] sets it on
     * exactly this branch.
     */
    fun maneuverTypeForStep(step: RouteStep): Int {
        val clockwise = step.roundaboutLeave?.clockwise ?: step.roundaboutEnter?.clockwise ?: true
        return when (turnTypeFor(step)) {
            TurnType.ROUNDABOUT_ENTER -> if (clockwise) {
                Maneuver.TYPE_ROUNDABOUT_ENTER_CW
            } else {
                Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
            }
            TurnType.ROUNDABOUT_LEAVE -> when {
                step.exitOrdinal <= 0 -> if (clockwise) {
                    Maneuver.TYPE_ROUNDABOUT_EXIT_CW
                } else {
                    Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
                }
                clockwise -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
                else -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
            }
            else -> maneuverTypeFromTurnType(turnTypeFor(step))
        }
    }

    /**
     * Whether [step]'s maneuver carries its exit ordinal to the host, so the host draws its own
     * numbered roundabout and the app supplies no icon for it.
     */
    fun carriesExitNumber(step: RouteStep): Boolean =
        turnTypeFor(step) == TurnType.ROUNDABOUT_LEAVE && step.exitOrdinal > 0

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
    fun stepForStep(
        step: RouteStep,
        icon: CarIcon,
        lanes: List<Lane> = emptyList(),
        lanesImage: CarIcon? = null,
        resolver: StringResolver
    ): Step {
        val manoeuvre = manoeuvreFromStep(step)
        val builder = Maneuver.Builder(maneuverTypeForStep(step))
        if (carriesExitNumber(step)) {
            // The number goes to the host, AND our own numbered glyph is supplied below. The two are
            // independent setters, and which of them the driver sees is host behaviour: phone
            // projection is rendered by Google's host while the DHU drives the AOSP reference
            // renderer, so relying on either precedence would rest the requirement on something no
            // test here can observe. Setting both identifies the exit whichever glyph the host picks
            // (spec: auto/navigation-view - A roundabout exit uses the number-bearing host maneuver).
            builder.setRoundaboutExitNumber(step.exitOrdinal)
        }
        builder.setIcon(icon)
        val maneuver = builder.build()
        val stepBuilder = Step.Builder()
            .setManeuver(maneuver)
            .setCue(ManoeuvreLabels.short(resolver, manoeuvre))
        manoeuvre.displayName?.takeIf { it.isNotBlank() }?.let { stepBuilder.setRoad(it) }
        lanes.forEach { stepBuilder.addLane(it) }
        if (lanes.isNotEmpty()) {
            lanesImage?.let { stepBuilder.setLanesImage(it) }
        }
        return stepBuilder.build()
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
        iconFor: (RouteStep) -> CarIcon,
        includeLanes: Boolean,
        laneImageFor: (List<LaneTurn>, IntRange) -> CarIcon? = { _, _ -> null },
        resolver: StringResolver
    ): RoutingInfo? {
        if (!state.isNavigating) return null
        // Prefer the live step ahead: the native engine re-emits it with an updated remaining
        // distance on every position update, while the step list is frozen at route start. Using the
        // list entry froze the distance after the first turn.
        val current = state.nextStep
            ?: state.steps.getOrNull(state.currentStepIndex)
            ?: return null
        // The following MANOEUVRE, not the next list entry: the route's step list carries one step per
        // described route node (route census on device: steps=501, rows=34), so the entry after the current
        // step is normally a name/way-type node. The host's next-step row would otherwise name a street a
        // few metres ahead instead of the turn after it, and a roundabout exit lost its ordinal
        // (spec: auto/navigation-view — Current and next step in host instruction panel).
        val next = nextManoeuvreFrom(state.steps, current)

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
                stepForStep(
                    current,
                    iconFor(current),
                    lanes,
                    if (lanes.isNotEmpty()) laneImageFor(state.laneTurns, recommended) else null,
                    resolver
                ),
                distanceForDisplay(current.remainingMeters)
            )
        if (next != null) {
            builder.setNextStep(stepForStep(next, iconFor(next), resolver = resolver))
        }
        return builder.build()
    }

    /**
     * Route-description entries from [state] for the in-app route description
     * screen (spec: auto/navigation-view — "Route description screen"),
     * ordered from the current step on.
     */
    fun stepsFromCurrentStep(state: NavigationState): List<RouteStep> {
        val start = state.currentStepIndex.coerceIn(0, state.steps.lastIndex.coerceAtLeast(0))
        return state.steps.drop(start)
    }

    /** One row of the route description screen. */
    data class RouteDescriptionRow(
        val turnType: TurnType,
        val title: String,
        val text: String,
        val isCurrent: Boolean,
        /**
         * The row's own roundabout exit ordinal, or 0. A `Row` image cannot be a host-drawn
         * `Maneuver`, so this row takes the app's numbered glyph (spec: auto/navigation-view —
         * Route description screen).
         */
        val roundaboutExitNumber: Int = 0
    )

    /**
     * Rows for the route description screen: one row per **driving decision** — the rows the shared
     * policy derives from the remaining steps (spec: auto/navigation-view — Route description screen;
     * change `share-route-rows-in-core`) — titled by the row's own leg distance and its target street,
     * with the manoeuvre's description as the row's text and the first row current.
     *
     * Deriving here rather than listing the steps 1:1 keeps this list the phone list's row set: a
     * facts-only step earns no row and its leg joins the row above, and a row's distance is its own leg
     * rather than the distance from the route's start.
     */
    fun routeDescriptionRows(carContext: CarContext, state: NavigationState): List<RouteDescriptionRow> {
        val resolver = carContext.stringResolver()
        return routeRows(stepsFromCurrentStep(state)).mapIndexed { index, row ->
            val manoeuvre = row.manoeuvre
            val distance = carContext.getString(
                if (roundDistanceMeters(row.distanceMeters) >= 1000) R.string.distance_unit_km else R.string.distance_unit_m,
                formatDistanceNumber(row.distanceMeters)
            )
            val target = manoeuvre.displayName?.takeIf { it.isNotBlank() }
                ?: ManoeuvreLabels.short(resolver, manoeuvre)
            RouteDescriptionRow(
                turnType = turnTypeFor(manoeuvre),
                title = carContext.getString(R.string.distance_target, distance, target),
                text = ManoeuvreLabels.description(resolver, manoeuvre),
                isCurrent = index == 0,
                roundaboutExitNumber = manoeuvre.roundaboutExit ?: 0
            )
        }
    }

    /**
     * The route description's row census, as one coordinate-free diagnostics line
     * (spec: `auto/navigation-view` — Route description screen; spec: `auto-diagnostics` — Diagnostics
     * carry no coordinates; change `share-route-rows-in-core`).
     *
     * Counts only: the rows the shared policy derived from the steps the screen was handed. It is what
     * says on device whether the car list is the phone's row set (`rows` far below `steps` on a route
     * whose nodes are mostly facts-only) or one row per step again, which is the difference a route
     * description sends to its host as a template.
     */
    fun routeDescriptionDiagnostic(rowCount: Int, stepCount: Int): String =
        "route description rows=$rowCount steps=$stepCount"

    /**
     * Determine if the displayed fields changed enough to warrant a template re-render.
     * Throttles invalidations to avoid jank from 1Hz GPS updates.
     *
     * The distance to the current step is compared as the host displays it (see
     * [displayedDistanceBucket]), not metre by metre: the panel renders the rounded
     * number, so a sub-bucket change cannot change the template, and rebuilding for it only
     * sends the host a new template (and a new lane image) per GPS update
     * (spec: car-host-fault-isolation — Bounded host-facing traffic while not visible).
     * The remaining route distance is compared the same way (the ETA card shows the same
     * rounding), while the arrival estimate keeps its own comparison: a shifted estimate is
     * displayed content.
     */
    fun hasStateChanged(oldState: NavigationState?, newState: NavigationState): Boolean {
        val old = oldState ?: return true
        return old.isNavigating != newState.isNavigating ||
                displayedDistanceBucket(old.nextStep?.remainingMeters) !=
                    displayedDistanceBucket(newState.nextStep?.remainingMeters) ||
                old.nextStep?.let(::turnTypeFor) != newState.nextStep?.let(::turnTypeFor) ||
                old.nextStep?.exitOrdinal != newState.nextStep?.exitOrdinal ||
                old.nextStep?.kind != newState.nextStep?.kind ||
                manoeuvreFromStepOrNull(old.nextStep)?.displayName !=
                    manoeuvreFromStepOrNull(newState.nextStep)?.displayName ||
                displayedDistanceBucket(old.remainingDistance) !=
                    displayedDistanceBucket(newState.remainingDistance) ||
                old.etaMillis / 1000 != newState.etaMillis / 1000 ||
                old.currentSpeedKmH.toInt() != newState.currentSpeedKmH.toInt() ||
                old.isRerouting != newState.isRerouting ||
                old.laneCount != newState.laneCount ||
                old.laneTurns != newState.laneTurns
    }

    /**
     * The distance to the current step as the host instruction panel shows it: the same
     * rounding [distanceForDisplay] applies (raw below 50 m, 50 m steps below 1 km, 100 m
     * steps above), as a comparable value. `null`/NaN (no current step) maps to -1, which
     * no real distance produces. Pure, so the rebuild bound is unit-testable.
     */
    fun displayedDistanceBucket(meters: Double?): Long =
        if (meters == null || meters.isNaN()) NO_DISTANCE
        else roundDistanceMeters(meters.coerceAtLeast(0.0)).toLong()

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
     * @param iconFor maneuver artwork provider (shared with the template), which returns nothing when
     *   the host draws the maneuver itself
     * @param nowMillis wall clock used for the step's remaining time; injectable
     *   for tests
     * @param timeZone time zone of the arrival times; injectable for tests
     */
    fun tripFromState(
        state: NavigationState,
        iconFor: (RouteStep) -> CarIcon,
        resolver: StringResolver,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Trip? {
        if (!state.isNavigating) return null
        if (state.isRerouting) return loadingTrip()

        val current = state.nextStep
            ?: state.steps.getOrNull(state.currentStepIndex)
            ?: return loadingTrip()
        if (state.etaMillis <= 0L) return loadingTrip()

        val step = stepForStep(current, iconFor(current), resolver = resolver)
        val remainingSeconds = current.remainingSeconds
        val stepArrivalMillis = if (remainingSeconds > 0.0) {
            nowMillis + (remainingSeconds * 1000.0).toLong()
        } else {
            state.etaMillis
        }
        val stepEstimate = TravelEstimate.Builder(
            distanceForDisplay(current.remainingMeters),
            DateTimeWithZone.create(stepArrivalMillis, timeZone)
        ).apply {
            if (remainingSeconds > 0.0) setRemainingTimeSeconds(remainingSeconds.toLong())
        }.build()

        val builder = Trip.Builder().addStep(step, stepEstimate)
        destinationFromState(state, nowMillis, timeZone)?.let { (destination, estimate) ->
            builder.addDestination(destination, estimate)
        }
        manoeuvreFromStepOrNull(current)?.displayName?.let { builder.setCurrentRoad(it) }
        return builder.build()
    }

    /** The manoeuvre of [step], or null for a state without a step. */
    private fun manoeuvreFromStepOrNull(step: RouteStep?) : com.naviveylin.core.Manoeuvre? =
        step?.let(::manoeuvreFromStep)

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
    internal fun coordinatesText(state: NavigationState): String? {
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
                old.nextStep?.let(::turnTypeFor) != current.nextStep?.let(::turnTypeFor) ||
                old.nextStep?.exitOrdinal != current.nextStep?.exitOrdinal ||
                old.nextStep?.kind != current.nextStep?.kind ||
                manoeuvreFromStepOrNull(old.nextStep)?.displayName !=
                    manoeuvreFromStepOrNull(current.nextStep)?.displayName ||
                roundDistanceMeters(old.nextStep?.remainingMeters ?: 0.0) !=
                    roundDistanceMeters(current.nextStep?.remainingMeters ?: 0.0) ||
                roundDistanceMeters(old.remainingDistance) !=
                    roundDistanceMeters(current.remainingDistance) ||
                old.etaMillis / 1000 != current.etaMillis / 1000 ||
                old.destinationName != current.destinationName
    }
}
