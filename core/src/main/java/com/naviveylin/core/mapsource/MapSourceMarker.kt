package com.naviveylin.core.mapsource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * What an installed directory records about the source it came from.
 *
 * @param sourceKind the family of the source that installed it
 * @param baseUrl the repository's base URL, or null for the built-in provider
 * @param databaseVersion the installed database format version, or null when unknown
 */
data class MapSourceRecord(
    val sourceKind: MapSourceKind,
    val baseUrl: String?,
    val databaseVersion: Int?
) {

    /** @return the source identity this record describes */
    fun toSource(): MapSource =
        if (sourceKind == MapSourceKind.REPOSITORY && !baseUrl.isNullOrBlank()) {
            MapSource.repository(baseUrl)
        } else {
            MapSource.BuiltInProvider
        }

    companion object {

        /** The record a directory without a readable marker is attributed to. */
        val BuiltInProvider = MapSourceRecord(MapSourceKind.BUILT_IN_PROVIDER, null, null)
    }
}

/** Outcome of reading a directory's source marker. */
sealed interface SourceMarkerRead {

    /** The directory carries a readable marker. */
    data class Present(val record: MapSourceRecord) : SourceMarkerRead

    /** The directory carries no marker: it was installed before this change, or by a tool that does not write one. */
    data object Absent : SourceMarkerRead

    /** The directory carries a marker that could not be read; it is treated as the built-in provider. */
    data object Unreadable : SourceMarkerRead
}

/**
 * Reads and writes the marker an installed directory carries, so an installed map's source is
 * answerable offline and a source switch can delete exactly the other source's data (spec
 * `map-source-selection` — "Installed map data records the source it came from"; design D4).
 *
 * The marker is one small JSON file inside the directory, so it survives a settings reset and travels
 * with a copied directory.
 */
object MapSourceMarker {

    /** Marker file name inside an installed map or basemap directory. */
    const val FILE_NAME = ".source.json"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Write the marker into [directory], creating the directory when needed.
     *
     * Non-fatal by design: a directory whose marker cannot be written is still installed, and the
     * reader attributes it to the built-in provider (see [sourceOf]), so an install never fails over
     * its provenance record.
     *
     * @param directory the installed map or basemap directory
     * @param record what to record about the source
     * @return true when the marker was written
     */
    fun write(directory: Path, record: MapSourceRecord): Boolean {
        val wire = MarkerDocument(
            schema = SCHEMA,
            sourceKind = record.sourceKind.name,
            baseUrl = record.baseUrl,
            databaseVersion = record.databaseVersion
        )
        return runCatching {
            Files.createDirectories(directory)
            Files.write(directory.resolve(FILE_NAME), json.encodeToString(MarkerDocument.serializer(), wire).toByteArray())
        }.isSuccess
    }

    /**
     * Read [directory]'s marker.
     *
     * @return the record, or whether the marker is absent or unreadable
     */
    fun read(directory: Path): SourceMarkerRead {
        val file = directory.resolve(FILE_NAME)
        if (!Files.isRegularFile(file)) {
            return SourceMarkerRead.Absent
        }
        val wire = runCatching {
            json.decodeFromString(
                MarkerDocument.serializer(),
                Files.readAllBytes(file).toString(Charsets.UTF_8)
            )
        }.getOrNull() ?: return SourceMarkerRead.Unreadable
        val kind = runCatching { MapSourceKind.valueOf(wire.sourceKind) }.getOrNull()
            ?: return SourceMarkerRead.Unreadable
        return SourceMarkerRead.Present(
            MapSourceRecord(kind, wire.baseUrl, wire.databaseVersion)
        )
    }

    /**
     * The source [directory] belongs to: its marker when it has a readable one, otherwise the
     * built-in provider (the rule that attributes everything installed before this change).
     *
     * @param directory the installed map or basemap directory
     * @return the record to attribute the directory to
     */
    fun sourceOf(directory: Path): MapSourceRecord =
        when (val read = read(directory)) {
            is SourceMarkerRead.Present -> read.record
            SourceMarkerRead.Absent, SourceMarkerRead.Unreadable -> MapSourceRecord.BuiltInProvider
        }

    private const val SCHEMA = 1

    @Serializable
    private data class MarkerDocument(
        val schema: Int,
        @SerialName("sourceKind") val sourceKind: String,
        @SerialName("baseUrl") val baseUrl: String? = null,
        @SerialName("databaseVersion") val databaseVersion: Int? = null
    )
}
