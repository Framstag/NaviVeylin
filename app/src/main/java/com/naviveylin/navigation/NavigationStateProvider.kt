package com.naviveylin.navigation

import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bridge that exposes navigation state to Android Auto.
 * Observes the app's [NavigationViewModel] and mirrors its state.
 */
@Singleton
class NavigationStateProvider @Inject constructor() : NavigationViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(NavigationState())
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var stopCallback: (() -> Unit)? = null
    private var navigateToCallback: ((Double, Double) -> Unit)? = null
    private var reportErrorCallback: ((String) -> Unit)? = null

    /** Start observing a [NavigationViewModel] and mirror its state + actions. */
    fun observe(source: NavigationViewModel) {
        scope.launch {
            source.state.collect { navState ->
                _state.value = navState
            }
        }
        stopCallback = { source.stopNavigation() }
        navigateToCallback = { destLat, destLon -> source.navigateTo(destLat, destLon) }
        reportErrorCallback = { message -> source.reportError(message) }
    }

    override fun stopNavigation() {
        stopCallback?.invoke()
    }

    override fun navigateTo(destLat: Double, destLon: Double) {
        navigateToCallback?.invoke(destLat, destLon)
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
        stopCallback = null
        navigateToCallback = null
        reportErrorCallback = null
    }
}
