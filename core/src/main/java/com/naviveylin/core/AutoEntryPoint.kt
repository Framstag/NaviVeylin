package com.naviveylin.core

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for Android Auto's [Session] to access shared dependencies.
 * Used via [EntryPointAccessors.fromApplication] with the application context.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoEntryPoint {
    fun navigationViewModel(): NavigationViewModel

    /** Search locations via [OSMScoutClient.searchLocations]. */
    fun autoSearchProvider(): AutoSearchProvider

    /** Reactive snapshot of all favorite locations, grouped by group name. */
    fun autoFavoritesProvider(): AutoFavoritesProvider

    /** Shared OSMScoutClient instance for map rendering. */
    fun autoClientProvider(): AutoClientProvider
}
