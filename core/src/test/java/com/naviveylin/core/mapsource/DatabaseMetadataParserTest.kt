package com.naviveylin.core.mapsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DatabaseMetadataParser].
 *
 * Spec: map-repository-source — "Download follows the metadata's file inventory and verifies every
 * file" (the metadata is the inventory the download must follow).
 */
class DatabaseMetadataParserTest {

    private val metadata = """
        {
          "schema": 1,
          "typeConfigVersion": 27,
          "generatedAt": "2026-09-07T16:35:44Z",
          "source": {
            "url": "https://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf",
            "md5": "146f59bf3b42630f89572160de6260bb"
          },
          "output": {
            "boundingBox": {"minLon": 13.4, "minLat": 52.5, "maxLon": 13.43, "maxLat": 52.53},
            "files": {
              "map.lib": {"size": 12345678, "crc32": 2912136757},
              "nodes.dat": {"size": 987654321, "crc32": 316299821}
            }
          },
          "stats": {"types": 671}
        }
    """.trimIndent()

    @Test
    fun inventoryIsRead() {
        val result = DatabaseMetadataParser.parse(metadata, expectedVersion = 27)

        assertTrue(result is DatabaseMetadataResult.Loaded)
        val loaded = (result as DatabaseMetadataResult.Loaded).metadata
        assertEquals(27, loaded.typeConfigVersion)
        assertEquals("2026-09-07T16:35:44Z", loaded.generatedAt)
        assertEquals(listOf("map.lib", "nodes.dat"), loaded.files.map { it.name })
        assertEquals(12345678L, loaded.files.first().sizeBytes)
        assertEquals(2912136757L, loaded.files.first().crc32)
        assertEquals(12345678L + 987654321L, loaded.totalSizeBytes)
        assertTrue("every file the metadata names can be checked", loaded.files.all { it.isVerifiable })
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val withExtra = metadata.replace(
            "\"stats\": {\"types\": 671}",
            "\"stats\": {\"types\": 671}, \"import\": {\"tool\": \"Import\", \"version\": \"1.1.1\"}"
        )

        assertTrue(DatabaseMetadataParser.parse(withExtra, 27) is DatabaseMetadataResult.Loaded)
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val result = DatabaseMetadataParser.parse(metadata.replace("\"schema\": 1", "\"schema\": 2"), 27)

        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.UnsupportedSchema(2)),
            result
        )
    }

    @Test
    fun rejectsMetadataNamingAnotherVersion() {
        val result = DatabaseMetadataParser.parse(metadata, expectedVersion = 26)

        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.DatabaseVersionMismatch(27)),
            result
        )
    }

    @Test
    fun rejectsMetadataWithoutFiles() {
        val withoutFiles = metadata.replace(
            "\"files\": {\n      \"map.lib\": {\"size\": 12345678, \"crc32\": 2912136757},\n" +
                "      \"nodes.dat\": {\"size\": 987654321, \"crc32\": 316299821}\n    }",
            "\"files\": {}"
        )

        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.NoFilesPublished),
            DatabaseMetadataParser.parse(withoutFiles, 27)
        )
    }

    @Test
    fun missingFileEntryKeepsOtherFilesListedAndIsNotVerifiable() {
        val oneEntryIncomplete = metadata.replace(
            "\"nodes.dat\": {\"size\": 987654321, \"crc32\": 316299821}",
            "\"nodes.dat\": {\"size\": 987654321}"
        )

        val loaded = DatabaseMetadataParser.parse(oneEntryIncomplete, 27) as DatabaseMetadataResult.Loaded

        assertEquals(listOf("map.lib", "nodes.dat"), loaded.metadata.files.map { it.name })
        assertTrue(loaded.metadata.files.first().isVerifiable)
        assertFalse(loaded.metadata.files.last().isVerifiable)
        // The refusal that follows from an uncheckable file is the download planner's, and its own case
        // asserts it (`DownloadPlanTest.planRefusesAnUnverifiableInventory`).
    }

    @Test
    fun rejectsMalformedAndWrongDocuments() {
        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.MalformedDocument),
            DatabaseMetadataParser.parse("{\"schema\": 1,", 27)
        )
        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.WrongDocumentKind),
            DatabaseMetadataParser.parse("[]", 27)
        )
        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.WrongDocumentKind),
            DatabaseMetadataParser.parse("""{"schema": 1}""", 27)
        )
    }
}
