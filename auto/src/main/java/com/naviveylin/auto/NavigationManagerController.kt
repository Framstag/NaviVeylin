package com.naviveylin.auto

import android.util.Log
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Trip
import com.naviveylin.core.DiagnosticsLog
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

    /**
     * Call when navigation starts, before showing the navigation screen.
     *
     * Guarded (spec: car-host-fault-isolation — No fault escapes into the host path):
     * the car-app library throws from these calls (`"No callback has been set"`, an
     * already-started session) and this runs on the session's main thread, where an
     * escaping exception kills the process. A rejected start leaves the controller not
     * navigating, so no trip is published to a host that refused the session.
     */
    fun onNavigationStarted() {
        runCatching { navigationManager.setNavigationManagerCallback(callback) }
            .onFailure { Log.w(TAG, "setNavigationManagerCallback failed", it) }
        runCatching { navigationManager.navigationStarted() }
            .onFailure {
                Log.w(TAG, "navigationStarted rejected — the host does not drive this session", it)
                navigating = false
                lastPublishedState = null
                return
            }
        navigating = true
        lastPublishedState = null
        DiagnosticsLog.log(HOST_TAG, "navigationStarted")
    }

    /**
     * Call when navigation ends (user stop, destination reached, host stop). Guarded:
     * `navigationEnded` / `clearNavigationManagerCallback` throw while the library still
     * considers the app navigating, and the host connection may already be gone
     * (spec: car-host-fault-isolation — "Host navigation call rejected").
     */
    fun onNavigationEnded() {
        navigating = false
        DiagnosticsLog.log(HOST_TAG, "navigationEnded")
        runCatching { navigationManager.navigationEnded() }
            .onFailure { Log.w(TAG, "navigationEnded failed", it) }
        runCatching { navigationManager.clearNavigationManagerCallback() }
            .onFailure { Log.w(TAG, "clearNavigationManagerCallback failed", it) }
    }

    /**
     * Publish the host trip for [state] when the displayed trip content changed
     * (spec: auto-navigation-hints — "Trip metadata for cluster and heads-up
     * display", "Trip publishing cadence"). No-op when navigation is not active.
     *
     * Both the trip construction and the host call are guarded (spec:
     * car-host-fault-isolation — "Trip metadata cannot be built"): the mapper runs
     * library validators that throw, and an escape would kill the collector that owns
     * the car session. A rejected update means the host's navigation session is gone,
     * so the controller treats it as ended instead of publishing into the void.
     */
    fun publishTrip(state: NavigationState, tripFor: (NavigationState) -> Trip?) {
        if (!navigating) return
        if (!NavigationTemplateMapper.hasTripChanged(lastPublishedState, state)) return
        val trip = runCatching { tripFor(state) }
            .onFailure { Log.w(TAG, "trip build failed — nothing published", it) }
            .getOrNull() ?: return
        runCatching { navigationManager.updateTrip(trip) }
            .onFailure {
                Log.w(TAG, "trip update rejected — treating the host session as ended", it)
                navigating = false
                return
            }
        // What the host was sent, with the content that changed (spec:
        // car-host-fault-isolation — Host interaction is diagnosable).
        DiagnosticsLog.log(
            HOST_TAG,
            "trip update remaining=${state.remainingDistance.toLong()}m eta=${state.etaMillis}"
        )
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

        /** Diagnostics tag for what the session sent the host (spec: car-host-fault-isolation — Host interaction is diagnosable). */
        const val HOST_TAG = "HOST"
    }
}
