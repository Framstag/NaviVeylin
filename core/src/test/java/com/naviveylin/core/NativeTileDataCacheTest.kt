package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the tile data cache seam ([NativeTileDataCache]) — the constants the spec constrains and the
 * once-per-client policy (spec: `native-tile-data-cache`).
 *
 * The policy is exercised through [NativeTileDataCache.applyTo], so no native client is needed: `:core`
 * has no JNI stub and `OSMScoutClient` loads the library in its static initializer. The wiring of the two
 * real surfaces is covered by `MapCanvasViewModelStyleTest` and `AutoClientProviderCacheTest` in `:app`,
 * which own the stub and the fake client.
 */
@RunWith(RobolectricTestRunner::class)
class NativeTileDataCacheTest {

    private class Recording {
        val configured = mutableListOf<Int>()
    }

    @Test
    fun capacitiesExceedTheLibraryDefaultWithAMeaningfulMargin() {
        assertTrue(
            "the phone capacity must exceed the library default",
            NativeTileDataCache.PHONE_TILES > NativeTileDataCache.LIBRARY_DEFAULT_TILES
        )
        assertTrue(
            "the car capacity must exceed the library default",
            NativeTileDataCache.CAR_TILES > NativeTileDataCache.LIBRARY_DEFAULT_TILES
        )
        assertTrue(
            "the car capacity must stay below the phone's (tighter RAM budget)",
            NativeTileDataCache.CAR_TILES < NativeTileDataCache.PHONE_TILES
        )
    }

    @Test
    fun appliesTheRequestedValue() {
        val recorded = Recording()

        val result = NativeTileDataCache.applyTo("client-a", NativeTileDataCache.CAR_TILES) {
            recorded.configured.add(it)
        }

        assertEquals(TileCacheConfig.APPLIED, result)
        assertEquals(listOf(NativeTileDataCache.CAR_TILES), recorded.configured)
    }

    @Test
    fun repeatedConfigurationWithTheSameValueIsIdempotent() {
        val recorded = Recording()

        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-b", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )
        assertEquals(
            TileCacheConfig.UNCHANGED,
            NativeTileDataCache.applyTo("client-b", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )

        assertEquals(
            "the native client must be configured exactly once",
            listOf(NativeTileDataCache.PHONE_TILES),
            recorded.configured
        )
    }

    @Test
    fun aSecondDifferentValueIsRejectedWithoutReachingTheClient() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-c", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        val result = NativeTileDataCache.applyTo("client-c", NativeTileDataCache.CAR_TILES) {
            recorded.configured.add(it)
        }

        assertEquals(TileCacheConfig.REJECTED, result)
        assertEquals(
            "a rejected request must not flip the capacity mid-session",
            listOf(NativeTileDataCache.PHONE_TILES),
            recorded.configured
        )
    }

    @Test
    fun eachClientKeepsItsOwnDecision() {
        val first = Recording()
        val second = Recording()

        // A car-only process and a phone-started process are separate clients (separate processes).
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-d", NativeTileDataCache.CAR_TILES) { first.configured.add(it) }
        )
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-e", NativeTileDataCache.PHONE_TILES) { second.configured.add(it) }
        )

        assertEquals(listOf(NativeTileDataCache.CAR_TILES), first.configured)
        assertEquals(listOf(NativeTileDataCache.PHONE_TILES), second.configured)
    }

    @Test
    fun aFailingClientIsNonFatalAndNotRemembered() {
        val attempts = mutableListOf<Int>()
        val failing = { size: Int ->
            attempts.add(size)
            throw IllegalStateException("bridge is gone")
        }

        assertEquals(
            TileCacheConfig.FAILED,
            NativeTileDataCache.applyTo("client-f", NativeTileDataCache.CAR_TILES, failing)
        )
        // The failure is not remembered, so a later attempt may still configure the client.
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-f", NativeTileDataCache.CAR_TILES) { attempts.add(it) }
        )
        assertEquals(listOf(NativeTileDataCache.CAR_TILES, NativeTileDataCache.CAR_TILES), attempts)
    }

    @Test
    fun aLinkageFailureIsNonFatal() {
        assertEquals(
            TileCacheConfig.FAILED,
            NativeTileDataCache.applyTo("client-g", NativeTileDataCache.CAR_TILES) {
                throw UnsatisfiedLinkError("no native library")
            }
        )
    }

    @Test
    fun aNonPositiveValueIsAProgrammingError() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTileDataCache.applyTo("client-h", 0) { }
        }
    }
}
