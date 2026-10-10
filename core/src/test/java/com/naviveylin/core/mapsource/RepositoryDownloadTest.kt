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
 * Unit tests for [DatabaseDownloader].
 *
 * Spec: map-repository-source — "Verified download completes" / "A corrupt file fails the download" /
 * "Only the files the metadata names are fetched" / "Files arrive from the metadata's own version slot" /
 * "Cancel during download" / "Cancelling or failing a repository download leaves no partial data";
 * map-download-infrastructure — "Repository download is cancellable".
 */
class RepositoryDownloadTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")
    private val baseUrl = "https://maps.example.org/repo"
    private val mapLib = "map data".toByteArray()
    private val typesDat = "type config".toByteArray()

    @Test
    fun requestsExactlyTheInventoriesNames() = runTest {
        val document = metadataDocument("map.lib" to mapLib, "types.dat" to typesDat)
        val fetcher = FakeRepositoryFetcher().apply {
            serve(fileUrl("map.lib"), mapLib)
            serve(fileUrl("types.dat"), typesDat)
        }

        val outcome = databaseDownloader(fetcher).download(plan(document), document, targetDir())

        assertEquals(DownloadOutcome.Completed, outcome)
        assertEquals(listOf(fileUrl("map.lib"), fileUrl("types.dat")), fetcher.requested)
    }

    @Test
    fun everyFileUrlSharesTheMetadataSlot() = runTest {
        val document = metadataDocument("map.lib" to mapLib)
        val fetcher = FakeRepositoryFetcher().apply { serve(fileUrl("map.lib"), mapLib) }

        databaseDownloader(fetcher).download(plan(document), document, targetDir())

        val slotUrl = RepositoryUrlPlanner.slotUrl(baseUrl, berlin, version)
        assertTrue(fetcher.requested.all { it.startsWith(slotUrl) })
    }

    @Test
    fun installsEveryFileAndTheMetadata() = runTest {
        val document = metadataDocument("map.lib" to mapLib, "types.dat" to typesDat)
        val fetcher = FakeRepositoryFetcher().apply {
            serve(fileUrl("map.lib"), mapLib)
            serve(fileUrl("types.dat"), typesDat)
        }
        val target = targetDir()

        databaseDownloader(fetcher).download(plan(document), document, target)

        assertEquals("map data", String(Files.readAllBytes(target.resolve("map.lib"))))
        assertEquals("type config", String(Files.readAllBytes(target.resolve("types.dat"))))
        assertEquals(document, String(Files.readAllBytes(target.resolve("db.json"))))
        assertTrue(
            "no half-written file survives",
            Files.list(target).noneMatch { it.fileName.toString().endsWith(".download") }
        )
    }

    @Test
    fun theTypeConfigurationIsWrittenLast() = runTest {
        val document = metadataDocument("types.dat" to typesDat, "map.lib" to mapLib)
        val fetcher = FakeRepositoryFetcher().apply {
            serve(fileUrl("types.dat"), typesDat)
            serve(fileUrl("map.lib"), mapLib)
        }

        databaseDownloader(fetcher).download(plan(document), document, targetDir())

        assertEquals(listOf(fileUrl("map.lib"), fileUrl("types.dat")), fetcher.requested)
    }

    @Test
    fun progressIsReportedPerFile() = runTest {
        val document = metadataDocument("map.lib" to mapLib, "types.dat" to typesDat)
        val fetcher = FakeRepositoryFetcher().apply {
            serve(fileUrl("map.lib"), mapLib)
            serve(fileUrl("types.dat"), typesDat)
        }
        val reported = mutableListOf<Pair<Long, Long>>()

        databaseDownloader(fetcher).download(
            plan(document),
            document,
            targetDir(),
            onProgress = { done, total -> reported += done to total }
        )

        val total = (mapLib.size + typesDat.size).toLong()
        assertTrue(reported.isNotEmpty())
        assertTrue("every report carries the planned total", reported.all { it.second == total })
        assertEquals(total, reported.last().first)
    }

    @Test
    fun transportFailureFailsTheDownloadAndRemovesTheDirectory() = runTest {
        val document = metadataDocument("map.lib" to mapLib)
        val target = targetDir()
        val fetcher = FakeRepositoryFetcher().apply {
            failDownloadOf = fileUrl("map.lib") to RepositoryFailure.HttpStatus(404)
        }

        val outcome = databaseDownloader(fetcher).download(plan(document), document, target)

        assertEquals(DownloadOutcome.Failed(RepositoryFailure.HttpStatus(404)), outcome)
        assertFalse("a failed download leaves no directory", Files.exists(target))
    }

    @Test
    fun aBodyThatDoesNotMatchItsChecksumFailsTheDownload() = runTest {
        val document = metadataDocument("map.lib" to mapLib)
        val target = targetDir()
        // The plan declares map.lib's checksum; the server serves a different body.
        val fetcher = FakeRepositoryFetcher().apply { serve(fileUrl("map.lib"), "corrupted!".toByteArray()) }

        val outcome = databaseDownloader(fetcher).download(plan(document), document, target)

        assertEquals(DownloadOutcome.Failed(RepositoryFailure.VerificationFailed("map.lib")), outcome)
        assertFalse(Files.exists(target))
    }

    @Test
    fun failureRemovesPartialDirectory() = runTest {
        val document = metadataDocument("map.lib" to mapLib, "types.dat" to typesDat)
        val target = targetDir()
        // map.lib arrives, the type configuration is missing: the directory must not survive.
        val fetcher = FakeRepositoryFetcher().apply { serve(fileUrl("map.lib"), mapLib) }

        val outcome = databaseDownloader(fetcher).download(plan(document), document, target)

        assertTrue(outcome is DownloadOutcome.Failed)
        assertFalse(Files.exists(target))
    }

    @Test
    fun cancelRemovesPartialDirectory() = runTest {
        val document = metadataDocument("map.lib" to mapLib, "types.dat" to typesDat)
        val target = targetDir()
        val fetcher = FakeRepositoryFetcher().apply {
            serve(fileUrl("map.lib"), mapLib)
            serve(fileUrl("types.dat"), typesDat)
        }
        var polls = 0

        val outcome = databaseDownloader(fetcher).download(
            plan(document),
            document,
            target,
            isCancelled = { polls++ > 0 }
        )

        assertEquals(DownloadOutcome.Cancelled, outcome)
        assertFalse("a cancelled download leaves no directory", Files.exists(target))
    }

    @Test
    fun aStaleDirectoryIsReplacedFromScratch() = runTest {
        val document = metadataDocument("map.lib" to mapLib)
        val fetcher = FakeRepositoryFetcher().apply { serve(fileUrl("map.lib"), mapLib) }
        val target = targetDir()
        Files.createDirectories(target)
        Files.write(target.resolve("stale.dat"), "left over".toByteArray())

        databaseDownloader(fetcher).download(plan(document), document, target)

        assertFalse("state from a previous attempt cannot survive", Files.exists(target.resolve("stale.dat")))
    }

    private fun databaseDownloader(fetcher: FakeRepositoryFetcher): DatabaseDownloader =
        DatabaseDownloader(fetcher)

    private fun fileUrl(name: String): String =
        RepositoryUrlPlanner.fileUrl(baseUrl, berlin, version, name)

    private fun plan(document: String): DownloadPlanResult.Planned {
        val metadata = (DatabaseMetadataParser.parse(document, version) as DatabaseMetadataResult.Loaded).metadata
        return DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned
    }

    private fun targetDir(): Path = temporaryFolder.newFolder().toPath().resolve("europe-germany-berlin")

    /** A metadata document describing [bodies], as a repository would serve it. */
    private fun metadataDocument(vararg bodies: Pair<String, ByteArray>): String {
        val files = bodies.joinToString(",") { (name, body) ->
            """"$name": {"size": ${body.size}, "crc32": ${FakeRepositoryFetcher.crc32Of(body)}}"""
        }
        return """{"schema": 1, "typeConfigVersion": $version, "generatedAt": "2026-09-07T16:35:44Z", """ +
            """"output": {"files": {$files}}}"""
    }
}
