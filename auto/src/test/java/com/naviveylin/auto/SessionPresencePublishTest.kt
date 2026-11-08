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
 * The lifecycle-specific seams the car session's two presence publishes run through (spec:
 * `car-session-presence` — Session start is published / Session end is published, and the
 * lifecycle callbacks select matching presence-publish seams).
 *
 * [NavigationSession] needs a host-provided `CarContext` and cannot be constructed in Robolectric.
 * The runtime cases therefore drive lifecycle-specific host-free seams, while one source-level case
 * binds the production `onStart` and `onDestroy` callbacks to the matching seam. Together they catch
 * both a seam with the wrong direction and swapped callback wiring.
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
    fun sessionCallbacksSelectMatchingPublishDirection() {
        val source = File("src/main/java/com/naviveylin/auto/NavigationSession.kt").readText()
        val startCallback = callbackBody(source, "onStart")
        val destroyCallback = callbackBody(source, "onDestroy")

        assertTrue(
            "the onStart callback selects the live publish",
            startCallback.contains("publishCarSessionPresenceOnStart(presence = ::carSessionPresence)")
        )
        assertFalse(
            "the onStart callback does not select the clearing publish",
            startCallback.contains("publishCarSessionPresenceOnDestroy(presence = ::carSessionPresence)")
        )
        assertTrue(
            "the onDestroy callback selects the clearing publish",
            destroyCallback.contains("publishCarSessionPresenceOnDestroy(presence = ::carSessionPresence)")
        )
        assertFalse(
            "the onDestroy callback does not select the live publish",
            destroyCallback.contains("publishCarSessionPresenceOnStart(presence = ::carSessionPresence)")
        )
    }

    @Test
    fun sessionStartPublishesLive() {
        val presence = FakePresence()

        val landed = publishCarSessionPresenceOnStart(presence = { presence })

        assertTrue("the start publish landed", landed)
        assertTrue("a started session is published live", presence.active.value)
    }

    @Test
    fun sessionEndClearsTheSignal() {
        val presence = FakePresence()
        presence.setActive(true)

        val landed = publishCarSessionPresenceOnDestroy(presence = { presence })

        assertTrue("the end publish landed", landed)
        assertFalse("an ended session leaves no stale signal", presence.active.value)
    }

    @Test
    fun aFaultingPublishIsConfinedAndNamed() {
        val presence = FakePresence(IllegalStateException("publish failed"))

        val landed = publishCarSessionPresenceOnStart(presence = { presence })

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

        val landed = publishCarSessionPresenceOnDestroy(presence = { presence })

        assertFalse(landed)
        assertTrue(
            "the clearing direction keeps its own log name",
            DiagnosticsLog.readEntries().any {
                it.contains("clear car session presence rejected:")
            }
        )
    }

    /** The body of one lifecycle callback in the production session source. */
    private fun callbackBody(source: String, callback: String): String {
        val marker = "override fun $callback(owner: LifecycleOwner) {"
        val start = source.indexOf(marker)
        assertTrue("production source has the $callback callback", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        throw AssertionError("production source never closes the $callback callback")
    }
}
