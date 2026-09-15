package com.naviveylin.auto

import android.util.Log
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Initial viewport for the AA map renderer (spec: auto-map-renderer —
 * "Renderer initialization off the car-app main thread").
 */
internal data class AutoInitialViewport(val lat: Double, val lon: Double, val zoom: Int)

/**
 * Resolve the initial renderer viewport: an explicit [initialCenter] (details
 * "Show" action), else the most recently modified phone-app viewport, else
 * the first installed map's bounding box, else [defaultCenter] at
 * [defaultZoom].
 *
 * Runs on [Dispatchers.Default] — every file read and native
 * [OSMScoutClient.getDatabaseBoundingBox] query happens here, never on the
 * car-app main thread (a frozen host thread during warmup delayed template
 * delivery and every screen coroutine on cold start).
 */
internal suspend fun resolveInitialAutoViewport(
    mapsRootDir: File,
    client: OSMScoutClient,
    initialCenter: Pair<Double, Double>?,
    initialZoom: Int,
    defaultZoom: Int,
    defaultCenter: Pair<Double, Double>
): AutoInitialViewport = withContext(Dispatchers.Default) {
    initialCenter?.let { (lat, lon) ->
        Log.d(TAG, "Show map: initialCenter=$lat,$lon zoom=$initialZoom")
        return@withContext AutoInitialViewport(lat, lon, initialZoom)
    }
    latestSavedAutoViewport(mapsRootDir, defaultZoom)?.let { return@withContext it }
    firstInstalledAutoMapBbox(mapsRootDir, client)?.let { (lat, lon) ->
        return@withContext AutoInitialViewport(lat, lon, defaultZoom)
    }
    AutoInitialViewport(defaultCenter.first, defaultCenter.second, defaultZoom)
}

/**
 * Most recently modified phone-app viewport (`maps/viewport-*.json`), parsed
 * to [AutoInitialViewport], or null when none is usable.
 */
internal fun latestSavedAutoViewport(mapsRootDir: File, defaultZoom: Int): AutoInitialViewport? {
    return try {
        val file = mapsRootDir.listFiles { f ->
            f.isFile && f.name.startsWith("viewport-") && f.name.endsWith(".json")
        }?.maxByOrNull { it.lastModified() } ?: return null
        val json = JSONObject(file.readText())
        val lat = json.optDouble("centerLat", Double.NaN)
        val lon = json.optDouble("centerLon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val mag = json.optInt("magnification", defaultZoom)
            .coerceIn(AutoMapRenderer.MIN_ZOOM, AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "initial viewport from saved ${file.name}: $lat,$lon mag=$mag")
        AutoInitialViewport(lat, lon, mag)
    } catch (e: Exception) {
        Log.w(TAG, "latestSavedAutoViewport failed", e)
        null
    }
}

/**
 * Center of the first installed (non-basemap) map database's bounding box, or
 * null when none resolves (per-DB failures fall through to the next DB).
 */
internal fun firstInstalledAutoMapBbox(
    mapsRootDir: File,
    client: OSMScoutClient
): Pair<Double, Double>? {
    return try {
        // Shared with the phone app + AA warmup (InstalledMaps): recursive
        // scan for database directories, excluding the basemap overlay.
        val dirs = InstalledMaps.findDatabaseDirectories(
            mapsRootDir.absolutePath,
            File(mapsRootDir, "basemap").absolutePath
        ).sorted()
        if (dirs.isEmpty()) return null
        for (dir in dirs) {
            try {
                val bbox = client.getDatabaseBoundingBox(dir)
                if (bbox != null && bbox.size >= 4) {
                    val lat = (bbox[0] + bbox[2]) / 2.0
                    val lon = (bbox[1] + bbox[3]) / 2.0
                    Log.d(TAG, "initial viewport from map ${File(dir).name} bbox $lat,$lon")
                    return Pair(lat, lon)
                }
            } catch (e: Exception) {
                Log.w(TAG, "bbox for ${File(dir).name} failed", e)
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

private const val TAG = "AutoInitialViewport"
