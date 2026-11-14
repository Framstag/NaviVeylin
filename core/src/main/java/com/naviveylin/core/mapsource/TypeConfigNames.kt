package com.naviveylin.core.mapsource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the type names an installed database carries in its `types.dat`.
 *
 * The file layout is the one the native reader walks in `TypeConfig::LoadFromDataFile()`
 * (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/TypeConfig.cpp:1470`): a little-endian
 * `uint32` format version, the feature records, then the type records. A type record is its name, a
 * `TypeInfo` field block (`TypeInfo::Read()`), its feature names, its groups and its per-language
 * descriptions; only the name is kept here.
 *
 * Two details of the format are load-bearing: the counts and lengths are LEB128-style variable
 * integers (`FileScanner::ReadUInt32Number()`), and a string is NUL-terminated
 * (`FileScanner::ReadString()`), so the record sizes are not fixed.
 *
 * Spec `map-updates` — "An installed database that predates the shipped stylesheet is reported"
 * (design D1 of change `report-a-stale-map-install`).
 */
object TypeConfigNames {

    /** The database file that carries a database's type configuration. */
    const val TYPES_FILE_NAME = "types.dat"

    /**
     * Bytes a `TypeInfo` record spends after its name: 13 flags, the special type, 4 more flags and
     * the two lane counters, one byte each (`TypeInfo::Read()` — 17 + 1 + 2).
     */
    private const val TYPE_INFO_FIXED_BYTES = 17 + 1 + 2

    /**
     * The type names [typesFile] carries.
     *
     * @param typesFile the database's `types.dat`
     * @param expectedFormatVersion the data format version this build reads
     *        (`MapDownloadManager.DATABASE_FORMAT_VERSION`, the same number
     *        `TypeConfig::FILE_FORMAT_VERSION` holds)
     * @return the names, or null when the file is absent, unreadable, truncated, or names another
     *         format version — a file this build cannot read must not be judged (design D1, the
     *         recorded decision on item #880: unknown, never a shortfall)
     */
    fun readNames(typesFile: Path, expectedFormatVersion: Int): Set<String>? =
        try {
            if (!Files.isRegularFile(typesFile)) {
                null
            } else {
                parse(Files.readAllBytes(typesFile), expectedFormatVersion)
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Parse the bytes of a `types.dat`.
     *
     * @return the type names, or null when the format version is not [expectedFormatVersion]
     */
    internal fun parse(bytes: ByteArray, expectedFormatVersion: Int): Set<String>? {
        val cursor = ByteCursor(bytes)
        if (cursor.readLittleEndianInt() != expectedFormatVersion) {
            return null
        }
        // Features: a name, then its per-language descriptions.
        repeat(cursor.readVarint()) {
            cursor.readString()
            repeat(cursor.readVarint()) {
                cursor.readString()
                cursor.readString()
            }
        }
        val typeCount = cursor.readVarint()
        val names = LinkedHashSet<String>(typeCount)
        repeat(typeCount) {
            val name = cursor.readString()
            cursor.skip(TYPE_INFO_FIXED_BYTES)
            // Its features.
            repeat(cursor.readVarint()) { cursor.readString() }
            // Its groups.
            repeat(cursor.readVarint()) { cursor.readString() }
            // Its per-language descriptions.
            repeat(cursor.readVarint()) {
                cursor.readString()
                cursor.readString()
            }
            names.add(name)
        }
        return names
    }

    /**
     * A cursor over the file's bytes, mirroring the four reads the native reader uses: a raw
     * little-endian `uint32`, a variable-length number, a NUL-terminated string and a fixed skip.
     *
     * Every read is bounds-checked, so a truncated or foreign file ends as an exception the caller
     * turns into "unknown" rather than as a wrong name set.
     */
    private class ByteCursor(private val bytes: ByteArray) {

        private var offset = 0

        fun readLittleEndianInt(): Int {
            require(offset + 4 <= bytes.size) { "truncated format version" }
            var value = 0
            for (byteIndex in 0 until 4) {
                value = value or ((bytes[offset + byteIndex].toInt() and 0xFF) shl (8 * byteIndex))
            }
            offset += 4
            return value
        }

        fun readVarint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                require(offset < bytes.size) { "truncated number" }
                val byte = bytes[offset++].toInt() and 0xFF
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) {
                    return result
                }
                shift += 7
                require(shift < 32) { "number longer than a uint32" }
            }
        }

        fun readString(): String {
            val start = offset
            while (offset < bytes.size && bytes[offset] != 0.toByte()) {
                offset++
            }
            require(offset < bytes.size) { "truncated string" }
            val value = String(bytes, start, offset - start, StandardCharsets.UTF_8)
            offset++
            return value
        }

        fun skip(count: Int) {
            require(offset + count <= bytes.size) { "truncated record" }
            offset += count
        }
    }
}
