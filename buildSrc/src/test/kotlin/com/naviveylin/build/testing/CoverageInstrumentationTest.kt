package com.naviveylin.build.testing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coverage-instrumentation switch's rule (change `speed-up-build-test-gate`, spec
 * `test-coverage` — "Instrumentation is attached only when coverage is requested"): the default
 * must stay attached, so an invocation that forgets the flag never loses its coverage data, and
 * a value the switch cannot read must be refused instead of quietly detaching the agent.
 */
class CoverageInstrumentationTest {

    @Test
    fun theAgentStaysAttachedByDefault() {
        assertFalse(CoverageInstrumentation.isDisabled(null))
        assertFalse(CoverageInstrumentation.isDisabled("false"))
    }

    @Test
    fun theOptOutDetachesTheAgent() {
        assertTrue(CoverageInstrumentation.isDisabled(""))
        assertTrue(CoverageInstrumentation.isDisabled("true"))
        assertTrue(CoverageInstrumentation.isDisabled(" TRUE "))
    }

    @Test
    fun anUnreadableValueIsRefusedWithActionableText() {
        val message = try {
            CoverageInstrumentation.isDisabled("nope")
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }
        requireNotNull(message) { "a value the switch cannot read must not be accepted silently" }
        assertTrue(message, message.contains("-PnoCoverage=nope"))
        assertTrue(message, message.contains("without the coverage agent"))
        assertFalse(message, message.contains("forceTests"))
    }
}
