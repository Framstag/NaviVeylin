package com.naviveylin.core

import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
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

    /** Search history shared with the phone app (pull-based). */
    fun autoSearchHistoryProvider(): AutoSearchHistoryProvider

    /** Contacts with postal addresses (address-book person search). */
    fun addressBookContactsProvider(): AddressBookContactsProvider

    /** Address resolution for the address-book person search. */
    fun addressBookSearchProvider(): AddressBookSearchProvider

    /** GPS position source for the car map (AA-only process has no phone UI). */
    fun autoLocationProvider(): AutoLocationProvider

    /**
     * Real navigation controller for the AA-only process (route calc +
     * turn-by-turn). Observes itself into the shared state provider; resolve
     * it during warmup to activate navigation.
     */
    fun autoNavigationController(): AutoNavigationController

    /** Shared OSMScoutClient instance for map rendering. */
    fun autoClientProvider(): AutoClientProvider

    /** Shared navigation settings (view + edit) for the preferences screen. */
    fun autoSettingsProvider(): AutoSettingsProvider
}
