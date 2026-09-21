package com.naviveylin.service

import com.naviveylin.core.NavigationStopRequests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The foreground service's action routing (spec: navigation-ongoing-notification
 * — "Stop action for navigation"): the shade's stop action must broadcast a stop
 * request through [NavigationStopRequests] so whichever controller is navigating
 * ends its own navigation. The branch is the fix for the on-device failure in
 * `TODO.md` §46, where the action reached the wrong surface.
 */
class NavigationNotificationServiceActionTest {

    private class RecordingStopRequests : NavigationStopRequests {
        var requests = 0
        override val stopRequests: Flow<Unit> = emptyFlow()
        override fun requestStop() {
            requests++
        }
    }

    @Test
    fun stopNavigationActionRequestsAStop() {
        val stopRequests = RecordingStopRequests()

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP_NAVIGATION,
            stopRequests
        )

        assertEquals(1, stopRequests.requests)
    }

    @Test
    fun startAndServiceStopActionsDoNotRequestANavigationStop() {
        val stopRequests = RecordingStopRequests()

        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_START,
            stopRequests
        )
        NavigationNotificationService.handleAction(
            NavigationNotificationService.ACTION_STOP,
            stopRequests
        )
        NavigationNotificationService.handleAction(null, stopRequests)
        NavigationNotificationService.handleAction("com.example.unknown", stopRequests)

        assertEquals("only the stop-navigation action ends a route", 0, stopRequests.requests)
    }
}
