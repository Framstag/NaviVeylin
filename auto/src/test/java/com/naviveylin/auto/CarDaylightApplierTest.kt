package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CarDaylightApplier]: the host's day/night state reaches the
 * native client's `daylight` flag, repeated re-applies of the same state do
 * not push again, and a failed push retries on the next apply.
 */
class CarDaylightApplierTest {

    @Test
    fun firstApplyPushesHostState() {
        val pushed = mutableListOf<Boolean>()
        val applier = CarDaylightApplier { dark ->
            pushed.add(dark)
            true
        }

        assertTrue(applier.apply(true))
        assertEquals(listOf(true), pushed)
    }

    @Test
    fun repeatedApplyOfSameStateIsDeduped() {
        val pushed = mutableListOf<Boolean>()
        val applier = CarDaylightApplier { dark ->
            pushed.add(dark)
            true
        }

        assertTrue(applier.apply(true))
        assertTrue(applier.apply(true))
        assertTrue(applier.apply(true))

        assertEquals(1, pushed.size)
    }

    @Test
    fun stateChangePushesAgain() {
        val pushed = mutableListOf<Boolean>()
        val applier = CarDaylightApplier { dark ->
            pushed.add(dark)
            true
        }

        applier.apply(true)
        applier.apply(false)

        assertEquals(listOf(true, false), pushed)
    }

    @Test
    fun failedPushRetriesOnNextApply() {
        val pushed = mutableListOf<Boolean>()
        val applier = CarDaylightApplier { dark ->
            pushed.add(dark)
            false
        }

        assertFalse(applier.apply(true))
        assertFalse(applier.apply(true))

        assertEquals("failed push must be attempted again", 2, pushed.size)
    }

    @Test
    fun forceRepushesEvenAfterSuccess() {
        // The native side can silently drop a push while the DB is still
        // initializing (warmup race) or when the stylesheet is reloaded — a caller
        // that knows this passes force instead of defeating the dedupe for every later
        // call (spec: car-host-fault-isolation — Bounded periodic render work).
        val pushed = mutableListOf<Boolean>()
        val applier = CarDaylightApplier { dark ->
            pushed.add(dark)
            true
        }

        assertTrue(applier.apply(true))
        assertTrue(applier.apply(true)) // deduped
        assertEquals(1, pushed.size)

        assertTrue(applier.apply(true, force = true)) // re-pushed despite same value
        assertEquals(2, pushed.size)
    }

    @Test
    fun needsPushReportsWhetherAPushWouldHappen() {
        val applier = CarDaylightApplier { true }

        assertTrue("nothing pushed yet", applier.needsPush(true))
        applier.apply(true)
        assertFalse("same value, no force", applier.needsPush(true))
        assertTrue("a different value", applier.needsPush(false))
        assertTrue("forced", applier.needsPush(true, force = true))
    }
}
