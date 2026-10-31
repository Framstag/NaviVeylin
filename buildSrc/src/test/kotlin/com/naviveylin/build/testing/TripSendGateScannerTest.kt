package com.naviveylin.build.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the trip-send gate (spec `car-host-fault-isolation` — Trip metadata publishing is bounded
 * by the navigation period; issue #71).
 *
 * The fixtures are the real shape: the navigation-state collector — which *must* ask
 * `hostGate.allowHostMutation()` — sits above the trip collector, which must not. That is what the
 * scoping has to get right, so it is the first case.
 *
 * Every mutation goes through [mutated], which refuses a pattern the fixture does not contain: a case
 * that silently changed nothing would be green for lack of input, not for correctness (the lesson the
 * `check-doc-routes-selftest` incident recorded in `ki_processing_failures.log`).
 */
class TripSendGateScannerTest {

    private val decisionLine = "if (!tripSendAllowed(navState.isNavigating, hostGate.started)) return@collect"
    private val gateLine = "if (!hostGate.allowHostMutation()) return@collect"

    private fun scan(source: String): List<TripSendGateFinding> =
        TripSendGateScanner.scan("src/main/auto/NavigationSession.kt", source)

    /** The shipped shape: the sibling collector asks the gate, the trip collector does not. */
    private val shippedShape = """
        private fun startObserving() {
            observeJob = scope.launch(CoroutineName("navigation-state")) {
                navigationViewModel.state
                    .map { it.isNavigating }
                    .collect { isNavigating ->
                        if (!hostGate.allowHostMutation()) {
                            return@collect
                        }
                        if (isNavigating) {
                            navigationManagerController().onNavigationStarted()
                        }
                    }
            }
            tripJob = scope.launch(CoroutineName("trip")) {
                navigationViewModel.state.collect { navState ->
                    if (!tripSendAllowed(navState.isNavigating, hostGate.started)) return@collect
                    navigationManagerController().publishTrip(navState) { state ->
                        carTripFor(state, carContext.stringResolver())
                    }
                }
            }
        }
    """.trimIndent()

    private fun mutated(vararg replacements: Pair<String, String>): String {
        var source = shippedShape
        replacements.forEach { (from, to) ->
            assertTrue("the fixture must contain '$from'", source.contains(from))
            source = source.replace(from, to)
        }
        return source
    }

    @Test
    fun acceptsTheShippedShape() {
        assertEquals(0, scan(shippedShape).size)
    }

    @Test
    fun refusesTheGateQuestionOnTheTripCollector() {
        // The wrong fix the rule exists for: the collector keeps its seam call *and* asks the gate.
        val findings = scan(mutated(decisionLine to "$gateLine\n$decisionLine"))

        assertEquals(1, findings.size)
        assertEquals("allowHostMutation(", findings.single().call)
        assertTrue(
            "the reason names the bound: ${findings.single().reason}",
            findings.single().reason.contains("bounded by the navigation period")
        )
        assertEquals("src/main/auto/NavigationSession.kt", findings.single().path)
    }

    @Test
    fun refusesACollectorThatDropsTheDecision() {
        val findings = scan(mutated(decisionLine to "navigationManagerController().publishTrip(navState)"))

        assertEquals(1, findings.size)
        assertTrue(
            "the reason names the seam: ${findings.single().reason}",
            findings.single().reason.contains("tripSendAllowed")
        )
    }

    @Test
    fun reportsBothProblemsTogether() {
        // The gate question replaces the seam call: the collector asks the wrong question and no longer
        // takes a named decision — two findings, not one.
        val findings = scan(mutated(decisionLine to gateLine))

        assertEquals(2, findings.size)
        assertTrue(findings.any { it.call == "allowHostMutation(" })
        assertTrue(findings.any { it.call.contains("trip") && it.reason.contains("tripSendAllowed") })
    }

    @Test
    fun ignoresAGateQuestionMentionedInAComment() {
        val source = mutated(decisionLine to "// never ask hostGate.allowHostMutation() here\n$decisionLine")

        assertTrue("the comment is really in the fixture", source.contains("// never ask hostGate.allowHostMutation()"))
        assertEquals("a comment is not a call", 0, scan(source).size)
    }

    @Test
    fun isNotConfusedByABraceInAString() {
        val source = mutated(decisionLine to "val closing = \"}\"\n$decisionLine")

        assertTrue("the string is really in the fixture", source.contains("\"}\""))
        assertEquals("a brace inside a string does not close the lambda", 0, scan(source).size)
    }

    @Test
    fun ignoresAFileWithoutATripCollector() {
        val source = """
            internal class SessionHostGate {
                fun allowHostMutation(): Boolean = started
            }
        """.trimIndent()

        assertEquals("the marker selects the file", 0, scan(source).size)
    }

    @Test
    fun refusesACollectorLambdaThatIsNeverClosed() {
        val source = """
            tripJob = scope.launch(CoroutineName("trip")) {
                navigationViewModel.state.collect { navState ->
        """.trimIndent()

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertTrue(
            "a shape the scanner cannot read is a finding, not silence: ${findings.single().reason}",
            findings.single().reason.contains("never closed")
        )
    }

    @Test
    fun reportNamesTheRuleTheLocationAndTheNoAllowlistRule() {
        val findings = scan(mutated(decisionLine to gateLine))
        val report = TripSendGateScanner.report(findings)

        assertTrue("names the file and a line: $report", report.contains("NavigationSession.kt:"))
        assertTrue("names the call: $report", report.contains("allowHostMutation("))
        assertTrue("names the rule: $report", report.contains("bounded by the navigation period"))
        assertTrue("names the spec: $report", report.contains("car-host-fault-isolation"))
        assertTrue("states there is no allowlist: $report", report.contains("no allowlist"))
    }

    @Test
    fun reportMissingCollectorNamesTheMarkerAndSaysTheRuleIsUnchecked() {
        val report = TripSendGateScanner.reportMissingCollector()

        assertTrue("names the marker: $report", report.contains(TripSendGateScanner.COLLECTOR_MARKER))
        assertTrue("says the rule could not be checked: $report", report.contains("cannot be checked"))
    }
}
