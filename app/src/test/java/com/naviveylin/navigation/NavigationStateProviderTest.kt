package com.naviveylin.navigation

import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Shared-mirror and stop-request contract of [NavigationStateProvider] (spec:
 * navigation-ongoing-notification — "Stop action for navigation").
 *
 * Two surfaces register in one process (phone [NavigationViewModel] and the car
 * `AANavigationController`), so the provider must not let the last registrant own
 * the mirror or the stop command: an idle car controller emits an empty
 * [NavigationState] when it registers, and the old single-callback design let the
 * phone notification's stop action end up at the idle car controller — the
 * on-device failure recorded in `TODO.md` §46.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationStateProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Minimal source: an observable state plus a stop counter. */
    private class FakeSource(initial: NavigationState = NavigationState()) : NavigationViewModel {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<NavigationState> = _state.asStateFlow()

        var stopCalls = 0

        override fun stopNavigation() {
            stopCalls++
        }

        override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) = Unit

        override fun clearError() = Unit

        override fun reportError(message: String) = Unit

        fun emit(state: NavigationState) {
            _state.value = state
        }
    }

    @Test
    fun navigatingSourceWinsTheMirror() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val idle = FakeSource()
        val navigating = FakeSource(
            NavigationState(isNavigating = true, destinationName = "Home")
        )

        provider.observe(idle)
        provider.observe(navigating)
        advanceUntilIdle()

        assertTrue("the navigating source is mirrored", provider.state.value.isNavigating)
        assertEquals("Home", provider.state.value.destinationName)
    }

    @Test
    fun lateIdleSourceDoesNotBlankLiveNavigation() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val phone = FakeSource(NavigationState(isNavigating = true, destinationName = "Home"))
        provider.observe(phone)
        advanceUntilIdle()

        // The car session warms up mid-drive and registers an idle controller
        // with an empty state — it must not blank the phone's navigation state
        // (which would stop the ongoing notification).
        val idleCar = FakeSource()
        provider.observe(idleCar)
        advanceUntilIdle()
        idleCar.emit(NavigationState())
        advanceUntilIdle()

        assertTrue("live navigation survives an idle registrant", provider.state.value.isNavigating)
        assertEquals("Home", provider.state.value.destinationName)
    }

    @Test
    fun unregisteringTheNavigatingSourceClearsTheMirror() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val phone = FakeSource(NavigationState(isNavigating = true, destinationName = "Home"))
        val idleCar = FakeSource()

        provider.observe(phone)
        provider.observe(idleCar)
        advanceUntilIdle()
        assertTrue(provider.state.value.isNavigating)

        // The phone Activity is destroyed in the background: its ViewModel calls
        // unregister, so a dead surface can no longer claim a driving state (or
        // keep the ongoing notification alive).
        provider.unregister(phone)
        advanceUntilIdle()

        assertFalse("no lying navigation state", provider.state.value.isNavigating)
    }

    @Test
    fun stopNavigationReachesEveryCollector() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val received = mutableListOf<Unit>()

        // Two collectors stand for the phone ViewModel and the car controller.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.stopRequests.collect { received += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.stopRequests.collect { received += it }
        }
        advanceUntilIdle()

        // The facade the car host calls (entryPoint.navigationViewModel()).
        provider.stopNavigation()
        advanceUntilIdle()

        assertEquals("both controllers are asked to stop", 2, received.size)
    }

    @Test
    fun requestStopIsTheOnlyEmission() = runTest(mainDispatcherRule.dispatcher) {
        val provider = NavigationStateProvider()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.stopRequests.collect { received += it }
        }
        advanceUntilIdle()

        provider.requestStop()
        advanceUntilIdle()

        assertEquals(1, received.size)
    }
}
