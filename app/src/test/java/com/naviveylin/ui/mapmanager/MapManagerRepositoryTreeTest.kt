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
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.data.AppSettings
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.test.MainDispatcherRule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * Unit tests for the repository source in [MapManagerViewModel]: the region tree, the lazy metadata
 * probe and the URL test action.
 *
 * Specs: map-repository-source — "Region index drives the available-maps tree" / "Database metadata is
 * fetched on demand per leaf" / "Leaf published for another database version"; map-source-selection —
 * "Base URL of the repository source is validated before use".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerRepositoryTreeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage
    private val requestedPaths = mutableListOf<String>()

    /** Answered metadata per leaf path, so a test can make one leaf not-found. */
    private val metadataByPath = mutableMapOf<String, String>()

    private val version = com.framstag.libosmscout.client.MapDownloadManager.DATABASE_FORMAT_VERSION

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
        storageManager = MapStorageManager(ApplicationProvider.getApplicationContext())
        settings = SettingsStorage(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun expandTriggersOneProbePerLeaf() = runTest(mainDispatcherRule.dispatcher) {
        serveMetadata("europe/germany/berlin")
        serveMetadata("europe/germany/brandenburg")
        val viewModel = repositoryViewModel()

        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()
        viewModel.probeLeavesUnder("europe/germany")
        advanceUntilIdle()

        val leafRequests = requestedPaths.filter { it.endsWith("/db.json") }
        assertEquals(2, leafRequests.size)
        assertTrue(leafRequests.contains(RepositoryUrlPlanner.metadataUrl(baseUrl, listOf("europe", "germany", "berlin"), version)))
        assertTrue(leafRequests.contains(RepositoryUrlPlanner.metadataUrl(baseUrl, listOf("europe", "germany", "brandenburg"), version)))
    }

    @Test
    fun probingTwiceIssuesOneRequest() = runTest(mainDispatcherRule.dispatcher) {
        serveMetadata("europe/germany/berlin")
        val viewModel = repositoryViewModel()
        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()

        viewModel.probeLeaf(listOf("europe", "germany", "berlin"))
        advanceUntilIdle()
        viewModel.probeLeaf(listOf("europe", "germany", "berlin"))
        advanceUntilIdle()

        assertEquals(
            1,
            requestedPaths.count { it == RepositoryUrlPlanner.metadataUrl(baseUrl, listOf("europe", "germany", "berlin"), version) }
        )
    }

    @Test
    fun notFoundStateNamesTheDatabaseVersion() = runTest(mainDispatcherRule.dispatcher) {
        // No metadata registered for `berlin`: the slot answers 404.
        val viewModel = repositoryViewModel()
        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()

        viewModel.probeLeaf(listOf("europe", "germany", "berlin"))
        advanceUntilIdle()

        val state = viewModel.uiState.value.leafMetadata["europe/germany/berlin"]
        assertEquals(LeafMetadataState.NotPublished(version), state)
    }

    @Test
    fun noGlobalErrorBannerOnLeafNotFound() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()
        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()

        viewModel.probeLeaf(listOf("europe", "germany", "berlin"))
        advanceUntilIdle()

        assertNull("a leaf without a published database is a row state, not a screen error",
                   viewModel.uiState.value.error)
        assertTrue("the rest of the tree stays usable", viewModel.uiState.value.repositoryLeaves.isNotEmpty())
    }

    @Test
    fun treeParentChainMatchesTheIndexNesting() = runTest(mainDispatcherRule.dispatcher) {
        serveMetadata("europe/germany/berlin")
        val viewModel = repositoryViewModel()
        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()
        viewModel.probeLeaf(listOf("europe", "germany", "berlin"))
        advanceUntilIdle()

        val berlin = viewModel.uiState.value.repositoryLeaves.first { it.name == "Berlin" }
        assertEquals(
            "a repository leaf's path segments are its index identifiers, which is what the tree builds from",
            listOf("europe", "germany"),
            berlin.path
        )
        assertEquals("europe/germany/berlin", berlin.serverDirectory)
        assertTrue("the probed metadata supplies the size", berlin.size > 0)
        assertEquals(version, berlin.version)
    }

    @Test
    fun directoryRowsCarryTheIndexNamesNotTheIdentifiers() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()

        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()

        // The tree builder labels a group row from its path segment, which for a repository is an
        // identifier (`europe`); the index's localized name must be available for it
        // (found on device 2026-10-09: the group row read "europe").
        val labels = viewModel.uiState.value.repositoryRegionLabels
        assertEquals("europe", labels.keys.first { it == "europe" })
        assertFalse("the group row must not fall back to the identifier", labels["europe"] == "europe")
        assertTrue(labels.getValue("europe").isNotBlank())
        assertTrue(labels.getValue("europe/germany").isNotBlank())
    }

    @Test
    fun localizedNamesReachTheRows() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()
        viewModel.refreshRepositoryRegions()
        advanceUntilIdle()

        // The index carries English and German names; whichever the device language picks, the row is
        // never blank and never the raw identifier.
        val names = viewModel.uiState.value.repositoryLeaves.map { it.name }
        assertTrue(names.contains("Berlin") || names.contains("Berlin (Stadt)"))
        assertFalse(names.contains("berlin"))
    }

    @Test
    fun testSuccessShowsRegionAndLeafCounts() = runTest(mainDispatcherRule.dispatcher) {
        serveMetadata("europe/germany/berlin")
        val viewModel = repositoryViewModel()
        viewModel.updateRepositoryUrlDraft(baseUrl)
        advanceUntilIdle()

        viewModel.testRepositoryUrl()
        advanceUntilIdle()

        val outcome = viewModel.uiState.value.sourceTestOutcome
        assertTrue("the test reports what the index offers", outcome is SourceTestOutcome.Success)
        assertEquals(1, (outcome as SourceTestOutcome.Success).regionCount)
        assertEquals(2, outcome.leafCount)
        assertEquals(RepositoryUrlPlanner.regionIndexUrl(baseUrl), outcome.url)
    }

    @Test
    fun testFailureNamesTheUrlAndTheReason() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()
        viewModel.updateRepositoryUrlDraft("$baseUrl/does-not-exist")
        advanceUntilIdle()

        viewModel.testRepositoryUrl()
        advanceUntilIdle()

        val outcome = viewModel.uiState.value.sourceTestOutcome
        assertTrue(outcome is SourceTestOutcome.Failure)
        val failure = (outcome as SourceTestOutcome.Failure)
        assertTrue("the message names the URL that was asked", failure.url.endsWith("/names.json"))
        assertEquals(
            com.naviveylin.core.mapsource.RepositoryFailure.HttpStatus(404),
            failure.failure
        )
    }

    @Test
    fun theActiveSourceIsNeverChangedByATest() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = repositoryViewModel()
        viewModel.updateRepositoryUrlDraft("$baseUrl/does-not-exist")
        advanceUntilIdle()

        viewModel.testRepositoryUrl()
        advanceUntilIdle()

        assertTrue("the repository stays the active source", viewModel.uiState.value.activeSource.isRepository)
        assertNull("a test never opens the switch dialog", viewModel.uiState.value.pendingSourceSwitch)
        assertTrue(viewModel.uiState.value.sourceTestOutcome is SourceTestOutcome.Failure)
    }

    @Test
    fun urlFieldRetainsPerSourceValue() = runTest(mainDispatcherRule.dispatcher) {
        val entered = "https://maps.example.org/repo"
        val viewModel = repositoryViewModel()
        viewModel.updateRepositoryUrlDraft(entered)
        advanceUntilIdle()

        // The field is the stored value, so switching away and back shows it again without a request.
        val requestsBefore = requestedPaths.size
        viewModel.refreshSources()
        advanceUntilIdle()

        assertEquals(entered, viewModel.uiState.value.repositoryUrlDraft)
        assertEquals(entered, settings.load().mapRepositoryUrl)
        assertEquals("reading the selection issues no request", requestsBefore, requestedPaths.size)
    }

    /** The view model under test, with the repository active and a built-in provider as its sibling. */
    private suspend fun TestScope.repositoryViewModel(storedUrl: String = baseUrl): MapManagerViewModel {
        val provider = MapProvider("karry.cz", baseUrl, "$baseUrl/latest.php?fromVersion=%1&toVersion=%2&locale=%3")
        val registry = MapSourceRegistry(
            settings,
            provider,
            HttpUrlFetcher().apply { ioDispatcher = mainDispatcherRule.dispatcher }
        )
        val viewModel = MapManagerViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            FakeMapDownloadManager(installedDirs = { emptyList() }),
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier(),
            BasemapRegistrar(FakeOSMScoutClient(), BasemapReloadNotifier())
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        // Persist the selection before the storage is moved onto the test scheduler (a save on that
        // scheduler would wait for an advance the test body has not reached yet).
        settings.save(AppSettings().withSelectedMapSource(MapSource(MapSourceKind.REPOSITORY, storedUrl)))
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel.refreshSources()
        // The published state is what the next call reads, so let it land before returning.
        advanceUntilIdle()
        return viewModel
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        requestedPaths += "http://127.0.0.1:${server.address.port}$path"
        when {
            path.endsWith("/names.json") && !path.contains("does-not-exist") -> respond(exchange, 200, regionIndex())
            path.endsWith("/db.json") -> {
                val leafPath = path.removePrefix("/").removeSuffix("/db.json")
                val body = metadataByPath[leafPath]
                if (body == null) respond(exchange, 404, "") else respond(exchange, 200, body)
            }
            else -> respond(exchange, 404, "")
        }
    }

    private fun regionIndex(): String = """
        {"schema": 1, "regions": [
          {"id": "europe", "names": {"en": "Europe", "de": "Europa"}, "children": [
            {"id": "germany", "names": {"en": "Germany", "de": "Deutschland"}, "children": [
              {"id": "berlin", "names": {"en": "Berlin", "de": "Berlin (Stadt)"}},
              {"id": "brandenburg", "names": {"en": "Brandenburg", "de": "Brandenburg"}}
            ]}
          ]}
        ]}
    """.trimIndent()

    /**
     * Serve [idPath]'s metadata from its own version slot, as a repository does (a slot is addressed
     * with the database format version this client reads).
     */
    private fun serveMetadata(idPath: String) {
        metadataByPath["$idPath/v$version"] = metadataDocument(version)
    }

    private fun metadataDocument(version: Int): String =
        """{"schema": 1, "typeConfigVersion": $version, "generatedAt": "2026-09-07T16:35:44Z",
           "output": {"files": {"map.lib": {"size": 1048576, "crc32": 12345}}}}"""

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** The marker a directory of the built-in provider carries, for switch tests of this class. */
    private fun builtInMarker(): MapSourceRecord =
        MapSourceRecord(MapSourceKind.BUILT_IN_PROVIDER, null, version)

    private fun markDirectory(name: String): java.nio.file.Path {
        val directory = storageManager.mapsRootDir.resolve(name)
        Files.createDirectories(directory)
        MapSourceMarker.write(directory, builtInMarker())
        return directory
    }
}
