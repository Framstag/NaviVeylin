package com.naviveylin.core.mapsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The stale-install detector: the `types.dat` reader, the declared-name set, and the three answers
 * `StaleInstallCheck` gives.
 *
 * The fixture `golden-types.dat` is a real database `types.dat` (format version 27, the one this
 * build reads — `MapDownloadManager.DATABASE_FORMAT_VERSION`, `TypeConfig::FILE_FORMAT_VERSION`) as
 * the libosmscout test region carries it, so the reader is exercised against the layout the native
 * `TypeConfig::LoadFromDataFile()` writes rather than against a shape invented here. It is what fails
 * loudly on a `FILE_FORMAT_VERSION` bump instead of every declared name reading as missing.
 *
 * Spec: map-updates — "An installed database that predates the shipped stylesheet is reported"
 * (change `report-a-stale-map-install`, design D1).
 */
class StaleInstallTest {

    /** The data format version this build reads. */
    private val expectedFormatVersion = 27

    @Test
    fun aDatabaseShortOfTheDeclaredTypesIsReported() {
        val verdict = StaleInstallCheck.of(
            databaseDirectory(goldenTypesDat()),
            DeclaredTypes.readDeclaredNames(fixture("short-of-types.ost")),
            expectedFormatVersion
        )

        assertTrue("a database short of declared names is reported, not current", verdict is InstalledTypeVerdict.Missing)
        val missing = verdict as InstalledTypeVerdict.Missing
        assertEquals(
            "the named count is the number of declared names the database lacks",
            2,
            missing.count
        )
        assertEquals(
            "the sample names the shortfall",
            listOf("amenity_theatre", "shop_bakery"),
            missing.sample
        )
    }

    @Test
    fun aDatabaseCarryingEveryDeclaredNameIsNotReported() {
        val verdict = StaleInstallCheck.of(
            databaseDirectory(goldenTypesDat()),
            DeclaredTypes.readDeclaredNames(fixture("every-declared-name.ost")),
            expectedFormatVersion
        )

        assertEquals("a database carrying every declared name is current", InstalledTypeVerdict.Current, verdict)
    }

    @Test
    fun aForeignFormatVersionIsNotJudged() {
        val verdict = StaleInstallCheck.of(
            databaseDirectory(goldenTypesDat()),
            DeclaredTypes.readDeclaredNames(fixture("short-of-types.ost")),
            expectedFormatVersion + 1
        )

        assertEquals(
            "a types.dat naming another format version is unknown, never a shortfall",
            InstalledTypeVerdict.Unknown,
            verdict
        )
    }

    @Test
    fun theGoldenTypesDataFileYieldsItsNameSet() {
        val typesFile = goldenTypesDat()

        val names = TypeConfigNames.readNames(typesFile, expectedFormatVersion)

        assertEquals(
            "the golden types.dat yields the names it carries",
            Pair(listOf("address", "aerialway_chair_lift"), 450),
            Pair(names!!.filter { it.isNotEmpty() }.sorted().take(2), names.size)
        )
        assertNull(
            "the same file read as another format version yields nothing to judge",
            TypeConfigNames.readNames(typesFile, expectedFormatVersion - 1)
        )
        assertEquals(
            "a file that is not a types.dat yields nothing to judge",
            null,
            TypeConfigNames.readNames(Paths.get("does-not-exist", "types.dat"), expectedFormatVersion)
        )
    }

    /** A directory holding the golden `types.dat`, the shape an installed database has. */
    private fun databaseDirectory(typesFile: Path): Path {
        val directory = Files.createTempDirectory("stale-install")
        Files.copy(typesFile, directory.resolve(TypeConfigNames.TYPES_FILE_NAME))
        directory.toFile().deleteOnExit()
        return directory
    }

    /** The golden `types.dat` fixture, as a path. */
    private fun goldenTypesDat(): Path =
        Paths.get(requireNotNull(javaClass.getResource("/mapsource/golden-types.dat")) { "missing golden fixture" }.toURI())

    /** A stylesheet fixture, as a path. */
    private fun fixture(name: String): Path =
        Paths.get(requireNotNull(javaClass.getResource("/mapsource/$name")) { "missing fixture $name" }.toURI())
}
