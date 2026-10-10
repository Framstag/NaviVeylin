package com.naviveylin.core.mapsource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * One data file of a repository database, as the database's metadata (`db.json`) declares it.
 *
 * A file whose metadata omits its size or its CRC-32 cannot be verified; the download planner
 * refuses such an inventory rather than installing a file it could not check (spec
 * `map-repository-source` — "Download follows the metadata's file inventory and verifies every
 * file"). The file stays listed here so the refusal can name it.
 */
data class DatabaseFile(
    /** File name relative to the database directory, e.g. `map.lib`. */
    val name: String,
    /** Declared size in bytes, or null when the metadata omits it. */
    val sizeBytes: Long?,
    /** Declared CRC-32 (IEEE 802.3, as `java.util.zip.CRC32` computes it), or null when omitted. */
    val crc32: Long?
) {

    /** True when this file can be checked against its declared size and checksum. */
    val isVerifiable: Boolean
        get() = sizeBytes != null && crc32 != null
}

/**
 * A repository database's metadata: everything needed to verify and install it.
 *
 * Repository format: `Documentation/MapRepository.md` §1.3 (the file is written by the import tool
 * and copied verbatim to the server).
 */
data class DatabaseMetadata(
    /** Database format version of this database. */
    val typeConfigVersion: Int,
    /** Creation timestamp as the metadata states it, or null when absent. */
    val generatedAt: String?,
    /** The data files, in the order the metadata lists them. */
    val files: List<DatabaseFile>
) {

    /** Total declared size of all files in bytes. */
    val totalSizeBytes: Long
        get() = files.sumOf { it.sizeBytes ?: 0L }
}

/** Outcome of reading a database's metadata. */
sealed interface DatabaseMetadataResult {

    /** The metadata was read and names at least one data file. */
    data class Loaded(val metadata: DatabaseMetadata) : DatabaseMetadataResult

    /** The metadata could not be used; [failure] says why. */
    data class Unusable(val failure: RepositoryFailure) : DatabaseMetadataResult
}

/**
 * Reads a repository database's metadata and checks it against the version this client reads.
 *
 * @see DatabaseMetadata
 */
object DatabaseMetadataParser {

    /** Schema version of the database metadata this client reads. */
    const val SUPPORTED_SCHEMA = 1

    /** Metadata file name inside a database's version slot. */
    const val METADATA_FILE_NAME = "db.json"

    /** File name of the database's type configuration, which a complete install writes last. */
    const val TYPE_CONFIGURATION_FILE_NAME = "types.dat"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param document the metadata document as served by the repository
     * @param expectedVersion the database format version this client reads
     * @return the metadata, or the reason it is unusable
     */
    fun parse(document: String, expectedVersion: Int): DatabaseMetadataResult {
        val element = runCatching { json.parseToJsonElement(document) }.getOrNull()
            ?: return DatabaseMetadataResult.Unusable(RepositoryFailure.MalformedDocument)
        val documentObject = element as? JsonObject
            ?: return DatabaseMetadataResult.Unusable(RepositoryFailure.WrongDocumentKind)
        val schema = (documentObject["schema"] as? JsonPrimitive)?.intOrNull
            ?: return DatabaseMetadataResult.Unusable(RepositoryFailure.WrongDocumentKind)
        if (schema != SUPPORTED_SCHEMA) {
            return DatabaseMetadataResult.Unusable(RepositoryFailure.UnsupportedSchema(schema))
        }
        val typeConfigVersion = (documentObject["typeConfigVersion"] as? JsonPrimitive)?.intOrNull
            ?: return DatabaseMetadataResult.Unusable(RepositoryFailure.WrongDocumentKind)
        if (typeConfigVersion != expectedVersion) {
            return DatabaseMetadataResult.Unusable(
                RepositoryFailure.DatabaseVersionMismatch(typeConfigVersion)
            )
        }

        val wire = runCatching { json.decodeFromString<MetadataDocument>(document) }.getOrNull()
            ?: return DatabaseMetadataResult.Unusable(RepositoryFailure.MalformedDocument)

        val files = wire.output.files.map { (name, entry) ->
            DatabaseFile(name = name, sizeBytes = entry.size, crc32 = entry.crc32)
        }
        if (files.isEmpty()) {
            return DatabaseMetadataResult.Unusable(RepositoryFailure.NoFilesPublished)
        }

        return DatabaseMetadataResult.Loaded(
            DatabaseMetadata(
                typeConfigVersion = typeConfigVersion,
                generatedAt = wire.generatedAt,
                files = files
            )
        )
    }

    @Serializable
    private data class MetadataDocument(
        val schema: Int,
        @SerialName("typeConfigVersion") val typeConfigVersion: Int? = null,
        @SerialName("generatedAt") val generatedAt: String? = null,
        val output: MetadataOutput = MetadataOutput()
    )

    @Serializable
    private data class MetadataOutput(
        val files: Map<String, FileEntry> = emptyMap()
    )

    @Serializable
    private data class FileEntry(
        val size: Long? = null,
        @SerialName("crc32") val crc32: Long? = null
    )
}
