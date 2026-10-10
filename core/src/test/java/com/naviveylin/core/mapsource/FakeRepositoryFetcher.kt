package com.naviveylin.core.mapsource

import java.util.zip.CRC32

/**
 * A [RepositoryFetcher] served from memory, so the repository protocol is tested without a server.
 *
 * Bodies are keyed by URL; a URL with no body answers not-found. Every requested URL is recorded, so a
 * test can assert which files were fetched and in which order.
 */
class FakeRepositoryFetcher(
    bodies: Map<String, ByteArray> = emptyMap(),
    texts: Map<String, String> = emptyMap()
) : RepositoryFetcher {

    private val bodies = bodies.toMutableMap()
    private val texts = texts.toMutableMap()

    /** Every URL requested through [download] or [text], in request order. */
    val requested = mutableListOf<String>()

    /** When set, the next [download] of this URL fails with this failure instead of serving a body. */
    var failDownloadOf: Pair<String, RepositoryFailure>? = null

    /** When set, [text] of this URL fails with this failure. */
    var failTextOf: Pair<String, RepositoryFailure>? = null

    /** How many chunks a served body is split into (2 by default, so multi-chunk paths are covered). */
    var chunksPerBody: Int = 2

    /** Register [body] for [url]. */
    fun serve(url: String, body: ByteArray) {
        bodies[url] = body
    }

    /** Register [text] for [url]. */
    fun serveText(url: String, text: String) {
        texts[url] = text
    }

    override suspend fun text(url: String): TextFetch {
        requested += url
        failTextOf?.takeIf { it.first == url }?.let { return TextFetch.Failed(it.second) }
        val body = texts[url] ?: return TextFetch.Failed(RepositoryFailure.HttpStatus(404))
        return TextFetch.Loaded(body)
    }

    override suspend fun download(url: String, onBytes: (ByteArray, Int) -> Unit): RepositoryFailure? {
        requested += url
        failDownloadOf?.takeIf { it.first == url }?.let { return it.second }
        val body = bodies[url] ?: return RepositoryFailure.HttpStatus(404)
        val chunkSize = maxOf(1, (body.size + chunksPerBody - 1) / chunksPerBody)
        var offset = 0
        while (offset < body.size) {
            val length = minOf(chunkSize, body.size - offset)
            onBytes(body.copyOfRange(offset, offset + length), length)
            offset += length
        }
        return null
    }

    /** [body]'s CRC-32, so a fixture can declare the checksum a real metadata document would. */
    companion object {
        fun crc32Of(body: ByteArray): Long {
            val crc = CRC32()
            crc.update(body, 0, body.size)
            return crc.value
        }
    }
}
