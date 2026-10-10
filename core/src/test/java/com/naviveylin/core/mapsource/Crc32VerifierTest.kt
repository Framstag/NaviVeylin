package com.naviveylin.core.mapsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.zip.CRC32

/**
 * Unit tests for the streaming verification of a downloaded file.
 *
 * Spec: map-repository-source — "Verified download completes" / "A corrupt file fails the
 * download". The check value used here is the standard CRC-32 check value for "123456789"
 * (0xCBF43926), the same value the library's `osmscout::Crc32` is verified against
 * (`Documentation/MapRepository.md` §3).
 */
class Crc32VerifierTest {

    private val standardCheckValue = 0xCBF43926L

    @Test
    fun verifierMatchesTheStandardCrc32Parameterisation() {
        val verifier = verifierFor("123456789")
        val bytes = "123456789".toByteArray()

        verifier.update(bytes, bytes.size)

        assertEquals(9L, verifier.writtenBytes())
        assertEquals(standardCheckValue, crcOf(bytes))
        assertNull(verifier.verify())
    }

    @Test
    fun matchPasses() {
        val body = "map data".toByteArray()

        val verifier = FileVerifier(DatabaseFile("map.lib", body.size.toLong(), crcOf(body)))
        verifier.update(body, body.size)

        assertNull(verifier.verify())
    }

    @Test
    fun mismatchIsReportedWithTheFileName() {
        val body = "map data".toByteArray()

        val verifier = FileVerifier(DatabaseFile("map.lib", body.size.toLong(), crcOf(body) + 1))
        verifier.update(body, body.size)

        assertEquals(
            RepositoryFailure.VerificationFailed("map.lib"),
            verifier.verify()
        )
    }

    @Test
    fun shortFileFailsAgainstItsSize() {
        val body = "map data".toByteArray()

        val verifier = FileVerifier(DatabaseFile("nodes.dat", body.size.toLong() + 10, crcOf(body)))
        verifier.update(body, body.size)

        assertEquals(
            RepositoryFailure.VerificationFailed("nodes.dat"),
            verifier.verify()
        )
    }

    @Test
    fun chunkedFeedEqualsOneShotFeed() {
        val body = "chunked stream body".toByteArray()

        val verifier = FileVerifier(DatabaseFile("types.dat", body.size.toLong(), crcOf(body)))
        var offset = 0
        while (offset < body.size) {
            val length = minOf(4, body.size - offset)
            verifier.update(body.copyOfRange(offset, offset + length), length)
            offset += length
        }

        assertNull(verifier.verify())
    }

    private fun verifierFor(text: String): FileVerifier {
        val bytes = text.toByteArray()
        return FileVerifier(DatabaseFile("check.dat", bytes.size.toLong(), crcOf(bytes)))
    }

    private fun crcOf(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes, 0, bytes.size)
        return crc.value
    }
}
