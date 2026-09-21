package com.naviveylin.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [SessionHostGate] (spec: car-host-fault-isolation — Bounded host-facing traffic
 * while not visible; design D6): host mutations are allowed while the session is
 * started, deferred while it is not, and re-applied exactly once on the next start.
 */
class SessionHostGateTest {

    @Test
    fun aStartedSessionAllowsHostMutations() {
        val gate = SessionHostGate()

        gate.onSessionStart()

        assertTrue(gate.allowHostMutation())
        assertTrue(gate.allowHostMutation())
        assertFalse("nothing deferred while started", gate.syncPending)
    }

    @Test
    fun aStoppedSessionDefersAndOwesExactlyOneSync() {
        val gate = SessionHostGate()
        gate.onSessionStart()
        gate.onSessionStop()

        // A transition while backgrounded must not touch the host...
        assertFalse(gate.allowHostMutation())
        assertFalse(gate.allowHostMutation())
        assertTrue("a sync is owed", gate.syncPending)

        // ...and is applied once when the session starts again.
        assertTrue(gate.onSessionStart())
        assertFalse("applied once, not repeatedly", gate.syncPending)
        assertFalse("no second application", gate.onSessionStart())
        assertTrue(gate.allowHostMutation())
    }

    @Test
    fun aStartWithoutDeferredWorkOwsNothing() {
        val gate = SessionHostGate()

        assertFalse(gate.onSessionStart())
        assertFalse(gate.syncPending)
    }

    @Test
    fun aStoppedSessionThatNeverMutatedAStartOwsNothing() {
        val gate = SessionHostGate()
        gate.onSessionStart()
        gate.onSessionStop()

        assertFalse("nothing was attempted while stopped", gate.onSessionStart())
    }
}
