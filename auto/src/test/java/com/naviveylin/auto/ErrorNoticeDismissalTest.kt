package com.naviveylin.auto

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the deferred dismissal of an error notice (spec: car-host-fault-isolation — Host
 * screen-stack mutations are balanced, "Deferred mutation after the session ended"; design
 * D5).
 *
 * The dismissal used to be `popToRoot()` four seconds later: it removed the navigation
 * view with the notice, it ran even when the session was gone, and the local error state
 * was only cleared as a side effect of that same coroutine. All three properties are
 * asserted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorNoticeDismissalTest {

    private val delayMs = 4_000L

    private class Fakes {
        var localErrorCleared = 0
        var removed: Any? = null
        var removals = 0
        var deferred = 0
        var skipped: String? = null
        var onRemovedCalls = 0
        var removeResult = true
    }

    private suspend fun dismiss(
        fakes: Fakes,
        notice: Any = "notice",
        sessionUsable: Boolean = true,
        hostMutationAllowed: Boolean = true
    ) = dismissErrorNotice(
        notice = notice,
        delayMs = delayMs,
        clearLocalError = { fakes.localErrorCleared++ },
        isSessionUsable = { sessionUsable },
        hostMutationAllowed = { hostMutationAllowed },
        removeNotice = { target ->
            fakes.removals++
            fakes.removed = target
            fakes.removeResult
        },
        onRemoved = { fakes.onRemovedCalls++ },
        onDeferred = { fakes.deferred++ },
        onSkipped = { reason -> fakes.skipped = reason }
    )

    @Test
    fun theNoticeIsRemovedAfterTheDelayAndNothingHappensBefore() = runTest {
        val fakes = Fakes()
        val job = launch { dismiss(fakes, notice = "notice") }

        advanceTimeBy(delayMs - 1)
        assertEquals("nothing before the delay elapses", 0, fakes.removals)

        advanceTimeBy(1)
        job.join()

        assertEquals("the notice itself is removed", listOf("notice"), listOf(fakes.removed))
        assertEquals(1, fakes.onRemovedCalls)
        assertEquals("the local error state is cleared once", 1, fakes.localErrorCleared)
        assertEquals("no deferral and no skip", 0, fakes.deferred)
        assertEquals(null, fakes.skipped)
    }

    @Test
    fun aDestroyedSessionGetsNoHostCallButStillClearsTheError() = runTest {
        val fakes = Fakes()

        dismiss(fakes, sessionUsable = false)

        assertEquals("no host mutation for a session that is gone", 0, fakes.removals)
        assertEquals("the local error state is cleared regardless", 1, fakes.localErrorCleared)
        assertEquals("nothing is owed: the session is gone", 0, fakes.deferred)
        assertTrue("the skip is reported: ${fakes.skipped}", fakes.skipped != null)
    }

    @Test
    fun aStoppedSessionGetsNoHostCallAndOwesTheRemoval() = runTest {
        val fakes = Fakes()

        dismiss(fakes, hostMutationAllowed = false)

        assertEquals("a stopped session mutates no host state", 0, fakes.removals)
        assertEquals("the local error state is cleared regardless", 1, fakes.localErrorCleared)
        assertEquals("the removal is owed to the next started sync", 1, fakes.deferred)
    }

    @Test
    fun aSupersededNoticeIsNotRemoved() = runTest {
        val fakes = Fakes()

        dismiss(fakes, sessionUsable = false)

        assertEquals(0, fakes.removals)
        assertEquals("a superseded notice is not owed either", 0, fakes.deferred)
    }

    @Test
    fun aRejectedRemovalDoesNotReportSuccess() = runTest {
        val fakes = Fakes().apply { removeResult = false }

        dismiss(fakes)

        assertEquals("the host was asked once", 1, fakes.removals)
        assertEquals("a rejected removal is not reported as done", 0, fakes.onRemovedCalls)
        assertFalse("the session keeps the notice it could not remove", fakes.removed == null)
    }
}
