package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Auto screen that displays turn-by-turn navigation via [NavigationTemplate].
 * Observes [NavigationViewModel.state] and invalidates the template on changes.
 */
class NavigationScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var lastState: NavigationState? = null

    init {
        // Back during navigation stops it; the session observer then pops the
        // screen back to the root menu.
        carContext.getOnBackPressedDispatcher().addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigationViewModel.stopNavigation()
                }
            }
        )
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                stopObserving()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        return try {
            buildTemplate()
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "NavigationTemplate build failed", e)
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun buildTemplate(): NavigationTemplate {
        val state = navigationViewModel.state.value

        if (!state.isNavigating) {
            return NavigationTemplate.Builder()
                .setBackgroundColor(androidx.car.app.model.CarColor.DEFAULT)
                .build()
        }

        val builder = NavigationTemplate.Builder()

        // Routing info: current step (next turn) + distance
        val routingInfo = buildRoutingInfo(state)
        builder.setNavigationInfo(routingInfo)

        // Travel estimate (ETA, remaining distance, remaining time)
        val estimate = buildTravelEstimate(state)
        builder.setDestinationTravelEstimate(estimate)

        // Stop navigation action + standard back icon (host back affordances
        // are unreliable on emulated hosts).
        val stopAction = Action.Builder()
            .setTitle("Stop")
            .setOnClickListener { onStopNavigation() }
            .build()
        builder.setActionStrip(
            ActionStrip.Builder()
                .addAction(Action.BACK)
                .addAction(stopAction)
                .build()
        )

        return builder.build()
    }

    /** Start observing state when screen becomes visible. */
    fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            navigationViewModel.state
                .collect { state ->
                    val changed = hasStateChanged(state)
                    lastState = state
                    if (changed) {
                        invalidate()
                    }
                }
        }
    }

    /** Stop observing state when screen is hidden. */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun hasStateChanged(newState: NavigationState): Boolean {
        return NavigationTemplateMapper.hasStateChanged(lastState, newState)
    }

    private fun buildRoutingInfo(state: NavigationState): RoutingInfo {
        val builder = RoutingInfo.Builder()

        if (state.isRerouting) {
            builder.setLoading(true)
        }

        state.nextInstruction?.let { instruction ->
            val step = buildStep(instruction, state)
            val distance = Distance.create(
                instruction.distanceTo.coerceAtLeast(0.0),
                Distance.UNIT_METERS
            )
            builder.setCurrentStep(step, distance)
        }

        return builder.build()
    }

    private fun buildTravelEstimate(state: NavigationState): TravelEstimate {
        val remainingDistance = Distance.create(
            state.remainingDistance.coerceAtLeast(0.0),
            Distance.UNIT_KILOMETERS
        )
        val remainingTimeSeconds = ((state.etaMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
        val arrivalDate = java.util.Date(state.etaMillis)
        val zonedDateTime = java.time.ZonedDateTime.ofInstant(
            arrivalDate.toInstant(),
            java.time.ZoneId.systemDefault()
        )

        return TravelEstimate.Builder(remainingDistance, zonedDateTime)
            .setRemainingTimeSeconds(remainingTimeSeconds)
            .build()
    }

    private fun buildStep(
        instruction: com.framstag.libosmscout.client.RouteInstruction,
        state: NavigationState
    ): Step {
        val maneuver = buildManeuver(instruction.turnType)
        val description = instruction.description

        val stepBuilder = Step.Builder(description)
            .setManeuver(maneuver)

        // Add lane guidance
        if (state.laneCount > 0 && state.laneTurns.isNotEmpty()) {
            val lanes = buildLanes(state)
            for (lane in lanes) {
                stepBuilder.addLane(lane)
            }
        }

        return stepBuilder.build()
    }

    private fun buildManeuver(turnType: com.framstag.libosmscout.client.TurnType): Maneuver {
        val type = NavigationTemplateMapper.maneuverTypeFromTurnType(turnType)
        return Maneuver.Builder(type).build()
    }

    private fun buildLanes(state: NavigationState): List<Lane> {
        val lanes = mutableListOf<Lane>()
        for (i in 0 until state.laneCount) {
            val turn = if (i < state.laneTurns.size) state.laneTurns[i] else null
            val shape = NavigationTemplateMapper.laneDirectionShapeFromLaneTurn(turn)
            val isSuggested = state.laneSuggested && i >= state.laneSuggestedFrom && i <= state.laneSuggestedTo
            val direction = LaneDirection.create(shape, isSuggested)
            val lane = Lane.Builder().addDirection(direction).build()
            lanes.add(lane)
        }
        return lanes
    }

    private fun onStopNavigation() {
        Log.d(TAG, "Stop navigation requested from car")
        navigationViewModel.stopNavigation()
        invalidate()
    }

    companion object {
        private const val TAG = "NavigationScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
    }
}
