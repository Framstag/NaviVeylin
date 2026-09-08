package com.naviveylin.ui.mapmanager

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * Compose UI tests for the basemap section states:
 * available (download button), unavailable (subtle status), installed (delete).
 * Uses a local loopback HTTP server — no external network.
 * NOTE: instantiates FakeOSMScoutClient → must run under RobolectricTestRunner
 * with the DEFAULT sandbox (no @Config overrides), per the classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class BasemapSectionComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private val sampleListing =
        "<html><body><table>" +
        "<tr><td><a href=\"BaseMap-2026-02-23.tar.gz\">BaseMap-2026-02-23.tar.gz</a></td>" +
        "<td align=\"right\">2026-02-24 00:16</td><td align=\"right\">39M</td></tr>" +
        "</table></body></html>"

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/basemap/") { exchange -> handler(exchange) }
        server.start()
        return server
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String = "") {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, if (status == 200) bytes.size.toLong() else -1)
        if (status == 200) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.close()
        }
    }

    private fun manager(port: Int, mapsDir: java.nio.file.Path): BasemapManager =
        BasemapManager(MapProvider("test", "http://127.0.0.1:$port", ""), mapsDir)

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun availableShowsDownloadButton() {
        val server = startServer { ex -> respond(ex, 200, sampleListing) }
        try {
            val mapsDir = Files.createTempDirectory("basemap-test")
            val vm = BasemapViewModel(
                context() as Application,
                manager(server.address.port, mapsDir),
                FakeOSMScoutClient(),
                BasemapReloadNotifier()
            )
            composeRule.setContent { BasemapSection(viewModel = vm) }

            waitForText("Download Basemap")
            composeRule.onNodeWithText("Download Basemap").assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun unavailableShowsSubtleStatus() {
        val server = startServer { ex -> respond(ex, 404) }
        try {
            val mapsDir = Files.createTempDirectory("basemap-test")
            val vm = BasemapViewModel(
                context() as Application,
                manager(server.address.port, mapsDir),
                FakeOSMScoutClient(),
                BasemapReloadNotifier()
            )
            composeRule.setContent { BasemapSection(viewModel = vm) }

            waitForText("Basemap unavailable")
            composeRule.onNodeWithText("Basemap unavailable").assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun installedShowsDeleteButton() {
        val server = startServer { ex -> respond(ex, 404) }
        try {
            val mapsDir = Files.createTempDirectory("basemap-test")
            val basemapDir = mapsDir.resolve("basemap")
            Files.createDirectories(basemapDir)
            Files.write(basemapDir.resolve("water.idx"), "data".toByteArray())

            val vm = BasemapViewModel(
                context() as Application,
                manager(server.address.port, mapsDir),
                FakeOSMScoutClient(),
                BasemapReloadNotifier()
            )
            composeRule.setContent { BasemapSection(viewModel = vm) }

            waitForText("Delete")
            composeRule.onNodeWithText("Installed", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Delete").assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadingShowsCompactStatus() {
        // Mocked ViewModel: the real download path would start a Hilt
        // foreground service whose component build calls native
        // OSMScoutClientBuilder.build() and crashes on the host stub .so.
        val bm = mock<BasemapViewModel>()
        whenever(bm.uiState).thenReturn(
            MutableStateFlow(BasemapUiState(isDownloading = true, progress = 42))
        )
        composeRule.setContent { BasemapSection(viewModel = bm) }

        waitForText("Downloading world basemap…")
        composeRule.onNodeWithText("Downloading world basemap…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        // Compact status: no percentage — full progress lives in the
        // active-downloads section (basemap-ui spec).
        composeRule.onAllNodesWithText("42%").assertCountEquals(0)
    }
}
