package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.MapStorageManager
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the installed-map source label.
 *
 * Spec: map-download-ui — "Installed maps name their source" / "Repository map is marked in the
 * installed list" / "Pre-existing installation is marked as the built-in provider" / "Row survives a
 * source switch that kept it".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerSourceLabelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var storageManager: MapStorageManager
    private lateinit var downloadManager: FakeMapDownloadManager

    @Before
    fun setUp() {
        storageManager = MapStorageManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun repositoryInstallIsLabelled() = runTest(mainDispatcherRule.dispatcher) {
        val directory = mapDirectory("berlin")
        MapSourceMarker.write(
            directory,
            MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27)
        )
        val viewModel = viewModel()

        assertEquals("https://maps.example.org/repo", viewModel.installedMapSourceName("berlin"))
        assertEquals(
            MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27),
            viewModel.installedMapSource("berlin")
        )
    }

    @Test
    fun unmarkedInstallationIsLabelledAsBuiltIn() = runTest(mainDispatcherRule.dispatcher) {
        mapDirectory("czech-republic")
        val viewModel = viewModel()

        assertEquals("karry.cz", viewModel.installedMapSourceName("Czech Republic"))
        assertEquals(MapSourceRecord.BuiltInProvider, viewModel.installedMapSource("Czech Republic"))
    }

    @Test
    fun anUnreadableMarkerIsLabelledAsBuiltIn() = runTest(mainDispatcherRule.dispatcher) {
        val directory = mapDirectory("berlin")
        Files.write(directory.resolve(MapSourceMarker.FILE_NAME), "{not json".toByteArray())
        val viewModel = viewModel()

        assertEquals("karry.cz", viewModel.installedMapSourceName("berlin"))
    }

    @Test
    fun installedStateCarriesTheSourceOfEveryDirectory() = runTest(mainDispatcherRule.dispatcher) {
        mapDirectory("berlin")
        MapSourceMarker.write(mapDirectory("iceland"), MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27))
        val viewModel = viewModel()

        viewModel.refreshInstalledMaps()
        advanceUntilIdle()

        val sources = viewModel.uiState.value.installedSources
        assertEquals(2, sources.size)
        assertEquals(
            MapSourceKind.BUILT_IN_PROVIDER,
            sources.entries.first { it.key.endsWith("berlin") }.value.sourceKind
        )
        assertEquals(
            MapSourceKind.REPOSITORY,
            sources.entries.first { it.key.endsWith("iceland") }.value.sourceKind
        )
    }

    @Test
    fun keptRowKeepsItsLabelAndDeleteAction() = runTest(mainDispatcherRule.dispatcher) {
        // A repository map that a switch to the repository source keeps: its label must survive, and
        // its directory is still an installed directory the row offers [Delete] for.
        val directory = mapDirectory("berlin")
        MapSourceMarker.write(
            directory,
            MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27)
        )
        val viewModel = viewModel()

        viewModel.refreshInstalledMaps()
        advanceUntilIdle()

        assertEquals("https://maps.example.org/repo", viewModel.installedMapSourceName("berlin"))
        assertEquals(
            "the kept directory stays installed",
            directory.toString(),
            viewModel.uiState.value.installedMapPaths.first { it.endsWith("berlin") }
        )
        assertEquals(directory.toString(), viewModel.getMapPath("berlin"))
    }

    private fun viewModel(): MapManagerViewModel {
        downloadManager = FakeMapDownloadManager(installedDirs = { installedDirectories() })
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storage = com.naviveylin.data.SettingsStorage(context)
        val provider = MapProvider("karry.cz", "https://osmscout.karry.cz", "")
        val viewModel = MapManagerViewModel(
            context,
            downloadManager,
            storageManager,
            provider,
            storage,
            com.naviveylin.data.MapSourceRegistry(storage, provider, com.naviveylin.data.HttpUrlFetcher()),
            com.naviveylin.core.BasemapReloadNotifier(),
            com.naviveylin.data.BasemapRegistrar(
                com.framstag.libosmscout.client.FakeOSMScoutClient(),
                com.naviveylin.core.BasemapReloadNotifier()
            )
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        return viewModel
    }

    /** The map directories that exist right now, as the native map manager would report them. */
    private fun installedDirectories(): List<String> =
        storageManager.mapsRootDir.toFile().listFiles().orEmpty().map { it.absolutePath }

    private fun mapDirectory(name: String): Path {
        val directory = storageManager.mapsRootDir.resolve(name)
        Files.createDirectories(directory)
        return directory
    }
}
