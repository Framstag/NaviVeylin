package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RepositoryUrlPlanner].
 *
 * Specs: map-repository-source — "Install with a localized display name" / "Language switch does not
 * orphan an installed map"; map-download-infrastructure — "Version slot and listing bounds agree";
 * map-source-selection — "A repository base URL is normalised before it is used" / "Whitespace does
 * not fork the stored source" / "An unencrypted repository source is marked as such".
 */
class RepositoryUrlPlannerTest {

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")

    @Test
    fun slotUrlUsesIdPathAndVersion() {
        assertEquals(
            "https://maps.example.org/repo/europe/germany/berlin/v$version/",
            RepositoryUrlPlanner.slotUrl("https://maps.example.org/repo", berlin, version)
        )
        assertEquals(
            "https://maps.example.org/repo/europe/germany/berlin/v$version/db.json",
            RepositoryUrlPlanner.metadataUrl("https://maps.example.org/repo", berlin, version)
        )
        assertEquals(
            "https://maps.example.org/repo/europe/germany/berlin/v$version/map.lib",
            RepositoryUrlPlanner.fileUrl("https://maps.example.org/repo", berlin, version, "map.lib")
        )
    }

    @Test
    fun baseUrlTrailingSlashIsNormalised() {
        assertEquals(
            "https://maps.example.org/repo/names.json",
            RepositoryUrlPlanner.regionIndexUrl("https://maps.example.org/repo/")
        )
        assertEquals(
            "https://maps.example.org/repo/names.json",
            RepositoryUrlPlanner.regionIndexUrl("  https://maps.example.org/repo///  ")
        )
        assertEquals(
            "https://maps.example.org/repo/europe/germany/berlin/v$version/",
            RepositoryUrlPlanner.slotUrl("https://maps.example.org/repo/", berlin, version)
        )
    }

    @Test
    fun directoryNameComesFromIdPathNotDisplayName() {
        assertEquals("europe-germany-berlin", RepositoryUrlPlanner.databaseDirectoryName(berlin))
        assertEquals("iceland", RepositoryUrlPlanner.databaseDirectoryName(listOf("iceland")))
    }

    @Test
    fun displayNameChangeLeavesDirectoryNameStable() {
        val germanIndex = """{"schema": 1, "regions": [{"id": "berlin", "names": {"de": "Berlin (Stadt)"}}]}"""
        val englishIndex = """{"schema": 1, "regions": [{"id": "berlin", "names": {"en": "Berlin"}}]}"""

        val germanLeaf = germanLeafOf(germanIndex)
        val englishLeaf = englishLeafOf(englishIndex)

        // Same identifier, different display names, one directory.
        assertEquals("berlin", RepositoryUrlPlanner.databaseDirectoryName(germanLeaf.idPath))
        assertEquals(
            RepositoryUrlPlanner.databaseDirectoryName(englishLeaf.idPath),
            RepositoryUrlPlanner.databaseDirectoryName(germanLeaf.idPath)
        )
        assertEquals("Berlin (Stadt)", germanLeaf.displayName)
        assertEquals("Berlin", englishLeaf.displayName)
    }

    @Test
    fun basemapUrlsUseTheManifestAndTheSlot() {
        assertEquals(
            "https://maps.example.org/repo/basemap/index.json",
            RepositoryUrlPlanner.basemapManifestUrl("https://maps.example.org/repo")
        )
        assertEquals(
            "https://maps.example.org/repo/basemap/v$version/db.json",
            RepositoryUrlPlanner.basemapMetadataUrl("https://maps.example.org/repo", version)
        )
        assertEquals(
            "https://maps.example.org/repo/basemap/v$version/types.dat",
            RepositoryUrlPlanner.basemapFileUrl("https://maps.example.org/repo", version, "types.dat")
        )
    }

    @Test
    fun whitespaceAnywhereInTheBaseUrlIsRemoved() {
        // The shape a phone keyboard produces after a period, and the shape a paste can carry.
        assertEquals(
            "http://10.0.2.2:30123/names.json",
            RepositoryUrlPlanner.regionIndexUrl("http://10.0. 2. 2:30123")
        )
        assertEquals(
            "http://truenas.home.framstag.com:30123/names.json",
            RepositoryUrlPlanner.regionIndexUrl("  http:// truenas.home.framstag.com :30123/  ")
        )
        assertEquals(
            "https://maps.example.org/repo",
            RepositoryUrlPlanner.normaliseBaseUrl("https://maps. example. org/repo")
        )
        assertEquals("", RepositoryUrlPlanner.normaliseBaseUrl("   "))
    }

    @Test
    fun whitespaceDoesNotForkTheSourceIdentity() {
        // MapSource normalises on construction, so a padded URL is the same source as the clean one
        // (spec map-source-selection — "Whitespace does not fork the stored source").
        assertEquals(
            MapSource.repository("https://maps.example.org/repo"),
            MapSource.repository(" https://maps.example.org/repo/ ")
        )
        assertEquals(
            MapSource.repository("http://10.0.2.2:30123"),
            MapSource.repository("http://10.0. 2. 2:30123")
        )
    }

    @Test
    fun plainHttpIsUnencryptedAndEverythingElseIsNot() {
        assertEquals(true, RepositoryUrlPlanner.isUnencrypted("http://10.0.2.2:30123"))
        assertEquals(true, RepositoryUrlPlanner.isUnencrypted("HTTP://truenas.home.framstag.com:30123/"))
        assertEquals(true, RepositoryUrlPlanner.isUnencrypted("  http://10.0.2.2:30123  "))

        assertEquals(false, RepositoryUrlPlanner.isUnencrypted("https://maps.example.org/repo"))
        assertEquals(false, RepositoryUrlPlanner.isUnencrypted("HTTPS://maps.example.org/repo"))
        // An unusable value is not "unencrypted": it reports itself as unusable instead.
        assertEquals(false, RepositoryUrlPlanner.isUnencrypted("truenas.home.framstag.com:30123"))
        assertEquals(false, RepositoryUrlPlanner.isUnencrypted(""))
    }

    private fun germanLeafOf(index: String): IndexRegion =
        (RegionIndexParser.parse(index, "de") as RegionIndexResult.Loaded).regions.single()

    private fun englishLeafOf(index: String): IndexRegion =
        (RegionIndexParser.parse(index, "en") as RegionIndexResult.Loaded).regions.single()
}
