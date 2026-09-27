package com.naviveylin.navigation

import com.naviveylin.core.DiagnosticsLog
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The engine's fault-handler seam (spec: `navigation-engine` — Engine coroutine fault
 * is confined; `auto-diagnostics` — A confined fault is recorded like a fatal one):
 * what a confined fault records, what it publishes, and that the handler itself can
 * never become a second fault. The engine-level wiring is covered by
 * [NavigationEngineFaultIsolationTest].
 */
@RunWith(RobolectricTestRunner::class)
class EngineFaultHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun logFile(): File = File(tempFolder.root, DiagnosticsLog.LOG_FILE)

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    @Test
    fun aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce() {
        DiagnosticsLog.initForTest(logFile())
        val published = mutableListOf<String>()
        val handler = engineFaultHandler { published += it }

        handler.handleException(
            CoroutineName("stale-speed-ticker"),
            UnsatisfiedLinkError("mock symbol")
        )

        val entries = DiagnosticsLog.readEntries()
        assertEquals(1, entries.size)
        assertTrue("the record carries the engine fault tag", entries[0].contains(ENGINE_FAULT_TAG))
        assertTrue("the record names the faulting coroutine", entries[0].contains("stale-speed-ticker"))
        assertTrue(
            "the record names the throwable class, including a non-exception one",
            entries[0].contains("java.lang.UnsatisfiedLinkError")
        )
        assertTrue(
            "the record carries a stack frame, which is what localizes the fault",
            entries[0].contains("EngineFaultHandlerTest")
        )
        assertEquals(
            listOf("Navigation engine fault in stale-speed-ticker: java.lang.UnsatisfiedLinkError"),
            published
        )
    }

    @Test
    fun aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands() {
        DiagnosticsLog.initForTest(logFile())
        val handler = engineFaultHandler { throw IllegalStateException("publish boom") }

        // The handler is the last line of defence: it must swallow its own failure
        // instead of reaching the thread's uncaught-exception handler.
        handler.handleException(CoroutineName("ticker"), RuntimeException("outer"))

        val entries = DiagnosticsLog.readEntries()
        assertEquals("the record is written before the publish step", 1, entries.size)
        assertTrue(entries[0].contains(ENGINE_FAULT_TAG))
    }

    @Test
    fun theFaultSummaryNamesOriginAndClassWithoutEchoingTheThrowableMessage() {
        val summary = faultSummary(
            CoroutineName("road-lookup"),
            RuntimeException("lookup failed at 51.5132981,7.4743416")
        )

        assertTrue(summary.contains("road-lookup"))
        assertTrue(summary.contains("java.lang.RuntimeException"))
        assertTrue(
            "the summary carries identity, never a position (spec: auto-diagnostics)",
            !Regex("[0-9]{1,3}\\.[0-9]{4,}").containsMatchIn(summary)
        )
        assertTrue(
            "a fault without a coroutine name still names its origin",
            faultSummary(EmptyCoroutineContext, RuntimeException("x")).contains("unnamed coroutine")
        )
    }

    @Test
    fun theHandlerMirrorsToLogcatAndTheRecordReachesTheFile() {
        ShadowLog.clear()
        DiagnosticsLog.initForTest(logFile())

        engineFaultHandler { }.handleException(CoroutineName("ticker"), RuntimeException("boom"))

        // The logcat line is the synchronous, caller-side part of a record; the file
        // entry goes through the log's worker, which owns the file (spec:
        // `auto-diagnostics` — Capture without blocking work on the logging thread).
        val mirrored = ShadowLog.getLogs().filter { it.tag == "Diag/$ENGINE_FAULT_TAG" }
        assertEquals(1, mirrored.size)
        assertTrue(mirrored[0].msg.contains("ticker"))
        assertEquals(1, DiagnosticsLog.readEntries().size)
    }
}
