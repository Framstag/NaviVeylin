package com.naviveylin.core

/**
 * Navigation controller for the Android Auto session.
 *
 * The phone's [com.naviveylin.navigation.NavigationViewModel] is a
 * HiltViewModel created by the phone UI; in the AA-only process (bound
 * service, no Activity) nothing instantiates it, so the shared
 * [NavigationStateProvider] mirror never gets a source and "Navigate here"
 * would be a no-op. This controller implements the shared
 * [NavigationViewModel] interface with real route calculation + turn-by-turn
 * navigation for the car (the same JNI APIs the phone VM uses), and observes
 * itself into the provider so all AA screens see live state.
 */
interface AutoNavigationController : NavigationViewModel {

    /** Feed a GPS fix into the native position agent during navigation. */
    fun processLocation(lat: Double, lon: Double, speedKmH: Double, accuracy: Double, timestamp: Long)
}
