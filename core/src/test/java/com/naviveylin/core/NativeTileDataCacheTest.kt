package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the tile data cache seam ([NativeTileDataCache]) — the constants the spec constrains, the
 * highest-requested-value policy (spec: `native-tile-data-cache` — the effective capacity is the highest
 * any surface requested and is never lowered *by a configuration request*), and the retention release
 * that *is* allowed to lower it (spec: `native-tile-data-cache` — Retention is released under platform
 * memory pressure).
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
    fun aLowerValueIsRejectedWithoutReachingTheClient() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-c", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        val result = NativeTileDataCache.applyTo("client-c", NativeTileDataCache.CAR_TILES) {
            recorded.configured.add(it)
        }

        assertEquals(TileCacheConfig.REJECTED, result)
        assertEquals(
            "a lower request must not shrink the capacity mid-session",
            listOf(NativeTileDataCache.PHONE_TILES),
            recorded.configured
        )
    }

    @Test
    fun aLaterHigherRequestRaisesTheCapacity() {
        // The reported defect: a car session opening databases first used to leave
        // the phone rendering on the car capacity for the rest of the process.
        val recorded = Recording()

        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-raise", NativeTileDataCache.CAR_TILES) { recorded.configured.add(it) }
        )
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-raise", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )

        assertEquals(
            "the raised value must reach the client",
            listOf(NativeTileDataCache.CAR_TILES, NativeTileDataCache.PHONE_TILES),
            recorded.configured
        )
        // The raised value is now the floor: an equal request is idempotent.
        assertEquals(
            TileCacheConfig.UNCHANGED,
            NativeTileDataCache.applyTo("client-raise", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )
    }

    @Test
    fun aRejectedRequestDoesNotChangeTheFloor() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-floor", NativeTileDataCache.CAR_TILES) { recorded.configured.add(it) }
        assertEquals(
            TileCacheConfig.REJECTED,
            NativeTileDataCache.applyTo("client-floor", NativeTileDataCache.LIBRARY_DEFAULT_TILES) {
                recorded.configured.add(it)
            }
        )
        // A rejected value must not have replaced the recorded floor.
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-floor", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )
        assertEquals(
            listOf(NativeTileDataCache.CAR_TILES, NativeTileDataCache.PHONE_TILES),
            recorded.configured
        )
    }

    @Test
    fun aFailedRaiseKeepsTheRecordedValueAndCanBeRetried() {
        val attempts = mutableListOf<Int>()

        NativeTileDataCache.applyTo("client-retry", NativeTileDataCache.CAR_TILES) { attempts.add(it) }
        assertEquals(
            TileCacheConfig.FAILED,
            NativeTileDataCache.applyTo("client-retry", NativeTileDataCache.PHONE_TILES) { size ->
                attempts.add(size)
                throw IllegalStateException("bridge is gone")
            }
        )
        // The failed raise is not remembered as applied: the client is still on the
        // car value, so the phone request may be retried and is judged a raise again.
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-retry", NativeTileDataCache.PHONE_TILES) { attempts.add(it) }
        )
        assertEquals(
            listOf(NativeTileDataCache.CAR_TILES, NativeTileDataCache.PHONE_TILES, NativeTileDataCache.PHONE_TILES),
            attempts
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

    // ---------------------------------------------------------------------------------------------
    // Retention release (spec: native-tile-data-cache — Retention is released under platform memory
    // pressure). The release is the one lowering path; a configuration request stays raise-only.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aReleaseLowersTheCapacityAndReachesTheClient() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-trim", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        val result = NativeTileDataCache.trimTo("client-trim", 128) { recorded.configured.add(it) }

        assertEquals(TileCacheTrim.TRIMMED, result)
        assertEquals(
            "the lowered value must reach the client (it evicts on a smaller value)",
            listOf(NativeTileDataCache.PHONE_TILES, 128),
            recorded.configured
        )
        assertEquals(128, NativeTileDataCache.capacityOf("client-trim"))
    }

    @Test
    fun aReleaseBelowTheCurrentCapacityIsIdempotent() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-idem", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        assertEquals(TileCacheTrim.TRIMMED, NativeTileDataCache.trimTo("client-idem", 128) { recorded.configured.add(it) })
        assertEquals(
            "a second release to the same target must not reach the client again",
            TileCacheTrim.UNCHANGED,
            NativeTileDataCache.trimTo("client-idem", 128) { recorded.configured.add(it) }
        )
        assertEquals(
            TileCacheTrim.UNCHANGED,
            NativeTileDataCache.trimTo("client-idem", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        )
        assertEquals(listOf(NativeTileDataCache.PHONE_TILES, 128), recorded.configured)
    }

    @Test
    fun aReleaseWithoutAConfiguredClientDoesNothing() {
        val recorded = Recording()

        // No surface configured this client: it runs on the library default, so there is no
        // retention to release and the client must not be touched at all.
        assertEquals(
            TileCacheTrim.UNCHANGED,
            NativeTileDataCache.trimTo("client-unconfigured", 128) { recorded.configured.add(it) }
        )
        assertEquals(emptyList<Int>(), recorded.configured)
    }

    @Test
    fun aConfigurationAfterAReleaseIsJudgedAgainstTheReleasedValue() {
        val recorded = Recording()

        NativeTileDataCache.applyTo("client-after-trim", NativeTileDataCache.PHONE_TILES) { recorded.configured.add(it) }
        NativeTileDataCache.trimTo("client-after-trim", NativeTileDataCache.LIBRARY_DEFAULT_TILES) {
            recorded.configured.add(it)
        }

        // The surface keeps asking for its own constant: after a release that is a raise again, and it
        // is applied rather than rejected — the bookkeeping follows the client's actual value.
        assertEquals(
            TileCacheConfig.APPLIED,
            NativeTileDataCache.applyTo("client-after-trim", NativeTileDataCache.PHONE_TILES) {
                recorded.configured.add(it)
            }
        )
        assertEquals(
            listOf(
                NativeTileDataCache.PHONE_TILES,
                NativeTileDataCache.LIBRARY_DEFAULT_TILES,
                NativeTileDataCache.PHONE_TILES
            ),
            recorded.configured
        )
    }

    @Test
    fun aFailedReleaseKeepsTheRecordedCapacityAndCanBeRetried() {
        val attempts = mutableListOf<Int>()

        NativeTileDataCache.applyTo("client-trim-fail", NativeTileDataCache.PHONE_TILES) { attempts.add(it) }
        assertEquals(
            TileCacheTrim.FAILED,
            NativeTileDataCache.trimTo("client-trim-fail", 128) { size ->
                attempts.add(size)
                throw IllegalStateException("bridge is gone")
            }
        )
        // The client still carries the higher value, so the bookkeeping must not pretend otherwise:
        // a retry reaches the client, and an equal configuration request is still idempotent.
        assertEquals(512, NativeTileDataCache.capacityOf("client-trim-fail"))
        assertEquals(
            TileCacheTrim.TRIMMED,
            NativeTileDataCache.trimTo("client-trim-fail", 128) { attempts.add(it) }
        )
        assertEquals(listOf(512, 128, 128), attempts)
    }

    @Test
    fun aReleaseLinkageFailureIsNonFatal() {
        NativeTileDataCache.applyTo("client-trim-link", NativeTileDataCache.PHONE_TILES) { }

        assertEquals(
            TileCacheTrim.FAILED,
            NativeTileDataCache.trimTo("client-trim-link", 128) {
                throw UnsatisfiedLinkError("no native library")
            }
        )
    }

    @Test
    fun aNonPositiveReleaseTargetIsAProgrammingError() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTileDataCache.trimTo("client-trim-zero", 0) { }
        }
    }

    @Test
    fun isConfiguredTracksWhetherAClientExists() {
        NativeTileDataCache.resetForTest()
        assertFalse("no client configured in a fresh process", NativeTileDataCache.isConfigured())

        NativeTileDataCache.applyTo("client-present", NativeTileDataCache.CAR_TILES) { }

        assertTrue(NativeTileDataCache.isConfigured())
    }
}
