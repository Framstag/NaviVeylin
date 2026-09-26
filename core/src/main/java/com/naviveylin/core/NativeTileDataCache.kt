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
 * Single owner of the native tile data cache capacity (spec: `native-tile-data-cache`).
 *
 * The value is a tuned constant per surface, never a user-facing setting, and every surface that opens
 * map databases must configure it: the phone map path ([PHONE_TILES]) and the car warmup
 * ([CAR_TILES]). Without a configured value the native layer falls back to its own default of
 * [LIBRARY_DEFAULT_TILES] tiles per database, which is what the car path used to run on.
 *
 * The value lives in the native client and is re-applied to **every** open database (and the basemap) on
 * each render, so it is client-global rather than per database or per surface: two surfaces writing
 * different values in one process — Android Auto projection runs the phone UI and a car session through
 * the same client — would flip the capacity between renders and thrash every cache. Therefore the first
 * successful configuration for a client decides for that client's lifetime; later requests are reported
 * as [TileCacheConfig.REJECTED] or [TileCacheConfig.UNCHANGED].
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
     * Configure [client]'s tile data cache with [tiles], unless that client already carries a value.
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
                return if (current == tiles) TileCacheConfig.UNCHANGED else TileCacheConfig.REJECTED
            }

            return try {
                setSize(tiles)
                configuredByClient[key] = tiles
                TileCacheConfig.APPLIED
            } catch (e: Exception) {
                // A bridge fault must not break rendering (spec: cache configuration failure is non-fatal).
                TileCacheConfig.FAILED
            } catch (e: UnsatisfiedLinkError) {
                // No native library (host tests, a failed load): same policy as any other failure.
                TileCacheConfig.FAILED
            }
        }
    }
}
