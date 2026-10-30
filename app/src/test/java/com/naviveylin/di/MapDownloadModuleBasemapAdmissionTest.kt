package com.naviveylin.di

import com.naviveylin.core.DiagnosticsLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The startup admission of the basemap (issue #23, `§99`): the client is built with a basemap lookup
 * directory only when the installed data is a format this build's reader opens, and a refusal leaves exactly
 * one diagnostics line naming both versions.
 *
 * The native `builder.build()` cannot run in a JVM test, so the seam this class reads is the decision the
 * builder's configuration is fed from.
 *
 * JNI/classloader rule: [admittedBasemapLookupDirectory] leads into the module that builds the native client,
 * so this class runs under Robolectric with the DEFAULT sandbox — no `@Config(sdk=…)`.
 */
@RunWith(RobolectricTestRunner::class)
class MapDownloadModuleBasemapAdmissionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var diagnosticsFile: File

    @Before
    fun setUp() {
        diagnosticsFile = File(temporaryFolder.root, "diagnostics/test.log")
        diagnosticsFile.parentFile?.mkdirs()
        diagnosticsFile.delete()
        DiagnosticsLog.initForTest(diagnosticsFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnosticsFile.delete()
    }

    @Test
    fun aBasemapOfThisBuildsFormatIsConfigured() {
        val root = basemapRoot(version = 27)

        assertEquals(root.absolutePath, admittedBasemapLookupDirectory(root.toPath(), expectedVersion = 27))
        assertTrue(
            "an admitted basemap says nothing in diagnostics",
            DiagnosticsLog.readEntries().isEmpty()
        )
    }

    @Test
    fun aBasemapOfAnotherFormatIsNotConfiguredAndIsReportedOnce() {
        val root = basemapRoot(version = 26)

        assertNull(
            "the client must not be built with a basemap whose data format it cannot read",
            admittedBasemapLookupDirectory(root.toPath(), expectedVersion = 27)
        )
        assertTrue("the line must reach the diagnostics file", DiagnosticsLog.readEntries().isNotEmpty())
        val entries = DiagnosticsLog.readEntries().filter { it.contains("Basemap not registered") }
        assertEquals("one line, not one per start-and-render: $entries", 1, entries.size)
        assertTrue(
            "the line names the installed version and the one this build reads: ${entries.single()}",
            entries.single().contains("data format 26") &&
                entries.single().contains("this build reads 27")
        )
    }

    @Test
    fun noBasemapDirectoryConfiguresNothing() {
        assertNull(
            admittedBasemapLookupDirectory(
                temporaryFolder.root.resolve("basemap").toPath(),
                expectedVersion = 27
            )
        )
    }

    /** A basemap directory whose `types.dat` names [version], flattened like the tar.gz install produces. */
    private fun basemapRoot(version: Int): File {
        val root = temporaryFolder.newFolder("basemap")
        File(root, "types.dat").writeBytes(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(version).array()
        )
        return root
    }
}
