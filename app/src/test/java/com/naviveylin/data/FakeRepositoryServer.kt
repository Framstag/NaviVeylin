package com.naviveylin.data

import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryFetcher
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.core.mapsource.TextFetch
import kotlinx.coroutines.CompletableDeferred

/**
 * A libosmscout mapgen repository served from memory, so the update check is tested without a server.
 *
 * Documents are keyed by URL; a URL with no document answers not-found, which is what a version slot the
 * repository does not publish looks like. Every requested URL is recorded, so a case can assert which
 * requests a check issued — including that it issued none.
 *
 * @param baseUrl the repository's base URL
 * @param version the database format version this client reads
 */
class FakeRepositoryServer(
    private val baseUrl: String,
    private val version: Int
) : RepositoryFetcher {

    /** Every URL requested through [text] or [download], in request order. */
    val requested = mutableListOf<String>()

    private val texts = mutableMapOf<String, String>()
    private val bodies = mutableMapOf<String, ByteArray>()

    /** When set, [text] of this URL fails with this failure instead of answering. */
    var failTextOf: Pair<String, RepositoryFailure>? = null

    /** When set, every [text] waits for it before answering, so a case can hold a request in flight. */
    var gate: CompletableDeferred<Unit>? = null

    /** When set, the next [download] of this URL fails with this failure instead of serving a body. */
    var failDownloadOf: Pair<String, RepositoryFailure>? = null

    /** The repository's region index URL. */
    val indexUrl: String = RepositoryUrlPlanner.regionIndexUrl(baseUrl)

    /** The repository's basemap availability manifest URL. */
    val basemapManifestUrl: String = RepositoryUrlPlanner.basemapManifestUrl(baseUrl)

    /** Serve a region index naming [leaves]. */
    fun serveIndex(leaves: List<List<String>>) {
        texts[indexUrl] = indexDocument(leaves)
    }

    /**
     * Serve one leaf's version slot: its metadata, the map file it names, and the type configuration that
     * marks a complete installation (the last file a real install writes).
     */
    fun serveSlot(
        idPath: List<String>,
        generatedAt: String = "2026-09-09T08:00:00Z",
        mapLibBody: ByteArray = ByteArray(4096) { 7 },
        typesDatBody: ByteArray = ByteArray(0)
    ) {
        val crc = java.util.zip.CRC32()
        val mapCrc = crc.apply { update(mapLibBody, 0, mapLibBody.size) }.value
        val typesCrc = java.util.zip.CRC32().apply { update(typesDatBody, 0, typesDatBody.size) }.value
        texts[RepositoryUrlPlanner.metadataUrl(baseUrl, idPath, version)] =
            """{"schema":1,"typeConfigVersion":$version,"generatedAt":"$generatedAt",""" +
                """"output":{"files":{"map.lib":{"size":${mapLibBody.size},"crc32":$mapCrc},""" +
                """"types.dat":{"size":${typesDatBody.size},"crc32":$typesCrc}}}}"""
        bodies[RepositoryUrlPlanner.fileUrl(baseUrl, idPath, version, "map.lib")] = mapLibBody
        bodies[RepositoryUrlPlanner.fileUrl(baseUrl, idPath, version, "types.dat")] = typesDatBody
    }

    /** Serve the basemap availability manifest. */
    fun serveBasemapManifest(document: String) {
        texts[basemapManifestUrl] = document
    }

    override suspend fun text(url: String): TextFetch {
        requested += url
        gate?.await()
        failTextOf?.takeIf { it.first == url }?.let { return TextFetch.Failed(it.second) }
        val document = texts[url] ?: return TextFetch.Failed(RepositoryFailure.HttpStatus(404))
        return TextFetch.Loaded(document)
    }

    override suspend fun download(url: String, onBytes: (ByteArray, Int) -> Unit): RepositoryFailure? {
        requested += url
        failDownloadOf?.takeIf { it.first == url }?.let { return it.second }
        val body = bodies[url] ?: return RepositoryFailure.HttpStatus(404)
        onBytes(body, body.size)
        return null
    }

    /** A region index that nests every id path of [leaves]. */
    private fun indexDocument(leaves: List<List<String>>): String =
        """{"schema":1,"regions":[${renderNodes(leaves)}]}"""

    private fun renderNodes(paths: List<List<String>>): String =
        paths.groupBy { it.first() }
            .map { (id, group) ->
                val children = group.map { it.drop(1) }.filter { it.isNotEmpty() }
                val names = """{"en":"${id.replaceFirstChar { it.uppercase() }}","de":"$id"}"""
                if (children.isEmpty()) {
                    """{"id":"$id","names":$names}"""
                } else {
                    """{"id":"$id","names":$names,"children":[${renderNodes(children)}]}"""
                }
            }
            .joinToString(",")
}
