package com.naviveylin.auto

import android.util.Log
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Trip
import com.naviveylin.core.NavigationState

/**
 * Owns the [NavigationManager] lifecycle for the car session (spec:
 * auto/navigation-view — "Leave navigation at any time", spec:
 * auto-navigation-hints — trip metadata and hint teardown): the host ETA card
 * stop button is delivered as [NavigationManagerCallback.onStopNavigation],
 * which the host only sends when the app has registered a callback AND called
 * [NavigationManager.navigationStarted] (both gates in the car-app 1.7.0
 * [NavigationManager] implementation). This controller registers the callback
 * before [NavigationManager.navigationStarted], clears it only after
 * [NavigationManager.navigationEnded] ([NavigationManager.clearNavigationManagerCallback]
 * throws while navigating), and forwards the host's stop request to the app's
 * stop handler.
 *
 * It also publishes the cluster / heads-up trip metadata through
 * [NavigationManager.updateTrip] while navigating, throttled by
 * [NavigationTemplateMapper.hasTripChanged] so the host is not flooded at the
 * position update rate. Publishing only happens between
 * [onNavigationStarted] and [onNavigationEnded]: the host drops (and the API
 * rejects) trip updates outside an active navigation session.
 *
 * All methods are main-thread (the [NavigationManager] contract); the session
 * calls them from its main-thread observer.
 */
class NavigationManagerController(
    private val navigationManager: NavigationManager,
    private val onStop: () -> Unit
) {

    private val callback = object : NavigationManagerCallback {
        override fun onStopNavigation() {
            onStop()
        }
    }

    /** True between [onNavigationStarted] and [onNavigationEnded]. */
    private var navigating = false

    /** Last state whose trip was published, for the change throttle. */
    private var lastPublishedState: NavigationState? = null

    /** Call when navigation starts, before showing the navigation screen. */
    fun onNavigationStarted() {
        navigationManager.setNavigationManagerCallback(callback)
        navigationManager.navigationStarted()
        navigating = true
        lastPublishedState = null
    }

    /** Call when navigation ends (user stop, destination reached, host stop). */
    fun onNavigationEnded() {
        navigating = false
        navigationManager.navigationEnded()
        navigationManager.clearNavigationManagerCallback()
    }

    /**
     * Publish the host trip for [state] when the displayed trip content changed
     * (spec: auto-navigation-hints — "Trip metadata for cluster and heads-up
     * display", "Trip publishing cadence"). No-op when navigation is not
     * active; host failures are swallowed (the connection may already be
     * gone), and [tripFor] returning null publishes nothing.
     */
    fun publishTrip(state: NavigationState, tripFor: (NavigationState) -> Trip?) {
        if (!navigating) return
        if (!NavigationTemplateMapper.hasTripChanged(lastPublishedState, state)) return
        val trip = tripFor(state) ?: return
        runCatching { navigationManager.updateTrip(trip) }
            .onFailure { Log.w(TAG, "trip update failed", it) }
        lastPublishedState = state
    }

    /**
     * Best-effort cleanup on session destroy — may be mid-navigation, and the
     * host connection may already be gone. Never throws, and stops trip
     * publication.
     */
    fun onDestroy() {
        navigating = false
        lastPublishedState = null
        runCatching { navigationManager.navigationEnded() }
        runCatching { navigationManager.clearNavigationManagerCallback() }
    }

    private companion object {
        private const val TAG = "NavigationManagerCtl"
    }
}
