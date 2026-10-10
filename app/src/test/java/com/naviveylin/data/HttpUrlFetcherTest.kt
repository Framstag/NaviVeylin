package com.naviveylin.data

import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.TextFetch
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Unit tests for [HttpUrlFetcher] against a real local HTTP server (the JDK's own, so no test
 * dependency is added).
 *
 * The default Robolectric sandbox is required only because a failed request is logged through
 * `android.util.Log`; this class loads no native library, so no sandbox config is set
 * (`guidelines/Build.md` §6).
 *
 * Spec: map-download-infrastructure — "Repository requests use HttpURLConnection", "Shipped builds
 * permit cleartext repository transport", "A denied cleartext request reports the denial itself",
 * "A base URL that cannot be parsed is reported as an unusable URL", "A base URL with a non-HTTP scheme
 * is reported as an unusable URL"; for the outcome cases see
 * `map-source-selection` — "Test fails on transport or status".
 */
@RunWith(RobolectricTestRunner::class)
class HttpUrlFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = null
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun servesTextAndFileOverHttp() = runTest {
        val body = "repository document".toByteArray()
        respond("/names.json", 200, body)
        val fetcher = HttpUrlFetcher().apply { ioDispatcher = Dispatchers.Unconfined }

        val text = fetcher.text("$baseUrl/names.json")
        val received = StringBuilder()
        val failure = fetcher.download("$baseUrl/names.json") { buffer, length ->
            received.append(String(buffer, 0, length, StandardCharsets.UTF_8))
        }

        assertEquals(TextFetch.Loaded("repository document"), text)
        assertEquals(null, failure)
        assertEquals("repository document", received.toString())
    }

    @Test
    fun nonSuccessStatusIsReported() = runTest {
        respond("/names.json", 404, "missing".toByteArray())
        val fetcher = HttpUrlFetcher().apply { ioDispatcher = Dispatchers.Unconfined }

        assertEquals(
            TextFetch.Failed(RepositoryFailure.HttpStatus(404)),
            fetcher.text("$baseUrl/names.json")
        )
        assertEquals(
            RepositoryFailure.HttpStatus(404),
            fetcher.download("$baseUrl/names.json") { _, _ -> }
        )
    }

    @Test
    fun connectFailureIsReported() = runTest {
        // Nothing listens on this port: the connection attempt itself fails.
        val unreachable = "http://127.0.0.1:1/names.json"
        val fetcher = HttpUrlFetcher().apply { ioDispatcher = Dispatchers.Unconfined }

        val text = fetcher.text(unreachable)
        val download = fetcher.download(unreachable) { _, _ -> }

        assertTrue("a connect failure is a transport failure, not a status",
                   text is TextFetch.Failed &&
                       (text as TextFetch.Failed).failure is RepositoryFailure.TransportFailed)
        assertTrue(download is RepositoryFailure.TransportFailed)
    }

    @Test
    fun aMalformedUrlIsReportedInsteadOfThrown() = runTest {
        var probed: String? = null
        val fetcher = HttpUrlFetcher().apply {
            ioDispatcher = Dispatchers.Unconfined
            cleartextPermitted = { host -> probed = host; true }
        }

        val text = fetcher.text("not a url")
        val download = fetcher.download("not a url") { _, _ -> }

        // The URL's fault, not the network's: nothing was sent and nothing was probed.
        assertEquals(TextFetch.Failed(RepositoryFailure.MalformedUrl), text)
        assertEquals(RepositoryFailure.MalformedUrl, download)
        assertEquals(null, probed)
    }

    @Test
    fun aCleartextRefusalIsReportedAsAPolicyDecision() = runTest {
        // Port 1 refuses every connection, so a transport attempt would report TransportFailed:
        // seeing CleartextBlocked instead is what proves no request left the fetcher.
        val refused = "http://127.0.0.1:1/names.json"
        val probed = mutableListOf<String>()
        val fetcher = HttpUrlFetcher().apply {
            ioDispatcher = Dispatchers.Unconfined
            cleartextPermitted = { host -> probed += host; false }
        }

        val text = fetcher.text(refused)
        val download = fetcher.download(refused) { _, _ -> }

        assertEquals(TextFetch.Failed(RepositoryFailure.CleartextBlocked), text)
        assertEquals(RepositoryFailure.CleartextBlocked, download)
        assertEquals(listOf("127.0.0.1", "127.0.0.1"), probed)
    }

    @Test
    fun aPermittedCleartextRequestProceeds() = runTest {
        respond("/names.json", 200, "repository document".toByteArray())
        val fetcher = HttpUrlFetcher().apply {
            ioDispatcher = Dispatchers.Unconfined
            cleartextPermitted = { true }
        }

        assertEquals(TextFetch.Loaded("repository document"), fetcher.text("$baseUrl/names.json"))
    }

    @Test
    fun anHttpsRequestDoesNotConsultTheCleartextPolicy() = runTest {
        val probed = mutableListOf<String>()
        val fetcher = HttpUrlFetcher().apply {
            ioDispatcher = Dispatchers.Unconfined
            cleartextPermitted = { host -> probed += host; false }
        }

        val text = fetcher.text("https://127.0.0.1:1/names.json")

        // TLS decides an https request; the cleartext policy neither grants nor withholds it.
        assertTrue(text is TextFetch.Failed)
        assertTrue((text as TextFetch.Failed).failure is RepositoryFailure.TransportFailed)
        assertEquals(emptyList<String>(), probed)
    }

    @Test
    fun aNonHttpSchemeIsReportedAsUnusable() = runTest {
        var probed: String? = null
        val fetcher = HttpUrlFetcher().apply {
            ioDispatcher = Dispatchers.Unconfined
            cleartextPermitted = { host -> probed = host; true }
        }

        // Parses as a URI and as a URL, but its protocol is ftp: it can never become an
        // HttpURLConnection, so the request is the URL's fault, not the network's.
        val text = fetcher.text("ftp://127.0.0.1/names.json")
        val download = fetcher.download("ftp://127.0.0.1/names.json") { _, _ -> }

        assertEquals(TextFetch.Failed(RepositoryFailure.MalformedUrl), text)
        assertEquals(RepositoryFailure.MalformedUrl, download)
        assertEquals(null, probed)
    }

    private fun respond(path: String, status: Int, body: ByteArray) {
        server.createContext(path) { exchange: HttpExchange ->
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
