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

        // No explicit flush request: only the worker's deadline can deliver this. The lines are buffered
        // and written by the logging worker, so ask the log to drain instead of polling the file on the
        // wall clock (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
        assertTrue("the flush deadline delivers the entry", DiagnosticsLog.awaitDrained())
        assertTrue("the deadline line must be on disk: ${disk()}", disk().contains("deadline-line"))
    }

    @Test
    fun highWaterMarkFlushesWithoutWaitingForTheDeadline() {
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.maxPendingChars = 2_000

        // Park the worker before the burst. With the ring empty and the worker waiting, the burst can be
        // split: the high-water signal can reach the worker while it is already awake, and the entries that
        // arrive after the drain it triggers are that drain's tail.
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))
        assertTrue("the worker must be parked before the burst", DiagnosticsLog.awaitParked(2_000))

        val batchesBefore = DiagnosticsLog.flushedBatchCount()
        repeat(20) { DiagnosticsLog.log("TEST", "burst-$it " + "x".repeat(80)) }

        // Only the entries present when the ring crossed the high-water mark are flushed by that signal; the
        // rest may still be buffered and can wait for the deadline, so the state to await is the *flush* the
        // crossing caused — not the worker being parked (it is not parked while its tail is pending, spec
        // `unit-test-suite-runtime` — "A published work state is true when it is read") and not an empty ring
        // (also true before the crossing is taken up). The bounded 2 s wait against the 60 s deadline makes
        // the flush that arrives the high-water signal's work, not the deadline's.
        assertTrue(
            "a burst is flushed without waiting for the deadline",
            DiagnosticsLog.awaitFlushedBatchAbove(batchesBefore, 2_000)
        )
        assertTrue(
            "the line at the high-water crossing must be on disk: ${disk()}",
            disk().contains("TEST burst-5")
        )

        // Drain the tail the crossing left behind, so no case leaves the worker on the 60 s deadline.
        assertTrue(DiagnosticsLog.flushNow(2_000))
    }

    @Test
    fun workPendingIsNotAParkedWorker() {
        DiagnosticsLog.flushIntervalMs = 60_000

        // Park the worker, then hand it work that does NOT cross the high-water mark: nothing signals it, so
        // it stays blocked on the deadline and only the published state can say anything. On HEAD that state
        // was still the "waiting" the worker had declared before this entry arrived, so a case asking now was
        // told "parked" about a worker that had already been given the entry — and a case that then read the
        // file read the pre-flush file (TODO §148 case 5, issue #93: red in 3 of 24 loaded aggregate runs and
        // twice in `main`'s CI). The state a caller awaits has to be true of the moment it reads it (spec
        // `unit-test-suite-runtime` — A published work state is true when it is read).
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))
        assertTrue("the worker must be parked before the check", DiagnosticsLog.awaitParked(2_000))

        DiagnosticsLog.log("TEST", "queued-below-the-high-water-mark")
        assertFalse(
            "the entry the worker has been handed is not a parked worker",
            DiagnosticsLog.awaitParked(0)
        )

        // Drain it again: the worker is blocked on the 60 s deadline, and no case may leave it waiting.
        assertTrue(DiagnosticsLog.flushNow(2_000))
    }

    @Test
    fun theParkedAwaitReportsTheWorkersWaitingState() {
        // The state `pendingBufferIsBoundedAndMarksTheDropOnce` assumes: the worker being *parked*, not
        // merely the ring being empty (spec `unit-test-suite-runtime` — A case awaits the state it needs,
        // not a proxy of it).
        DiagnosticsLog.flushIntervalMs = 60_000

        DiagnosticsLog.log("TEST", "park-warm-up")
        assertTrue("the warm-up entry drains", DiagnosticsLog.flushNow(2_000))
        assertTrue(
            "the worker publishes that it is waiting",
            DiagnosticsLog.awaitParked(2_000)
        )

        // Without a worker nothing can park, and the await reports that instead of blocking.
        DiagnosticsLog.reset()
        assertFalse("no worker means no parked state", DiagnosticsLog.awaitParked(200))
    }

    @Test
    fun pendingBufferIsBoundedAndMarksTheDropOnce() {
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.maxPendingEntries = 5
        DiagnosticsLog.maxPendingChars = 100_000

        // Warm-up: start the worker, drain a first entry, and wait until it is *parked* — not merely
        // until the ring is empty. `flushNow`/`awaitDrained` return as soon as `pending` is empty and
        // nothing is being flushed, a state the worker reaches before it waits again; a burst landing in
        // that window is drained mid-burst, and the file then holds k + 5 burst lines instead of 5
        // (TODO.md §148 case 2). Parked, the burst below is bounded entirely in the ring.
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))
        assertTrue("the worker must be parked before the burst", DiagnosticsLog.awaitParked(2_000))

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
    fun aMissedDrainIsRecordedAndRetriedOnce() {
        // The worker is parked on a 60 s deadline, so the read's own bounded wait expires with the entry
        // still buffered — the state a loaded device reaches when the worker is late (issue #701).
        DiagnosticsLog.flushIntervalMs = 60_000
        DiagnosticsLog.log("TEST", "warm-up")
        assertTrue(DiagnosticsLog.flushNow(2_000))
        assertTrue("the worker must be parked before the read", DiagnosticsLog.awaitParked(2_000))

        DiagnosticsLog.log("TEST", "late-line")

        val entries = DiagnosticsLog.readEntries()

        assertTrue("the missed drain is recorded: $entries", entries.any { it.contains("read drain expired") })
        assertTrue("the retry delivers the buffered entry: $entries", entries.any { it.contains("late-line") })
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
