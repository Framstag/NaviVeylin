package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.AppSettings
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.FakeRepositoryServer
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/**
 * Unit tests for what the map manager shows about updates: only what can be updated, everything that can, and
 * whether a check has run at all.
 *
 * Spec: map-updates — "The updates section lists what can be updated" / "The user can run the check on
 * demand" / "The check result survives a restart".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerUpdatesStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val baseUrl = "https://maps.example.org/repo"
    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")
    private val hamburg = listOf("europe", "germany", "hamburg")
    private val berlinDirectory = "europe-germany-berlin"
    private val hamburgDirectory = "europe-germany-hamburg"

    private val provider = MapProvider("test", "http://127.0.0.1:1", "http://127.0.0.1:1/list.php")
    private val server = FakeRepositoryServer(baseUrl, version)

    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        storageManager = MapStorageManager(context)
        storageManager.mapsRootDir.toFile().deleteRecursively()
        settings = SettingsStorage(context)
    }

    @Test
    fun noCheckYetIsStated() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()

        advanceUntilIdle()

        assertTrue("no check has completed on this install", viewModel.updates.value.neverChecked)
        assertTrue(viewModel.updates.value.updates.isEmpty())
    }

    @Test
    fun theCheckControlReportsItsOutcome() = runTest(mainDispatcherRule.dispatcher) {
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val viewModel = repositoryViewModel()

        val updates = checkedState(viewModel)
        assertFalse("the check completed", updates.neverChecked)
        assertTrue("and reports when it ran", updates.lastCheckedAtEpochMs > 0L)
        assertEquals(1, updates.updates.size)
        assertEquals(berlinDirectory, updates.updates.single().directoryName)
    }

    @Test
    fun onlyUpdatableItemsAreListed() = runTest(mainDispatcherRule.dispatcher) {
        installRegion(berlinDirectory)
        installRegion(hamburgDirectory)
        server.serveIndex(listOf(berlin, hamburg))
        // Berlin is newer on the server; Hamburg is exactly what is installed.
        server.serveSlot(berlin)
        server.serveSlot(hamburg, generatedAt = INSTALLED_AT)
        val viewModel = repositoryViewModel()

        checkedState(viewModel)

        assertEquals(
            listOf(berlinDirectory),
            viewModel.updates.value.updates.map { it.directoryName }
        )
    }

    @Test
    fun everyUpdatableItemIsListed() = runTest(mainDispatcherRule.dispatcher) {
        installRegion(berlinDirectory)
        installRegion(hamburgDirectory)
        server.serveIndex(listOf(berlin, hamburg))
        server.serveSlot(berlin)
        server.serveSlot(hamburg)
        val viewModel = repositoryViewModel()

        checkedState(viewModel)

        assertEquals(
            listOf(berlinDirectory, hamburgDirectory),
            viewModel.updates.value.updates.map { it.directoryName }.sorted()
        )
    }

    @Test
    fun aProviderSourceShowsNothingToUpdate() = runTest(mainDispatcherRule.dispatcher) {
        // The section itself is the screen's rule (MapUpdatesSectionTest / MapManagerScreenOrderingTest);
        // what the provider source must not do is probe anything or claim an update.
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        settings.save(AppSettings().withSelectedMapSource(MapSource.BuiltInProvider))
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        val viewModel = viewModel(updates = mapUpdates(fetcher = server))

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertTrue("no request is issued for a provider source", server.requested.isEmpty())
        assertTrue(viewModel.updates.value.updates.isEmpty())
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /**
     * Run the check and let it publish, then read the check's own state — the flow the screen shows, rather
     * than a copy the view model keeps (spec `unit-test-suite-runtime` — a case drives its work and reads the
     * state it needs).
     */
    private fun TestScope.checkedState(viewModel: MapManagerViewModel): com.naviveylin.data.MapUpdatesState {
        viewModel.checkForUpdates()
        advanceUntilIdle()
        return viewModel.updates.value
    }

    private suspend fun repositoryViewModel(): MapManagerViewModel {
        settings.save(
            AppSettings().withSelectedMapSource(MapSource(MapSourceKind.REPOSITORY, baseUrl))
        )
        return viewModel(updates = mapUpdates(fetcher = server))
    }

    private suspend fun viewModel(updates: com.naviveylin.data.MapUpdates): MapManagerViewModel {
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        val notifier = BasemapReloadNotifier()
        val viewModel = mapManagerViewModelForTest(
            application = ApplicationProvider.getApplicationContext(),
            downloadManager = FakeMapDownloadManager(installedDirs = { emptyList() }),
            storageManager = storageManager,
            provider = provider,
            settings = settings,
            registry = registry,
            reloadNotifier = notifier,
            basemapRegistrar = BasemapRegistrar(
                com.framstag.libosmscout.client.FakeOSMScoutClient(),
                notifier
            ),
            updates = updates
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        updates.ioDispatcher = mainDispatcherRule.dispatcher
        return viewModel
    }

    private suspend fun mapUpdates(
        fetcher: com.naviveylin.core.mapsource.RepositoryFetcher
    ): com.naviveylin.data.MapUpdates {
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        val updates = mapUpdatesForTest(settings, registry, storageManager, fetcher)
        updates.ioDispatcher = mainDispatcherRule.dispatcher
        return updates
    }

    /** Install a repository map carrying the metadata that was installed, and its source marker. */
    private fun installRegion(directoryName: String) {
        val directory = storageManager.mapsRootDir.resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("types.dat"), "types".toByteArray())
        val document =
            """{"schema":1,"typeConfigVersion":$version,"generatedAt":"$INSTALLED_AT",""" +
                """"output":{"files":{"map.lib":{"size":10,"crc32":1}}}}"""
        Files.write(directory.resolve("db.json"), document.toByteArray())
        MapSourceMarker.write(
            directory,
            MapSourceRecord(MapSourceKind.REPOSITORY, baseUrl, version)
        )
    }

    private companion object {
        /** What the fixtures installed; the server's default answer is newer than this. */
        const val INSTALLED_AT = "2026-09-01T00:00:00Z"
    }
}
