package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.InstalledMaps
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [MapTreeDiscovery] over a real maps tree.
 *
 * Spec: map-updates — "Data left by an interrupted update is recognised, not mistaken for a map" (the
 * discovery half: what a `.backup` or `.incoming` holds is staging data, reported and never opened or
 * listed).
 */
class MapTreeDiscoveryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun mapsRoot(): Path = temporaryFolder.newFolder("maps").toPath()

    /** Create a database directory: the shared discovery requires a `types.dat` marker. */
    private fun database(mapsRoot: Path, relativePath: String): Path {
        val directory = mapsRoot.resolve(relativePath)
        Files.createDirectories(directory)
        Files.write(directory.resolve(InstalledMaps.TYPES_DAT), "marker".toByteArray())
        return directory
    }

    @Test
    fun theInstallationsAreTheOnlyDirectoriesOpened() {
        val mapsRoot = mapsRoot()
        // A map name may contain '/', so a leftover can sit one level below the maps root.
        val region = database(mapsRoot, "berlin")
        val nested = database(mapsRoot, "iceland/iceland")
        // The staging data of an update attempt: a leftover transfer and the previous installation a
        // swap kept.
        val backup = database(mapsRoot, ".berlin.backup")
        val incoming = database(mapsRoot, "iceland/.iceland.incoming")
        // A dot directory with no staging suffix is not staging data, so it stays an installation.
        val plainDot = database(mapsRoot, ".hidden")

        val found = MapTreeDiscovery.under(mapsRoot, report = {})

        assertEquals("only installations are opened or listed", setOf(region, nested, plainDot), found.installed.toSet())
        assertEquals("the leftovers are kept apart", setOf(backup, incoming), found.staging.toSet())
    }

    @Test
    fun aLeftoverIsReportedWithTheInstallationItBelongsTo() {
        val mapsRoot = mapsRoot()
        database(mapsRoot, "berlin")
        database(mapsRoot, ".berlin.backup")
        val lines = mutableListOf<String>()

        MapTreeDiscovery.under(mapsRoot, report = { lines += it })

        assertEquals(
            listOf("map discovery: leftover staging .berlin.backup of berlin — not opened or listed"),
            lines
        )
    }

    @Test
    fun theBasemapOverlayStaysExcluded() {
        val mapsRoot = mapsRoot()
        val region = database(mapsRoot, "berlin")
        val basemap = database(mapsRoot, "basemap")

        val found = MapTreeDiscovery.under(mapsRoot, basemap, report = {})

        assertEquals(listOf(region), found.installed)
    }

    @Test
    fun aNativeInstalledListIsClassifiedByTheSameRule() {
        // The map manager's list comes from the native scan, not from the shared discovery, and carries
        // the same staging data.
        val lines = mutableListOf<String>()

        val installed = MapTreeDiscovery.installedOnly(
            listOf("/files/maps/.berlin.backup", "/files/maps/berlin", "/files/maps/.iceland.incoming"),
            report = { lines += it }
        )

        assertEquals("only the installation is listed", listOf("/files/maps/berlin"), installed)
        assertEquals(
            "every leftover the list drops is named with the installation it belongs to",
            listOf(
                "map discovery: leftover staging .berlin.backup of berlin — not opened or listed",
                "map discovery: leftover staging .iceland.incoming of iceland — not opened or listed"
            ),
            lines
        )
    }
}
