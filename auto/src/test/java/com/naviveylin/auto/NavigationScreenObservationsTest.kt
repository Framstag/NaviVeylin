package com.naviveylin.auto

import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [NavigationScreenObservations] (spec: auto/screen-observation — One
 * instance of each observation per started period; A stopped screen performs no
 * renderer or host work; Observations are re-established with the current state on
 * start; design D2).
 *
 * The screen object itself needs a live `CarContext` and a Hilt entry point
 * (`NavigationScreenTest`), so the screen's observation wiring is covered here through
 * its seam: the real [CarScreenObservations] plus mockable `:core` providers and
 * counting effects. No `CarContext`, no Robolectric. Assertions on counts are deltas
 * against a counter taken before the emission (see [CarScreenObservationsTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationScreenObservationsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeLocationProvider : AutoLocationProvider {
        val flow = MutableStateFlow<AutoPosition?>(null)
        override fun position(): StateFlow<AutoPosition?> = flow.asStateFlow()
        override fun start() = Unit
        override fun stop() = Unit
    }

    private val states = MutableStateFlow(NavigationState(isNavigating = true))
    private val navigationViewModel = mockk<NavigationViewModel>(relaxed = true).also {
        every { it.state } returns states
    }
    private val location = FakeLocationProvider()
    private val basemap = BasemapReloadNotifier()
    private val dark = MutableStateFlow(false)

    private val navStates = mutableListOf<NavigationState>()
    private val fixes = mutableListOf<AutoPosition>()
    private val darkValues = mutableListOf<Boolean>()
    private var basemapInvalidations = 0

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(observations: CarScreenObservations) = NavigationScreenObservations(
        observations = observations,
        navigationViewModel = navigationViewModel,
        locationProvider = location,
        basemapNotifier = basemap,
        resolvedDark = dark,
        onNavigationState = { navStates += it },
        onFix = { fixes += it },
        onDark = { darkValues += it },
        onBasemapRevision = { basemapInvalidations++ }
    )

    private val fix = AutoPosition(lat = 51.5, lon = 7.5, bearing = 90.0, accuracy = 5.0, speedKmH = 40.0)

    @Test
    fun everyObservedSourceReachesItsEffect() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals(4, observations.liveObservationCount)

        val navBefore = navStates.size
        val darkBefore = darkValues.size
        states.value = NavigationState(isNavigating = true, remainingDistance = 100.0)
        location.flow.value = fix
        dark.value = true
        basemap.bump()
        advanceUntilIdle()

        assertEquals("the navigation state is observed", 1, navStates.size - navBefore)
        assertEquals("the GPS feed is observed", listOf(fix), fixes)
        assertEquals("the resolved presentation is observed", 1, darkValues.size - darkBefore)
        assertEquals("a basemap revision forces a re-render", 1, basemapInvalidations)
    }

    @Test
    fun threeStartStopCyclesLeaveOneInstancePerSource() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)

        repeat(3) { cycle ->
            wiring.start()
            advanceUntilIdle()
            assertEquals("four observations in cycle ${cycle + 1}", 4, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${cycle + 1}", 0, observations.liveObservationCount)
        }

        wiring.start()
        advanceUntilIdle()
        assertEquals("a restarted screen still observes four sources", 4, observations.liveObservationCount)

        val navBefore = navStates.size
        states.value = NavigationState(isNavigating = true, remainingDistance = 250.0)
        advanceUntilIdle()
        assertEquals("one state change reaches one observation", 1, navStates.size - navBefore)
    }

    @Test
    fun aStoppedScreenObservesNothing() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()

        val before = navStates.size
        states.value = NavigationState(isNavigating = true, remainingDistance = 100.0)
        advanceUntilIdle()
        assertEquals("a started screen observes its state", 1, navStates.size - before)

        observations.stop()
        val navBefore = navStates.size
        val fixesBefore = fixes.size
        states.value = NavigationState(isNavigating = true, remainingDistance = 50.0)
        location.flow.value = fix
        basemap.bump()
        advanceUntilIdle()

        assertEquals(
            "a stopped screen must not rebuild a template or touch the renderer",
            navBefore,
            navStates.size
        )
        assertEquals(fixesBefore, fixes.size)
        assertEquals(0, basemapInvalidations)
    }

    @Test
    fun aChangeWhileStoppedIsAppliedOnceOnStart() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()
        observations.stop()

        // The route recalculates while the screen is stopped.
        states.value = NavigationState(isNavigating = true, remainingDistance = 42.0)

        val before = navStates.size
        wiring.start()
        advanceUntilIdle()

        assertEquals("the change is applied exactly once on return", 1, navStates.size - before)
        assertEquals(42.0, navStates.last().remainingDistance, 0.0)
    }

    @Test
    fun theInitialBasemapRevisionIsNotApplied() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)

        wiring.start()
        advanceUntilIdle()

        assertEquals(0, basemapInvalidations)
    }

    @Test
    fun aNullFixIsNotApplied() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        location.flow.value = null
        advanceUntilIdle()

        assertEquals("no fix yet is not a position", 0, fixes.size)
    }
}
