package com.naviveylin.di

import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoSearchProvider
import com.naviveylin.data.FavoriteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        return AutoFavoritesProvider { repository.favorites }
    }

    @Provides
    @Singleton
    fun provideAutoClientProvider(client: OSMScoutClient): AutoClientProvider {
        return AutoClientProvider { client }
    }
}
