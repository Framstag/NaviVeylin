package com.naviveylin.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.NavigationState
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.ui.route.RoutePanelViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The phone surface's adapter onto the process-scoped [NavigationEngine]
 * (design D7): it exposes the engine's state and position flow to the phone map
 * and owns the *surface* reactions to a navigation session — follow mode, the
 * route panel view, and the mode snapshot/restore the map view model performs on
 * its own.
 *
 * It owns no navigation state and no native controller: navigation state is
 * process-owned by the engine (spec: `navigation-engine` — Per-surface view state
 * never moves into the engine, in the other direction too).
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val engine: NavigationEngine
) : ViewModel(), com.naviveylin.core.NavigationViewModel {

    override val state: StateFlow<NavigationState> = engine.state

    /** Engine-owned position stream for the map's follow mode. */
    override val positionFlow: StateFlow<NavigationPosition?> = engine.positionFlow

    private var onFollowModeChanged: ((Boolean) -> Unit)? = null
    private var routePanelViewModel: RoutePanelViewModel? = null

    /** Route the panel view has already adopted from the engine (identity compare). */
    private var adoptedRouteLats: DoubleArray? = null

    /**
     * True while the *phone* is the surface that started the running session.
     * Follow mode is view state: the phone enables it only for a session it started
     * itself, and a car-initiated session must leave the phone's map mode, center
     * and rotation untouched (spec: `navigation-controller` — Follow mode is not
     * imposed on the other surface).
     */
    private var phoneInitiatedSession = false

    /** Callback for follow mode toggling — set by MapCanvasScreen. */
    fun setFollowModeCallback(cb: (Boolean) -> Unit) {
        onFollowModeChanged = cb
    }

    /**
     * Wire the phone's route panel to the engine session.
     *
     * Follow mode is surface state, so the adapter enables it when a session
     * *starts* (never on a reroute of the running session, which must not rotate or
     * snap the map) and disables it when the session ends. The panel's route view
     * reflects whatever route the session runs on — including a route the engine
     * acquired for itself on a reroute (spec: `navigation-controller` — Reroute
     * handling) — and the panel is left non-navigating with the route hidden on
     * every stop path, notification and car stop included (spec:
     * `navigation-controller` — Stop navigation).
     */
    fun setRoutePanelViewModel(vm: RoutePanelViewModel) {
        routePanelViewModel = vm
        viewModelScope.launch {
            var wasNavigating = false
            engine.state.collect { navState ->
                if (navState.isNavigating) {
                    if (!wasNavigating) {
                        wasNavigating = true
                        // Restarting navigation redraws the route on the map (it may
                        // have been hidden by a previous stop — spec:
                        // stop-navigation-hides-route).
                        vm.showRouteOnMap()
                        vm.setNavigating(true)
                        if (phoneInitiatedSession) onFollowModeChanged?.invoke(true)
                    }
                    engine.acquiredRoute.value?.let { route ->
                        if (route.latitudes !== adoptedRouteLats) {
                            adoptedRouteLats = route.latitudes
                            vm.adoptRoute(route, navState.vehicle ?: Vehicle.CAR)
                        }
                    }
                    // Keep the route panel summary's active step in sync
                    // (spec: routing-summary — active step highlighting).
                    vm.setActiveStepIndex(navState.currentStepIndex)
                } else if (wasNavigating) {
                    wasNavigating = false
                    adoptedRouteLats = null
                    if (phoneInitiatedSession) onFollowModeChanged?.invoke(false)
                    phoneInitiatedSession = false
                    vm.setNavigating(false)
                    vm.clearRouteFromMap()
                }
            }
        }
    }

    /** Start navigation on a route the phone route panel acquired. */
    fun start(routeEntry: RouteEntry, vehicle: Vehicle) {
        phoneInitiatedSession = true
        engine.start(routeEntry, vehicle)
    }

    override fun stopNavigation() {
        engine.stopNavigation()
    }

    override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) {
        engine.navigateTo(destLat, destLon, destinationName)
    }

    override fun clearError() {
        engine.clearError()
    }

    override fun reportError(message: String, origin: SurfaceOrigin) {
        engine.reportError(message, origin)
    }
}
