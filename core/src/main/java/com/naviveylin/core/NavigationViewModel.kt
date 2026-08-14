package com.naviveylin.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the navigation controller, shared between the phone UI and Android Auto.
 * Implemented by [com.naviveylin.navigation.NavigationViewModel] in the :app module.
 */
interface NavigationViewModel {
    val state: StateFlow<NavigationState>

    /** Stop active navigation. */
    fun stopNavigation()

    /**
     * Navigate to a destination from current GPS position.
     * Calculates route and starts turn-by-turn navigation.
     */
    fun navigateTo(destLat: Double, destLon: Double)

    /** Clear any displayed error message. */
    fun clearError()

    /**
     * Surface an error message on the car screen (e.g. deep-link geocoding
     * found no match). Mirrored into [NavigationState.errorMessage].
     */
    fun reportError(message: String)
}
