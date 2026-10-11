package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [RepositoryDatabaseInstaller].
 *
 * Spec: map-updates — "An update replaces the installation only when it is complete"; migration and
 * rollback: an interrupted attempt leaves nothing partial behind.
 */
class RepositoryDatabaseInstallerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val source = MapSource.repository("https://maps.example.org/repo")
    private val berlin = listOf("europe", "germany", "berlin")
    private val berlinDirectory = "europe-germany-berlin"

    private val mapLib = "new map data".toByteArray()
    private val typesDat = "new type config".toByteArray()

    @Test
    fun aFailedVerificationLeavesTheInstalledMapIntact() = runTest {
        val target = installMap()
        val fetcher = FakeRepositoryFetcher()
        serveSlot(fetcher, typesDatOverride = "tampered".toByteArray())

        val outcome = installer(fetcher).install(source, berlin, target)

        assertTrue("a file that does not match its checksum fails the update", outcome is DownloadOutcome.Failed)
        assertEquals(
            RepositoryFailure.VerificationFailed("types.dat"),
            (outcome as DownloadOutcome.Failed).failure
        )
        assertEquals("installed map", installedTypesDat(target))
        assertFalse("the incomplete transfer is gone", Files.exists(incoming(target)))
    }

    @Test
    fun anInterruptedUpdateRemovesTheIncomingData() = runTest {
        val target = installMap()
        val fetcher = FakeRepositoryFetcher()
        serveSlot(fetcher)
        var polls = 0

        val outcome = installer(fetcher).install(source, berlin, target, isCancelled = { polls++ > 0 })

        assertEquals(DownloadOutcome.Cancelled, outcome)
        assertEquals("installed map", installedTypesDat(target))
        assertFalse("the cancelled transfer is gone", Files.exists(incoming(target)))
    }

    @Test
    fun aSuccessfulSwapKeepsTheDirectoryNameAndReplacesItsContents() = runTest {
        val target = installMap()
        Files.write(target.resolve("stale.dat"), "old slot".toByteArray())
        val fetcher = FakeRepositoryFetcher()
        serveSlot(fetcher)

        val outcome = installer(fetcher).install(source, berlin, target)

        assertEquals(DownloadOutcome.Completed, outcome)
        assertEquals("the installed directory keeps its name", berlinDirectory, target.fileName.toString())
        assertEquals("new type config", installedTypesDat(target))
        assertEquals("new map data", String(Files.readAllBytes(target.resolve("map.lib"))))
        assertFalse("the replaced slot does not survive", Files.exists(target.resolve("stale.dat")))
        assertFalse(Files.exists(incoming(target)))
        assertFalse(Files.exists(backup(target)))
    }

    @Test
    fun aLeftoverIncomingDirectoryIsSweptBeforeTheNextAttempt() = runTest {
        val target = installMap()
        // What a completed transfer that lost its swap, and a swap that could not delete its backup, leave
        // behind: both carry a `types.dat`, which is what the installed-map scan looks for.
        Files.createDirectories(incoming(target))
        Files.write(incoming(target).resolve("types.dat"), "half".toByteArray())
        Files.createDirectories(backup(target))
        Files.write(backup(target).resolve("types.dat"), "previous".toByteArray())
        val fetcher = FakeRepositoryFetcher()
        serveSlot(fetcher, typesDatOverride = "tampered".toByteArray())

        val outcome = installer(fetcher).install(source, berlin, target)

        assertTrue(outcome is DownloadOutcome.Failed)
        assertFalse("no leftover staging directory survives the attempt", Files.exists(incoming(target)))
        assertFalse("no leftover backup directory survives the attempt", Files.exists(backup(target)))
        assertEquals("installed map", installedTypesDat(target))
    }

    @Test
    fun stagingNamesAreRecognised() {
        assertEquals(berlinDirectory, SlotInstall.stagingTargetOf(".$berlinDirectory.incoming"))
        assertEquals(berlinDirectory, SlotInstall.stagingTargetOf(".$berlinDirectory.backup"))
        assertEquals("basemap", SlotInstall.stagingTargetOf(".basemap.incoming"))
        assertTrue(SlotInstall.isStaging(".basemap.backup"))
        assertNull("an installation is not staging data", SlotInstall.stagingTargetOf(berlinDirectory))
        assertNull("a dot-directory that stages nothing is not staging", SlotInstall.stagingTargetOf(".hidden"))
        assertFalse(SlotInstall.isStaging(berlinDirectory))
    }

    @Test
    fun aSuccessfulSwapKeepsTheInstallationRecorded() = runTest {
        val target = installMap()
        val fetcher = FakeRepositoryFetcher()
        serveSlot(fetcher)

        val outcome = installer(fetcher).install(source, berlin, target)

        assertEquals(DownloadOutcome.Completed, outcome)
        // The slot's inventory names only its own data files, so the swap would replace everything else the
        // installation carried — including the marker that records which source it came from. Without it the
        // app attributes the map to the built-in provider and the update check stops comparing it at all
        // (spec `map-source-selection` — "Every map directory the app installs contains a marker recording the
        // source it was downloaded from"; spec `map-updates` — "The map keeps its identity").
        val record = MapSourceMarker.sourceOf(target)
        assertEquals(MapSourceKind.REPOSITORY, record.sourceKind)
        assertEquals(source.baseUrl, record.baseUrl)
        assertEquals(version, record.databaseVersion)
    }

    @Test
    fun aRefusedPlanInstallsNothing() = runTest {
        val target = installMap()
        // The slot's metadata names a file but declares no checksum for it, so it cannot be verified.
        val fetcher = FakeRepositoryFetcher().apply {
            serveText(
                RepositoryUrlPlanner.metadataUrl(source.baseUrl, berlin, version),
                """{"schema":1,"typeConfigVersion":$version,"generatedAt":"2026-09-09T08:00:00Z",""" +
                    """"output":{"files":{"map.lib":{"size":13}}}}"""
            )
        }

        val outcome = installer(fetcher).install(source, berlin, target)

        assertEquals(
            RepositoryFailure.VerificationFailed("map.lib"),
            (outcome as DownloadOutcome.Failed).failure
        )
        assertEquals("installed map", installedTypesDat(target))
        assertFalse(Files.exists(incoming(target)))
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun installer(fetcher: FakeRepositoryFetcher) = RepositoryDatabaseInstaller(fetcher)

    /** An installed database carrying a type configuration, a metadata document and a source marker. */
    private fun installMap(): Path {
        val directory = temporaryFolder.root.toPath().resolve(berlinDirectory)
        Files.createDirectories(directory)
        Files.write(directory.resolve(DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME), "installed map".toByteArray())
        Files.write(
            directory.resolve(DatabaseMetadataParser.METADATA_FILE_NAME),
            metadataDocument(mapLib, typesDat).toByteArray()
        )
        return directory
    }

    private fun installedTypesDat(directory: Path): String =
        String(Files.readAllBytes(directory.resolve(DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME)))

    private fun incoming(target: Path): Path = target.resolveSibling(".${target.fileName}.incoming")

    private fun backup(target: Path): Path = target.resolveSibling(".${target.fileName}.backup")

    /**
     * Serve the leaf's metadata and the files it names, with the checksums a real repository states.
     *
     * @param typesDatOverride a body that does not match the declared checksum, i.e. a corrupt transfer
     */
    private fun serveSlot(fetcher: FakeRepositoryFetcher, typesDatOverride: ByteArray? = null) {
        fetcher.serveText(
            RepositoryUrlPlanner.metadataUrl(source.baseUrl, berlin, version),
            metadataDocument(mapLib, typesDat)
        )
        fetcher.serve(RepositoryUrlPlanner.fileUrl(source.baseUrl, berlin, version, "map.lib"), mapLib)
        fetcher.serve(
            RepositoryUrlPlanner.fileUrl(source.baseUrl, berlin, version, "types.dat"),
            typesDatOverride ?: typesDat
        )
    }

    private fun metadataDocument(mapLibBody: ByteArray, typesDatBody: ByteArray): String =
        """{"schema":1,"typeConfigVersion":$version,"generatedAt":"2026-09-09T08:00:00Z",""" +
            """"output":{"files":{"map.lib":{"size":${mapLibBody.size},"crc32":${crc(mapLibBody)}},""" +
            """"types.dat":{"size":${typesDatBody.size},"crc32":${crc(typesDatBody)}}}}}"""

    private fun crc(body: ByteArray): Long = FakeRepositoryFetcher.crc32Of(body)
}
