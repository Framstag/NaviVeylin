package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.AppSettings
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapSourceSwitcher
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

/**
 * Unit tests for the switch plan behind the confirmation dialog.
 *
 * Specs: map-source-selection — "Switching source is gated by a confirmation naming what will be
 * deleted" / "Switch with nothing to delete is not gated by a dialog"; map-download-ui — "Switching
 * source asks before deleting".
 *
 * What the confirmation *deletes* is asserted in `MapSourceSwitchTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerSourceSwitchPlanTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage
    private lateinit var downloadManager: FakeMapDownloadManager
    private val provider = MapProvider("karry.cz", "https://osmscout.karry.cz", "")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        storageManager = MapStorageManager(context)
        settings = SettingsStorage(context)
    }

    @Test
    fun aSwitchThatDeletesTheBasemapUnloadsItFromTheRunningClient() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        markBasemap(MapSourceKind.BUILT_IN_PROVIDER, bytes = 2048)
        val client = FakeOSMScoutClient()
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider, client = client)

        viewModel.selectSource(repository)
        advanceUntilIdle()
        viewModel.confirmSourceSwitch()
        advanceUntilIdle()

        assertFalse("the deleted basemap directory is gone", Files.exists(storageManager.mapsRootDir.resolve("basemap")))
        assertEquals(
            "the session must stop using the deleted basemap",
            "",
            client.basemapLookupDirectories.last()
        )
        assertTrue("and it must reload", client.reloadBasemapCount > 0)
    }

    @Test
    fun aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        markBasemap(MapSourceKind.REPOSITORY, bytes = 2048, baseUrl = repository.baseUrl)
        val client = FakeOSMScoutClient()
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider, client = client)

        viewModel.selectSource(repository)
        advanceUntilIdle()
        viewModel.confirmSourceSwitch()
        advanceUntilIdle()

        assertEquals(
            "the target source's own basemap stays registered",
            storageManager.mapsRootDir.resolve("basemap").toString(),
            client.basemapLookupDirectories.last()
        )
    }

    @Test
    fun selectingTheRepositoryWithoutAUrlRevealsTheFieldInsteadOfSwitching() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)

        // The chip the UI shows before any URL was entered: a repository source with a blank URL.
        viewModel.selectSource(MapSource(MapSourceKind.REPOSITORY, ""))
        advanceUntilIdle()

        assertTrue("the URL field is revealed so a URL can be entered", viewModel.uiState.value.repositoryUrlRevealed)
        assertNull("nothing is deleted and no dialog opens", viewModel.uiState.value.pendingSourceSwitch)
        assertEquals(
            "and the selection is not persisted, which the empty-URL fallback would revert on the next start",
            MapSourceKind.BUILT_IN_PROVIDER.name,
            settings.load().mapSourceKind
        )
    }

    @Test
    fun selectSourceUsesTheUrlThatWasTypedAfterTheSelectorWasBuilt() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)
        // The chip carries the URL the selector knew when it was built; the user typed a newer one.
        viewModel.updateRepositoryUrlDraft(repository.baseUrl)
        advanceUntilIdle()

        viewModel.selectSource(MapSource(MapSourceKind.REPOSITORY, ""))
        advanceUntilIdle()

        assertEquals(repository, viewModel.uiState.value.pendingSourceTarget)
    }

    @Test
    fun dialogNamesCountAndSize() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 2048)
        markMap("iceland", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        markMap("europe-germany-berlin", MapSourceKind.REPOSITORY, bytes = 4096, baseUrl = repository.baseUrl)
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)

        viewModel.selectSource(repository)
        advanceUntilIdle()

        val plan = viewModel.uiState.value.pendingSourceSwitch
        assertTrue("the dialog is shown with the plan", plan != null)
        assertEquals("only the other source's maps are counted", 2, plan!!.mapCount)
        assertEquals(
            "the size named is the total of the directories that will be deleted, marker files included",
            sizeOf("berlin") + sizeOf("iceland"),
            plan.mapBytes
        )
        assertNull("no basemap is installed", plan.basemapDirectory)
        assertEquals(plan.mapBytes, plan.totalBytes)
        assertEquals(repository, viewModel.uiState.value.pendingSourceTarget)
    }

    @Test
    fun theBasemapIsCountedWhenItBelongsToTheOtherSource() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        markBasemap(MapSourceKind.BUILT_IN_PROVIDER, bytes = 2048)
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)

        viewModel.selectSource(repository)
        advanceUntilIdle()

        val plan = viewModel.uiState.value.pendingSourceSwitch!!
        assertEquals(1, plan.mapCount)
        assertEquals(sizeOf("basemap"), plan.basemapBytes)
        assertEquals(sizeOf("berlin") + sizeOf("basemap"), plan.totalBytes)
    }

    @Test
    fun aBasemapOfTheTargetSourceIsNotDeleted() = runTest(mainDispatcherRule.dispatcher) {
        markBasemap(MapSourceKind.REPOSITORY, bytes = 2048, baseUrl = repository.baseUrl)
        val viewModel = viewModel(activeSource = repository)

        val plan = MapSourceSwitcher(storageManager.mapsRootDir).plan(repository)

        assertNull("a basemap the target source installed stays", plan.basemapDirectory)
        assertTrue(plan.isEmpty)
        assertEquals(0, viewModel.uiState.value.installedSources.size)
    }

    @Test
    fun noDialogWhenNothingWouldBeDeleted() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)

        viewModel.selectSource(repository)
        advanceUntilIdle()

        assertNull("nothing to delete, so no confirmation", viewModel.uiState.value.pendingSourceSwitch)
        assertEquals("the switch is applied directly", repository.kind, viewModel.uiState.value.activeSource.kind)
        assertEquals(
            "and it is persisted",
            MapSourceKind.REPOSITORY.name,
            settings.load().mapSourceKind
        )
    }

    @Test
    fun cancelDropsThePlanAndKeepsTheActiveSource() = runTest(mainDispatcherRule.dispatcher) {
        markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, bytes = 1024)
        val viewModel = viewModel(activeSource = MapSource.BuiltInProvider)

        viewModel.selectSource(repository)
        advanceUntilIdle()
        viewModel.cancelSourceSwitch()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingSourceSwitch)
        assertEquals(MapSource.BuiltInProvider, viewModel.uiState.value.activeSource)
        assertTrue("nothing was deleted", Files.isDirectory(storageManager.mapsRootDir.resolve("berlin")))
    }

    private val repository = MapSource(MapSourceKind.REPOSITORY, "https://maps.example.org/repo")

    private fun viewModel(
        activeSource: MapSource,
        client: FakeOSMScoutClient = FakeOSMScoutClient()
    ): MapManagerViewModel {
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        downloadManager = FakeMapDownloadManager(installedDirs = { emptyList() })
        val viewModel = MapManagerViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            downloadManager,
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier(),
            com.naviveylin.data.BasemapRegistrar(client, BasemapReloadNotifier())
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        kotlinx.coroutines.runBlocking { settings.save(AppSettings().withSelectedMapSource(activeSource)) }
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel.refreshSources()
        return viewModel
    }

    /** The bytes stored in [name]'s directory, as the plan must count them. */
    private fun sizeOf(name: String): Long =
        Files.walk(storageManager.mapsRootDir.resolve(name))
            .filter { Files.isRegularFile(it) }
            .mapToLong { Files.size(it) }
            .sum()

    private fun markMap(
        name: String,
        kind: MapSourceKind,
        bytes: Int,
        baseUrl: String? = null
    ): Path {
        val directory = storageManager.mapsRootDir.resolve(name)
        Files.createDirectories(directory)
        Files.write(directory.resolve("map.lib"), ByteArray(bytes))
        MapSourceMarker.write(directory, MapSourceRecord(kind, baseUrl, 27))
        return directory
    }

    private fun markBasemap(kind: MapSourceKind, bytes: Int, baseUrl: String? = null): Path {
        val directory = storageManager.mapsRootDir.resolve("basemap")
        Files.createDirectories(directory)
        Files.write(directory.resolve("types.dat"), ByteArray(bytes))
        MapSourceMarker.write(directory, MapSourceRecord(kind, baseUrl, 27))
        return directory
    }
}
