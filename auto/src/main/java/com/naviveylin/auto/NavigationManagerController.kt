package com.naviveylin.auto

import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback

/**
 * Owns the [NavigationManager] lifecycle for the car session (spec:
 * auto/navigation-view — "Leave navigation at any time"): the host ETA card
 * stop button is delivered as [NavigationManagerCallback.onStopNavigation],
 * which the host only sends when the app has registered a callback AND called
 * [NavigationManager.navigationStarted] (both gates in the car-app 1.7.0
 * [NavigationManager] implementation). This controller registers the callback
 * before [NavigationManager.navigationStarted], clears it only after
 * [NavigationManager.navigationEnded] ([NavigationManager.clearNavigationManagerCallback]
 * throws while navigating), and forwards the host's stop request to the app's
 * stop handler.
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

    /** Call when navigation starts, before showing the navigation screen. */
    fun onNavigationStarted() {
        navigationManager.setNavigationManagerCallback(callback)
        navigationManager.navigationStarted()
    }

    /** Call when navigation ends (user stop, destination reached, host stop). */
    fun onNavigationEnded() {
        navigationManager.navigationEnded()
        navigationManager.clearNavigationManagerCallback()
    }

    /**
     * Best-effort cleanup on session destroy — may be mid-navigation, and the
     * host connection may already be gone. Never throws.
     */
    fun onDestroy() {
        runCatching { navigationManager.navigationEnded() }
        runCatching { navigationManager.clearNavigationManagerCallback() }
    }
}
