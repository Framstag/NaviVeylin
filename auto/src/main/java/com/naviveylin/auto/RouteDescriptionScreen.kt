package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.RouteStep
import com.naviveylin.auto.R
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NavigationViewModel
import kotlinx.coroutines.Job
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

    private val scope = carScreenScope("RouteDescriptionScreen")
    private var observeJob: Job? = null
    private var lastSteps: List<RouteStep>? = null
    private var lastIndex = 0

    /** The last census line written, so an unchanged row set does not repeat it per invalidation. */
    private var lastDiagnostic: String? = null

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
            SafeScreen.errorTemplate(carContext, e.message)
        }
    }

    private fun buildTemplate(): ListTemplate {
        val state = navigationViewModel.state.value
        val rows = NavigationTemplateMapper.routeDescriptionRows(carContext, state)
        logRowCensus(NavigationTemplateMapper.stepsFromCurrentStep(state).size, rows.size)
        val itemList = androidx.car.app.model.ItemList.Builder().apply {
            rows.forEach { row ->
                val title = if (row.isCurrent) "▶ ${row.title}" else row.title
                addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(row.text)
                        .setImage(ManeuverGlyphs.forTurnTypeAndExit(row.turnType, row.roundaboutExitNumber))
                        .build()
                )
            }
        }.build()
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(state.destinationName ?: "Route")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    /**
     * Write the row census once per distinct row set (spec: `auto/navigation-view` — Route description
     * screen; spec: `auto-diagnostics` — no coordinates, counts only). The car list's row count is the
     * measurement of the change `share-route-rows-in-core`: it is what tells a route whose nodes are
     * mostly facts-only from a list that lists every published step.
     */
    private fun logRowCensus(stepCount: Int, rowCount: Int) {
        val line = NavigationTemplateMapper.routeDescriptionDiagnostic(rowCount, stepCount)
        if (line == lastDiagnostic) return
        lastDiagnostic = line
        DiagnosticsLog.log(CAR_SCREEN_DIAG_TAG, line)
    }

    /** Invalidate when the route or the current step changes. */
    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            navigationViewModel.state.collect { state ->
                val changed = state.steps !== lastSteps || state.currentStepIndex != lastIndex
                lastSteps = state.steps
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
