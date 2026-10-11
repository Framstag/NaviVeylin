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
 * Unit tests for [MapUpdateChecker]'s regional comparison.
 *
 * Spec: map-updates — "An installed repository database is compared with its own version slot" / "Each
 * compared item carries one update state" / "The check covers the active repository source only".
 */
class MapUpdateCheckTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val source = MapSource.repository("https://maps.example.org/repo")
    private val otherRepository = MapSource.repository("https://maps.example.org/other")

    private val berlin = listOf("europe", "germany", "berlin")
    private val hamburg = listOf("europe", "germany", "hamburg")
    private val berlinDirectory = directoryNameOf(berlin)
    private val hamburgDirectory = directoryNameOf(hamburg)

    /** The bytes the fixture's metadata declares for `types.dat`, on top of the map file. */
    private val typeConfigBytes = 8L

    private val indexUrl = RepositoryUrlPlanner.regionIndexUrl(source.baseUrl)

    @Test
    fun aDirectoryMatchingNoLeafIsUnknown() = runTest {
        val installed = installRegion("atlantis")
        val fetcher = indexFetcher(listOf(berlin))

        val item = check(fetcher, listOf(installed)).single()

        assertTrue("no leaf of the index names this directory", item.state is UpdateState.Unknown)
        assertNull(item.idPath)
        assertFalse(
            "a directory with no leaf is never probed at a guessed path",
            fetcher.requested.any { it.contains("atlantis") }
        )
    }

    @Test
    fun twoLeavesOnOneDirectoryNameAreUnknown() = runTest {
        // `north-rhine-westphalia` is one identifier and `north/rhine/westphalia` is three, but the index
        // implies the same installed directory name for both.
        val hyphenated = listOf("north-rhine-westphalia")
        val split = listOf("north", "rhine", "westphalia")
        assertEquals(directoryNameOf(hyphenated), directoryNameOf(split))
        val installed = installRegion(directoryNameOf(hyphenated))
        val fetcher = indexFetcher(listOf(hyphenated, split))

        val item = check(fetcher, listOf(installed)).single()

        assertTrue("two leaves claim this directory, so it is never probed", item.state is UpdateState.Unknown)
        assertNull(item.idPath)
    }

    @Test
    fun installedDataOfAnotherRepositoryIsNotCompared() = runTest {
        val installed = installRegion(
            berlinDirectory,
            record = MapSourceRecord(MapSourceKind.REPOSITORY, otherRepository.baseUrl, version)
        )
        val fetcher = indexFetcher(listOf(berlin))

        val items = check(fetcher, listOf(installed))

        assertTrue("another repository's data is not this source's to compare", items.isEmpty())
        assertEquals("only the index was read", listOf(indexUrl), fetcher.requested)
    }

    @Test
    fun aNewerDatabaseIsPublishedForTheClientsVersion() = runTest {
        val installed = installRegion(berlinDirectory, generatedAt = "2026-09-07T16:35:44Z")
        val fetcher = indexFetcher(listOf(berlin)).apply {
            serveText(
                metadataUrl(berlin),
                metadataDocument(generatedAt = "2026-09-09T08:00:00Z", mapLibBytes = 4096)
            )
        }

        val item = check(fetcher, listOf(installed)).single()

        assertEquals(UpdateState.UpdateAvailable(4096 + typeConfigBytes), item.state)
        assertEquals(berlin, item.idPath)
        assertEquals("the index names the row", "Berlin", item.displayName)
    }

    @Test
    fun thePublishedDatabaseIsNotNewer() = runTest {
        val installed = installRegion(berlinDirectory, generatedAt = "2026-09-07T16:35:44Z")
        val same = indexFetcher(listOf(berlin)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2026-09-07T16:35:44Z"))
        }
        val older = indexFetcher(listOf(berlin)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2026-09-01T00:00:00Z"))
        }

        assertEquals(UpdateState.UpToDate, check(same, listOf(installed)).single().state)
        assertEquals(UpdateState.UpToDate, check(older, listOf(installed)).single().state)
    }

    @Test
    fun aSlotThatIsNoLongerPublishedIsNotAnUpdate() = runTest {
        val installed = installRegion(berlinDirectory)
        // Nothing is registered for the leaf's slot, so the repository answers 404 for it.
        val fetcher = indexFetcher(listOf(berlin))

        val item = check(fetcher, listOf(installed)).single()

        assertEquals(UpdateState.NoLongerOffered, item.state)
        assertEquals("the leaf is known, its slot is only gone", berlin, item.idPath)
        assertTrue(
            "the installation stays installed and usable",
            Files.isRegularFile(installed.resolve(DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME))
        )
    }

    @Test
    fun theInstalledMetadataCannotBeRead() = runTest {
        val installed = installRegion(berlinDirectory)
        Files.delete(installed.resolve(DatabaseMetadataParser.METADATA_FILE_NAME))
        val fetcher = indexFetcher(listOf(berlin)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2027-01-01T00:00:00Z"))
        }

        val item = check(fetcher, listOf(installed)).single()

        assertTrue("without a baseline nothing can be newer", item.state is UpdateState.Unknown)
        assertFalse(
            "an installation with no readable baseline is not even probed",
            fetcher.requested.any { it.contains("/v$version/") }
        )
    }

    @Test
    fun aNewerVersionSlotIsNotOffered() = runTest {
        val installed = installRegion(berlinDirectory)
        val fetcher = indexFetcher(listOf(berlin)).apply {
            // Only a slot for a *newer* database format version exists.
            serveText(
                RepositoryUrlPlanner.metadataUrl(source.baseUrl, berlin, version + 1),
                metadataDocument(generatedAt = "2027-01-01T00:00:00Z", typeConfigVersion = version + 1)
            )
        }

        val item = check(fetcher, listOf(installed)).single()

        assertEquals(UpdateState.NoLongerOffered, item.state)
        assertFalse(
            "the client never probes a newer version slot",
            fetcher.requested.any { it.contains("/v${version + 1}/") }
        )
    }

    @Test
    fun oneUnreachableItemDoesNotHideTheOthers() = runTest {
        val berlinInstalled = installRegion(berlinDirectory)
        val hamburgInstalled = installRegion(hamburgDirectory)
        val fetcher = indexFetcher(listOf(berlin, hamburg)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2026-09-09T08:00:00Z"))
            failTextOf = metadataUrl(hamburg) to RepositoryFailure.TransportFailed("no route")
        }

        val items = check(fetcher, listOf(berlinInstalled, hamburgInstalled))

        assertEquals(2, items.size)
        assertEquals(
            UpdateState.UpdateAvailable(1024 + typeConfigBytes),
            items.single { it.directoryName == berlinDirectory }.state
        )
        val failed = items.single { it.directoryName == hamburgDirectory }.state
        assertTrue("the unreachable one is unknown", failed is UpdateState.Unknown)
        assertEquals(
            RepositoryFailure.TransportFailed("no route"),
            (failed as UpdateState.Unknown).failure
        )
    }

    @Test
    fun onlyUpdateAvailableItemsAreActionable() = runTest {
        val upToDate = installRegion(berlinDirectory, generatedAt = "2026-09-07T16:35:44Z")
        val removed = installRegion(hamburgDirectory)
        val fetcher = indexFetcher(listOf(berlin, hamburg)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2026-09-07T16:35:44Z"))
        }

        val items = check(fetcher, listOf(upToDate, removed))

        assertTrue(
            "neither an up-to-date nor a no-longer-offered installation offers an update",
            items.none { it.state is UpdateState.UpdateAvailable }
        )
        assertEquals(
            listOf(UpdateState.UpToDate, UpdateState.NoLongerOffered),
            items.map { it.state }
        )
    }

    @Test
    fun aProviderInstalledMapReportsNoUpdateState() = runTest {
        val installed = installRegion(berlinDirectory, record = MapSourceRecord.BuiltInProvider)
        val fetcher = indexFetcher(listOf(berlin))

        val items = check(fetcher, listOf(installed))

        assertTrue("the built-in provider's map is not this source's to compare", items.isEmpty())
        assertEquals("no request is issued for it", listOf(indexUrl), fetcher.requested)
    }

    @Test
    fun aLeftoverStagingDirectoryIsNotCompared() = runTest {
        val installed = installRegion(berlinDirectory)
        // What a swap whose cleanup failed leaves behind: a complete database carrying a `types.dat`.
        val leftover = installRegion(".$berlinDirectory${SlotInstall.BACKUP_SUFFIX}")
        val fetcher = indexFetcher(listOf(berlin)).apply {
            serveText(metadataUrl(berlin), metadataDocument(generatedAt = "2026-09-09T08:00:00Z"))
        }

        val result = checkResult(fetcher, listOf(installed, leftover))

        assertEquals(
            "only the installation is compared",
            listOf(berlinDirectory),
            result.items.map { it.directoryName }
        )
        assertEquals(
            "the leftover is recognised as staging data of berlin",
            listOf(berlinDirectory),
            result.leftoverStaging
        )
        assertFalse(
            "and it is never probed as an installation",
            fetcher.requested.any { it.contains(SlotInstall.BACKUP_SUFFIX) }
        )
    }

    @Test
    fun stagingDataOfAMapThatIsNotInstalledIsStillReported() = runTest {
        val leftover = installRegion(".$hamburgDirectory${SlotInstall.INCOMING_SUFFIX}")
        val fetcher = indexFetcher(listOf(berlin))

        val result = checkResult(fetcher, listOf(leftover))

        assertTrue(result.items.isEmpty())
        assertEquals(listOf(hamburgDirectory), result.leftoverStaging)
    }

    @Test
    fun anUnreadableIndexFailsTheWholeCheck() = runTest {
        val installed = installRegion(berlinDirectory)
        val fetcher = FakeRepositoryFetcher().apply {
            failTextOf = indexUrl to RepositoryFailure.TransportFailed("offline")
        }

        val result = MapUpdateChecker(fetcher).check(source, listOf(installed), null, "en")

        assertEquals(
            "a check that cannot run is not a check, so the day's check stays due",
            MapUpdateCheckResult.Failed(RepositoryFailure.TransportFailed("offline")),
            result
        )
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private suspend fun check(fetcher: FakeRepositoryFetcher, installed: List<Path>): List<MapUpdateItem> =
        checkResult(fetcher, installed).items

    private suspend fun checkResult(
        fetcher: FakeRepositoryFetcher,
        installed: List<Path>
    ): MapUpdateCheckResult.Compared =
        when (val result = MapUpdateChecker(fetcher).check(source, installed, null, "en")) {
            is MapUpdateCheckResult.Compared -> result
            is MapUpdateCheckResult.Failed -> throw AssertionError("the check failed: ${result.failure}")
        }

    /** A fetcher serving the index for [leaves]; no version slot is served unless a case registers one. */
    private fun indexFetcher(leaves: List<List<String>>): FakeRepositoryFetcher =
        FakeRepositoryFetcher().apply { serveText(indexUrl, indexDocument(leaves)) }

    /**
     * Install a region directory carrying a metadata document, the type configuration that marks a
     * complete installation, and a source marker.
     */
    private fun installRegion(
        directoryName: String,
        generatedAt: String = "2026-09-07T16:35:44Z",
        record: MapSourceRecord = MapSourceRecord(MapSourceKind.REPOSITORY, source.baseUrl, version)
    ): Path {
        val directory = temporaryFolder.root.toPath().resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(
            directory.resolve(DatabaseMetadataParser.METADATA_FILE_NAME),
            metadataDocument(generatedAt).toByteArray()
        )
        Files.write(
            directory.resolve(DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME),
            "types".toByteArray()
        )
        assertTrue("the installation carries a source marker", MapSourceMarker.write(directory, record))
        return directory
    }

    private fun directoryNameOf(idPath: List<String>): String =
        RepositoryUrlPlanner.databaseDirectoryName(idPath)

    private fun metadataUrl(idPath: List<String>): String =
        RepositoryUrlPlanner.metadataUrl(source.baseUrl, idPath, version)

    private fun metadataDocument(
        generatedAt: String,
        typeConfigVersion: Int = version,
        mapLibBytes: Long = 1024
    ): String =
        """{"schema":1,"typeConfigVersion":$typeConfigVersion,"generatedAt":"$generatedAt",""" +
            """"output":{"files":{"map.lib":{"size":$mapLibBytes,"crc32":1},""" +
            """"types.dat":{"size":$typeConfigBytes,"crc32":2}}}}"""

    /** A region index that nests every id path of [leaves], so one leaf's directory name is reachable. */
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
