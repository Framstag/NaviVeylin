package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests for the persisted map-source selection.
 *
 * Spec: map-source-selection — "Map source is a registered, persisted choice" / "Selection survives a
 * restart" / "Unusable stored selection falls back".
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStorageSourceTest {

    private lateinit var logFile: File

    /** A coordinate-shaped token — a whole-number part and 4+ decimals, as the privacy class defines it. */
    private val coordinateShape = Regex("""-?\b\d{1,3}\.\d{4,}\b""")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/map-source-${System.nanoTime()}.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(logFile)
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    @Test
    fun defaultsToBuiltInProvider() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())

        val settings = storage.load()

        assertEquals(MapSourceKind.BUILT_IN_PROVIDER.name, settings.mapSourceKind)
        assertEquals(MapSource.BuiltInProvider, settings.selectedMapSource())
        assertEquals("", settings.mapRepositoryUrl)
    }

    @Test
    fun roundTripsSelectedSourceAndUrl() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val repository = MapSource.repository("https://maps.example.org/repo")

        storage.save(AppSettings().withSelectedMapSource(repository))
        val loaded = storage.load()

        assertEquals(repository, loaded.selectedMapSource())
        assertEquals("https://maps.example.org/repo", loaded.mapRepositoryUrl)
    }

    @Test
    fun switchingBackKeepsTheEnteredUrl() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val repository = MapSource.repository("https://maps.example.org/repo")

        storage.save(AppSettings().withSelectedMapSource(repository))
        storage.update { settings ->
            settings.withSelectedMapSource(MapSource.BuiltInProvider)
        }
        val loaded = storage.load()

        assertEquals(MapSource.BuiltInProvider, loaded.selectedMapSource())
        assertEquals("the URL field remembers the last value per source", "https://maps.example.org/repo",
                     loaded.mapRepositoryUrl)
    }

    @Test
    fun unknownStoredSourceFallsBackToBuiltIn() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(AppSettings().copy(mapSourceKind = "SOMETHING_ELSE"))

        val source = storage.load().selectedMapSource()

        assertEquals(MapSource.BuiltInProvider, source)
        // The file a case installs is the process-wide sink: work another class left running logs into it
        // while this case holds it (measured 2026-10-10, `car-favorites-group-level` task 4.2, issue #115).
        DiagnosticsLog.log("OTHER", "a line another class logged into this file: lat=52.520008 lon=13.404954")
        assertTrue("the buffered line reaches the file", DiagnosticsLog.awaitDrained())
        val logged = logFile.takeIf { it.isFile }?.readText().orEmpty()
        // Only the line this case's own subject wrote decides it (issue #115): the file is the process-wide
        // sink, so a line another class logs while this case holds it is not this class's evidence.
        val own = logged.lines().filter { it.contains("map source unknown value=") }
        assertEquals("the fallback logs exactly one line about the stored value: $own", 1, own.size)
        val line = own.single()
        assertTrue("the fallback names the stored value", line.contains("SOMETHING_ELSE"))
        assertFalse("the diagnostics line carries no coordinate", coordinateShape.containsMatchIn(line))
    }

    @Test
    fun repositorySourceWithoutAUrlFallsBackToBuiltIn() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        storage.save(
            AppSettings().copy(
                mapSourceKind = MapSourceKind.REPOSITORY.name,
                mapRepositoryUrl = "   "
            )
        )

        val source = storage.load().selectedMapSource()

        assertEquals(MapSource.BuiltInProvider, source)
        assertTrue(DiagnosticsLog.awaitDrained())
        // Only the line this case's own subject wrote decides it (issue #115): the file is the
        // process-wide sink, so a line another class logs while this case holds it is not evidence.
        DiagnosticsLog.log("OTHER", "a line another class logged into this file: lat=52.520008 lon=13.404954")
        val own = DiagnosticsLog.readEntries()
            .filter { it.contains("repository source without a base URL") }
        assertEquals("the fallback logs exactly one line: $own", 1, own.size)
        assertTrue(own.single(), own.single().contains("fallback=BUILT_IN_PROVIDER"))
    }

    @Test
    fun aStoredRepositorySelectionSurvivesAReadOfTheWholeDocument() = runTest {
        val storage = SettingsStorage(ApplicationProvider.getApplicationContext())
        val repository = MapSource.repository("https://maps.example.org/repo/")

        storage.update { settings -> settings.withSelectedMapSource(repository) }
        val loaded = storage.load()

        // A trailing slash the user typed is not significant, but the URL itself survives.
        assertEquals("https://maps.example.org/repo", loaded.selectedMapSource().baseUrl)
        assertEquals("https://maps.example.org/repo", MapSource.repository(loaded.mapRepositoryUrl).baseUrl)
    }
}
