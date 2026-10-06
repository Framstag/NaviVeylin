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
 * route panel view, and telling the map view model when to record its own mode
 * (the snapshot itself and the restore stay with the map surface).
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

    /**
     * Callback for the surface to record its own map mode — set by `MapCanvasScreen` and invoked
     * when a session *starts*, before anything forces follow on (spec: `map-modes` — navigation end
     * restores prior mode).
     */
    private var onPreNavigationSnapshot: (() -> Unit)? = null
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
     * Callback for the pre-navigation mode snapshot — set by `MapCanvasScreen`.
     *
     * The snapshot belongs here, at the one place that knows a session starts: taking it inside the
     * follow forcing instead leaves the ordering to two collectors of the same state, and a session
     * that holds the camera refuses that forcing before any snapshot could be taken — both ways the
     * restore then read a value navigation itself had already imposed (measured on the device:
     * `mode restored follow=true` after a browse-before navigation; spec: `map-modes` — navigation
     * end restores prior mode).
     */
    fun setPreNavigationSnapshotCallback(cb: () -> Unit) {
        onPreNavigationSnapshot = cb
    }

    /**
     * Wire the phone's route panel to the engine session.
     *
     * Follow mode is surface state, so the adapter enables it when a session
     * *starts* (never on a reroute of the running session, which must not rotate or
     * snap the map) and disables it when the session ends. The panel's route view
     * reflects whatever route the session runs on — including a route the engine
     * acquired for itself on a reroute (spec: `navigation-controller` — Reroute
     * handling). On a stop the session decides the route's fate: an open session keeps
     * it for its stopped state and grace window, and a stop with no session open leaves
     * the panel non-navigating with the route hidden — the notification's and the car
     * host's stop reach the same branch (spec: `navigation-controller` — Stop
     * navigation; spec: `route-planning-session` — Grace period after navigation is
     * stopped).
     */
    fun setRoutePanelViewModel(vm: RoutePanelViewModel) {
        routePanelViewModel = vm
        viewModelScope.launch {
            var wasNavigating = false
            engine.state.collect { navState ->
                if (navState.isNavigating) {
                    if (!wasNavigating) {
                        wasNavigating = true
                        // The surface records its own mode first: navigation forces follow on below,
                        // and the snapshot must never observe that forced-on value
                        // (spec: `map-modes` — navigation end restores prior mode).
                        onPreNavigationSnapshot?.invoke()
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
                    // An open session takes the route over for its stopped state and its grace
                    // window; with no session the stop clears the route from the map
                    // (spec: `route-planning-session` — Grace period after navigation is
                    // stopped; spec: `map-modes` — navigation end).
                    if (!vm.onNavigationStopped()) vm.clearRouteFromMap()
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

    override fun cancelAcquisition() {
        engine.cancelAcquisition()
    }

    override fun reportError(message: String, origin: SurfaceOrigin) {
        engine.reportError(message, origin)
    }
}
