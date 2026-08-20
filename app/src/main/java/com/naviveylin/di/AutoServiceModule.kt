package com.naviveylin.di

import android.util.Log
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoNavigationController
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSearchProvider
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.toAppSettings
import com.naviveylin.data.toAutoSettings
import com.naviveylin.location.LocationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provides Android Auto service interfaces for search and favorites.
 * These are consumed via [com.naviveylin.core.AutoEntryPoint].
 */
@Module
@InstallIn(SingletonComponent::class)
object AutoServiceModule {

    @Provides
    @Singleton
    fun provideAutoSearchProvider(client: OSMScoutClient): AutoSearchProvider {
        return AutoSearchProvider { query, limit ->
            val results = client.searchLocations(query, limit, OSMScoutClient.NO_ADMIN_REGION)
            results?.toList() ?: emptyList()
        }
    }

    @Provides
    @Singleton
    fun provideAutoFavoritesProvider(repository: FavoriteRepository): AutoFavoritesProvider {
        return object : AutoFavoritesProvider {
            override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> =
                repository.favorites

            override suspend fun init(filePath: String): Boolean = repository.init(filePath)
        }
    }

    @Provides
    @Singleton
    fun provideAutoClientProvider(client: OSMScoutClient): AutoClientProvider {
        return object : AutoClientProvider {
            override fun client(): OSMScoutClient = client
            override suspend fun openMapDatabases(filesDir: String) {
                withContext(Dispatchers.Default) {
                    val mapsDir = File(filesDir, "maps")
                    if (!mapsDir.isDirectory) {
                        Log.w(TAG, "openMapDatabases: $mapsDir is not a directory")
                        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "No maps dir at $mapsDir")
                        return@withContext
                    }
                    // Shared with the phone app (MapCanvasViewModel.initMap):
                    // every database directory under maps/ (any depth), except
                    // the basemap overlay which the client loads via its
                    // basemap lookup directory.
                    val databases = InstalledMaps.findDatabaseDirectories(
                        mapsDir.absolutePath,
                        File(mapsDir, "basemap").absolutePath
                    )
                    DiagnosticsLog.log(
                        DiagnosticsLog.WARMUP_TAG,
                        "Installed map databases: ${databases.size} under $mapsDir"
                    )
                    for (dir in databases) {
                        try {
                            val success = client.openDatabase(dir)
                            Log.i(TAG, "openDatabase($dir) -> $success")
                            DiagnosticsLog.log(
                                DiagnosticsLog.WARMUP_TAG,
                                "openDatabase(${File(dir).name}) -> $success"
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "openDatabase failed for $dir", e)
                            DiagnosticsLog.log(
                                DiagnosticsLog.WARMUP_TAG,
                                "openDatabase(${File(dir).name}) failed: ${e.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideAutoLocationProvider(locationService: LocationService): AutoLocationProvider {
        return object : AutoLocationProvider {
            private val _position = MutableStateFlow<AutoPosition?>(null)
            private var collectJob: Job? = null

            override fun position(): StateFlow<AutoPosition?> = _position.asStateFlow()

            override fun start() {
                locationService.startLocationUpdates()
                if (collectJob == null) {
                    collectJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        locationService.location.collect { loc ->
                            if (loc != null) {
                                _position.value = AutoPosition(
                                    lat = loc.latitude,
                                    lon = loc.longitude,
                                    bearing = if (loc.hasBearing()) loc.bearing.toDouble() else Double.NaN,
                                    accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else -1.0
                                )
                            }
                        }
                    }
                }
            }

            override fun stop() {
                collectJob?.cancel()
                collectJob = null
                locationService.stopLocationUpdates()
            }
        }
    }

    @Provides
    @Singleton
    fun provideAutoSettingsProvider(settingsStorage: SettingsStorage): AutoSettingsProvider {
        return object : AutoSettingsProvider {
            override suspend fun load(): AutoSettings =
                settingsStorage.load().toAutoSettings()

            override suspend fun save(settings: AutoSettings) {
                // Preserve phone-only fields (e.g. keepScreenOn) by applying the
                // car-edited subset onto the current persisted settings.
                val current = settingsStorage.load()
                settingsStorage.save(settings.toAppSettings(current))
            }
        }
    }

    @Provides
    @Singleton
    fun provideAutoNavigationController(
        impl: com.naviveylin.navigation.AANavigationController
    ): AutoNavigationController = impl

    private const val TAG = "AutoServiceModule"
}
