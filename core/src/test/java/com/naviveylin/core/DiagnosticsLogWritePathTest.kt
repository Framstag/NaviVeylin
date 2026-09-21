package com.naviveylin.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the non-blocking write path of [DiagnosticsLog] (spec:
 * auto-diagnostics — Logging never blocks the caller, The in-memory diagnostic
 * buffer is bounded, Buffered entries reach the file within a bounded delay,
 * Crash capture does not depend on the logging worker, Reading diagnostics does
 * not block the UI).
 *
 * The assertions are deliberately about *observable* behaviour: whether the file
 * on disk contains a line at a given moment (caller-thread IO would put it there
 * immediately), and how long a line takes to appear without an explicit flush
 * request (the flush deadline / high-water mark). Default Robolectric sandbox, no
 * @Config (per AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogWritePathTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/write-path-${System.nanoTime()}.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        // reset() retires the previous test's worker, so "no worker before the
        // first entry" is observable in every test of this class.
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.flushIntervalMs = DiagnosticsLog.FLUSH_INTERVAL_MS
        DiagnosticsLog.maxPendingEntries = DiagnosticsLog.MAX_PENDING_ENTRIES
        DiagnosticsLog.maxPendingChars = DiagnosticsLog.MAX_PENDING_CHARS
        DiagnosticsLog.reset()
        logFile.delete()
    }

    private fun disk(): String = if (logFile.exists()) logFile.readText() else ""

    private fun poll(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun workerIsCreatedLazilyAndIsADaemon() {
        assertNull("no worker before the first entry", DiagnosticsLog.workerThreadOrNull())

        DiagnosticsLog.log("TEST", "first-entry")

        val worker = DiagnosticsLog.workerThreadOrNull()
        assertNotNull("the first entry starts the worker", worker)
        assertTrue("the worker must not keep a JVM alive", worker!!.isDaemon)
    }

    @Test
    fun loggingDoesNotWriteOnTheCallingThread() {
        // Park the worker far in the future AND prove it is idle: warm up with one
        // entry and flush it. With the worker provably waiting, whatever reaches the
        // file after the next log call could only have come from the calling thread.
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))
        val before = disk()

        DiagnosticsLog.log("TEST", "caller-thread-line")

        assertEquals("the logging call must not touch the file", before, disk())
        assertFalse("the entry is still buffered", DiagnosticsLog.awaitDrained(0))

        assertTrue(DiagnosticsLog.flushNow(2_000))
        assertTrue("the entry reaches the file once the worker flushes", disk().contains("caller-thread-line"))
    }

    @Test
    fun entriesReachTheFileWithinTheFlushDeadline() {
        DiagnosticsLog.flushIntervalMs = 40

        DiagnosticsLog.log("TEST", "deadline-line")

        // No explicit flush request: only the worker's deadline can deliver this.
        assertTrue(
            "the flush deadline delivers the entry",
            poll { disk().contains("deadline-line") }
        )
    }

    @Test
    fun highWaterMarkFlushesWithoutWaitingForTheDeadline() {
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.maxPendingChars = 2_000

        repeat(20) { DiagnosticsLog.log("TEST", "burst-$it " + "x".repeat(80)) }

        // Only the entries present when the ring crossed the high-water mark are
        // flushed by that signal; the rest stays buffered (the worker is parked on a
        // 60 s deadline, so the 3 s poll bound proves the burst, not the deadline,
        // triggered the flush).
        assertTrue(
            "a burst flushes at the high-water mark",
            poll { disk().contains("TEST burst-5") }
        )
    }

    @Test
    fun pendingBufferIsBoundedAndMarksTheDropOnce() {
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.maxPendingEntries = 5
        DiagnosticsLog.maxPendingChars = 100_000

        // Warm-up: start the worker and drain a first entry, so the worker is parked
        // and the burst below is bounded entirely in the ring (no early drain can
        // move the oldest entries to the file).
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))

        repeat(20) { DiagnosticsLog.log("TEST", "bound-$it") }

        assertTrue("the drops are recorded", DiagnosticsLog.droppedEntryCount() > 0)
        assertTrue(DiagnosticsLog.flushNow(2_000))

        val text = disk()
        assertEquals(
            "the drop is recorded exactly once",
            1,
            Regex("dropped older entries").findAll(text).count()
        )
        assertTrue("the newest entries survive", text.contains("TEST bound-19"))
        assertFalse("the oldest entries were dropped", text.contains("TEST bound-0\n"))
        assertEquals(
            "the ring held exactly its capacity",
            5,
            text.lines().count { it.contains("TEST bound-") }
        )
    }

    @Test
    fun crashTraceIsWrittenWithoutWaitingForTheWorker() {
        DiagnosticsLog.flushIntervalMs = 60_000
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var chained = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained = true }
        try {
            DiagnosticsLog.installCrashHandler()
            val thread = Thread { throw IllegalStateException("direct-crash") }
            thread.start()
            thread.join()

            // No flush request, and the worker is parked: a buffered write could
            // not have reached the file.
            assertTrue("the crash trace is on disk immediately", disk().contains("direct-crash"))
            assertTrue("the previous handler still runs", chained)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun uninitialisedLogIsANoOp() {
        DiagnosticsLog.reset()

        DiagnosticsLog.log("TEST", "nowhere")
        DiagnosticsLog.logThrowable("TEST", "boom", IllegalStateException("x"))

        assertNull("no file means no worker", DiagnosticsLog.workerThreadOrNull())
        assertTrue(DiagnosticsLog.readEntries().isEmpty())
        assertEquals(0L, DiagnosticsLog.droppedEntryCount())
        assertFalse("nothing was created", logFile.exists())
    }

    @Test
    fun backgroundReadsMatchTheSynchronousOnes() {
        DiagnosticsLog.log("TEST", "async-line")

        val sync = DiagnosticsLog.readEntries()
        val async = runBlocking { DiagnosticsLog.readEntriesAsync() }
        assertEquals(sync, async)
        assertTrue(async.any { it.contains("async-line") })

        assertEquals(DiagnosticsLog.exportText(), runBlocking { DiagnosticsLog.exportTextAsync() })
    }
}
