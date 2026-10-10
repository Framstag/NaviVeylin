package com.naviveylin.core.mapsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [RepositorySource]'s listing and metadata probe.
 *
 * Spec: map-repository-source — "Region index drives the available-maps tree" / "Database metadata is
 * fetched on demand per leaf" / "Leaf published for another database version".
 */
class RepositorySourceListerTest {

    private val baseUrl = "https://maps.example.org/repo"
    private val indexUrl = RepositoryUrlPlanner.regionIndexUrl(baseUrl)
    private val index = """
        {"schema": 1, "regions": [
          {"id": "europe", "names": {"en": "Europe", "de": "Europa"}, "children": [
            {"id": "berlin", "names": {"en": "Berlin", "de": "Berlin"}}
          ]}
        ]}
    """.trimIndent()

    @Test
    fun listsRegionsFromTheIndexUrl() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(indexUrl, index)

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).availableRegions("de")

        assertEquals(listOf(indexUrl), fetcher.requested)
        val loaded = result as RegionIndexResult.Loaded
        assertEquals("Europa", loaded.regions.single().displayName)
        assertEquals(1, loaded.leafCount)
    }

    @Test
    fun reportsATransportFailureInsteadOfAnEmptyTree() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.failTextOf = indexUrl to RepositoryFailure.HttpStatus(500)

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).availableRegions("en")

        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.HttpStatus(500)), result)
    }

    @Test
    fun reportsAnUnsupportedSchema() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(indexUrl, """{"schema": 7, "regions": []}""")

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).availableRegions("en")

        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.UnsupportedSchema(7)), result)
    }

    @Test
    fun probesTheLeavesOwnVersionSlot() = runTest {
        val version = 27
        val leafPath = listOf("europe", "berlin")
        val metadataUrl = RepositoryUrlPlanner.metadataUrl(baseUrl, leafPath, version)
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(
            metadataUrl,
            """{"schema": 1, "typeConfigVersion": $version, "output": {"files": {"map.lib": {"size": 4, "crc32": 1}}}}"""
        )

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).metadataOf(leafPath, version)

        assertEquals(listOf(metadataUrl), fetcher.requested)
        assertTrue(result is DatabaseMetadataResult.Loaded)
    }

    @Test
    fun aMissingSlotIsReportedAsNotPublished() = runTest {
        val version = 27
        val leafPath = listOf("europe", "berlin")
        val fetcher = FakeRepositoryFetcher()

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).metadataOf(leafPath, version)

        assertEquals(
            DatabaseMetadataResult.Unusable(RepositoryFailure.DatabaseNotPublished),
            result
        )
    }

    @Test
    fun plansTheDownloadFromTheSameDocumentItRead() = runTest {
        val version = 27
        val leafPath = listOf("europe", "berlin")
        val metadataUrl = RepositoryUrlPlanner.metadataUrl(baseUrl, leafPath, version)
        val document =
            """{"schema": 1, "typeConfigVersion": $version, "output": {"files": {"map.lib": {"size": 4, "crc32": 1}}}}"""
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(metadataUrl, document)

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl))
            .planDownload(leafPath, version)

        val planned = result as RepositoryDownloadPlanResult.Planned
        assertEquals(document, planned.metadataDocument)
        assertEquals(
            RepositoryUrlPlanner.fileUrl(baseUrl, leafPath, version, "map.lib"),
            planned.plan.files.single().url
        )
    }

    @Test
    fun aLeafWithoutFilesIsRefused() = runTest {
        val version = 27
        val leafPath = listOf("europe", "berlin")
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(
            RepositoryUrlPlanner.metadataUrl(baseUrl, leafPath, version),
            """{"schema": 1, "typeConfigVersion": $version, "output": {"files": {}}}"""
        )

        val result = RepositorySource(fetcher, MapSource.repository(baseUrl)).planDownload(leafPath, version)

        assertEquals(RepositoryDownloadPlanResult.Refused(RepositoryFailure.NoFilesPublished), result)
    }
}
