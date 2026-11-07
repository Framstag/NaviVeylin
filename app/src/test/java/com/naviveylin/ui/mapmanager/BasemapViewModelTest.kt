package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.MainDispatcherRule
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * Unit tests for [BasemapViewModel] basemap reload wiring: download-complete
 * and delete flows must register the basemap lookup directory for the running
 * session, reload the native database, and notify renderers (spec:
 * basemap-loading — download/update/delete while the app runs, no restart).
 *
 * NOTE: instantiates FakeOSMScoutClient → must run under RobolectricTestRunner
 * with the DEFAULT sandbox (no @Config overrides), per the classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BasemapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/basemap/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        return server
    }

    private class Fixture(
        val viewModel: BasemapViewModel,
        val client: FakeOSMScoutClient,
        val notifier: BasemapReloadNotifier,
        val mapsDir: java.nio.file.Path
    )

    private fun fixture(
        server: HttpServer,
        withInstalledBasemap: Boolean = false,
        typesVersion: Int? = null
    ): Fixture {
        val mapsDir = Files.createTempDirectory("basemap-vm-test")
        if (withInstalledBasemap) {
            val basemapDir = mapsDir.resolve("basemap")
            Files.createDirectories(basemapDir)
            Files.write(basemapDir.resolve("water.idx"), "data".toByteArray())
            if (typesVersion != null) {
                writeTypesVersion(basemapDir, typesVersion)
            }
        }
        val manager = BasemapManager(
            MapProvider("test", "http://127.0.0.1:${server.address.port}", ""),
            mapsDir
        )
        val client = FakeOSMScoutClient()
        val notifier = BasemapReloadNotifier()
        val provider = MapProvider("test", "http://127.0.0.1:${server.address.port}", "")
        val vm = BasemapViewModel(
            ApplicationProvider.getApplicationContext() as Application,
            manager,
            BasemapRegistrar(client, notifier),
            MapSourceRegistry(
                SettingsStorage(ApplicationProvider.getApplicationContext()),
                provider,
                HttpUrlFetcher()
            )
        )
        return Fixture(vm, client, notifier, mapsDir)
    }

    /** Write `types.dat` with [version] as its little-endian header, the database's own version field. */
    private fun writeTypesVersion(basemapDir: java.nio.file.Path, version: Int) {
        Files.write(
            basemapDir.resolve("types.dat"),
            java.nio.ByteBuffer.allocate(4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(version)
                .array()
        )
    }

    @Test
    fun theSectionStateCarriesTheInstalledDataFormatWhenThisBuildCannotReadIt() =
        runTest(mainDispatcherRule.dispatcher) {
            val server = startServer()
            try {
                val f = fixture(server, withInstalledBasemap = true, typesVersion = 26)

                // The probe suspends on real IO (settings, then the listing), so the state is awaited the way
                // this class' delete case awaits the notifier: a published state, not a drained scheduler.
                val stale = withContext(Dispatchers.Default) {
                    f.viewModel.uiState.first {
                        it.availability != BasemapAvailability.Unknown && it.outdatedFormat != null
                    }
                }
                assertEquals(
                    "the section must be told the installed version and the one this build reads",
                    26,
                    stale.outdatedFormat?.installedVersion
                )
                assertEquals(27, stale.outdatedFormat?.expectedVersion)

                writeTypesVersion(f.mapsDir.resolve("basemap"), version = 27)
                f.viewModel.refresh()

                val readable = withContext(Dispatchers.Default) {
                    f.viewModel.uiState.first { it.outdatedFormat == null && it.installedInfo != null }
                }
                assertEquals(
                    "a readable basemap clears the notice (spec `basemap-ui` — Notice clears once a " +
                        "readable basemap is installed)",
                    null,
                    readable.outdatedFormat
                )
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun applyBasemapChangeRegistersReloadsAndNotifies() = runTest(mainDispatcherRule.dispatcher) {
        val server = startServer()
        try {
            val f = fixture(server)
            f.viewModel.applyBasemapChange("/data/maps/basemap")

            assertEquals(listOf("/data/maps/basemap"), f.client.basemapLookupDirectories)
            assertEquals(1, f.client.reloadBasemapCount)
            assertEquals(1L, f.notifier.revision.value)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun applyBasemapChangeEmptyUnloadsBasemap() = runTest(mainDispatcherRule.dispatcher) {
        val server = startServer()
        try {
            val f = fixture(server)
            f.viewModel.applyBasemapChange("")

            assertEquals(listOf(""), f.client.basemapLookupDirectories)
            assertEquals(1, f.client.reloadBasemapCount)
            assertEquals(1L, f.notifier.revision.value)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadCompleteRegistersInstalledDirectory() = runTest(mainDispatcherRule.dispatcher) {
        val server = startServer()
        try {
            val f = fixture(server)
            val listener = f.viewModel.createDownloadListener()

            listener.onComplete("basemap", "/data/maps/basemap")

            assertEquals(listOf("/data/maps/basemap"), f.client.basemapLookupDirectories)
            assertEquals(1, f.client.reloadBasemapCount)
            assertEquals(1L, f.notifier.revision.value)
            assertTrue(!f.viewModel.uiState.value.isDownloading)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadErrorDoesNotReloadOrNotify() = runTest(mainDispatcherRule.dispatcher) {
        val server = startServer()
        try {
            val f = fixture(server)
            val listener = f.viewModel.createDownloadListener()

            listener.onError("basemap", "boom")

            assertTrue(f.client.basemapLookupDirectories.isEmpty())
            assertEquals(0, f.client.reloadBasemapCount)
            assertEquals(0L, f.notifier.revision.value)
            assertTrue(!f.viewModel.uiState.value.isDownloading)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun deleteUnloadsBasemapReloadsAndNotifies() = runTest(mainDispatcherRule.dispatcher) {
        val server = startServer()
        try {
            val f = fixture(server, withInstalledBasemap = true)
            assertEquals("initial state must be Installed", 0L, f.notifier.revision.value)

            f.viewModel.delete()
            advanceUntilIdle()
            withContext(Dispatchers.Default) { f.notifier.revision.first { it == 1L } }

            assertEquals(listOf(""), f.client.basemapLookupDirectories)
            assertEquals(1, f.client.reloadBasemapCount)
            assertEquals(1L, f.notifier.revision.value)
        } finally {
            server.stop(0)
        }
    }
}
