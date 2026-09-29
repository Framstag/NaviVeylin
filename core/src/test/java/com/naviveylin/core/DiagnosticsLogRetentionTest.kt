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
        // The same holds for a position: the coordinate rule runs in the pass, not on
        // the caller's path (spec: auto-diagnostics — Purging never blocks the caller).
        write(
            logFile,
            listOf(
                line(daysAgo(30), "OLD entry"),
                line(daysAgo(1), "LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0")
            )
        )
        DiagnosticsLog.initForTest(logFile)

        assertTrue(
            "the caller path must not prune",
            diskText(logFile).contains("OLD entry")
        )
        assertTrue(
            "the caller path must not purge a position either",
            diskText(logFile).contains("lat=51.513298135108705")
        )

        logAndDrain()

        assertFalse("the worker prunes", diskText(logFile).contains("OLD entry"))
        assertFalse("the worker purges the position", diskText(logFile).contains("lat=51.513298135108705"))
    }

    @Test
    fun aYoungCoordinateEntryIsRemovedFromTheActiveFileAndTheRotatedOne() {
        // The §88 case: written by a pre-redaction build, younger than the 7-day window,
        // so the age bound would keep it for the rest of the window.
        val identity = line(daysAgo(1), "LONGPRESS x=540 y=1500 mag=18.0 map=iceland")
        write(
            logFile,
            listOf(
                line(daysAgo(1), "LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0"),
                identity
            )
        )
        write(rotatedFile, listOf(line(daysAgo(1), "FIX lat=51.513298135108705 lon=7.474341597216892")))
        DiagnosticsLog.initForTest(logFile)

        logAndDrain()

        assertFalse(
            "a position is dropped whatever its age: ${diskText(logFile)}",
            diskText(logFile).contains("lat=51.513298135108705")
        )
        assertFalse(
            "the rotated file is purged too",
            diskText(rotatedFile).contains("lat=51.513298135108705")
        )
        assertTrue("precision-free identity stays", diskText(logFile).contains(identity))
    }

    @Test
    fun theCoordinateDropIsReportedOnceWithCountsAndWithoutThePosition() {
        write(
            logFile,
            listOf(
                line(daysAgo(1), "LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0"),
                line(daysAgo(1), "MAP render center=51.60987926464756,7.621644390462239 mag=17.0"),
                line(daysAgo(30), "OLD aged entry")
            )
        )
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()
        val reports = entries.filter { it.contains("retention: dropped") }

        assertEquals("one report line per pass: $entries", 1, reports.size)
        val report = reports.single()
        assertEquals("both positions are counted: $report", 2, coordinateCountIn(report))
        assertTrue("the aged count stays in the same line: $report", report.contains("1 entry older than 168h"))
        assertFalse("a report must not carry a position: $report", LogLineCoordinates.carriesPosition(report))
        assertFalse("the digits are gone too: $report", report.contains("51.513298135108705"))
    }

    @Test
    fun anExpiredCoordinateEntryIsCountedAsCoordinateOnly() {
        // A position that is also expired is dropped for one reason, not two: the report
        // must not inflate the aged count with it.
        write(logFile, listOf(line(daysAgo(30), "FIX lat=51.513298135108705 lon=7.474341597216892")))
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()
        val report = entries.single { it.contains("retention: dropped") }

        assertEquals("counted as coordinate-carrying: $report", 1, coordinateCountIn(report))
        assertFalse("not double-counted as aged: $report", report.contains("older than 168h"))
    }

    @Test
    fun aLogWithoutPositionsKeepsTheAgedReportWording() {
        write(logFile, listOf(line(daysAgo(30), "OLD entry")))
        DiagnosticsLog.initForTest(logFile)

        val entries = logAndDrain()
        val report = entries.single { it.contains("retention: dropped") }

        assertTrue("the existing wording stays: $report", report.contains("1 entry older than 168h"))
        assertFalse("no coordinate part when none was dropped: $report", report.contains("coordinate-carrying"))
    }

    private fun coordinateCountIn(report: String): Int =
        Regex("""(\d+) coordinate-carrying""").find(report)?.groupValues?.get(1)?.toInt() ?: 0

    @Test
    fun theReaderSeesNoPositionAfterThePass() {
        // The viewer/export path reads the file; a stale position must not reach it. The
        // disclosure that leads the shared text is composed in the app layer
        // (`diagnosticsShareText`, covered by the :app disclosure test) and is unchanged
        // by this change, so the assertion here is on the log text itself.
        val stale = line(daysAgo(1), "LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0")
        val identity = line(daysAgo(1), "LONGPRESS x=540 y=1500 mag=18.0 map=iceland")
        write(logFile, listOf(stale, identity))
        DiagnosticsLog.initForTest(logFile)

        DiagnosticsLog.log("TEST", "fresh entry")
        val entries = DiagnosticsLog.readEntries()
        val exported = DiagnosticsLog.exportText()

        assertFalse("a reader must not see the stale position: $entries", entries.any { it.contains(stale) })
        assertTrue("the identity line is readable", entries.any { it.contains(identity) })
        assertFalse("the export carries no position", exported.contains("51.513298135108705"))
        assertTrue("the export carries the identity line", exported.contains(identity))
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
