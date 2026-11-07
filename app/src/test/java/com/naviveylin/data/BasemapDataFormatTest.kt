package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The data format probe behind the basemap admission decision (issue #23, `§99`).
 *
 * The version is the first little-endian `uint32` of `types.dat`, which is what
 * `TypeConfig::LoadFromDataFile()` compares against `FILE_FORMAT_VERSION`
 * (`app/src/main/cpp/libosmscout/guidelines/FileFormatVersion.md`), and two layouts are on devices: the
 * flattened one the tar.gz install produces and the slot-nested one the AAOS AVD of issue #23 carries.
 *
 * Pure JVM: no Android class is touched on the probe's paths.
 */
class BasemapDataFormatTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun aMissingBasemapDirectoryAdmitsNothing() {
        val absent = temporaryFolder.root.resolve("basemap")

        assertEquals(BasemapAdmission.Absent, BasemapDataFormat.admit(absent.toPath(), 27))
    }

    @Test
    fun aFlattenedBasemapOfThisBuildsFormatIsAdmitted() {
        val root = basemapRoot(version = 27)

        assertEquals(
            BasemapAdmission.Admitted(root.toPath()),
            BasemapDataFormat.admit(root.toPath(), 27)
        )
    }

    @Test
    fun aFlattenedBasemapOfAnotherFormatIsRejectedWithBothVersions() {
        val root = basemapRoot(version = 26)

        assertEquals(
            "the refusal must name the installed version and the one this build reads, or the reader " +
                "cannot tell a stale download from a defect",
            BasemapAdmission.Rejected(root.toPath(), installedVersion = 26, expectedVersion = 27),
            BasemapDataFormat.admit(root.toPath(), 27)
        )
    }

    @Test
    fun aSlotNestedBasemapOfAnotherFormatIsRejectedAtTheNestedDirectory() {
        val root = basemapRoot(version = 26, nested = true)

        assertEquals(
            BasemapAdmission.Rejected(
                root.resolve("basemap").toPath(),
                installedVersion = 26,
                expectedVersion = 27
            ),
            BasemapDataFormat.admit(root.toPath(), 27)
        )
    }

    @Test
    fun aBasemapWithoutTypesFileIsAdmittedSoTheNativeReaderStaysTheAuthority() {
        val root = temporaryFolder.newFolder("basemap")
        File(root, "water.idx").writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(BasemapAdmission.Admitted(root.toPath()), BasemapDataFormat.admit(root.toPath(), 27))
    }

    @Test
    fun aTruncatedTypesFileIsNotReadAsAVersion() {
        val root = temporaryFolder.newFolder("basemap")
        File(root, "types.dat").writeBytes(byteArrayOf(26, 0))

        assertNull("two bytes are not a uint32", BasemapDataFormat.readFormatVersion(root.toPath()))
        assertEquals(
            "an undecidable version must not refuse the basemap",
            BasemapAdmission.Admitted(root.toPath()),
            BasemapDataFormat.admit(root.toPath(), 27)
        )
    }

    @Test
    fun theVersionIsReadLittleEndian() {
        val root = temporaryFolder.newFolder("basemap")
        // 0x0000001B is 27; big-endian would read it as 452 984 832.
        File(root, "types.dat").writeBytes(byteArrayOf(0x1B, 0x00, 0x00, 0x00))

        assertEquals(27, BasemapDataFormat.readFormatVersion(root.toPath()))
    }

    @Test
    fun theShallowLayoutDecidesWhenBothLayoutsExist() {
        val root = basemapRoot(version = 27)
        val nested = File(root, "basemap").apply { mkdirs() }
        writeVersion(File(nested, "types.dat"), version = 26)

        assertEquals(root.toPath(), BasemapDataFormat.databaseDirectoryOf(root.toPath()))
        assertEquals(
            BasemapAdmission.Admitted(root.toPath()),
            BasemapDataFormat.admit(root.toPath(), 27)
        )
    }

    @Test
    fun aReadableNestedLayoutIsFoundWhenTheShallowOneCarriesNoTypesFile() {
        val root = temporaryFolder.newFolder("basemap")
        val nested = File(root, "basemap").apply { mkdirs() }
        writeVersion(File(nested, "types.dat"), version = 27)

        assertEquals(nested.toPath(), BasemapDataFormat.databaseDirectoryOf(root.toPath()))
        assertTrue(
            "a slot-nested install of this build's format is admitted",
            BasemapDataFormat.admit(root.toPath(), 27) is BasemapAdmission.Admitted
        )
    }

    /** A basemap directory whose `types.dat` names [version], flat unless [nested]. */
    private fun basemapRoot(version: Int, nested: Boolean = false): File {
        val root = temporaryFolder.newFolder("basemap")
        val databaseDirectory = if (nested) File(root, "basemap").apply { mkdirs() } else root
        writeVersion(File(databaseDirectory, "types.dat"), version)
        File(databaseDirectory, "water.idx").writeBytes(byteArrayOf(1, 2, 3))
        return root
    }

    private fun writeVersion(typesFile: File, version: Int) {
        typesFile.writeBytes(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(version).array()
        )
    }
}
