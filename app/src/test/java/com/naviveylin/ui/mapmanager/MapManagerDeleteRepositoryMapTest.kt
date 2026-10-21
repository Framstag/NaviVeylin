package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.R
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.DatabaseMetadataParser
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.AppSettings
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Unit tests for deleting an installed map whose directory holds files the native database delete does not
 * know — the app's own `db.json` and `.source.json` and the slot's `ptroutes.dat`.
 *
 * The native delete removes the files in `MapDirectory::MandatoryFiles()`/`OptionalFiles()`/`MetadataFiles()`
 * and then needs the directory to be empty (`MapDirectory.cpp:144-174`). A repository installation always
 * holds files outside those lists, so it reports failure and leaves an invalid directory behind — the
 * defect of issue #119, found on `emulator-5554` while applying `add-map-update-check`.
 *
 * Spec: map-download-infrastructure — "Deleting an installation removes the whole directory".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerDeleteRepositoryMapTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val provider = MapProvider("test", "http://127.0.0.1:1", "http://127.0.0.1:1/list.php")
    private val directoryName = "europe-germany-bremen"

    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage
    private lateinit var downloadManager: FakeMapDownloadManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        storageManager = MapStorageManager(context)
        storageManager.mapsRootDir.toFile().deleteRecursively()
        settings = SettingsStorage(context)
    }

    @Test
    fun deletingARepositoryInstallationRemovesItsDirectory() = runTest(mainDispatcherRule.dispatcher) {
        val directory = installRepositoryMapWithLeftovers()
        val viewModel = viewModel()

        viewModel.deleteMap(directoryName)
        advanceUntilIdle()

        assertFalse(
            "the directory the native delete cannot empty must be gone after the app's own removal",
            Files.exists(directory)
        )
        assertNull(
            "the delete succeeded, so the map manager shows no failure banner",
            viewModel.uiState.value.error
        )
    }

    @Test
    fun theDeleteIsRepeatedSoTheMapManagerDropsTheRegistration() = runTest(mainDispatcherRule.dispatcher) {
        val directory = installRepositoryMapWithLeftovers()
        val viewModel = viewModel()

        viewModel.deleteMap(directoryName)
        advanceUntilIdle()

        assertEquals(
            "the native delete is asked twice: once by the action, once after the sweep",
            listOf(directory.toString(), directory.toString()),
            downloadManager.deleted
        )
        assertFalse(
            "and the map no longer appears in the installed list",
            directory.toString() in viewModel.uiState.value.installedMapPaths
        )
    }

    /**
     * A refused delete of a directory outside the maps root is not the app's to sweep, so it stays a failure.
     *
     * The path comes through the public action: a map name with a parent segment resolves outside the maps
     * root (`MapStorageManager.targetDirForMap`), which is one way a path that is not an installation's
     * directory can reach the delete — the source switch's `unregister` lambda is the other, and it passes a
     * stored path.
     */
    @Test
    fun aDirectoryOutsideTheMapsRootIsNotSwept() = runTest(mainDispatcherRule.dispatcher) {
        installRepositoryMapWithLeftovers()
        val outside = storageManager.mapsRootDir.parent.resolve("outside-the-maps-root")
        Files.createDirectories(outside)
        Files.write(outside.resolve("keep.txt"), ByteArray(4))
        val viewModel = viewModel()
        val name = "../${outside.fileName}"

        viewModel.deleteMap(name)
        advanceUntilIdle()

        assertTrue(
            "a directory outside the maps root must not be swept",
            Files.exists(outside.resolve("keep.txt"))
        )
        assertEquals(
            "the native delete is asked once: a refusal outside the maps root is not retried",
            listOf(storageManager.targetDirPath(name)),
            downloadManager.deleted
        )
        assertEquals(
            "the refusal stands, so the map manager shows the failure banner",
            ApplicationProvider.getApplicationContext<Application>()
                .getString(R.string.map_delete_failed, name),
            viewModel.uiState.value.error
        )
    }

    private suspend fun viewModel(): MapManagerViewModel {
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        downloadManager = FakeMapDownloadManager(
            installedDirs = ::installedMapDirectories,
            // The native rule, mirrored: `DeleteDatabase()` succeeds only when the directory is gone.
            deleteSucceeds = { path -> !Files.exists(Paths.get(path)) }
        )
        val viewModel = mapManagerViewModelForTest(
            ApplicationProvider.getApplicationContext<Application>(),
            downloadManager,
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier()
        )
        // The constructor's own refresh (`MapManagerViewModel.kt:250`) launches on the default
        // `Dispatchers.IO` before this test can replace the dispatcher, so `advanceUntilIdle()` cannot see it
        // and its `installedMapPaths` write could land after the delete's refresh — the one flake the first
        // post-fix run showed. Joining it here makes the case observe only writes it drove itself.
        viewModel.viewModelScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        kotlinx.coroutines.runBlocking { settings.save(AppSettings()) }
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        return viewModel
    }

    /** The installed map directories as a scan of the maps root reports them. */
    private fun installedMapDirectories(): List<String> =
        storageManager.mapsRootDir.toFile()
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.absolutePath }
            ?: emptyList()

    /**
     * The state the device was left in: the map's own files, plus what the native file lists do not know —
     * the app's `db.json`, the source marker `.source.json` and the mapgen slot's `ptroutes.dat`.
     */
    private fun installRepositoryMapWithLeftovers(): Path {
        val directory = storageManager.mapsRootDir.resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve(DatabaseMetadataParser.METADATA_FILE_NAME), ByteArray(2635))
        Files.write(directory.resolve("ptroutes.dat"), ByteArray(402_761))
        MapSourceMarker.write(
            directory,
            MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27)
        )
        return directory
    }
}
