package com.naviveylin.data

import android.security.NetworkSecurityPolicy
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryFetcher
import com.naviveylin.core.mapsource.RepositoryTransferCancelled
import com.naviveylin.core.mapsource.TextFetch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's implementation of the repository protocol's network seam, over [HttpURLConnection].
 *
 * [HttpURLConnection] rather than `java.net.http.HttpClient`, so the repository path needs no
 * desugaring on any supported Android version (spec `map-download-infrastructure` — "HttpURLConnection
 * for HTTP").
 *
 * Every request runs on [ioDispatcher], so a caller never blocks the main thread on it
 * (`guidelines/Design.md` §4). A failed request is logged with its URL and reason, never with a
 * coordinate (`guidelines/Logging.md` §2).
 *
 * Two failures are told apart from a transport error on purpose, because both are decided before a
 * byte is sent and both point at the request rather than at the network: a plain-HTTP request the
 * platform's cleartext policy refuses ([RepositoryFailure.CleartextBlocked]) and a base URL that is
 * not an HTTP URL ([RepositoryFailure.MalformedUrl]) — one that cannot be parsed, or one whose scheme
 * is neither `http` nor `https`.
 */
@Singleton
class HttpUrlFetcher @Inject constructor() : RepositoryFetcher {

    /**
     * Dispatcher the blocking request runs on. A test may replace it; the default is the IO dispatcher
     * (the seam pattern `SettingsStorage` and `ViewportStorage` use in this module).
     */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Whether the platform permits a plain-HTTP request to [host]. A test may replace it; the default
     * reads `NetworkSecurityPolicy`, which exists only on Android.
     *
     * Consulted for `http` URLs only: an `https` URL's transport is decided by TLS validation, which
     * this policy neither grants nor withholds.
     */
    internal var cleartextPermitted: (String) -> Boolean = { host ->
        NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)
    }

    override suspend fun text(url: String): TextFetch = withContext(ioDispatcher) {
        policyRefusal(url)?.let { return@withContext TextFetch.Failed(it) }
        val connection = try {
            openConnection(url)
        } catch (unusable: UnusableUrl) {
            return@withContext TextFetch.Failed(unusableUrlFailure(url, unusable))
        } catch (error: Exception) {
            return@withContext TextFetch.Failed(failureFor(url, error))
        }
        try {
            val status = connection.responseCode
            if (status / 100 != 2) {
                report(url, "HTTP $status")
                TextFetch.Failed(RepositoryFailure.HttpStatus(status))
            } else {
                TextFetch.Loaded(
                    connection.inputStream.use(InputStream::readBytes).toString(Charsets.UTF_8)
                )
            }
        } catch (error: Exception) {
            TextFetch.Failed(failureFor(url, error))
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(url: String, onBytes: (ByteArray, Int) -> Unit): RepositoryFailure? =
        withContext(ioDispatcher) {
            policyRefusal(url)?.let { return@withContext it }
            val connection = try {
                openConnection(url)
            } catch (unusable: UnusableUrl) {
                return@withContext unusableUrlFailure(url, unusable)
            } catch (error: Exception) {
                return@withContext failureFor(url, error)
            }
            try {
                val status = connection.responseCode
                if (status / 100 != 2) {
                    report(url, "HTTP $status")
                    return@withContext RepositoryFailure.HttpStatus(status)
                }
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        // Throws RepositoryTransferCancelled when the caller cancels: it must pass
                        // through as a cancellation, not as a transfer failure.
                        onBytes(buffer, read)
                    }
                }
                null
            } catch (cancelled: RepositoryTransferCancelled) {
                throw cancelled
            } catch (error: Exception) {
                failureFor(url, error)
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Open a GET connection with the timeouts the download path uses.
     *
     * A URL whose scheme is neither `http` nor `https` is rejected as unusable here: no such URL can
     * become an [HttpURLConnection], so the cast below would otherwise throw `ClassCastException` and
     * reach the caller as a transport failure.
     */
    private fun openConnection(url: String): HttpURLConnection {
        val parsed = try {
            URI.create(url).toURL()
        } catch (error: Exception) {
            throw UnusableUrl(error)
        }
        if (!isHttpScheme(parsed.protocol)) {
            throw UnusableUrl(IllegalArgumentException("unsupported scheme ${parsed.protocol}"))
        }
        val connection = parsed.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return connection
    }

    /** Whether [protocol] is one of the two schemes an HTTP request can carry. */
    private fun isHttpScheme(protocol: String): Boolean =
        protocol.equals("http", ignoreCase = true) || protocol.equals("https", ignoreCase = true)

    /**
     * A plain-HTTP request the platform's cleartext policy refuses, or null when it permits it or the
     * request is not plain HTTP.
     *
     * Asked before connecting, so a refusal is reported as the policy decision it is rather than as
     * whatever exception the platform throws, and no request leaves the device.
     */
    private fun policyRefusal(url: String): RepositoryFailure? {
        val parsed = try {
            URI.create(url)
        } catch (error: Exception) {
            return null // an unparseable URL is reported as such by openConnection
        }
        if (!parsed.scheme.equals("http", ignoreCase = true)) return null
        val host = parsed.host ?: return null
        return if (cleartextPermitted(host)) null else RepositoryFailure.CleartextBlocked
    }

    /** Log and classify a URL the parse step rejected. */
    private fun unusableUrlFailure(url: String, unusable: UnusableUrl): RepositoryFailure {
        report(url, unusable.cause?.message ?: "unusable URL")
        return RepositoryFailure.MalformedUrl
    }

    /** Log and classify a thrown transport error. */
    private fun failureFor(url: String, error: Exception): RepositoryFailure {
        report(url, error.message ?: error.javaClass.simpleName)
        return RepositoryFailure.TransportFailed(error.message)
    }

    /** The parse step's failure: the URL's fault, never the transport's. */
    private class UnusableUrl(cause: Exception) : Exception(cause)

    /** Log a failed request with the URL and the reason — never a coordinate (`Logging.md` §2). */
    private fun report(url: String, reason: String) {
        DiagnosticsLog.log(TAG, "repository request failed url=$url reason=$reason")
    }

    private companion object {
        private const val TAG = "HttpUrlFetcher"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 8 * 1024
    }
}
