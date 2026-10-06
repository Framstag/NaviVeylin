package com.naviveylin.service

import com.framstag.libosmscout.client.NavigationPosition
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.SurfaceOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The foreground service's action routing (spec: `navigation-controller` — Stop
 * from a notification or car action; `navigation-ongoing-notification` — "Stop
 * action for navigation"): the shade's stop action ends the one process-scoped
 * navigation session. This is the branch that fixed the on-device failure in
 * `TODO.md` §46, where the action reached the wrong surface — with a single engine
 * there is only one target.
 */
class NavigationNotificationServiceActionTest {

    /** Records the stop calls the service routes into the navigation engine. */
    private class RecordingNavigationViewModel : NavigationViewModel {
        var stops = 0
        override val state: StateFlow<NavigationState> = MutableStateFlow(NavigationState())
        override val positionFlow: StateFlow<NavigationPosition?> = MutableStateFlow(null)
        override fun stopNavigation() {
            stops++
        }

        override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) = Unit
        override fun clearError() = Unit
        override fun cancelAcquisition() = Unit
        override fun reportError(message: String, origin: SurfaceOrigin) = Unit
    }

    @Test
    fun stopNavigationActionStopsTheEngine() {
        val navigation = RecordingNavigationViewModel()

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP_NAVIGATION,
            navigation
        )

        assertEquals(1, navigation.stops)
    }

    @Test
    fun startAndServiceStopActionsDoNotStopNavigation() {
        val navigation = RecordingNavigationViewModel()

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_START,
            navigation
        )
        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP,
            navigation
        )
        NavigationNotificationService.handleAction(null, navigation)
        NavigationNotificationService.handleAction("com.example.unknown", navigation)

        assertEquals("only the stop-navigation action ends a route", 0, navigation.stops)
    }
}
