package com.naviveylin.di

import android.content.Context
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.mapsource.RepositoryFetcher
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideViewportStorage(@ApplicationContext context: Context): ViewportStorage {
        return ViewportStorage(context)
    }

    @Provides
    @Singleton
    fun provideAssetCopier(@ApplicationContext context: Context): AssetCopier {
        return AssetCopier(context)
    }

    @Provides
    @Singleton
    fun provideFavoriteRepository(client: com.framstag.libosmscout.client.OSMScoutClient): FavoriteRepository {
        return FavoriteRepository(client)
    }

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService {
        return LocationService(context)
    }

    @Provides
    @Singleton
    fun provideSettingsStorage(@ApplicationContext context: Context): SettingsStorage {
        return SettingsStorage(context)
    }

    @Provides
    @Singleton
    fun provideSearchHistoryRepository(@ApplicationContext context: Context): SearchHistoryRepository {
        return SearchHistoryRepository(context)
    }

    /**
     * The engine's timing seam: components read the injected source instead of the system clock, so a
     * test moves time rather than waiting for it (spec: `navigation-engine` — Engine lifecycle and
     * threading).
     */
    @Provides
    @Singleton
    fun provideEngineTimeSource(): EngineTimeSource = EngineTimeSource.System

    /**
     * The repository protocol's network seam.
     *
     * `:core` describes the repository as a pure protocol layer driven by a `RepositoryFetcher`, so a JVM
     * test can drive it without a server; the Android implementation is `HttpUrlFetcher`. Binding the
     * interface rather than injecting that class is what lets the update check's tests serve a repository
     * from memory (spec: `map-download-infrastructure`).
     */
    @Provides
    @Singleton
    fun provideRepositoryFetcher(fetcher: HttpUrlFetcher): RepositoryFetcher = fetcher

    /**
     * The engine's dispatcher seam: native calls and reroute arithmetic run on a named pair rather
     * than on inline `Dispatchers.X` references, so a test schedules that work on its own scheduler
     * (spec: `navigation-engine` — Engine lifecycle and threading).
     */
    @Provides
    @Singleton
    fun provideEngineDispatchers(): EngineDispatchers = EngineDispatchers.Production
}
