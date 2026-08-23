package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.RouteInstruction
import com.naviveylin.core.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Route description for the active navigation (spec: auto/navigation-view —
 * "Route description screen"): a scrollable [ListTemplate] with every upcoming
 * route instruction from the current step on, each row showing the maneuver
 * icon, distance and target street; the current step is highlighted. The host
 * instruction panel only shows current + next step, so the full list lives
 * here. Pushed from the navigation screen's route-list action; BACK pops back
 * to the navigation view (navigation keeps running).
 */
class RouteDescriptionScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var lastInstructions: List<RouteInstruction>? = null
    private var lastIndex = 0

    init {
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
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun buildTemplate(): ListTemplate {
        val state = navigationViewModel.state.value
        val rows = NavigationTemplateMapper.routeDescriptionRows(state)
        val itemList = androidx.car.app.model.ItemList.Builder().apply {
            rows.forEach { row ->
                val title = if (row.isCurrent) "▶ ${row.title}" else row.title
                addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(row.text)
                        .setImage(ManeuverGlyphs.forTurnType(row.turnType))
                        .build()
                )
            }
        }.build()
        return ListTemplate.Builder()
            .setTitle(state.destinationName ?: "Route")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList)
            .build()
    }

    /** Invalidate when the route or the current step changes. */
    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            navigationViewModel.state.collect { state ->
                val changed = state.instructions !== lastInstructions || state.currentStepIndex != lastIndex
                lastInstructions = state.instructions
                lastIndex = state.currentStepIndex
                if (changed) {
                    Log.d(TAG, "route description state changed — invalidating")
                    invalidate()
                }
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    companion object {
        private const val TAG = "RouteDescriptionScreen"
    }
}
