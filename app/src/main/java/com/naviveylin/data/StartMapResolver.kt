package com.naviveylin.data

import android.util.Log
import com.framstag.libosmscout.client.InstalledMaps
import com.naviveylin.core.StartMapSelection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves which installed map database the phone opens as its primary map.
 *
 * One owner for the two halves of the start map (spec: `start-map-selection` — the start map comes
 * from the shared installed-map discovery; the last opened map is reopened at the next start; a
 * recorded map that is no longer installed falls back deterministically): the discovery
 * [InstalledMaps.findDatabaseDirectories] shares its rule with the additional-database batch
 * (`MapCanvasViewModel.initMap`) and the car session (`AutoServiceModule`), and the choice itself is
 * the pure [StartMapSelection.choose] rule (design D1).
 *
 * The whole body runs off the main thread (spec: `start-map-selection` — start-map resolution does
 * not block the main thread; `guidelines/Design.md` §4): the directory walk is file I/O and the
 * settings file is read under its own lock.
 */
@Singleton
class StartMapResolver @Inject constructor(
    private val settingsStorage: SettingsStorage,
    private val storageManager: MapStorageManager
) {

    /** Dispatcher for the scan and the settings read; swapped to a test dispatcher in unit tests. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * @return absolute path of the database to open, or `null` when nothing is installed — the caller
     *   then shows the map-manager entry point instead of a map screen.
     */
    suspend fun resolve(): String? = withContext(ioDispatcher) {
        val mapsRoot = storageManager.mapsRootDir
        // Same exclusion the additional-maps batch and the car session use: the basemap overlay is
        // loaded by the client's own basemap lookup directory, never as a map the user opened.
        val installed = InstalledMaps.findDatabaseDirectories(
            mapsRoot.toString(),
            mapsRoot.resolve(BASEMAP_DIR_NAME).toString()
        )
        val lastUsed = settingsStorage.load().lastMapPath
        val chosen = StartMapSelection.choose(installed, lastUsed)
        // Identity, never a position: which database was opened and how many were candidates.
        Log.d(TAG, "start map resolved: ${chosen ?: "<none>"} (recorded: ${lastUsed ?: "<none>"}) of ${installed.size} installed")
        chosen
    }

    private companion object {
        const val TAG = "StartMapResolver"

        /** Maps root child that holds the basemap package (see `MapDownloadModule`). */
        const val BASEMAP_DIR_NAME = "basemap"
    }
}
