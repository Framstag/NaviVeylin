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
 * rejects) trip updates outside an active navigation session. A rejected update
 * does not end the period by itself (spec: car-host-fault-isolation — "A
 * rejected trip update re-arms publishing within a retry budget"): the refused
 * content counts as delivered, so the next changed trip is sent again, and
 * [MAX_REJECTED_TRIP_UPDATES] consecutive refusals — a run with no accepted send in
 * between — stop publishing until the next [onNavigationStarted].
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
     * Trip updates the host refused in this navigation period, against
     * [MAX_REJECTED_TRIP_UPDATES]; reset by a delivered trip and by a navigation start.
     */
    private var rejectedUpdates = 0

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
                rejectedUpdates = 0
                return
            }
        navigating = true
        lastPublishedState = null
        rejectedUpdates = 0
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
     * the car session. A rejected update is not the end of the period by itself
     * (spec: car-host-fault-isolation — "A rejected trip update re-arms publishing
     * within a retry budget"): the refused content counts as delivered, so the next
     * changed trip is sent again — guidance resumes for a host that accepts again —
     * and once [MAX_REJECTED_TRIP_UPDATES] consecutive refusals have been spent, with
     * no accepted send in between, the controller treats the host session as ended
     * and stops publishing until the next [onNavigationStarted].
     */
    fun publishTrip(state: NavigationState, tripFor: (NavigationState) -> Trip?) {
        if (!navigating) return
        if (!NavigationTemplateMapper.hasTripChanged(lastPublishedState, state)) return
        val trip = runCatching { tripFor(state) }
            .onFailure { Log.w(TAG, "trip build failed — nothing published", it) }
            .getOrNull() ?: return
        runCatching { navigationManager.updateTrip(trip) }
            .onFailure {
                // The refused content counts as delivered, so the next *changed* trip is the
                // one that is re-sent: one rejection no longer ends the period.
                lastPublishedState = state
                rejectedUpdates++
                if (rejectedUpdates < MAX_REJECTED_TRIP_UPDATES) {
                    Log.w(TAG, "trip update rejected — re-arming for the next changed trip", it)
                } else {
                    Log.w(
                        TAG,
                        "trip update rejected — the retry budget is spent, treating the host session as ended",
                        it
                    )
                    navigating = false
                }
                return
            }
        rejectedUpdates = 0
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
        rejectedUpdates = 0
        runCatching { navigationManager.navigationEnded() }
        runCatching { navigationManager.clearNavigationManagerCallback() }
    }

    private companion object {
        private const val TAG = "NavigationManagerCtl"

        /**
         * Consecutive refused trip updates spent before publishing stops — an accepted
         * send ends the run and resets the count (spec: car-host-fault-isolation —
         * "A rejected trip update re-arms publishing within a retry budget").
         */
        private const val MAX_REJECTED_TRIP_UPDATES = 3

        /** Diagnostics tag for what the session sent the host (spec: car-host-fault-isolation — Host interaction is diagnosable). */
        const val HOST_TAG = "HOST"
    }
}
