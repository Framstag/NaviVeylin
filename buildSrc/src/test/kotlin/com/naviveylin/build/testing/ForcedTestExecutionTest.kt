package com.naviveylin.build.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forced-test-execution toggle's rule (change `speed-up-build-test-gate`, spec
 * `build-test-gate` — "A gate run proves that its tests executed"): the value must force
 * execution, must not force it by accident, and a value it cannot read must be refused
 * rather than quietly meaning "not forced".
 */
class ForcedTestExecutionTest {

    private fun refusal(value: String): String? = try {
        ForcedTestExecution.isRequested(value)
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    @Test
    fun absentPropertyDoesNotForce() {
        assertFalse(ForcedTestExecution.isRequested(null))
    }

    @Test
    fun explicitFalseDoesNotForce() {
        assertFalse(ForcedTestExecution.isRequested("false"))
        assertFalse(ForcedTestExecution.isRequested("FALSE"))
        assertFalse(ForcedTestExecution.isRequested(" false "))
    }

    @Test
    fun barePropertyForces() {
        assertTrue(ForcedTestExecution.isRequested(""))
        assertTrue(ForcedTestExecution.isRequested(" "))
    }

    @Test
    fun explicitTrueForces() {
        assertTrue(ForcedTestExecution.isRequested("true"))
        assertTrue(ForcedTestExecution.isRequested("TRUE"))
        assertTrue(ForcedTestExecution.isRequested(" true "))
    }

    @Test
    fun anUnreadableValueIsRefusedWithActionableText() {
        val message = refusal("yes")
        requireNotNull(message) { "a value the toggle cannot read must not be accepted silently" }
        assertTrue(message, message.contains("-PforceTests=yes"))
        assertTrue(message, message.contains("to force test execution"))
        assertTrue(message, message.contains("-PforceTests=false"))
        assertTrue(message, message.contains("looks as asked for but is not"))
    }

    @Test
    fun theRefusalNamesThePropertyAndItsValues() {
        assertEquals(
            "-PforceTests=maybe is not a value this build understands. Use -PforceTests " +
                "(or -PforceTests=true) to force test execution, or leave the property out " +
                "(-PforceTests=false). Guessing would let a typo produce a build that looks as " +
                "asked for but is not.",
            refusal("maybe")
        )
    }
}
