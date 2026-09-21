package com.naviveylin.navigation

import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationStopRequests
import com.naviveylin.core.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bridge that exposes navigation state to Android Auto and carries
 * the process-wide stop-request seam.
 *
 * **State mirror**: every surface that owns navigation ([NavigationViewModel]
 * on the phone, `AANavigationController` for the car) registers itself with
 * [observe]. The exposed [state] is the *active* source's state — the first
 * registered source that is navigating, else the first registered one. A
 * single last-writer-wins mirror is wrong here: an idle car controller emits
 * its empty [NavigationState] when it registers, which would blank the phone's
 * live navigation state and can trip the ongoing-notification gate into
 * stopping itself mid-navigation (see `TODO.md` §46).
 *
 * **Stop requests**: [stopNavigation] no longer routes to one callback slot
 * (that slot was overwritten by whichever surface registered last, so the
 * phone notification's stop action could end up stopping the idle car
 * controller). It broadcasts through [NavigationStopRequests] instead, and
 * every registered controller stops its own navigation.
 */
@Singleton
class NavigationStateProvider @Inject constructor() : NavigationViewModel, NavigationStopRequests {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(NavigationState())
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val stopRequests: Flow<Unit> = _stopRequests.asSharedFlow()

    /**
     * Registered sources in registration order with their last mirrored state.
     * Guarded by the monitor of this instance ([observe], [unregister] and the
     * collector callbacks run on `Dispatchers.Main`, but the map is not assumed
     * to be confined to it).
     */
    private val sources = LinkedHashMap<NavigationViewModel, NavigationState>()

    private var navigateToCallback: ((Double, Double, String?) -> Unit)? = null
    private var reportErrorCallback: ((String) -> Unit)? = null

    /**
     * Start observing a [NavigationViewModel]: mirror its state and route
     * [navigateTo] / [reportError] to it. A source that stops living (phone
     * Activity cleared) must call [unregister] so it cannot keep claiming the
     * mirror.
     */
    fun observe(source: NavigationViewModel) {
        synchronized(sources) {
            sources[source] = source.state.value
        }
        scope.launch {
            source.state.collect { navState -> onSourceState(source, navState) }
        }
        navigateToCallback = { destLat, destLon, name -> source.navigateTo(destLat, destLon, name) }
        reportErrorCallback = { message -> source.reportError(message) }
        publishActiveState()
    }

    /**
     * Forget a [source] (its owner is being torn down — e.g. a phone
     * `NavigationViewModel` in `onCleared`). Its last state stops being
     * mirrored, so a dead surface cannot keep a driving state alive.
     */
    fun unregister(source: NavigationViewModel) {
        synchronized(sources) {
            sources.remove(source)
        }
        publishActiveState()
    }

    /**
     * Broadcast a stop request (see [NavigationStopRequests]): every registered
     * controller stops its own navigation; an idle one does nothing.
     */
    override fun stopNavigation() {
        requestStop()
    }

    override fun requestStop() {
        _stopRequests.tryEmit(Unit)
    }

    override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) {
        navigateToCallback?.invoke(destLat, destLon, destinationName)
    }

    override fun clearError() {
        // Error is cleared by the source NavigationViewModel
    }

    override fun reportError(message: String) {
        reportErrorCallback?.invoke(message)
    }

    /** Release resources. */
    fun dispose() {
        scope.cancel()
        synchronized(sources) {
            sources.clear()
        }
        navigateToCallback = null
        reportErrorCallback = null
    }

    private fun onSourceState(source: NavigationViewModel, navState: NavigationState) {
        synchronized(sources) {
            if (!sources.containsKey(source)) return
            sources[source] = navState
        }
        publishActiveState()
    }

    /**
     * Publish the active source's state: a navigating source wins over an idle
     * one (so an idle surface registering cannot blank a live navigation), the
     * first registered source is the fallback, and an empty registry publishes
     * a cleared state.
     */
    private fun publishActiveState() {
        val active = synchronized(sources) {
            (sources.entries.firstOrNull { it.value.isNavigating } ?: sources.entries.firstOrNull())
                ?.value
        } ?: NavigationState()
        if (_state.value != active) {
            _state.value = active
        }
    }
}
