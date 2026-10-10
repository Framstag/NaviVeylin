package com.naviveylin.core.mapsource

import java.util.zip.CRC32

/**
 * Verifies one downloaded file against the size and checksum its metadata declares, while the bytes
 * are written — one read pass, no second pass over the file (design D2).
 *
 * CRC-32 here is the IEEE 802.3 table-based CRC-32, the same value `libosmscout`'s
 * `osmscout::ComputeFileCrc32` and the metadata's `crc32` field carry
 * (`Documentation/MapRepository.md` §3). A file is never installed unverified (spec
 * `map-repository-source` — "Download follows the metadata's file inventory and verifies every
 * file").
 */
class FileVerifier(private val expected: DatabaseFile) {

    private val crc32 = CRC32()
    private var writtenBytes = 0L

    /**
     * Feed the next chunk of the file.
     *
     * @param buffer the bytes read
     * @param length how many of [buffer]'s first bytes belong to the file
     */
    fun update(buffer: ByteArray, length: Int) {
        crc32.update(buffer, 0, length)
        writtenBytes += length
    }

    /** @return the number of bytes written so far */
    fun writtenBytes(): Long = writtenBytes

    /**
     * @return null when the file matched its declared size and checksum, otherwise the failure to
     *         report — naming the file, so the message can name what to fetch again
     */
    fun verify(): RepositoryFailure? {
        val declaredSize = expected.sizeBytes
        if (declaredSize != null && writtenBytes != declaredSize) {
            return RepositoryFailure.VerificationFailed(expected.name)
        }
        val declaredCrc = expected.crc32
        if (declaredCrc == null || crc32.value != declaredCrc) {
            return RepositoryFailure.VerificationFailed(expected.name)
        }
        return null
    }
}
