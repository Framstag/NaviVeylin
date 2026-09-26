package com.naviveylin.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the age bound on the on-device diagnostics copy (spec:
 * auto-diagnostics — Log storage is bounded in size **and** age): entries older
 * than the window are removed from the active and the rotated file by the worker,
 * a line that cannot be aged is removed too, a young log is left alone, and the
 * drop is reported exactly once so a reader can tell a pruned log from a complete
 * one.
 *
 * The write path's "the caller never touches the filesystem" contract is covered by
 * [DiagnosticsLogWritePathTest]; the extra assertion here is that the *caller* path
 * performs no retention work either (nothing is pruned until the worker starts).
 * Default Robolectric sandbox, no @Config (per the AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogRetentionTest {

    private lateinit var dir: File
    private lateinit var logFile: File
    private lateinit var rotatedFile: File

    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.filesDir, "diagnostics/retention-${System.nanoTime()}").apply { mkdirs() }
        logFile = File(dir, DiagnosticsLog.LOG_FILE)
        rotatedFile = File(dir, DiagnosticsLog.ROTATED_FILE)
        DiagnosticsLog.reset()
        DiagnosticsLog.retentionMs = DiagnosticsLog.RETENTION_MS
    }

    @After
    fun tearDown() {
        DiagnosticsLog.retentionMs = DiagnosticsLog.RETENTION_MS
        DiagnosticsLog.reset()
        dir.deleteRecursively()
    }

    private fun line(at: Long, text: String): String =
        "[${stampFormat.format(Date(at))}] $text"

    private fun daysAgo(days: Long): Long = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L

    private fun write(target: File, lines: List<String>) {
        target.parentFile?.mkdirs()
        target.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun diskText(target: File): String = if (target.exists()) target.readText() else ""

    /** Log one line and wait for the worker (a read waits for the drain). */
    private fun logAndDrain(): List<String> {
        DiagnosticsLog.log("TEST", "fresh entry")
        return DiagnosticsLog.readEntries()
    }

    @Test
    fun expiredEntriesAreRemovedFromTheActiveFileAndTheRotatedOne() {
        write(
            logFile,
            listOf(
                line(daysAgo(30), "OLD active entry"),
                line(daysAgo(8), "OLD active entry 2"),
                line(daysAgo(1), "young active entry")
            )
        )
        write(rotatedFile, listOf(line(daysAgo(20), "OLD rotated entry")))
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()

        assertFalse("expired entries must be gone", diskText(logFile).contains("OLD active entry"))
        assertFalse("the rotated file is pruned too", diskText(rotatedFile).contains("OLD rotated entry"))
        assertTrue("entries inside the window stay", diskText(logFile).contains("young active entry"))
        assertTrue("the fresh entry landed", entries.any { it.contains("fresh entry") })
    }

    @Test
    fun theDropIsReportedOnce() {
        write(logFile, listOf(line(daysAgo(30), "OLD"), line(daysAgo(29), "OLD 2")))
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()

        assertEquals(
            "one report line per retention pass: $entries",
            1,
            entries.count { it.contains("retention: dropped") }
        )
    }

    @Test
    fun aYoungLogIsLeftIntact() {
        val young = listOf(line(daysAgo(1), "young one"), line(daysAgo(2), "young two"))
        write(logFile, young)
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()

        young.forEach { assertTrue("a young entry must not be touched: $it", diskText(logFile).contains(it)) }
        assertTrue(
            "nothing was dropped, so nothing is reported",
            entries.none { it.contains("retention: dropped") }
        )
    }

    @Test
    fun aLineThatCannotBeAgedIsRemoved() {
        write(
            logFile,
            listOf(
                "no timestamp at all",
                "[not a date] also unparseable",
                line(daysAgo(1), "young entry")
            )
        )
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()

        assertFalse(
            "a line that cannot be aged must not defeat the bound",
            diskText(logFile).contains("no timestamp at all")
        )
        assertFalse(diskText(logFile).contains("also unparseable"))
        assertTrue(diskText(logFile).contains("young entry"))
        assertEquals(1, entries.count { it.contains("retention: dropped") })
    }

    @Test
    fun theCallerPathPerformsNoRetentionWork() {
        // Configure only: no entry has been logged, so no worker exists yet — an
        // expired entry must still be on disk, because pruning is the worker's job.
        write(logFile, listOf(line(daysAgo(30), "OLD entry")))
        DiagnosticsLog.initForTest(logFile)

        assertTrue(
            "the caller path must not prune",
            diskText(logFile).contains("OLD entry")
        )

        logAndDrain()

        assertFalse("the worker prunes", diskText(logFile).contains("OLD entry"))
    }

    @Test
    fun theCrashLineIsWrittenSynchronouslyAndSurvivesThePrune() {
        // Crash capture must not depend on the worker (spec: auto-diagnostics — Crash
        // capture does not depend on the logging worker), and its line is inside the
        // retention window — so the prune must keep it while dropping the expired ones.
        write(logFile, listOf(line(daysAgo(30), "OLD entry")))
        DiagnosticsLog.initForTest(logFile)
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> /* swallow for the test */ }
        try {
            DiagnosticsLog.installCrashHandler()

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertTrue(
                "the crash line is on disk before any flush: ${diskText(logFile)}",
                diskText(logFile).contains("CRASH") && diskText(logFile).contains("boom")
            )

            logAndDrain()

            assertTrue(
                "a fresh crash line is inside the window and must survive: ${diskText(logFile)}",
                diskText(logFile).contains("boom")
            )
            assertFalse(
                "the expired entry is still dropped",
                diskText(logFile).contains("OLD entry")
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun anEmptyResultLeavesNoFileBehind() {
        write(logFile, listOf(line(daysAgo(30), "OLD")))
        DiagnosticsLog.initForTest(logFile)

        // The prune empties the file; the fresh entry then recreates it.
        logAndDrain()

        assertTrue("the log file is usable after the prune", diskText(logFile).contains("fresh entry"))
    }
}
