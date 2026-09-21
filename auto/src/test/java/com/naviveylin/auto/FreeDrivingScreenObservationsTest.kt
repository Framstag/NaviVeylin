package com.naviveylin.auto

import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [FreeDrivingScreenObservations] (spec: auto/screen-observation — One
 * instance of each observation per started period; A stopped screen performs no
 * renderer or host work; Observations are re-established with the current state on
 * start; design D2).
 *
 * The screen object itself needs a live `CarContext` and a Hilt entry point
 * (`FreeDrivingScreenTest`), so the screen's observation wiring is covered here through
 * its seam: the real [CarScreenObservations] plus a mockable `:core` provider and
 * counting effects. Assertions on counts are deltas against a counter taken before
 * the emission (see [CarScreenObservationsTest]).
 *
 * Robolectric (default sandbox) is required for the fault-confinement case: the wiring
 * logs a failed fix through `android.util.Log`, which the stub implementation does not
 * provide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FreeDrivingScreenObservationsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeLocationProvider : AutoLocationProvider {
        val flow = MutableStateFlow<AutoPosition?>(null)
        override fun position(): StateFlow<AutoPosition?> = flow.asStateFlow()
        override fun start() = Unit
        override fun stop() = Unit
    }

    private val location = FakeLocationProvider()
    private val basemap = BasemapReloadNotifier()

    private val fixes = mutableListOf<AutoPosition>()
    private var basemapInvalidations = 0
    private var throwOnNextFix = false

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(observations: CarScreenObservations) = FreeDrivingScreenObservations(
        observations = observations,
        locationProvider = location,
        basemapNotifier = basemap,
        onFix = {
            fixes += it
            if (throwOnNextFix) {
                throwOnNextFix = false
                throw IllegalStateException("one bad fix")
            }
        },
        onBasemapRevision = { basemapInvalidations++ }
    )

    private val fix = AutoPosition(lat = 51.5, lon = 7.5, bearing = 90.0, accuracy = 5.0, speedKmH = 40.0)

    @Test
    fun everyObservedSourceReachesItsEffect() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals(2, observations.liveObservationCount)

        location.flow.value = fix
        basemap.bump()
        advanceUntilIdle()

        assertEquals("the GPS feed is observed", listOf(fix), fixes)
        assertEquals("a basemap revision forces a re-render", 1, basemapInvalidations)
    }

    @Test
    fun threeStartStopCyclesLeaveOneInstancePerSource() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)

        repeat(3) { cycle ->
            wiring.start()
            advanceUntilIdle()
            assertEquals("two observations in cycle ${cycle + 1}", 2, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${cycle + 1}", 0, observations.liveObservationCount)
        }

        wiring.start()
        advanceUntilIdle()
        assertEquals("a restarted screen still observes two sources", 2, observations.liveObservationCount)

        val before = fixes.size
        location.flow.value = fix
        advanceUntilIdle()
        assertEquals("one fix reaches one observation", 1, fixes.size - before)
    }

    @Test
    fun aStoppedScreenObservesNothing() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()

        location.flow.value = fix
        advanceUntilIdle()
        assertEquals(1, fixes.size)

        observations.stop()
        val fixesBefore = fixes.size
        location.flow.value = AutoPosition(lat = 51.6, lon = 7.6)
        basemap.bump()
        advanceUntilIdle()

        assertEquals(
            "a stopped screen must not touch the renderer or the host",
            fixesBefore,
            fixes.size
        )
        assertEquals(0, basemapInvalidations)
    }

    @Test
    fun aChangeWhileStoppedIsAppliedOnceOnStart() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()
        observations.stop()

        // The vehicle moved while the screen was stopped.
        val moved = AutoPosition(lat = 51.7, lon = 7.7)
        location.flow.value = moved

        val before = fixes.size
        wiring.start()
        advanceUntilIdle()

        assertEquals("the current fix is applied exactly once on return", 1, fixes.size - before)
        assertEquals(moved, fixes.last())
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

    @Test
    fun aThrowingFixDoesNotKillTheObservation() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        // The first fix fails; the observation must survive it, otherwise the
        // marker and the speed badge freeze at the last good fix (spec:
        // auto/free-driving — the badge mirrors the live speed).
        throwOnNextFix = true
        location.flow.value = fix
        advanceUntilIdle()

        val afterFailure = fixes.size
        assertEquals(1, afterFailure)

        location.flow.value = AutoPosition(lat = 51.8, lon = 7.8)
        advanceUntilIdle()

        assertEquals("the next fix still reaches the screen", 1, fixes.size - afterFailure)
    }
}
