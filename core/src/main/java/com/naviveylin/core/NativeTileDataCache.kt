package com.naviveylin.core

import com.framstag.libosmscout.client.OSMScoutClient
import java.util.WeakHashMap

/**
 * Outcome of [NativeTileDataCache.apply].
 *
 * [REJECTED] is not an error: the capacity is a property of the native client, and the first surface to
 * configure it in a process decides (spec: `native-tile-data-cache` — the effective capacity is decided
 * once per client process).
 */
enum class TileCacheConfig {
    /** The value was applied to the client. */
    APPLIED,

    /** The client already carried exactly this value — nothing to do. */
    UNCHANGED,

    /** The client already carries a different value; the request was ignored. */
    REJECTED,

    /** The native call failed; rendering continues on the library default. */
    FAILED
}

/**
 * Outcome of [NativeTileDataCache.trim] — a deliberate retention release, distinct from a
 * configuration request ([TileCacheConfig]).
 */
enum class TileCacheTrim {
    /** The capacity was lowered and the native cache evicted least-recently-used tile data. */
    TRIMMED,

    /** Nothing to release: no capacity configured yet, or already at or below the target. */
    UNCHANGED,

    /** The native call failed; the capacity stands as it was. */
    FAILED
}

/**
 * Single owner of the native tile data cache capacity (spec: `native-tile-data-cache`).
 *
 * The value is a tuned constant per surface, never a user-facing setting, and every surface that opens
 * map databases must configure it: the phone map path ([PHONE_TILES]) and the car warmup
 * ([CAR_TILES]). Without a configured value the native layer falls back to its own default of
 * [LIBRARY_DEFAULT_TILES] tiles per database, which is what the car path used to run on.
 *
 * The value lives in the native client and is re-applied to **every** open database (and the basemap) on
 * each render, so it is client-global rather than per database or per surface. Two surfaces writing
 * different values in one process — Android Auto projection runs the phone UI and a car session through
 * the same client — therefore cannot keep separate capacities: the effective capacity is the **highest**
 * value any surface requested, and a request below it is reported as [TileCacheConfig.REJECTED] instead
 * of lowering it. Raising is safe (the native layer re-applies the capacity per render) and the phone's
 * larger budget is already committed whenever the phone is in the process, so the outcome no longer
 * depends on which surface opened databases first; a car-only process keeps the car capacity because
 * nothing raises it.
 *
 * Thread-safe: both callers configure from a background dispatcher and may race. The state is keyed by
 * client identity with weak keys, so a replaced client is not kept alive. No Android or logging
 * dependency: the callers map the returned [TileCacheConfig] to their own log or diagnostics stream.
 */
object NativeTileDataCache {

    /** Capacity for the phone/foldable/tablet map, tuned for the fractional-zoom tile reuse there. */
    const val PHONE_TILES: Int = 512

    /**
     * Capacity for the car surface. Smaller than [PHONE_TILES] because a head unit's RAM budget is the
     * tighter one (`TODO.md` §51 measures the lmkd frame), and larger than [LIBRARY_DEFAULT_TILES] by a
     * meaningful margin so the car does not render on the least cache. The exact number is pending a
     * `dumpsys meminfo` measurement on an AAOS device — it is this one constant.
     */
    const val CAR_TILES: Int = 128

    /** libosmscout's own per-database default, used when no capacity is ever configured. */
    const val LIBRARY_DEFAULT_TILES: Int = 25

    /** Configured capacity per client (identity, weak so a rebuilt client does not pin the old one). */
    private val configuredByClient = WeakHashMap<Any, Int>()

    /**
     * True when some surface has configured a capacity in this process, i.e. when a native client
     * exists (a capacity is only ever recorded by a caller that already holds the client instance).
     * The retention release reads it to avoid *building* a client from a lifecycle callback.
     */
    fun isConfigured(): Boolean =
        synchronized(configuredByClient) { configuredByClient.isNotEmpty() }

    /**
     * The capacity currently recorded for [client], or null when it was never configured (the client
     * then runs on the library default). The retention release computes its target from this.
     */
    fun currentCapacity(client: OSMScoutClient): Int? = capacityOf(client)

    /** [currentCapacity] keyed by client identity; testable without a native client. */
    internal fun capacityOf(key: Any): Int? =
        synchronized(configuredByClient) { configuredByClient[key] }

    /**
     * Configure [client]'s tile data cache with [tiles]. A value equal to the current one is
     * [TileCacheConfig.UNCHANGED], a **higher** value raises the capacity ([TileCacheConfig.APPLIED]),
     * and a lower value is [TileCacheConfig.REJECTED] — the effective capacity never decreases
     * (spec: `native-tile-data-cache` — the capacity is the highest any surface requested).
     *
     * @return the outcome; never throws (spec: cache configuration failure is non-fatal)
     */
    fun apply(client: OSMScoutClient, tiles: Int): TileCacheConfig =
        applyTo(client, tiles) { client.setNativeDataCacheSize(it) }

    /**
     * The policy behind [apply], decoupled from the JNI call so it is testable without a native client:
     * [key] identifies the client, [setSize] performs the native call.
     */
    internal fun applyTo(key: Any, tiles: Int, setSize: (Int) -> Unit): TileCacheConfig {
        require(tiles > 0) {
            "tile data cache size must be positive (the native layer reads <= 0 as the library default)"
        }

        synchronized(configuredByClient) {
            val current = configuredByClient[key]
            if (current != null) {
                if (current == tiles) return TileCacheConfig.UNCHANGED
                // A smaller request must not shrink the client-wide capacity; the first surface
                // (or the phone, later) keeps the larger one.
                if (current > tiles) return TileCacheConfig.REJECTED
            }

            return try {
                setSize(tiles)
                configuredByClient[key] = tiles
                TileCacheConfig.APPLIED
            } catch (e: Exception) {
                // A bridge fault must not break rendering (spec: cache configuration failure is non-fatal).
                // A failed raise leaves the previous value recorded, so a later request is judged
                // against what the client actually carries.
                TileCacheConfig.FAILED
            } catch (e: UnsatisfiedLinkError) {
                // No native library (host tests, a failed load): same policy as any other failure.
                TileCacheConfig.FAILED
            }
        }
    }

    /**
     * Release retention: lower [client]'s capacity to [tiles] and let the native cache evict
     * least-recently-used tile data (spec: `native-tile-data-cache` — Retention is released under
     * platform memory pressure). This is the **only** operation allowed to lower the capacity;
     * [apply] stays raise-only for surface configuration, so a surface can never shrink another
     * surface's working set mid-session.
     *
     * The recorded capacity follows the release, so a later configuration request is judged against
     * the client's actual value (the phone's own capacity raises again after a release).
     *
     * @return the outcome; never throws (a failed release keeps the previous capacity)
     */
    fun trim(client: OSMScoutClient, tiles: Int): TileCacheTrim =
        trimTo(client, tiles) { client.setNativeDataCacheSize(it) }

    /**
     * The policy behind [trim], decoupled from the JNI call so it is testable without a native client:
     * [key] identifies the client, [setSize] performs the native call.
     */
    internal fun trimTo(key: Any, tiles: Int, setSize: (Int) -> Unit): TileCacheTrim {
        require(tiles > 0) {
            "tile data cache size must be positive (the native layer reads <= 0 as the library default)"
        }

        synchronized(configuredByClient) {
            // Nothing configured yet: the client runs on the library default, so there is no
            // retention to release and no reason to touch it.
            val current = configuredByClient[key] ?: return TileCacheTrim.UNCHANGED
            if (current <= tiles) return TileCacheTrim.UNCHANGED

            return try {
                setSize(tiles)
                configuredByClient[key] = tiles
                TileCacheTrim.TRIMMED
            } catch (e: Exception) {
                // A failed release keeps the recorded capacity, so the client's actual value and the
                // bookkeeping stay in step and a later release can be retried.
                TileCacheTrim.FAILED
            } catch (e: UnsatisfiedLinkError) {
                TileCacheTrim.FAILED
            }
        }
    }

    /**
     * Forgets every recorded decision (test-only, mirroring [RenderBitmapPool.resetForTest]). The
     * registry is process-wide by design, so a test that asserts on "no client configured" needs a
     * clean slate.
     */
    fun resetForTest() {
        synchronized(configuredByClient) { configuredByClient.clear() }
    }
}
