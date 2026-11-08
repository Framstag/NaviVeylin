package com.naviveylin.auto

import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The seam the car session's two presence publishes run through (spec:
 * `car-session-presence` — Session start is published / Session end is published, and the publish
 * is one host-free seam).
 *
 * [NavigationSession] needs a host-provided `CarContext` and cannot be constructed in Robolectric,
 * so its lifecycle callbacks are unreachable from a unit case; the seam they call carries the
 * direction each callback publishes, and these cases assert it host-free — the same pattern as
 * [SessionEndArrivalDecisionTest] and [SessionHostGateTest].
 *
 * Robolectric is required because a rejection records through `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
class SessionPresencePublishTest {

    /** A presence a case can throw from, so the confinement half is asserted too. */
    private class FakePresence(
        private val throwOnSet: Throwable? = null
    ) : CarSessionPresence {
        private val _active = MutableStateFlow(false)
        override val active: StateFlow<Boolean> = _active
        override fun setActive(active: Boolean) {
            throwOnSet?.let { throw it }
            _active.value = active
        }
    }

    private val diagnostics = File.createTempFile("session-presence", ".log")

    @Before
    fun pointTheLogAtTheTempFile() {
        DiagnosticsLog.initForTest(diagnostics)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnostics.delete()
    }

    @Test
    fun sessionStartPublishesLive() {
        val presence = FakePresence()

        val landed = publishCarSessionPresence(presence = { presence }, live = true)

        assertTrue("the start publish landed", landed)
        assertTrue("a started session is published live", presence.active.value)
    }

    @Test
    fun sessionEndClearsTheSignal() {
        val presence = FakePresence()
        presence.setActive(true)

        val landed = publishCarSessionPresence(presence = { presence }, live = false)

        assertTrue("the end publish landed", landed)
        assertFalse("an ended session leaves no stale signal", presence.active.value)
    }

    @Test
    fun aFaultingPublishIsConfinedAndNamed() {
        val presence = FakePresence(IllegalStateException("publish failed"))

        val landed = publishCarSessionPresence(presence = { presence }, live = true)

        assertFalse("a rejected publish must not report success", landed)
        assertTrue(
            "the rejection names the step and is recorded, not thrown",
            DiagnosticsLog.readEntries().any {
                it.contains("publish car session presence rejected:")
            }
        )
    }

    @Test
    fun clearingAFaultingPublishIsNamedForTheClear() {
        val presence = FakePresence(IllegalStateException("clear failed"))

        val landed = publishCarSessionPresence(presence = { presence }, live = false)

        assertFalse(landed)
        assertTrue(
            "the clearing direction keeps its own log name",
            DiagnosticsLog.readEntries().any {
                it.contains("clear car session presence rejected:")
            }
        )
    }
}
