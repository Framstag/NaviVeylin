package com.naviveylin.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [BasemapReloadNotifier]: bump increments the revision and
 * collectors always observe the latest value after rapid successive events
 * (spec: basemap-loading — renderers re-render on basemap data changes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BasemapReloadNotifierTest {

    @Test
    fun bumpIncrementsRevision() {
        val notifier = BasemapReloadNotifier()
        assertEquals(0L, notifier.revision.value)

        notifier.bump()
        assertEquals(1L, notifier.revision.value)

        notifier.bump()
        notifier.bump()
        assertEquals(3L, notifier.revision.value)
    }

    @Test
    fun collectorsObserveLatestAfterCoalescing() = runTest {
        val notifier = BasemapReloadNotifier()

        var seen: Long? = null
        val done = CompletableDeferred<Unit>()
        backgroundScope.launch {
            notifier.revision.collect { v ->
                seen = v
                if (v == 2L) done.complete(Unit)
            }
        }

        // Rapid successive events: the collector may coalesce 1 → 2, but it
        // MUST observe the latest revision (2) — never a stale value.
        notifier.bump()
        notifier.bump()
        withTimeout(1_000) { done.await() }

        assertEquals(2L, seen)
    }
}
