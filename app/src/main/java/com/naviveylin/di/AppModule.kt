package com.naviveylin.di

import android.content.Context
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.FavoriteRepository
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
}
