package com.naviveylin.data

import com.naviveylin.core.DiagnosticsLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * The admission decision for the basemap on disk: the directory the native client may be pointed at, or the
 * reason there is none.
 *
 * The reader that opens a basemap refuses a database whose `types.dat` names another data format version
 * — `TypeConfig::LoadFromDataFile()` compares the file's first `uint32` with `FILE_FORMAT_VERSION` and the
 * database never opens (`app/src/main/cpp/libosmscout/guidelines/FileFormatVersion.md`). A library bump moves
 * that number, so a basemap downloaded before the bump is unreadable while its directory still looks installed:
 * every surface that needs it (sea/land background, borders, country names) silently degrades.
 *
 * This decision is taken here, before the directory reaches the client, so the session registers only data it
 * can open and the app records the refusal once (issue #23). It does not silence the native reader for that
 * directory: the client also scans the maps directory for databases and opens what it finds there, so the
 * native refusal line can still appear — see `design.md` D6 of change `fix-basemap-data-format-admission`.
 */
sealed interface BasemapAdmission {

    /** No basemap directory on disk. */
    data object Absent : BasemapAdmission

    /** The basemap is installed and its data format is the one this build reads (or cannot be read here). */
    data class Admitted(val directory: Path) : BasemapAdmission

    /**
     * The basemap is installed but its `types.dat` names another data format version.
     *
     * @param databaseDirectory the directory whose `types.dat` carries the version
     * @param installedVersion the data format version on disk
     * @param expectedVersion the data format version this build reads
     */
    data class Rejected(
        val databaseDirectory: Path,
        val installedVersion: Int,
        val expectedVersion: Int
    ) : BasemapAdmission
}

/**
 * Reads the data format version an installed basemap carries and compares it with the one this build reads.
 *
 * The version is the first `uint32` of `types.dat`, little-endian, which is the only database file carrying a
 * version header (`TypeConfig::LoadFromDataFile()`, `app/src/main/cpp/libosmscout/guidelines/FileFormatVersion.md`).
 * Two layouts are on devices, so both are probed: the flattened one the tar.gz install produces
 * (`basemap/types.dat`) and the slot-nested one an archive with more than one top-level entry leaves behind
 * (`basemap/basemap/types.dat`, the shape the AAOS AVD carries in issue #23).
 *
 * A directory whose version cannot be read is admitted unchanged: this probe only removes the case it can
 * decide, and the native reader stays the authority on everything else.
 */
object BasemapDataFormat {

    /** The type configuration file that carries the database's data format version. */
    const val TYPES_FILE = "types.dat"

    /** Bytes of `types.dat` that carry the version: one little-endian `uint32`. */
    private const val VERSION_BYTES = 4

    /**
     * Decide whether the basemap at [basemapDirectory] may be handed to the native client.
     *
     * @param basemapDirectory the basemap lookup directory (`{mapsDir}/basemap`)
     * @param expectedVersion the data format version this build reads
     */
    fun admit(basemapDirectory: Path, expectedVersion: Int): BasemapAdmission {
        if (!Files.isDirectory(basemapDirectory)) {
            return BasemapAdmission.Absent
        }
        val databaseDirectory = databaseDirectoryOf(basemapDirectory)
            ?: return BasemapAdmission.Admitted(basemapDirectory)
        val installedVersion = readFormatVersion(databaseDirectory)
            ?: return BasemapAdmission.Admitted(basemapDirectory)
        return if (installedVersion == expectedVersion) {
            BasemapAdmission.Admitted(basemapDirectory)
        } else {
            BasemapAdmission.Rejected(databaseDirectory, installedVersion, expectedVersion)
        }
    }

    /**
     * The directory below [basemapDirectory] whose `types.dat` carries a readable version, or null when none
     * does.
     *
     * The shallow layout is tried first, so a flattened install decides the answer when both shapes exist.
     *
     * @param basemapDirectory the basemap lookup directory
     */
    fun databaseDirectoryOf(basemapDirectory: Path): Path? =
        listOf(basemapDirectory, basemapDirectory.resolve(BASEMAP_DIR_NAME))
            .firstOrNull { readFormatVersion(it) != null }

    /**
     * The data format version [databaseDirectory]'s `types.dat` names, or null when the file is missing,
     * shorter than the version field or unreadable.
     *
     * @param databaseDirectory the database directory that holds `types.dat`
     */
    fun readFormatVersion(databaseDirectory: Path): Int? =
        try {
            Files.newByteChannel(databaseDirectory.resolve(TYPES_FILE)).use { channel ->
                readLittleEndianInt(channel)
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Record the refusal once, as one coordinate-free diagnostics line naming both versions — a database path
     * is identity, never a position (spec `auto-diagnostics`).
     *
     * @param rejected the decided refusal
     */
    fun logRejection(rejected: BasemapAdmission.Rejected) {
        DiagnosticsLog.log(
            DiagnosticsLog.WARMUP_TAG,
            "Basemap not registered: ${rejected.databaseDirectory}/$TYPES_FILE names data format " +
                "${rejected.installedVersion}, this build reads ${rejected.expectedVersion} — download the " +
                "basemap again"
        )
    }

    /** The directory name the basemap occupies below the maps root, and the one a slot archive nests. */
    private const val BASEMAP_DIR_NAME = "basemap"

    /** Read the first little-endian `uint32` of [channel], or null when the file is shorter than that. */
    private fun readLittleEndianInt(channel: SeekableByteChannel): Int? {
        val buffer = ByteBuffer.allocate(VERSION_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.hasRemaining() && channel.read(buffer) > 0) {
            // Keep reading: a channel may return fewer bytes than asked for.
        }
        return if (buffer.position() == VERSION_BYTES) buffer.getInt(0) else null
    }
}
