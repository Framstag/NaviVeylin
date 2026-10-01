package com.naviveylin.di

import android.util.Log
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.auto.SessionCarSurfaceHost
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSearchProvider
import com.naviveylin.core.AutoSearchHistoryProvider
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.CarSurfaceHost
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.core.TileCacheConfig
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.StructuredAddressSearch
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.toAppSettings
import com.naviveylin.data.toAutoSettings
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationLease
import com.naviveylin.location.LocationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Provider
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
    fun provideAutoSearchProvider(
        client: Provider<OSMScoutClient>,
        regionScope: CarSearchRegionScope
    ): AutoSearchProvider {
        return AutoSearchProvider { query, limit, reference ->
            // Resolved per search, never while the provider is being resolved: the
            // native client is built on the caller's thread (spec: auto-map-renderer —
            // Renderer initialization off the car-app main thread). The region scope
            // resolves the same client lazily, on the same thread, and scopes the
            // search with the region containing the car (spec: auto-search — Search
            // scoped by the car position's admin region).
            val client = client.get()
            val adminRegionHandle = regionScope.handleForSearch()
            // Full formatted addresses resolve via the structured form search
            // first (a postal code inside the query otherwise empties the
            // native string search); structured house/street results rank
            // above raw free-text results (spec: auto-search "Full formatted
            // address resolution on car screen").
            //
            // The candidate set is larger than the displayed list so the
            // ranking can promote a perfect match the backend's own order put
            // past the page (spec: search-result-ranking).
            val raw = client.searchLocations(
                query,
                SearchResultRanker.CANDIDATE_LIMIT,
                adminRegionHandle
            )?.toList() ?: emptyList()
            val combined = StructuredAddressSearch.merge(StructuredAddressSearch.resolve(query, client), raw)
            SearchResultRanker.rank(combined, SearchQueryParser.criteriaOf(query), reference)
                .take(limit)
        }
    }

    @Provides
    @Singleton
    fun provideAutoFavoritesProvider(repository: dagger.Lazy<FavoriteRepository>): AutoFavoritesProvider {
        return AutoFavoritesProviderImpl(repository)
    }

    /**
     * Session-scoped car surface owner (spec: car-host-fault-isolation — Single-owner
     * car surface): one registration per car session, one owner screen at a time. The
     * session starts/ends it; screens attach/detach.
     */
    @Provides
    @Singleton
    fun provideCarSurfaceHost(): CarSurfaceHost = SessionCarSurfaceHost()

    @Provides
    @Singleton
    fun provideAutoClientProvider(client: Provider<OSMScoutClient>): AutoClientProvider {
        return object : AutoClientProvider {
            // The provider object itself must not touch the native client: a car
            // screen constructor resolves it, and building the client there would run
            // on the car-app host thread (spec: auto-map-renderer — Renderer
            // initialization off the car-app main thread).
            override fun client(): OSMScoutClient = client.get()
            override suspend fun openMapDatabases(filesDir: String) {
                withContext(Dispatchers.Default) {
                    // Resolved on this (background) dispatcher, not when the provider was
                    // built (spec: auto-map-renderer — Renderer initialization off the
                    // car-app main thread).
                    val client = client.get()
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
                    // One batch call for the whole discovered set: each
                    // openDatabase() call closes and reopens every open database
                    // on the database thread, so a per-directory loop costs one
                    // database-set change per directory for a single logical set
                    // (spec: native-database-open — A whole database set is
                    // registered in one coordinated change).
                    // Nothing installed: no call at all — an empty batch would
                    // still close and reopen every open database for no change.
                    if (databases.isNotEmpty()) {
                        // Configure the car surface's own tile data cache capacity
                        // before the databases open (spec: native-tile-data-cache —
                        // Car-only process does not run on the library default).
                        // Without this the car ran on the library default (25) in a
                        // car-only process. The value is client-global and the highest
                        // requested value wins (spec: native-tile-data-cache): a car
                        // request below a capacity the phone already configured is a
                        // no-op, and a phone request after this one raises the capacity.
                        when (NativeTileDataCache.apply(client, NativeTileDataCache.CAR_TILES)) {
                            TileCacheConfig.APPLIED ->
                                DiagnosticsLog.log(
                                    DiagnosticsLog.WARMUP_TAG,
                                    "Tile data cache configured: ${NativeTileDataCache.CAR_TILES} tiles"
                                )
                            TileCacheConfig.UNCHANGED -> Unit
                            TileCacheConfig.REJECTED ->
                                DiagnosticsLog.log(
                                    DiagnosticsLog.WARMUP_TAG,
                                    "Tile data cache kept at the higher value already configured " +
                                            "(car requested ${NativeTileDataCache.CAR_TILES})"
                                )
                            TileCacheConfig.FAILED ->
                                DiagnosticsLog.log(
                                    DiagnosticsLog.WARMUP_TAG,
                                    "Tile data cache configuration failed"
                                )
                        }
                        try {
                            val registered = client.openDatabases(databases.toTypedArray())
                            val registeredCount = registered.count { it }
                            Log.i(TAG, "openDatabases -> $registeredCount/${databases.size} registered")
                            DiagnosticsLog.log(
                                DiagnosticsLog.WARMUP_TAG,
                                "openDatabases -> $registeredCount/${databases.size} registered"
                            )
                            databases.forEachIndexed { index, dir ->
                                val success = registered.getOrElse(index) { false }
                                if (!success) {
                                    Log.w(TAG, "openDatabase($dir) -> not registered")
                                }
                                DiagnosticsLog.log(
                                    DiagnosticsLog.WARMUP_TAG,
                                    "openDatabase(${File(dir).name}) -> $success"
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "openDatabases failed", e)
                            DiagnosticsLog.log(
                                DiagnosticsLog.WARMUP_TAG,
                                "openDatabases failed: ${e.message}"
                            )
                        }
                    }                }
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

            /** Lease held for the live car session (spec: `location-updates-lease`). */
            private var sessionLease: LocationLease? = null

            override fun start() {
                if (sessionLease == null) {
                    sessionLease = locationService.acquire(LocationConsumers.CAR_SESSION)
                }
                if (collectJob == null) {
                    collectJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        locationService.location.collect { fix ->
                            if (fix != null) {
                                _position.value = AutoPosition(
                                    lat = fix.lat,
                                    lon = fix.lon,
                                    bearing = fix.smoothedBearing,
                                    accuracy = fix.accuracy,
                                    speedKmH = fix.speedKmH
                                )
                            }
                        }
                    }
                }
            }

            override fun stop() {
                collectJob?.cancel()
                collectJob = null
                sessionLease?.release()
                sessionLease = null
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
                // car-edited subset onto the current persisted settings, inside the
                // storage's write transaction so a concurrent phone write cannot
                // be lost (spec: `settings-persistence`). The car's own anchor
                // values are NOT part of that subset — they are written only by
                // saveCarAnchor (below), so a generic settings write cannot freeze
                // an anchor the car merely inherited from the phone.
                settingsStorage.update { current -> settings.toAppSettings(current) }
            }

            override suspend fun saveCarAnchor(routingAnchorId: String?, freeDrivingAnchorId: String?) {
                settingsStorage.update { current ->
                    current.copy(
                        autoRoutingAnchorId = routingAnchorId ?: current.autoRoutingAnchorId,
                        autoFreeDrivingAnchorId = freeDrivingAnchorId ?: current.autoFreeDrivingAnchorId
                    )
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideAutoSearchHistoryProvider(
        repository: SearchHistoryRepository
    ): AutoSearchHistoryProvider {
        return object : AutoSearchHistoryProvider {
            override suspend fun load(): List<String> {
                repository.load()
                return repository.history.value.map { it.text }
            }
        }
    }

    @Provides
    @Singleton
    fun provideDrivingModeProvider(
        impl: com.naviveylin.navigation.DrivingModeProviderImpl
    ): DrivingModeProvider = impl

    private const val TAG = "AutoServiceModule"
}
