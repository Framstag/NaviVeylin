package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DownloadPlan].
 *
 * Spec: map-repository-source — "Download follows the metadata's file inventory and verifies every
 * file": exactly the named files, from the metadata's own version slot, type configuration last.
 */
class DownloadPlanTest {

    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")
    private val baseUrl = "https://maps.example.org/repo"

    @Test
    fun planWritesTheTypeConfigurationLast() {
        // `water.idx` sorts after `types.dat`, so an alphabetical plan would write the type
        // configuration first — this fixture can tell the two orders apart.
        val metadata = metadataOf("types.dat" to 100L, "map.lib" to 200L, "water.idx" to 300L)

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned

        assertEquals(listOf("map.lib", "water.idx", "types.dat"), plan.files.map { it.name })
        assertEquals(600L, plan.totalBytes)
    }

    @Test
    fun planKeepsMetadataOrderAmongDataFiles() {
        val metadata = metadataOf("nodes.dat" to 1L, "map.lib" to 2L, "areas.dat" to 3L)

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned

        assertEquals(listOf("nodes.dat", "map.lib", "areas.dat"), plan.files.map { it.name })
    }

    @Test
    fun onlyTheMetadataNamesArePlanned() {
        val metadata = metadataOf("map.lib" to 10L)

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned

        assertEquals(listOf("map.lib"), plan.files.map { it.name })
    }

    @Test
    fun everyPlannedUrlSharesTheMetadataSlot() {
        val metadata = metadataOf("map.lib" to 10L, "types.dat" to 5L)

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned

        val expectedSlot = RepositoryUrlPlanner.slotUrl(baseUrl, berlin, version)
        assertTrue(plan.files.all { it.url.startsWith(expectedSlot) })
        assertEquals(
            expectedSlot + "map.lib",
            plan.files.first { it.name == "map.lib" }.url
        )
    }

    @Test
    fun planRefusesAnUnverifiableInventory() {
        val metadata = DatabaseMetadata(
            typeConfigVersion = version,
            generatedAt = null,
            files = listOf(
                DatabaseFile("map.lib", 10L, 1234L),
                DatabaseFile("nodes.dat", 20L, null)
            )
        )

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version)

        assertEquals(
            DownloadPlanResult.Refused(RepositoryFailure.VerificationFailed("nodes.dat")),
            plan
        )
    }

    @Test
    fun planCarriesTheDeclaredSizeAndChecksum() {
        val metadata = metadataOf("map.lib" to 42L)

        val plan = DownloadPlan.forDatabase(baseUrl, berlin, metadata, version) as DownloadPlanResult.Planned

        assertEquals(42L, plan.files.single().sizeBytes)
        assertEquals(crcFor("map.lib"), plan.files.single().crc32)
    }

    private fun metadataOf(vararg files: Pair<String, Long>): DatabaseMetadata =
        DatabaseMetadata(
            typeConfigVersion = version,
            generatedAt = "2026-09-07T16:35:44Z",
            files = files.map { (name, size) -> DatabaseFile(name, size, crcFor(name)) }
        )

    private fun crcFor(name: String): Long {
        val crc = java.util.zip.CRC32()
        crc.update(name.toByteArray())
        return crc.value
    }
}
