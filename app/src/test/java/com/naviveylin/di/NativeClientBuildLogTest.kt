package com.naviveylin.di

import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Native client build logging (spec: car-host-fault-isolation — Host interaction is
 * diagnosable): the build (stylesheet sync + dlopen + native build) must never run on the
 * car-app host thread, and a host-crash report has to name the thread that ran it.
 */
@RunWith(RobolectricTestRunner::class)
class NativeClientBuildLogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun theBuildStartEntryNamesTheBuildingThread() {
        DiagnosticsLog.initForTest(File(tempFolder.root, DiagnosticsLog.LOG_FILE))
        try {
            logNativeClientBuildStart()
            logNativeClientBuildDone(Thread.currentThread().name)

            val entries = DiagnosticsLog.readEntries()
            assertTrue(
                "the start entry names the thread",
                entries.any { it.contains("native build start thread=") }
            )
            assertTrue(
                "the done entry names the thread",
                entries.any { it.contains("native build done thread=") }
            )
        } finally {
            DiagnosticsLog.reset()
        }
    }
}
