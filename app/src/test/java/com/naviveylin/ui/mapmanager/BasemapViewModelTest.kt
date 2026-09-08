package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.test.MainDispatcherRule
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private class Fixture(
        val viewModel: BasemapViewModel,
        val client: FakeOSMScoutClient,
        val notifier: BasemapReloadNotifier
    )

    private fun fixture(server: HttpServer, withInstalledBasemap: Boolean = false): Fixture {
        val mapsDir = Files.createTempDirectory("basemap-vm-test")
        if (withInstalledBasemap) {
            val basemapDir = mapsDir.resolve("basemap")
            Files.createDirectories(basemapDir)
            Files.write(basemapDir.resolve("water.idx"), "data".toByteArray())
        }
        val manager = BasemapManager(
            MapProvider("test", "http://127.0.0.1:${server.address.port}", ""),
            mapsDir
        )
        val client = FakeOSMScoutClient()
        val notifier = BasemapReloadNotifier()
        val vm = BasemapViewModel(
            ApplicationProvider.getApplicationContext() as Application,
            manager,
            client,
            notifier
        )
        return Fixture(vm, client, notifier)
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
            await { f.client.reloadBasemapCount >= 1 }

            assertEquals(listOf(""), f.client.basemapLookupDirectories)
            assertEquals(1, f.client.reloadBasemapCount)
            assertEquals(1L, f.notifier.revision.value)
        } finally {
            server.stop(0)
        }
    }
}
