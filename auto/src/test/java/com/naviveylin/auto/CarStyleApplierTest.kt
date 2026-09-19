package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CarStyleApplier]: the persisted phone style reaches the native
 * client at car session start, repeated settings re-reads do not reload the
 * same style, and a failed load retries on the next apply.
 */
class CarStyleApplierTest {

    @Test
    fun firstApplyLoadsPersistedStyle() {
        val loaded = mutableListOf<String>()
        val applier = CarStyleApplier { style ->
            loaded.add(style)
            true
        }

        assertTrue(applier.apply("cycle"))
        assertEquals(listOf("cycle"), loaded)
    }

    @Test
    fun repeatedApplyOfSameStyleIsDeduped() {
        val loaded = mutableListOf<String>()
        val applier = CarStyleApplier { style ->
            loaded.add(style)
            true
        }

        assertTrue(applier.apply("cycle"))
        assertTrue(applier.apply("cycle"))
        assertTrue(applier.apply("cycle"))

        assertEquals(1, loaded.size)
    }

    @Test
    fun styleChangeReloads() {
        val loaded = mutableListOf<String>()
        val applier = CarStyleApplier { style ->
            loaded.add(style)
            true
        }

        applier.apply("standard")
        applier.apply("cycle")

        assertEquals(listOf("standard", "cycle"), loaded)
    }

    @Test
    fun failedLoadRetriesOnNextApply() {
        val loaded = mutableListOf<String>()
        val applier = CarStyleApplier { style ->
            loaded.add(style)
            false
        }

        assertFalse(applier.apply("cycle"))
        assertFalse(applier.apply("cycle"))

        assertEquals("failed load must be attempted again", 2, loaded.size)
    }

    @Test
    fun failedLoadIsSurfacedOncePerAttempt() {
        // The car screens pass the failure to the shared reporting seam
        // (spec: map-styles — "Car reports the failure"); the callback fires
        // exactly once per failed attempt, including a retry of the same style.
        val requested = mutableListOf<String>()
        val applier = CarStyleApplier(
            onLoadFailed = { style -> requested.add(style) },
            loadStyle = { false }
        )

        assertFalse(applier.apply("cycle"))
        assertFalse(applier.apply("cycle"))
        assertEquals(listOf("cycle", "cycle"), requested)
    }

    @Test
    fun successfulLoadDoesNotReportAFailure() {
        val requested = mutableListOf<String>()
        val applier = CarStyleApplier(
            onLoadFailed = { style -> requested.add(style) },
            loadStyle = { true }
        )

        assertTrue(applier.apply("cycle"))
        assertTrue("already applied is not a failure", applier.apply("cycle"))
        assertTrue(requested.isEmpty())
    }
}
