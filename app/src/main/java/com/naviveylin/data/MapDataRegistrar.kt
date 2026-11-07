package com.naviveylin.data

import android.util.Log
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.MapTreeDiscovery
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the running native client the installed map data it must render, after an update replaced some of it,
 * and asks the renderers to re-render.
 *
 * The regional counterpart of [BasemapRegistrar], and it exists for the same reason: an update swaps a
 * database's files under a client that already has that database open, so the client has to be told to let go
 * of it. One batch call does that — `openDatabases` closes and reopens the whole set as one coordinated change
 * (spec `native-database-open`), so the replaced database is re-read and the set a render, a search or a
 * routing call sees stays complete. The reload signal then drops the cached tiles that were drawn from the
 * data that is gone (spec `map-updates` — "An installed update is rendered without an app restart";
 * `guidelines/MapRendering.md` §5).
 *
 * Called from a background dispatcher only: this is a native call, and `guidelines/Design.md` §4 forbids it on
 * the main thread and on a car host callback. Any throwable is confined here and reported, so a failed re-open
 * degrades the rendering instead of ending the process.
 */
@Singleton
class MapDataRegistrar @Inject constructor(
    private val client: OSMScoutClient,
    private val storageManager: MapStorageManager,
    private val reloadNotifier: BasemapReloadNotifier
) {

    /**
     * Re-open every installed database — the replaced one included — and tell the renderers the data changed.
     *
     * A no-op when nothing is installed: an empty set would close every database the client has open for no
     * gain.
     */
    fun apply() {
        try {
            val mapsRoot = storageManager.mapsRootDir
            // The installations only: a leftover staging directory of a failed swap holds a `types.dat`
            // too, and re-opening it would register a database the app must not render.
            val installed = MapTreeDiscovery.under(
                mapsRoot,
                mapsRoot.resolve(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME)
            ).installed.map { it.toString() }
            if (installed.isEmpty()) {
                return
            }
            client.openDatabases(installed.toTypedArray())
            reloadNotifier.bump()
        } catch (fault: Throwable) {
            Log.w(TAG, "re-opening the installed map data set failed", fault)
            DiagnosticsLog.log(TAG, "map data re-open failed: ${fault.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "MapDataRegistrar"
    }
}
