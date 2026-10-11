package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [MapUpdateChecker]'s basemap comparison, which reads the repository's availability
 * manifest instead of a leaf's version slot.
 *
 * Spec: basemap-discovery — "Report basemap version for updates" / "Source publishes no comparable
 * version"; map-updates — "The updates section lists what can be updated".
 */
class BasemapUpdateComparisonTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val source = MapSource.repository("https://maps.example.org/repo")
    private val basemapDirectoryName = RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME

    private val indexUrl = RepositoryUrlPlanner.regionIndexUrl(source.baseUrl)
    private val manifestUrl = RepositoryUrlPlanner.basemapManifestUrl(source.baseUrl)

    /** The bytes the fixture's slot metadata declares for `types.dat`, on top of the map file. */
    private val typeConfigBytes = 8L

    @Test
    fun aNewerManifestChangeTimeIsAnUpdate() = runTest {
        val installed = installBasemap(generatedAt = "2026-09-01T00:00:00Z")
        val fetcher = manifestFetcher(manifest(version, "2026-09-07T10:00:00Z")).apply {
            serveText(
                RepositoryUrlPlanner.basemapMetadataUrl(source.baseUrl, version),
                slotMetadataDocument(generatedAt = "2026-09-07T10:00:00Z", mapLibBytes = 2048)
            )
        }

        val item = check(fetcher, installed).single()

        assertEquals(MapUpdateKind.BASEMAP, item.kind)
        assertEquals(basemapDirectoryName, item.directoryName)
        assertEquals(UpdateState.UpdateAvailable(2048 + typeConfigBytes), item.state)
    }

    @Test
    fun aManifestMatchingTheInstallationIsUpToDate() = runTest {
        val installedAt = "2026-09-07T16:35:44Z"
        val installed = installBasemap(generatedAt = installedAt)
        val fetcher = manifestFetcher(manifest(version, installedAt))

        val item = check(fetcher, installed).single()

        assertEquals(UpdateState.UpToDate, item.state)
        assertEquals(
            "an up-to-date basemap costs no further request",
            listOf(indexUrl, manifestUrl),
            fetcher.requested
        )
    }

    @Test
    fun anOlderPublishedVersionIsNotAnUpdate() = runTest {
        val installed = installBasemap(generatedAt = "2026-09-07T16:35:44Z")
        val fetcher = manifestFetcher(manifest(version, "2026-09-01T00:00:00Z"))

        val item = check(fetcher, installed).single()

        assertEquals(UpdateState.UpToDate, item.state)
    }

    @Test
    fun aVersionThisClientCannotReadIsNotOffered() = runTest {
        val installed = installBasemap(generatedAt = "2026-09-01T00:00:00Z")
        val fetcher = manifestFetcher(manifest(version + 1, "2027-01-01T00:00:00Z"))

        val items = check(fetcher, installed)

        assertTrue("no comparable version is published, so there is no update state", items.isEmpty())
        assertFalse(
            "the client never reads a version slot newer than its own",
            fetcher.requested.any { it.contains("/v${version + 1}/") }
        )
    }

    @Test
    fun aRepositoryWithoutAManifestReportsNoUpdateState() = runTest {
        val installed = installBasemap(generatedAt = "2026-09-01T00:00:00Z")
        // The manifest is not registered: the repository answers 404 for it.
        val fetcher = FakeRepositoryFetcher().apply {
            serveText(indexUrl, emptyIndexDocument())
        }

        val items = check(fetcher, installed)

        assertTrue("a repository with no basemap has no update state to report", items.isEmpty())
    }

    @Test
    fun aBasemapOfAnotherSourceIsNotCompared() = runTest {
        val installed = installBasemap(
            generatedAt = "2026-09-01T00:00:00Z",
            record = MapSourceRecord.BuiltInProvider
        )
        val fetcher = manifestFetcher(manifest(version, "2026-09-07T10:00:00Z"))

        val items = check(fetcher, installed)

        assertTrue("the built-in provider's basemap is not this source's to compare", items.isEmpty())
        assertEquals(listOf(indexUrl), fetcher.requested)
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private suspend fun check(fetcher: FakeRepositoryFetcher, basemap: Path): List<MapUpdateItem> =
        when (
            val result = MapUpdateChecker(fetcher).check(source, emptyList(), basemap, "en")
        ) {
            is MapUpdateCheckResult.Compared -> result.items
            is MapUpdateCheckResult.Failed -> throw AssertionError("the check failed: ${result.failure}")
        }

    private fun manifestFetcher(manifestDocument: String): FakeRepositoryFetcher =
        FakeRepositoryFetcher().apply {
            serveText(indexUrl, emptyIndexDocument())
            serveText(manifestUrl, manifestDocument)
        }

    /** Install the basemap directory, carrying a slot's metadata and a source marker. */
    private fun installBasemap(
        generatedAt: String,
        record: MapSourceRecord = MapSourceRecord(MapSourceKind.REPOSITORY, source.baseUrl, version)
    ): Path {
        val directory = temporaryFolder.root.toPath().resolve(basemapDirectoryName)
        Files.createDirectories(directory)
        Files.write(
            directory.resolve(DatabaseMetadataParser.METADATA_FILE_NAME),
            slotMetadataDocument(generatedAt).toByteArray()
        )
        Files.write(
            directory.resolve(DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME),
            "types".toByteArray()
        )
        assertTrue("the installation carries a source marker", MapSourceMarker.write(directory, record))
        return directory
    }

    private fun manifest(typeConfigVersion: Int, changedAt: String): String =
        """{"schema":1,"versions":[{"typeConfigVersion":$typeConfigVersion,"changedAt":"$changedAt"}]}"""

    private fun slotMetadataDocument(
        generatedAt: String,
        typeConfigVersion: Int = version,
        mapLibBytes: Long = 1024
    ): String =
        """{"schema":1,"typeConfigVersion":$typeConfigVersion,"generatedAt":"$generatedAt",""" +
            """"output":{"files":{"map.lib":{"size":$mapLibBytes,"crc32":1},""" +
            """"types.dat":{"size":$typeConfigBytes,"crc32":2}}}}"""

    /** An index naming no region: this test class compares the basemap only. */
    private fun emptyIndexDocument(): String = """{"schema":1,"regions":[]}"""
}
