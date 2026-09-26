package com.naviveylin.navigation

import com.naviveylin.core.CarSessionPresence
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the process-scoped car-session-presence signal (spec:
 * `car-session-presence`): a fresh instance is inactive, publishing is visible to
 * consumers, it is idempotent, and clearing it returns the signal to inactive.
 *
 * Plain JUnit: the implementation has no Android or logging dependency (the car
 * session records the transition in its own stream), so this class needs no
 * Robolectric sandbox and adds no heap pressure to the single unit-test fork — see
 * the fork-budget note in `app/build.gradle.kts`.
 */
class CarSessionPresenceImplTest {

    private fun activeOf(presence: CarSessionPresence): StateFlow<Boolean> = presence.active

    @Test
    fun aFreshProcessStartsWithNoLiveSession() {
        // In-memory only: nothing is persisted, so a restarted process cannot show
        // a stale "session live" value (spec scenario: Process restart starts clear).
        assertEquals(false, activeOf(CarSessionPresenceImpl()).value)
    }

    @Test
    fun publishingASessionStartIsVisibleToConsumers() {
        val presence = CarSessionPresenceImpl()

        presence.setActive(true)

        assertTrue(presence.active.value)
    }

    @Test
    fun publishingIsIdempotent() {
        val presence = CarSessionPresenceImpl()

        presence.setActive(true)
        val afterFirst = presence.active.value
        presence.setActive(true)

        assertTrue(afterFirst)
        assertTrue("a repeated publish must change nothing", presence.active.value)
    }

    @Test
    fun aDestroyedSessionClearsTheSignal() {
        val presence = CarSessionPresenceImpl()
        presence.setActive(true)

        presence.setActive(false)

        assertFalse("the signal must not claim a session that is gone", presence.active.value)
    }

    @Test
    fun aSecondInstanceIsIndependentOfTheFirst() {
        // Guards the "no stale value across a restart" contract: a new process gets
        // a new instance, and the old instance's value does not leak in.
        val first = CarSessionPresenceImpl()
        first.setActive(true)

        val second = CarSessionPresenceImpl()

        assertFalse(second.active.value)
    }
}
