package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.core.mapsource.SlotInstall
import com.naviveylin.data.AppSettings
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.FakeRepositoryServer
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.MapUpdates
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.test.MainDispatcherRule
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Unit tests for what updating more than one installation does: one transfer at a time, each cancellable on
 * its own, a failure of one reported for its own row without stopping the rest, and one item updated alone.
 *
 * Spec: map-updates — "Updating every listed item is a queue" / "An update replaces the installation only when
 * it is complete" / "An installed update is rendered without an app restart".
 *
 * Every seam the queue touches — the view model's, the settings file's and the update check's — is on the test
 * scheduler, so `advanceUntilIdle()` drives a transfer to its end instead of racing real thread pools. Where a
 * transfer must be *held* mid-flight, the repository read is gated instead of raced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerUpdatesQueueTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val baseUrl = "https://maps.example.org/repo"
    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")
    private val hamburg = listOf("europe", "germany", "hamburg")
    private val berlinDirectory = "europe-germany-berlin"
    private val hamburgDirectory = "europe-germany-hamburg"
    private val installedAt = "2026-09-01T00:00:00Z"

    private val provider = MapProvider("test", "http://127.0.0.1:1", "http://127.0.0.1:1/list.php")
    private val server = FakeRepositoryServer(baseUrl, version)

    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage
    private lateinit var client: FakeOSMScoutClient

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        storageManager = MapStorageManager(context)
        storageManager.mapsRootDir.toFile().deleteRecursively()
        settings = SettingsStorage(context)
        client = FakeOSMScoutClient()
    }

    @Test
    fun theQueueRunsOneTransferAtATime() = runTest(mainDispatcherRule.dispatcher) {
        installMap(berlinDirectory)
        installMap(hamburgDirectory)
        serveBothSlots()
        val viewModel = updatesViewModel()
        checkAndWait(viewModel)
        assertEquals("the check found both installations", 2, viewModel.updates.value.updates.size)
        // Hold the first transfer inside its repository read: a queue runs one at a time, so while one is held
        // no other item's files may be requested. That is what makes this case able to see a parallel queue.
        val gate = CompletableDeferred<Unit>()
        server.gate = gate

        viewModel.updateAll()
        advanceUntilIdle()

        val inFlight = viewModel.uiState.value.updateTransfers
            .filterValues { it is UpdateTransferState.Transferring }
        assertEquals("exactly one transfer is in flight", 1, inFlight.size)
        val otherFiles = if (inFlight.keys.single() == berlinDirectory) fileUrls(hamburg) else fileUrls(berlin)
        assertTrue(
            "and the other item's transfer has not started",
            server.requested.none { url -> url in otherFiles }
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(NEW_TYPES, typesDat(berlinDirectory))
        assertEquals(NEW_TYPES, typesDat(hamburgDirectory))
        assertTrue("the running client re-opened the set", client.openedDatabaseBatches.isNotEmpty())
        assertTrue(
            "and no staging data is left behind",
            listOf(berlinDirectory, hamburgDirectory).none { Files.exists(incomingOf(it)) }
        )
        assertTrue("nothing is left to update", viewModel.updates.value.updates.isEmpty())
        // Serialised, not interleaved: one installation's two files are fetched together, and the other's
        // transfer starts only after it finished. Which of the two goes first is the installed-set order, so
        // the assertion is written order-agnostically.
        val berlinPositions = server.requested.withIndex()
            .filter { (_, url) -> url in fileUrls(berlin) }.map { it.index }
        val hamburgPositions = server.requested.withIndex()
            .filter { (_, url) -> url in fileUrls(hamburg) }.map { it.index }
        assertEquals("both transfers fetched both files", 2, berlinPositions.size)
        assertEquals("both transfers fetched both files", 2, hamburgPositions.size)
        assertTrue(
            "the two transfers do not interleave",
            berlinPositions.max() < hamburgPositions.min() ||
                hamburgPositions.max() < berlinPositions.min()
        )
    }

    @Test
    fun aFailureDoesNotStopTheQueue() = runTest(mainDispatcherRule.dispatcher) {
        installMap(berlinDirectory)
        installMap(hamburgDirectory)
        // Berlin's slot is listed but one of its files cannot be transferred; Hamburg's whole slot is fine.
        server.serveIndex(listOf(berlin, hamburg))
        server.serveSlot(berlin, typesDatBody = NEW_TYPES.toByteArray())
        server.serveSlot(hamburg, typesDatBody = NEW_TYPES.toByteArray())
        server.failDownloadOf = RepositoryUrlPlanner.fileUrl(baseUrl, berlin, version, "map.lib") to
            RepositoryFailure.TransportFailed("connection reset")
        val viewModel = updatesViewModel()
        checkAndWait(viewModel)

        viewModel.updateAll()
        advanceUntilIdle()

        assertTrue(
            "the failed item says so",
            viewModel.uiState.value.updateTransfers[berlinDirectory] is UpdateTransferState.Failed
        )
        assertEquals("the other item was still updated", NEW_TYPES, typesDat(hamburgDirectory))
        assertEquals("and the failed item keeps the map it had", INSTALLED_TYPES, typesDat(berlinDirectory))
        assertFalse("with no staging data left over", Files.exists(incomingOf(berlinDirectory)))
    }

    @Test
    fun anItemIsCancelledOnItsOwn() = runTest(mainDispatcherRule.dispatcher) {
        installMap(berlinDirectory)
        installMap(hamburgDirectory)
        serveBothSlots()
        val viewModel = updatesViewModel()
        checkAndWait(viewModel)
        // Hold the first transfer inside its repository read, so the cancel lands while it runs.
        val gate = CompletableDeferred<Unit>()
        server.gate = gate

        viewModel.updateAll()
        advanceUntilIdle()
        assertTrue("the first transfer is in flight", viewModel.uiState.value.isUpdating)

        viewModel.cancelUpdate(berlinDirectory)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(
            "the cancelled item reports it",
            viewModel.uiState.value.updateTransfers[berlinDirectory] is UpdateTransferState.Failed
        )
        assertEquals("its installation is what it was", INSTALLED_TYPES, typesDat(berlinDirectory))
        assertFalse("and its partial transfer is gone", Files.exists(incomingOf(berlinDirectory)))
        assertEquals("while the other item was still updated", NEW_TYPES, typesDat(hamburgDirectory))
    }

    @Test
    fun updateOneItemRunsOnlyThatOne() = runTest(mainDispatcherRule.dispatcher) {
        installMap(berlinDirectory)
        installMap(hamburgDirectory)
        serveBothSlots()
        val viewModel = updatesViewModel()
        checkAndWait(viewModel)
        val hamburgItem = viewModel.updates.value.updates.first { it.directoryName == hamburgDirectory }

        viewModel.update(hamburgItem)
        advanceUntilIdle()

        assertEquals(NEW_TYPES, typesDat(hamburgDirectory))
        assertEquals("the other installation is untouched", INSTALLED_TYPES, typesDat(berlinDirectory))
        assertTrue(
            "and its files were never fetched",
            server.requested.none { url ->
                url.contains("/berlin/v$version/map.lib") || url.contains("/berlin/v$version/types.dat")
            }
        )
    }

    @Test
    fun theQueueIsVisibleAsADownloadWhileItRuns() = runTest(mainDispatcherRule.dispatcher) {
        installMap(berlinDirectory)
        serveBothSlots()
        val viewModel = updatesViewModel()
        checkAndWait(viewModel)
        val gate = CompletableDeferred<Unit>()
        server.gate = gate

        viewModel.updateAll()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("the queue is running", state.isUpdating)
        assertTrue(
            "and the running transfer is published where the other downloads are",
            state.updateTransfers[berlinDirectory] is UpdateTransferState.Transferring
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("the queue ends", viewModel.uiState.value.isUpdating)
        assertTrue("and the transfer is no longer in flight", viewModel.uiState.value.updateTransfers.isEmpty())
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /** Run the check and let it reach this screen's state (the check publishes into its own flow). */
    private fun TestScope.checkAndWait(viewModel: MapManagerViewModel) {
        viewModel.checkForUpdates()
        advanceUntilIdle()
        assertFalse("the check completed", viewModel.updates.value.neverChecked)
    }

    private fun TestScope.updatesViewModel(): MapManagerViewModel {
        kotlinx.coroutines.runBlocking {
            settings.save(AppSettings().withSelectedMapSource(MapSource(MapSourceKind.REPOSITORY, baseUrl)))
        }
        val registry = MapSourceRegistry(settings, provider, server)
        val notifier = BasemapReloadNotifier()
        val updates = MapUpdates(
            settingsStorage = settings,
            sourceRegistry = registry,
            storageManager = storageManager,
            fetcher = server,
            timeSource = EngineTimeSource.System
        ).apply { ioDispatcher = mainDispatcherRule.dispatcher }
        val viewModel = mapManagerViewModelForTest(
            application = ApplicationProvider.getApplicationContext(),
            downloadManager = FakeMapDownloadManager(installedDirs = { emptyList() }),
            storageManager = storageManager,
            provider = provider,
            settings = settings,
            registry = registry,
            reloadNotifier = notifier,
            client = client,
            basemapRegistrar = BasemapRegistrar(client, notifier),
            updates = updates
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        // The active source the queue reads is the one the screen publishes; the view model starts on the
        // built-in provider until it has read the settings (spec `map-source-selection`).
        viewModel.refreshSources()
        advanceUntilIdle()
        return viewModel
    }

    /** Serve both leaves' slots, with the type configuration that marks a completed installation. */
    private fun serveBothSlots() {
        server.serveIndex(listOf(berlin, hamburg))
        server.serveSlot(berlin, typesDatBody = NEW_TYPES.toByteArray())
        server.serveSlot(hamburg, typesDatBody = NEW_TYPES.toByteArray())
    }

    /** The two data files a slot holds, in the order a transfer writes them. */
    private fun fileUrls(idPath: List<String>): List<String> = listOf("map.lib", "types.dat").map { name ->
        RepositoryUrlPlanner.fileUrl(baseUrl, idPath, version, name)
    }

    private fun typesDat(directoryName: String): String =
        String(Files.readAllBytes(storageManager.mapsRootDir.resolve(directoryName).resolve("types.dat")))
    private fun installMap(directoryName: String) {
        val directory = storageManager.mapsRootDir.resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("types.dat"), INSTALLED_TYPES.toByteArray())
        val document =
            """{"schema":1,"typeConfigVersion":$version,"generatedAt":"$installedAt",""" +
                """"output":{"files":{"map.lib":{"size":10,"crc32":1}}}}"""
        Files.write(directory.resolve("db.json"), document.toByteArray())
        MapSourceMarker.write(
            directory,
            MapSourceRecord(MapSourceKind.REPOSITORY, baseUrl, version)
        )
    }

    private fun incomingOf(directoryName: String): Path =
        storageManager.mapsRootDir.resolve(".$directoryName${SlotInstall.INCOMING_SUFFIX}")

    private companion object {
        const val INSTALLED_TYPES = "installed"
        const val NEW_TYPES = "new types"
    }
}
