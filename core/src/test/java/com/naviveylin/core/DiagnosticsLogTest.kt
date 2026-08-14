package com.naviveylin.core

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [DiagnosticsLog] — append/read, rotation, timing, export cap,
 * share intent, and the crash handler. Default Robolectric sandbox, no
 * @Config (per AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogTest {

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/test.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        File(logFile.parentFile, DiagnosticsLog.ROTATED_FILE).delete()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
        File(logFile.parentFile, DiagnosticsLog.ROTATED_FILE).delete()
    }

    @Test
    fun appendAndRead() {
        DiagnosticsLog.log("TEST", "hello")
        DiagnosticsLog.logThrowable("TEST", "boom", IllegalStateException("nope"))

        val entries = DiagnosticsLog.readEntries()
        assertEquals(2, entries.size)
        assertTrue(entries[0].contains("TEST hello"))
        assertTrue(entries[1].contains("IllegalStateException: nope"))
    }

    @Test
    fun rotationKeepsNewest() {
        DiagnosticsLog.maxBytes = 200
        try {
            repeat(50) { DiagnosticsLog.log("TEST", "line $it") }
            val entries = DiagnosticsLog.readEntries()
            assertTrue("expected entries after rotation", entries.isNotEmpty())
            assertTrue(entries.last().contains("line 49"))
        } finally {
            DiagnosticsLog.maxBytes = DiagnosticsLog.MAX_BYTES
        }
    }

    @Test
    fun timeHelperLogsDuration() {
        DiagnosticsLog.time("do work") { Thread.sleep(5) }
        assertTrue(DiagnosticsLog.readEntries().last().contains("do work took"))
    }

    @Test
    fun exportTextIsCapped() {
        DiagnosticsLog.log("TEST", "x".repeat(60_000))
        val exported = DiagnosticsLog.exportText()
        assertTrue(exported.length <= DiagnosticsLog.MAX_SHARE_CHARS + 128)
    }

    @Test
    fun shareIntentCarriesLogText() {
        DiagnosticsLog.log("TEST", "share me")
        val intent = DiagnosticsLog.shareIntent()
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("share me"))
    }

    @Test
    fun crashHandlerWritesEntryAndChains() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var chained = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained = true }
        try {
            DiagnosticsLog.installCrashHandler()
            val t = Thread { throw IllegalStateException("fatal test crash") }
            t.start()
            t.join()

            assertTrue(chained)
            assertTrue(
                DiagnosticsLog.readEntries().any {
                    it.contains(DiagnosticsLog.CRASH_TAG) && it.contains("fatal test crash")
                }
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
