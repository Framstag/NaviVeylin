package com.naviveylin.auto

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [CarScreenObservations] (spec: auto/screen-observation — One instance
 * of each observation per started period; A stopped screen performs no renderer or
 * host work; An observation fault is confined to its observation; design D1/D4).
 *
 * The class is the pure lifetime seam the car screens drive from their lifecycle
 * callbacks (no `CarContext`), but it runs under Robolectric (default sandbox): the
 * fault-confinement cases assert the failure is *logged*, and `android.util.Log` has no
 * working stub in a plain unit test.
 *
 * Every "one emission reaches one observation" assertion is written as a
 * **delta** against the counter taken just before the emission: a `StateFlow`
 * re-emits its current value to every new collector, so the absolute count grows by
 * one per started period by design (spec: "Observations are re-established with the
 * current state on start") and an absolute expectation would only measure the
 * number of past starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CarScreenObservationsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val observations = CarScreenObservations(mainDispatcher.dispatcher)

    /** An endless observation over a state flow, counting every emission it sees. */
    private class CountingObservation {
        val flow = MutableStateFlow(0)
        var emissions = 0
            private set

        suspend fun collectForever() {
            flow.collect { emissions++ }
        }
    }

    @Test
    fun tenStartStopCyclesLeaveExactlyOneInstancePerObservation() = runTest(mainDispatcher.dispatcher) {
        val gps = CountingObservation()
        val favorites = CountingObservation()
        val basemap = CountingObservation()

        repeat(10) {
            observations.start()
            observations.observe("gps") { gps.collectForever() }
            observations.observe("favorites") { favorites.collectForever() }
            observations.observe("basemap") { basemap.collectForever() }
            advanceUntilIdle()

            assertEquals("three observations after start ${it + 1}", 3, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${it + 1}", 0, observations.liveObservationCount)
            assertFalse(observations.isRunning)
        }

        // Stopped after the tenth cycle: an emission reaches nothing.
        val whileStopped = gps.emissions
        gps.flow.value = 1
        advanceUntilIdle()
        assertEquals("a stopped screen observes nothing", whileStopped, gps.emissions)

        // An eleventh period leaves exactly one instance of each: one emission
        // reaches each observation exactly once.
        observations.start()
        observations.observe("gps") { gps.collectForever() }
        observations.observe("favorites") { favorites.collectForever() }
        observations.observe("basemap") { basemap.collectForever() }
        advanceUntilIdle()
        assertEquals(3, observations.liveObservationCount)

        val gpsBefore = gps.emissions
        val favoritesBefore = favorites.emissions
        val basemapBefore = basemap.emissions
        gps.flow.value = 2
        favorites.flow.value = 2
        basemap.flow.value = 2
        advanceUntilIdle()

        assertEquals(1, gps.emissions - gpsBefore)
        assertEquals(1, favorites.emissions - favoritesBefore)
        assertEquals(1, basemap.emissions - basemapBefore)
    }

    @Test
    fun startIsIdempotent() = runTest(mainDispatcher.dispatcher) {
        val gps = CountingObservation()

        observations.start()
        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()
        assertEquals(1, observations.liveObservationCount)

        // A second start without a stop must not open a second started period.
        observations.start()
        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()

        assertEquals(1, observations.liveObservationCount)
        val before = gps.emissions
        gps.flow.value = 1
        advanceUntilIdle()
        assertEquals("one emission must reach one observation", 1, gps.emissions - before)
    }

    @Test
    fun theSameKeyIsObservedAtMostOncePerStartedPeriod() = runTest(mainDispatcher.dispatcher) {
        val first = CountingObservation()
        val second = CountingObservation()

        observations.start()
        observations.observe("gps") { first.collectForever() }
        observations.observe("gps") { second.collectForever() }
        advanceUntilIdle()

        assertEquals(1, observations.liveObservationCount)
        val before = first.emissions
        first.flow.value = 1
        second.flow.value = 1
        advanceUntilIdle()

        assertEquals(1, first.emissions - before)
        assertEquals("the duplicate registration must not run", 0, second.emissions)
    }

    @Test
    fun observeBeforeStartDoesNothing() = runTest(mainDispatcher.dispatcher) {
        val gps = CountingObservation()

        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()

        assertFalse(observations.isRunning)
        assertEquals(0, observations.liveObservationCount)
        gps.flow.value = 1
        advanceUntilIdle()
        assertEquals("nothing may run before the screen is started", 0, gps.emissions)
    }

    @Test
    fun stopBeforeStartIsANoOp() {
        observations.stop()
        observations.stop()

        assertFalse(observations.isRunning)
        assertEquals(0, observations.liveObservationCount)
    }

    @Test
    fun aStoppedObservationSeesNoFurtherEmissions() = runTest(mainDispatcher.dispatcher) {
        val gps = CountingObservation()

        observations.start()
        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()
        val running = gps.emissions
        gps.flow.value = 1
        advanceUntilIdle()
        assertEquals("a started screen observes its source", 1, gps.emissions - running)

        observations.stop()
        gps.flow.value = 2
        advanceUntilIdle()

        assertEquals("a stopped screen's observation must not run", running + 1, gps.emissions)
    }

    @Test
    fun restartingAppliesTheCurrentValueOnce() = runTest(mainDispatcher.dispatcher) {
        val gps = CountingObservation()
        gps.flow.value = 7

        observations.start()
        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()
        assertEquals("the current value is applied on start", 1, gps.emissions)

        observations.stop()
        val beforeRestart = gps.emissions
        observations.start()
        observations.observe("gps") { gps.collectForever() }
        advanceUntilIdle()

        assertEquals(
            "re-establishing applies the current value once, not per past start",
            1,
            gps.emissions - beforeRestart
        )
        assertTrue(observations.isRunning)
    }

    // ── fault confinement (spec: auto/screen-observation — An observation fault is confined to its observation) ──

    @Test
    fun anObservationBodyThatThrowsEndsOnlyThatObservation() = runTest(mainDispatcher.dispatcher) {
        // Without the period's exception handler the exception reaches the main thread's
        // uncaught handler: the process dies, and an app process that dies while a car
        // session is live is what takes the templates host down with it (TODO.md §51).
        val file = java.io.File.createTempFile("diag", ".log")
        com.naviveylin.core.DiagnosticsLog.initForTest(file)
        try {
            val healthy = CountingObservation()
            observations.start()
            observations.observe("gps") { throw IllegalStateException("boom") }
            observations.observe("favorites") { healthy.collectForever() }

            advanceUntilIdle()

            assertEquals("the faulting observation ended", 1, observations.liveObservationCount)
            val entries = com.naviveylin.core.DiagnosticsLog.readEntries()
            assertTrue(
                "the fault is logged with the observation's key: $entries",
                entries.any { it.contains("observation 'gps' failed") }
            )
        } finally {
            com.naviveylin.core.DiagnosticsLog.reset()
            file.delete()
        }
    }

    @Test
    fun siblingsKeepRunningAfterAFault() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "the surviving observations still deliver their
        // updates". A screen that lost one observation must still work; a GPS fault must not
        // blind its favorites or its day/night observation.
        val favorites = CountingObservation()
        val basemap = CountingObservation()
        observations.start()
        observations.observe("gps") { throw IllegalStateException("boom") }
        observations.observe("favorites") { favorites.collectForever() }
        observations.observe("basemap") { basemap.collectForever() }
        advanceUntilIdle()

        val favoritesBefore = favorites.emissions
        val basemapBefore = basemap.emissions
        favorites.flow.value = 1
        basemap.flow.value = 1
        advanceUntilIdle()

        assertEquals("favorites still deliver", favoritesBefore + 1, favorites.emissions)
        assertEquals("basemap revisions still deliver", basemapBefore + 1, basemap.emissions)
    }

    @Test
    fun guidanceKeepsRunningAfterAFault() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "Faulting observation while navigating". The
        // navigation screen's observations are independent: a fault in one of them must not
        // stop the others from driving guidance, the ETA card or the host trip.
        val navigation = CountingObservation()
        observations.start()
        observations.observe("gps") { throw IllegalStateException("boom") }
        observations.observe("navigation") { navigation.collectForever() }
        advanceUntilIdle()

        val before = navigation.emissions
        navigation.flow.value = 42
        advanceUntilIdle()

        assertEquals("navigation state still reaches the screen", before + 1, navigation.emissions)
    }

    @Test
    fun aFaultDoesNotConsumeTheStartedPeriod() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "A fault does not consume the started period":
        // the next start establishes the observation again, alongside the others.
        observations.start()
        observations.observe("gps") { throw IllegalStateException("boom") }
        advanceUntilIdle()

        observations.stop()
        observations.start()
        val gps = CountingObservation()
        observations.observe("gps") { gps.collectForever() }
        observations.observe("favorites") { CountingObservation().collectForever() }
        advanceUntilIdle()

        assertEquals(2, observations.liveObservationCount)
        gps.flow.value = 3
        advanceUntilIdle()
        assertTrue("the re-established observation delivers", gps.emissions >= 2)
    }
}
