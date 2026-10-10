package com.naviveylin.core.mapsource

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
 * Unit tests for [RepositoryBasemap] and [BasemapManifestParser].
 *
 * Spec: map-repository-source — "The repository source installs its own basemap version slot";
 * basemap-download — "A tar.gz archive is not used by a repository source".
 */
class RepositoryBasemapTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val baseUrl = "https://maps.example.org/repo"
    private val manifestUrl = RepositoryUrlPlanner.basemapManifestUrl(baseUrl)
    private val typesDat = "type config".toByteArray()

    @Test
    fun newestReadableVersionIsSelected() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(
            manifestUrl,
            """{"schema": 1, "versions": [
                 {"typeConfigVersion": 25, "changedAt": "2026-08-01T10:00:00Z"},
                 {"typeConfigVersion": 27, "changedAt": "2026-09-07T10:00:00Z"}
               ]}"""
        )

        val availability = basemap(fetcher).availableVersion()

        assertEquals(
            BasemapAvailability.Available(BasemapVersion(27, "2026-09-07T10:00:00Z")),
            availability
        )
        assertEquals(listOf(manifestUrl), fetcher.requested)
    }

    @Test
    fun unreadableNewestIsSkipped() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(
            manifestUrl,
            """{"schema": 1, "versions": [
                 {"typeConfigVersion": 31, "changedAt": "2026-10-01T10:00:00Z"},
                 {"typeConfigVersion": 27, "changedAt": "2026-09-07T10:00:00Z"}
               ]}"""
        )

        val availability = basemap(fetcher).availableVersion()

        assertEquals(BasemapAvailability.Available(BasemapVersion(27, "2026-09-07T10:00:00Z")), availability)
    }

    @Test
    fun manifestWithoutReadableVersionIsUnavailable() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(manifestUrl, """{"schema": 1, "versions": [{"typeConfigVersion": 31}]}""")

        assertEquals(BasemapAvailability.Unavailable, basemap(fetcher).availableVersion())
    }

    @Test
    fun missingManifestIsUnavailableNotAnError() = runTest {
        val fetcher = FakeRepositoryFetcher()

        assertEquals(BasemapAvailability.Unavailable, basemap(fetcher).availableVersion())
    }

    @Test
    fun unsupportedManifestSchemaIsReported() = runTest {
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(manifestUrl, """{"schema": 2, "versions": []}""")

        assertEquals(
            BasemapAvailability.Failed(RepositoryFailure.UnsupportedSchema(2)),
            basemap(fetcher).availableVersion()
        )
    }

    @Test
    fun noHtmlListingIsParsed() = runTest {
        // A karry-style directory listing at the basemap location is not a repository basemap.
        val fetcher = FakeRepositoryFetcher()
        fetcher.serveText(
            manifestUrl,
            "<html><body><a href=\"BaseMap-2026-02-23.tar.gz\">BaseMap-2026-02-23.tar.gz</a></body></html>"
        )

        assertEquals(
            BasemapAvailability.Failed(RepositoryFailure.MalformedDocument),
            basemap(fetcher).availableVersion()
        )
    }

    @Test
    fun slotInstallWritesTheBasemapSlot() = runTest {
        val fetcher = fetcherFor(version = 27, bodies = mapOf("types.dat" to typesDat))
        val basemapDirectory = temporaryFolder.newFolder().toPath().resolve("basemap")

        val outcome = basemap(fetcher).install(BasemapVersion(27, null), basemapDirectory)

        assertEquals(DownloadOutcome.Completed, outcome)
        assertEquals("type config", String(Files.readAllBytes(basemapDirectory.resolve("types.dat"))))
        assertTrue(Files.isRegularFile(basemapDirectory.resolve("db.json")))
        assertFalse("the incoming directory is gone", Files.exists(incomingOf(basemapDirectory)))
    }

    @Test
    fun failedVerifiedDownloadKeepsTheInstalledBasemap() = runTest {
        val basemapDirectory = temporaryFolder.newFolder().toPath().resolve("basemap")
        Files.createDirectories(basemapDirectory)
        Files.write(basemapDirectory.resolve("types.dat"), "installed basemap".toByteArray())
        // The slot's metadata declares a checksum its body does not match.
        val fetcher = fetcherFor(version = 27, bodies = mapOf("types.dat" to typesDat))
        fetcher.failDownloadOf = RepositoryUrlPlanner.basemapFileUrl(baseUrl, 27, "types.dat") to
            RepositoryFailure.VerificationFailed("types.dat")

        val outcome = basemap(fetcher).install(BasemapVersion(27, null), basemapDirectory)

        assertTrue(outcome is DownloadOutcome.Failed)
        assertEquals("installed basemap", String(Files.readAllBytes(basemapDirectory.resolve("types.dat"))))
        assertFalse(Files.exists(incomingOf(basemapDirectory)))
    }

    @Test
    fun cancelledInstallKeepsTheInstalledBasemap() = runTest {
        val basemapDirectory = temporaryFolder.newFolder().toPath().resolve("basemap")
        Files.createDirectories(basemapDirectory)
        Files.write(basemapDirectory.resolve("types.dat"), "installed basemap".toByteArray())
        val fetcher = fetcherFor(version = 27, bodies = mapOf("types.dat" to typesDat))
        var polls = 0

        val outcome = basemap(fetcher).install(
            BasemapVersion(27, null), basemapDirectory, isCancelled = { polls++ > 0 }
        )

        assertEquals(DownloadOutcome.Cancelled, outcome)
        assertEquals("installed basemap", String(Files.readAllBytes(basemapDirectory.resolve("types.dat"))))
    }

    @Test
    fun installReplacesAnOlderBasemapSlot() = runTest {
        val basemapDirectory = temporaryFolder.newFolder().toPath().resolve("basemap")
        Files.createDirectories(basemapDirectory)
        Files.write(basemapDirectory.resolve("stale.dat"), "old slot".toByteArray())
        val fetcher = fetcherFor(version = 27, bodies = mapOf("types.dat" to typesDat))

        basemap(fetcher).install(BasemapVersion(27, null), basemapDirectory)

        assertFalse("the replaced slot does not survive", Files.exists(basemapDirectory.resolve("stale.dat")))
        assertTrue(Files.isRegularFile(basemapDirectory.resolve("types.dat")))
        assertFalse(Files.exists(basemapDirectory.resolveSibling(".basemap.backup")))
    }

    @Test
    fun theSlotIsAddressedWithTheSlotsOwnVersion() = runTest {
        val fetcher = fetcherFor(version = 25, bodies = mapOf("types.dat" to typesDat))
        val basemapDirectory = temporaryFolder.newFolder().toPath().resolve("basemap")

        basemap(fetcher).install(BasemapVersion(25, null), basemapDirectory)

        assertEquals(RepositoryUrlPlanner.basemapMetadataUrl(baseUrl, 25), fetcher.requested.first())
        assertTrue(
            fetcher.requested.drop(1).all { it.startsWith(RepositoryUrlPlanner.basemapSlotUrl(baseUrl, 25)) }
        )
    }

    /** A fetcher that serves a basemap slot of [version] with [bodies]. */
    private fun fetcherFor(version: Int, bodies: Map<String, ByteArray>): FakeRepositoryFetcher {
        val fetcher = FakeRepositoryFetcher()
        val files = bodies.entries.joinToString(",") { (name, body) ->
            """"$name": {"size": ${body.size}, "crc32": ${FakeRepositoryFetcher.crc32Of(body)}}"""
        }
        fetcher.serveText(
            RepositoryUrlPlanner.basemapMetadataUrl(baseUrl, version),
            """{"schema": 1, "typeConfigVersion": $version, "output": {"files": {$files}}}"""
        )
        bodies.forEach { (name, body) ->
            fetcher.serve(RepositoryUrlPlanner.basemapFileUrl(baseUrl, version, name), body)
        }
        return fetcher
    }

    private fun basemap(fetcher: FakeRepositoryFetcher): RepositoryBasemap =
        RepositoryBasemap(fetcher, MapSource.repository(baseUrl), readableDatabaseVersion = 27)

    private fun incomingOf(basemapDirectory: Path): Path =
        basemapDirectory.resolveSibling(".${basemapDirectory.fileName}.incoming")
}
