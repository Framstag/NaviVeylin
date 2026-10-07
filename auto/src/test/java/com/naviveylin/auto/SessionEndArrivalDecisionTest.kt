package com.naviveylin.auto

import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NavigationState
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car session's end decision (spec: `auto/navigation-view` — Navigation ends when the car
 * session ends after arrival): the session that ends after reaching its destination ends the
 * navigation, one that ends without arrival keeps it, a session with no navigation does nothing,
 * the decision cannot escape a fault while the host is gone, and the recorded line is
 * coordinate-free.
 *
 * These are the pure seams of [NavigationSession]'s teardown: the session itself needs a
 * host-provided `CarContext` and cannot be constructed in Robolectric, so the predicate, the call it
 * makes and the line it records are asserted here. The lifecycle ordering around them (the host
 * navigation teardown runs first) is design D4 and is checked on the device by the change's tasks
 * 3.2/3.3.
 *
 * Robolectric is required because the rejection path calls `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
class SessionEndArrivalDecisionTest {

    private val diagnostics = File.createTempFile("session-end", ".log")

    @Before
    fun pointTheLogAtTheTempFile() {
        DiagnosticsLog.initForTest(diagnostics)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnostics.delete()
    }

    private fun state(
        isNavigating: Boolean,
        hasReachedDestination: Boolean,
        remainingDistance: Double = 0.0
    ) = NavigationState(
        isNavigating = isNavigating,
        hasReachedDestination = hasReachedDestination,
        remainingDistance = remainingDistance
    )

    @Test
    fun sessionEndAfterArrivalEndsNavigation() {
        var stops = 0

        val ended = endNavigationAfterArrival(state(true, true, remainingDistance = 12.0)) { stops++ }

        assertTrue("the ended session was driving to a destination it had reached", ended)
        assertEquals("the navigation is stopped exactly once", 1, stops)
    }

    @Test
    fun sessionEndWithoutArrivalKeepsNavigation() {
        var stops = 0

        val ended = endNavigationAfterArrival(state(true, false, remainingDistance = 340.0)) { stops++ }

        assertFalse("an unfinished route keeps driving without a car session", ended)
        assertEquals("nothing was stopped", 0, stops)
    }

    @Test
    fun sessionEndWithNoNavigationIsANoOp() {
        var stops = 0

        assertFalse(
            "a session that never navigated ends nothing",
            endNavigationAfterArrival(state(false, false)) { stops++ }
        )
        assertFalse(
            "a stale arrival fact must not end a session that is not navigating",
            endNavigationAfterArrival(state(false, true)) { stops++ }
        )
        assertEquals("nothing was stopped", 0, stops)
    }

    @Test
    fun stopIsCalledAtMostOnce() {
        var stops = 0

        assertTrue(
            endNavigationAfterArrival(state(true, true, remainingDistance = 5.0)) { stops++ }
        )
        // The stop resets the engine's state (arrival fact included), so a second evaluation of the
        // same teardown sees a session that is not navigating and calls nothing.
        assertFalse(endNavigationAfterArrival(NavigationState()) { stops++ })

        assertEquals("the exit is idempotent through the state it resets", 1, stops)
    }

    @Test
    fun aThrowingStopDoesNotEscapeTheLifecycleCallback() {
        var stops = 0

        val landed = guardedHostCall("end navigation after arrival", tag = SessionLog.SESSION_TAG) {
            endNavigationAfterArrival(state(true, true)) {
                stops++
                throw IllegalStateException("host is gone")
            }
        }

        assertEquals("the stop was attempted", 1, stops)
        assertFalse("the confined step reports the fault instead of throwing", landed)
        assertTrue(
            "the rejection is recorded for a device extract",
            DiagnosticsLog.readEntries().any {
                it.contains("end navigation after arrival rejected:")
            }
        )
    }

    @Test
    fun theDecisionLineNamesTheOutcomeWithoutACoordinate() {
        assertEquals(
            "Session end: navigating=true reached=true remaining=12m -> navigation ended",
            sessionEndNavigationDecisionMessage(state(true, true, remainingDistance = 12.4), ended = true)
        )
        assertEquals(
            "Session end: navigating=true reached=false remaining=340m -> navigation kept",
            sessionEndNavigationDecisionMessage(state(true, false, remainingDistance = 340.6), ended = false)
        )
    }
}
