package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.data.AppSettings
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.test.MainDispatcherRule
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.nio.file.Path

/**
 * Unit tests for the basemap's per-source behaviour in [BasemapViewModel].
 *
 * Specs: basemap-discovery — "Basemap available on a repository source" / "Basemap unavailable on
 * server" / "Probe follows the source, not the stale one"; basemap-ui — "Source reports no update
 * state" / "Basemap status clears when its source's data is deleted"; basemap-download — "A tar.gz
 * archive is not used by a repository source".
 *
 * [MainDispatcherRule]'s dispatcher drives both `viewModelScope` and the view model's IO seam, so a
 * probe is advanced step by step instead of raced against real thread pools.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BasemapSourceSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: HttpServer
    private lateinit var providerBaseUrl: String
    private lateinit var mapsDir: Path
    private val requestedPaths = mutableListOf<String>()

    /** Status the basemap manifest request answers with; 200 unless a test says otherwise. */
    private var manifestStatus = 200

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestedPaths += exchange.requestURI.path
            when (exchange.requestURI.path) {
                "/basemap/index.json" -> if (manifestStatus == 200) {
                    respond(
                        exchange,
                        200,
                        """{"schema": 1, "versions": [{"typeConfigVersion": 27, "changedAt": "2026-09-07T10:00:00Z"}]}"""
                    )
                } else {
                    respond(exchange, manifestStatus, "")
                }
                "/basemap/" -> respond(
                    exchange,
                    200,
                    "<html><body><table>" +
                        "<tr><td><a href=\"BaseMap-2026-02-23.tar.gz\">BaseMap-2026-02-23.tar.gz</a></td>" +
                        "<td align=\"right\">2026-02-24 00:16</td>" +
                        "<td align=\"right\">39M</td><td>&nbsp;</td></tr>" +
                        "</table></body></html>"
                )
                else -> respond(exchange, 404, "")
            }
        }
        server.start()
        providerBaseUrl = "http://127.0.0.1:${server.address.port}"
        mapsDir = Files.createTempDirectory("basemap-source-test")
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun probesTheActiveSourceOnly() = runTest(mainDispatcherRule.dispatcher) {
        val (viewModel, _) = viewModel(activeSource = repository())

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("the repository source is the active one", state.sourceIsRepository)
        assertEquals(BasemapAvailability.Available, state.availability)
        assertEquals(27, state.repositoryVersion?.typeConfigVersion)
        assertTrue("the manifest was read", requestedPaths.contains("/basemap/index.json"))
        assertFalse(
            "no provider archive listing is read for a repository source",
            requestedPaths.contains("/basemap/")
        )
        assertTrue("no tar.gz archive is offered", state.variants.isEmpty())
        assertTrue("no tar.gz archive is treated as a basemap", state.archives.isEmpty())
    }

    @Test
    fun switchingSourceInvalidatesThePreviousProbeResult() = runTest(mainDispatcherRule.dispatcher) {
        val (viewModel, storage) = viewModel(activeSource = repository())

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(BasemapAvailability.Available, viewModel.uiState.value.availability)

        // The user switches source; the repository probe's result may no longer be published.
        storage.save(AppSettings().withSelectedMapSource(MapSource.BuiltInProvider))
        assertFalse("a result of the retired source is not publishable",
                    viewModel.mayPublish(repository()))

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MapSource.BuiltInProvider, state.source)
        assertNull("the retired source's version is not reported", state.repositoryVersion)
        assertEquals(
            "the published state is the provider's own archive listing, not the repository's answer",
            listOf("BaseMap-2026-02-23.tar.gz"),
            state.variants.map { it.fileName }
        )
        assertEquals(BasemapAvailability.Available, state.availability)
    }

    @Test
    fun missingBasemapIsUnavailableWithoutAnError() = runTest(mainDispatcherRule.dispatcher) {
        manifestStatus = 404
        val (viewModel, _) = viewModel(activeSource = repository())

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BasemapAvailability.Unavailable, state.availability)
        assertNull("the basemap is optional, so no failure is shown", state.error)
        assertNull(state.repositoryVersion)
    }

    @Test
    fun repositorySourceOffersNoUpdateControl() = runTest(mainDispatcherRule.dispatcher) {
        val installed = Files.createDirectories(mapsDir.resolve("basemap"))
        Files.write(installed.resolve("types.dat"), "installed".toByteArray())
        val (viewModel, _) = viewModel(activeSource = repository())

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.sourceIsRepository)
        assertFalse("no update check runs for this source, so no update control appears",
                    state.updateAvailable)
        assertTrue("the installed basemap is still reported", state.installedInfo != null)
        assertEquals("the published version is still offered for download",
                     27, state.repositoryVersion?.typeConfigVersion)
    }

    @Test
    fun deletedBasemapClearsTheInstalledState() = runTest(mainDispatcherRule.dispatcher) {
        val installed = Files.createDirectories(mapsDir.resolve("basemap"))
        Files.write(installed.resolve("types.dat"), "installed".toByteArray())
        val (viewModel, _) = viewModel(activeSource = repository())
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.installedInfo != null)

        viewModel.delete()
        advanceUntilIdle()

        assertNull("the deleted basemap is no longer reported as installed",
                   viewModel.uiState.value.installedInfo)
    }

    @Test
    fun theProviderSourceKeepsItsArchiveFlow() = runTest(mainDispatcherRule.dispatcher) {
        val (viewModel, _) = viewModel(activeSource = MapSource.BuiltInProvider)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MapSource.BuiltInProvider, state.source)
        assertFalse(state.sourceIsRepository)
        assertEquals(BasemapAvailability.Available, state.availability)
        assertEquals(listOf("BaseMap-2026-02-23.tar.gz"), state.variants.map { it.fileName })
        assertNull(state.repositoryVersion)
    }

    /** Build the view model under test with [activeSource] persisted. */
    private suspend fun viewModel(activeSource: MapSource): Pair<BasemapViewModel, SettingsStorage> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storage = SettingsStorage(context)
        // Everything the probe touches shares the test scheduler: the settings read, the HTTP fetcher
        // and the view model's own work — otherwise `advanceUntilIdle` returns while real thread pools
        // are still running (the reason MainDispatcherRule exists).
        storage.ioDispatcher = mainDispatcherRule.dispatcher
        storage.save(AppSettings().withSelectedMapSource(activeSource))
        val provider = MapProvider("test", providerBaseUrl, "$providerBaseUrl/latest.php?fromVersion=%1&toVersion=%2&locale=%3")
        // The fetcher shares the test scheduler, so `advanceUntilIdle` drives the probe instead of
        // racing a real thread pool (the reason MainDispatcherRule exists).
        val fetcher = HttpUrlFetcher().apply { ioDispatcher = mainDispatcherRule.dispatcher }
        val registry = MapSourceRegistry(storage, provider, fetcher)
        val viewModel = BasemapViewModel(
            context,
            BasemapManager(provider, mapsDir),
            BasemapRegistrar(FakeOSMScoutClient(), BasemapReloadNotifier()),
            registry
        )
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        return viewModel to storage
    }

    private fun repository(): MapSource = MapSource(MapSourceKind.REPOSITORY, providerBaseUrl)

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
