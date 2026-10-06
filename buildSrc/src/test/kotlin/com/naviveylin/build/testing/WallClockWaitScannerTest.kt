package com.naviveylin.build.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the unit-test wall-clock gate (change `speed-up-test-iteration`, spec
 * `unit-test-suite-runtime` — Wall-clock waits in test sources are refused by a build check).
 *
 * The fixtures are the real shapes the gate has to catch — a fixed sleep, a wall-clock deadline loop
 * whose header spans lines, a clock-derived deadline binding — plus the shapes it must leave alone:
 * a wall-clock read used as *data*, and a comment or string that merely mentions a sleep.
 */
class WallClockWaitScannerTest {

    private fun scan(source: String): List<WallClockWaitFinding> =
        WallClockWaitScanner.scan("src/test/Fixture.kt", source)

    @Test
    fun refusesAFixedSleep() {
        val source = """
            fun a() {
                Thread.sleep(10)
            }
        """.trimIndent()

        val findings = scan(source)

        assertEquals(1, findings.size)
        assertEquals(2, findings.single().line)
        assertEquals("fixed sleep", findings.single().reason)
        assertEquals("src/test/Fixture.kt", findings.single().path)
    }

    @Test
    fun refusesATimeUnitSleep() {
        val source = """
            fun a() {
                TimeUnit.MILLISECONDS.sleep(250)
            }
        """.trimIndent()

        assertEquals(1, scan(source).size)
        assertTrue(scan(source).single().reason == "fixed sleep")
    }

    @Test
    fun refusesAWallClockDeadlineLoop() {
        val source = """
            fun await(isReady: () -> Boolean) {
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline && !isReady()) {
                    Thread.sleep(10)
                }
            }
        """.trimIndent()

        val reasons = scan(source).map { it.reason }

        assertTrue(
            "the loop must be named: $reasons",
            reasons.any { it.contains("wall-clock deadline loop") && it.contains("currentTimeMillis") }
        )
        assertTrue("the sleep inside it must be named: $reasons", reasons.contains("fixed sleep"))
        assertTrue("the deadline binding must be named: $reasons", reasons.any { it.contains("deadline") })
    }

    @Test
    fun refusesAWhileHeaderThatSpansLines() {
        val source = """
            fun await(isReady: () -> Boolean) {
                while (
                    System.nanoTime() < expiry
                        && !isReady()
                ) {
                    Thread.sleep(5)
                }
            }
        """.trimIndent()

        val loop = scan(source).first { it.reason.contains("wall-clock deadline loop") }

        assertEquals("the finding is reported at the while, not at the clock read", 2, loop.line)
        assertTrue(loop.call.startsWith("while ("))
    }

    @Test
    fun refusesAnElapsedRealtimeLoop() {
        val source = """
            fun await() {
                while (SystemClock.elapsedRealtime() < expiry) { Thread.sleep(5) }
            }
        """.trimIndent()

        assertTrue(scan(source).any { it.reason.contains("elapsedRealtime") })
    }

    @Test
    fun refusesAClockDerivedDeadlineBinding() {
        val source = """
            fun await() {
                val expiry = System.currentTimeMillis() + 5_000
                pump(expiry)
            }
        """.trimIndent()

        val finding = scan(source).single()

        assertEquals(2, finding.line)
        assertTrue("the binding is named: ${finding.reason}", finding.reason.contains("expiry"))
    }

    @Test
    fun leavesAWallClockReadUsedAsDataAlone() {
        val source = """
            fun a() {
                val stamp = System.currentTimeMillis()
                val fix = Fix(lat = 51.0, lon = 7.0, time = System.currentTimeMillis() + 1_000)
                DiagnosticsLog.time("do work") { work() }
            }
        """.trimIndent()

        assertEquals("a wall-clock read as data is input, not a wait", 0, scan(source).size)
    }

    @Test
    fun leavesACommentMentioningASleepAlone() {
        val source = """
            fun a() {
                // the helper's use of Thread.sleep is gone now
                /* Thread.sleep(230) was here */
                work()
            }
        """.trimIndent()

        assertEquals(0, scan(source).size)
    }

    @Test
    fun leavesAStringMentioningASleepAlone() {
        val source = """
            fun a() {
                val message = "paced with Thread.sleep(10) before"
                val raw = ${"\"\"\""}Thread.sleep(1)${"\"\"\""}
                report(message, raw)
            }
        """.trimIndent()

        assertEquals(0, scan(source).size)
    }

    @Test
    fun reportsOneFindingPerWaitNotPerMatch() {
        val source = """
            fun await() {
                while (System.currentTimeMillis() < deadline && a() && b()) { Thread.sleep(1) }
            }
        """.trimIndent()

        val lines = scan(source).map { it.line to it.reason }

        assertEquals("one finding per line and reason", lines.size, lines.distinct().size)
        assertTrue(lines.any { it.second == "fixed sleep" })
        assertTrue(lines.any { it.second.contains("wall-clock deadline loop") })
    }

    @Test
    fun reportNamesTheRunsEveryWaitAndTheNoAllowlistRule() {
        val findings = scan(
            """
            fun a() {
                Thread.sleep(10)
            }
            """.trimIndent()
        )

        val report = WallClockWaitScanner.report(findings)

        assertTrue("names the file and line: $report", report.contains("src/test/Fixture.kt:2"))
        assertTrue("names the pattern: $report", report.contains("fixed sleep"))
        assertTrue("states there is no allowlist: $report", report.contains("no allowlist"))
        assertTrue("counts the work: $report", report.contains("1 wait(s) to convert"))
    }

    @Test
    fun cleanSourceHasNoFindings() {
        val source = """
            @Test
            fun aCase() = runTest(mainDispatcherRule.dispatcher) {
                val vm = viewModel(timeSource = EngineTimeSource { fakeNow })
                vm.start()
                advanceUntilIdle()
                assertEquals(expected, vm.state.value)
            }
        """.trimIndent()

        assertEquals(0, scan(source).size)
    }
}
