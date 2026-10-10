package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.AvailableMapEntry
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * Unit tests for a repository database download from the map manager: it joins the same lifecycle as a
 * provider download (progress, foreground service, registration, installed list).
 *
 * Spec: map-download-infrastructure — "Repository download keeps the foreground service alive" /
 * "Repository download completes into the installed list"; map-repository-source — "Only the files the
 * metadata names are fetched" / "A repository database's directory name comes from the index".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapDownloadServiceRepositoryLifecycleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage
    private val requestedPaths = mutableListOf<String>()

    private val version = com.framstag.libosmscout.client.MapDownloadManager.DATABASE_FORMAT_VERSION
    private val mapLib = "map data".toByteArray()
    private val typesDat = "type config".toByteArray()
    private val leafPath = "europe/germany/berlin"

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
    fun repositoryDownloadInstallsAndRegistersTheDatabase() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        val installed = storageManager.mapsRootDir.resolve("europe-germany-berlin")
        assertTrue("the directory is named from the index id path", Files.isDirectory(installed))
        assertEquals("map data", String(Files.readAllBytes(installed.resolve("map.lib"))))
        assertEquals("type config", String(Files.readAllBytes(installed.resolve("types.dat"))))
        assertTrue(
            "the completed download is registered with the map manager",
            fixture.downloadManager.registered.contains(installed.toString())
        )
        assertEquals(
            "and it records which source installed it",
            MapSourceKind.REPOSITORY,
            MapSourceMarker.sourceOf(installed).sourceKind
        )
    }

    @Test
    fun serviceRunsForARepositoryDownload() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val shadow = org.robolectric.Shadows.shadowOf(application)

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        val started = mutableListOf<android.content.Intent>()
        while (true) {
            val intent = shadow.nextStartedService ?: break
            started.add(intent)
        }
        assertTrue(
            "the download starts the foreground service",
            started.firstOrNull()?.component?.className == "com.naviveylin.service.MapDownloadService"
        )
        assertTrue(
            "and it stops the service when it finishes",
            started.any { it.action == "com.naviveylin.action.STOP_DOWNLOAD" }
        )
    }

    @Test
    fun wakeLockDoesNotOutliveTheService() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        assertFalse(
            "no wake lock outlives the download service",
            org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()?.isHeld == true
        )
    }

    @Test
    fun installedListRefreshesAfterRegistration() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        val installedPath = storageManager.mapsRootDir.resolve("europe-germany-berlin").toString()
        assertTrue(
            "the installed list is refreshed, so the map shows without a restart",
            fixture.viewModel.uiState.value.installedMapPaths.contains(installedPath)
        )
        assertTrue("and the download is no longer active", fixture.viewModel.uiState.value.activeDownloads.isEmpty())
    }

    @Test
    fun onlyTheMetadataNamesAreRequested() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        val slotUrl = RepositoryUrlPlanner.slotUrl(baseUrl, leafPath.split("/"), version)
        assertEquals(
            listOf(
                RepositoryUrlPlanner.metadataUrl(baseUrl, leafPath.split("/"), version),
                slotUrl + "map.lib",
                slotUrl + "types.dat"
            ),
            requestedPaths
        )
    }

    @Test
    fun aCorruptDownloadLeavesNoDirectoryAndReportsIt() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()
        // The server serves a body that does not match the metadata's checksum.
        corrupted = "map.lib"

        fixture.viewModel.downloadMap(fixture.entry)
        advanceUntilIdle()

        assertFalse(
            "nothing is installed",
            Files.exists(storageManager.mapsRootDir.resolve("europe-germany-berlin"))
        )
        val error = fixture.viewModel.uiState.value.activeDownloads.firstOrNull()?.statusText.orEmpty()
        assertTrue("the failure names what to fetch again: $error", error.contains("map.lib"))
    }

    @Test
    fun aCancelledDownloadLeavesNothingBehind() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = fixture()

        fixture.viewModel.downloadMap(fixture.entry)
        fixture.viewModel.cancelDownload(fixture.entry.name)
        advanceUntilIdle()

        assertFalse(Files.exists(storageManager.mapsRootDir.resolve("europe-germany-berlin")))
        assertTrue("a cancelled download is not an error state to dismiss", fixture.viewModel.uiState.value.activeDownloads.isEmpty())
    }

    /** The download answers a slot for [leafPath] with a metadata document, unless a test corrupts it. */
    private var corrupted: String? = null

    private fun handle(exchange: HttpExchange) {
        val url = "http://127.0.0.1:${server.address.port}${exchange.requestURI.path}"
        requestedPaths += url
        val slot = RepositoryUrlPlanner.slotUrl(baseUrl, leafPath.split("/"), version)
        when (url) {
            RepositoryUrlPlanner.metadataUrl(baseUrl, leafPath.split("/"), version) ->
                respond(exchange, 200, metadataDocument().toByteArray())
            slot + "map.lib" -> respond(exchange, 200, if (corrupted == "map.lib") "corrupted!".toByteArray() else mapLib)
            slot + "types.dat" -> respond(exchange, 200, typesDat)
            else -> respond(exchange, 404, ByteArray(0))
        }
    }

    private fun metadataDocument(): String {
        val files = listOf("map.lib" to mapLib, "types.dat" to typesDat).joinToString(",") { (name, body) ->
            """"$name": {"size": ${body.size}, "crc32": ${crc32Of(body)}}"""
        }
        return """{"schema": 1, "typeConfigVersion": $version, "output": {"files": {$files}}}"""
    }

    private fun crc32Of(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes, 0, bytes.size)
        return crc.value
    }

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private class Fixture(
        val viewModel: MapManagerViewModel,
        val downloadManager: FakeMapDownloadManager,
        val entry: AvailableMapEntry
    )

    private suspend fun TestScope.fixture(): Fixture {
        val provider = MapProvider("karry.cz", baseUrl, "$baseUrl/latest.php?fromVersion=%1&toVersion=%2&locale=%3")
        val registry = MapSourceRegistry(
            settings,
            provider,
            HttpUrlFetcher().apply { ioDispatcher = mainDispatcherRule.dispatcher }
        )
        val downloadManager = FakeMapDownloadManager(installedDirs = { installedDirectories() })
        val viewModel = MapManagerViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            downloadManager,
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier(),
            BasemapRegistrar(FakeOSMScoutClient(), BasemapReloadNotifier())
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        settings.save(AppSettings().withSelectedMapSource(MapSource(MapSourceKind.REPOSITORY, baseUrl)))
        settings.ioDispatcher = mainDispatcherRule.dispatcher
        viewModel.refreshSources()
        advanceUntilIdle()
        val entry = AvailableMapEntry(
            "Berlin", leafPath.split("/").dropLast(1), "", null, 0L, leafPath, 0L, -1
        )
        return Fixture(viewModel, downloadManager, entry)
    }

    private fun installedDirectories(): List<String> =
        storageManager.mapsRootDir.toFile().listFiles().orEmpty().map { it.absolutePath }
}
