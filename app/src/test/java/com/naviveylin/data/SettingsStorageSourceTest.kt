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
        assertTrue("the buffered line reaches the file", DiagnosticsLog.awaitDrained())
        val logged = logFile.takeIf { it.isFile }?.readText().orEmpty()
        assertTrue("the fallback names the stored value", logged.contains("SOMETHING_ELSE"))
        assertFalse("the diagnostics line carries no coordinate", logged.contains("52."))
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
        assertTrue(logFile.takeIf { it.isFile }?.readText().orEmpty().contains("without a base URL"))
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
