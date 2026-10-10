package com.naviveylin.core.mapsource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * One basemap version a repository publishes.
 *
 * @param typeConfigVersion the database format version of that basemap slot, which is also its slot
 *        directory name (`v<typeConfigVersion>`)
 * @param changedAt the change time the manifest states for it, or null when absent
 */
data class BasemapVersion(
    val typeConfigVersion: Int,
    val changedAt: String?
)

/** What a repository offers as a basemap. */
sealed interface BasemapAvailability {

    /** A basemap version this client can read is published. */
    data class Available(val version: BasemapVersion) : BasemapAvailability

    /** The repository publishes no basemap, or none this client can read (not an error — the basemap is optional). */
    data object Unavailable : BasemapAvailability

    /** The manifest could not be read for a reason worth reporting (transport, malformed document). */
    data class Failed(val failure: RepositoryFailure) : BasemapAvailability
}

/** Outcome of reading a basemap availability manifest. */
sealed interface BasemapManifestResult {

    /** The versions the manifest names, newest first, restricted to what this client can read. */
    data class Loaded(val versions: List<BasemapVersion>) : BasemapManifestResult

    /** The manifest could not be used; [failure] says why. */
    data class Unusable(val failure: RepositoryFailure) : BasemapManifestResult
}

/**
 * Reads a repository's basemap availability manifest (`basemap/index.json`).
 *
 * Manifest format (written by the mapgen basemap step):
 * ```json
 * {"schema": 1, "versions": [{"typeConfigVersion": 27, "changedAt": "2026-09-07T10:00:00Z"}]}
 * ```
 * A version is readable when its database format version is not newer than this client's, the rule
 * the library's own repository client uses; the newest readable one is offered
 * (spec `map-repository-source` — "The repository source installs its own basemap version slot").
 */
object BasemapManifestParser {

    /** Schema version of the basemap availability manifest this client reads. */
    const val SUPPORTED_SCHEMA = 1

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param document the manifest document as served by the repository
     * @param readableDatabaseVersion the newest database format version this client can read
     * @return the readable versions, newest first, or the reason the manifest is unusable
     */
    fun parse(document: String, readableDatabaseVersion: Int): BasemapManifestResult {
        val element = runCatching { json.parseToJsonElement(document) }.getOrNull()
            ?: return BasemapManifestResult.Unusable(RepositoryFailure.MalformedDocument)
        val documentObject = element as? JsonObject
            ?: return BasemapManifestResult.Unusable(RepositoryFailure.WrongDocumentKind)
        val schema = (documentObject["schema"] as? JsonPrimitive)?.intOrNull
            ?: return BasemapManifestResult.Unusable(RepositoryFailure.WrongDocumentKind)
        if (schema != SUPPORTED_SCHEMA) {
            return BasemapManifestResult.Unusable(RepositoryFailure.UnsupportedSchema(schema))
        }

        val wire = runCatching { json.decodeFromString<ManifestDocument>(document) }.getOrNull()
            ?: return BasemapManifestResult.Unusable(RepositoryFailure.MalformedDocument)

        return BasemapManifestResult.Loaded(
            wire.versions
                .filter { it.typeConfigVersion <= readableDatabaseVersion }
                .map { BasemapVersion(it.typeConfigVersion, it.changedAt) }
                .sortedByDescending { it.typeConfigVersion }
        )
    }

    /**
     * The basemap version to offer: the newest readable one.
     *
     * @return the version to install, or null when the manifest names none this client can read
     */
    fun newestReadable(document: String, readableDatabaseVersion: Int): BasemapVersion? =
        (parse(document, readableDatabaseVersion) as? BasemapManifestResult.Loaded)
            ?.versions
            ?.firstOrNull()

    @Serializable
    private data class ManifestDocument(
        val schema: Int,
        val versions: List<ManifestVersion> = emptyList()
    )

    @Serializable
    private data class ManifestVersion(
        @SerialName("typeConfigVersion") val typeConfigVersion: Int,
        @SerialName("changedAt") val changedAt: String? = null
    )
}
