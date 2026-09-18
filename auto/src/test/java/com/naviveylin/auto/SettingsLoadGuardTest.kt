package com.naviveylin.auto

import androidx.lifecycle.LifecycleOwner
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic tests for [SettingsLoadGuard]'s render-recovery decisions
 * (spec: auto/preferences — "Settings screens never wedge on a loading
 * placeholder"). The guard is host-free: [android.util.Log] is replaced with
 * a no-op logger, and the watchdog/retry delays run on the test scheduler's
 * virtual clock, so `advanceTimeBy`/`advanceUntilIdle` drive every branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLoadGuardTest {

    private fun runGuard(
        contentLoaded: () -> Boolean = { true },
        invalidate: () -> Unit = { },
        maxAttempts: Int = 2,
        block: (TestScope, TestHarness) -> Unit
    ) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val invalidations = mutableListOf<Int>()
        val guard = SettingsLoadGuard(
            scope = scope,
            invalidate = { invalidations.add(invalidations.size) },
            contentLoaded = contentLoaded,
            maxInvalidateAttempts = maxAttempts,
            log = { _, _ -> }
        )
        block(
            this,
            TestHarness(
                guard = guard,
                scope = scope,
                invalidations = invalidations
            )
        )
        scope.cancel()
    }

    private class TestHarness(
        val guard: SettingsLoadGuard,
        val scope: CoroutineScope,
        val invalidations: MutableList<Int>
    )

    @Test
    fun contentArrivalDeliversWithConfirmRefresh() = runGuard(
        contentLoaded = { true }
    ) { ts, h ->
        h.guard.onLoadStarted()
        h.guard.onLoadSucceeded()
        // Content path: one delivery now + one confirm refresh after the
        // load-timeout window (covers a silently dropped success invalidate).
        ts.advanceTimeBy(2_500L)
        ts.advanceUntilIdle()
        assertEquals(2, h.invalidations.size)
        assertFalse(h.guard.failed)
    }

    @Test
    fun watchdogFiresOnlyAfterTimeoutWindow() = runGuard(
        contentLoaded = { false }
    ) { ts, h ->
        h.guard.onLoadStarted()
        // Inside the 2 s watchdog window (t=1999): nothing fired yet.
        ts.advanceTimeBy(1_999L)
        assertEquals(0, h.invalidations.size)
        // Cross the window (t=2001): exactly the first recovery invalidate.
        ts.advanceTimeBy(2L)
        assertEquals(1, h.invalidations.size)
        assertFalse(h.guard.failed)
    }

    @Test
    fun stallExhaustsCapAndEndsInErrorState() = runGuard(
        contentLoaded = { false },
        maxAttempts = 2
    ) { ts, h ->
        h.guard.onLoadStarted()
        ts.advanceTimeBy(5_000L)
        ts.advanceUntilIdle()
        // t=2s and t=4s recovery invalidates; the budget is gone, so the
        // t=6s step renders the error state (one more invalidate).
        assertEquals(3, h.invalidations.size)
        assertTrue(h.guard.failed)
    }

    @Test
    fun laterContentClearsErrorState() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        var loaded = false
        val guard = SettingsLoadGuard(
            scope = scope,
            invalidate = { },
            contentLoaded = { loaded },
            log = { _, _ -> }
        )
        // Stall past the recovery budget: the guard gives up with an error.
        guard.onLoadStarted()
        advanceTimeBy(5_000L)
        advanceUntilIdle()
        assertTrue(guard.failed)
        // Content finally arrives: the error state must clear, not linger.
        loaded = true
        guard.onLoadSucceeded()
        advanceUntilIdle()
        assertFalse(guard.failed)
        scope.cancel()
    }

    @Test
    fun thrownInvalidateIsSwallowedAndRetried() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        var calls = 0
        val guard = SettingsLoadGuard(
            scope = scope,
            invalidate = {
                calls++
                if (calls <= 2) throw RuntimeException("host gone")
            },
            contentLoaded = { true },
            log = { _, _ -> }
        )
        // Success path: content is present; the throwing host must not kill
        // anything — the retry fires after RETRY_DELAY_MS.
        guard.onLoadSucceeded()
        advanceUntilIdle()
        assertTrue(calls >= 2)
        scope.cancel()
    }

    @Test
    fun loadFailureTransitionsToErrorImmediately() = runGuard(
        contentLoaded = { false }
    ) { ts, h ->
        h.guard.onLoadStarted()
        h.guard.onLoadFailed()
        ts.advanceUntilIdle()
        assertTrue(h.guard.failed)
        // Error render invalidates once; no further watchdog nudging.
        assertEquals(1, h.invalidations.size)
    }

    @Test
    fun onLifecycleStartReRendersStalePlaceholder() = runGuard(
        contentLoaded = { false }
    ) { ts, h ->
        h.guard.onLoadStarted()
        h.guard.onLifecycleStart()
        ts.advanceUntilIdle()
        // Immediate recovery invalidate on re-visibility.
        assertTrue(h.invalidations.size >= 1)
    }

    @Test
    fun onLifecycleStartDeliversContentWhenLoaded() = runGuard(
        contentLoaded = { true }
    ) { ts, h ->
        h.guard.onLifecycleStart()
        ts.advanceUntilIdle()
        assertEquals(1, h.invalidations.size)
    }

    @Test
    fun retryResetsBudgetAndErrorState() = runGuard(
        contentLoaded = { false },
        maxAttempts = 1
    ) { ts, h ->
        h.guard.onLoadStarted()
        ts.advanceTimeBy(5_000L)
        ts.advanceUntilIdle()
        assertTrue(h.guard.failed)
        // User retries: fresh cycle with a reset budget.
        h.guard.onLoadStarted()
        ts.advanceTimeBy(5_000L)
        ts.advanceUntilIdle()
        assertTrue(h.guard.failed)
        assertTrue(h.invalidations.size >= 3)
    }

    @Test
    fun lifecycleObserverRunsReReadHookOnStartAndCancelsOnDestroy() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val invalidations = mutableListOf<Int>()
        val guard = SettingsLoadGuard(
            scope = scope,
            invalidate = { invalidations.add(invalidations.size) },
            contentLoaded = { true },
            log = { _, _ -> }
        )
        val hookCalls = mutableListOf<Int>()
        val observer = settingsLifecycleObserver(scope, guard) { hookCalls.add(hookCalls.size) }

        // The re-read hook must run before the guard re-render on re-visibility.
        observer.onStart(mockk<LifecycleOwner>(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, hookCalls.size)
        assertTrue(invalidations.size >= 1)

        // After destroy the scope is cancelled: no hook or guard job can fire.
        observer.onDestroy(mockk<LifecycleOwner>(relaxed = true))
        assertFalse(scope.isActive)
    }

    @Test
    fun throwingReReadHookDoesNotBlockReRender() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val invalidations = mutableListOf<Int>()
        val guard = SettingsLoadGuard(
            scope = scope,
            invalidate = { invalidations.add(invalidations.size) },
            contentLoaded = { true },
            log = { _, _ -> }
        )
        var hookCalls = 0
        val observer = settingsLifecycleObserver(scope, guard) {
            hookCalls++
            throw RuntimeException("hook exploded")
        }

        observer.onStart(mockk<LifecycleOwner>(relaxed = true))
        advanceUntilIdle()

        // The hook threw, but the guard re-render still ran and nothing crashed.
        assertEquals(1, hookCalls)
        assertTrue(invalidations.size >= 1)
        assertFalse(guard.failed)
    }
}
