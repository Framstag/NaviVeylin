package com.naviveylin.core

import kotlinx.coroutines.flow.Flow

/**
 * Process-wide, one-shot "end navigation" request seam (spec:
 * navigation-ongoing-notification — "Stop action for navigation").
 *
 * The notification's stop action (and the car host's end-navigation action)
 * must reach whichever surface is actually navigating — the phone
 * `NavigationViewModel` or the car `AANavigationController` — without either of
 * them owning a callback slot the other can overwrite. Implementations
 * broadcast instead: every live controller collects [stopRequests] and stops
 * its own navigation, and stopping an already-idle controller is a no-op.
 *
 * Direction of the two members matters:
 *  - **Emitters** (the foreground service on the notification action, and the
 *    shared state provider's `stopNavigation()` facade used by the car host)
 *    call [requestStop].
 *  - **Controllers** implement the stop themselves and collect [stopRequests].
 *    A controller's own `stopNavigation()` MUST NOT call [requestStop] — that
 *    would loop through its own collector.
 *
 * Implemented by the app-module singleton `NavigationStateProvider` (the same
 * shared seam that mirrors navigation state to both surfaces).
 */
interface NavigationStopRequests {

    /**
     * Emits once per stop request. Collectors are process-lifetime
     * (ViewModel/singleton scope), and the flow holds no replay: a request
     * emitted while nobody collects is dropped rather than replayed into a
     * later session.
     */
    val stopRequests: Flow<Unit>

    /** Ask every live navigation controller to end its active navigation. */
    fun requestStop()
}
