package com.naviveylin.di

import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.data.AddressBookResolver
import com.naviveylin.data.ContactsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the address-book providers (contacts + address resolution)
 * consumed by the phone address-book sheet and the Android Auto
 * [com.naviveylin.auto.AddressBookScreen] via AutoEntryPoint.
 */
@Module
@InstallIn(SingletonComponent::class)
object AddressBookModule {

    @Provides
    @Singleton
    fun provideAddressBookContactsProvider(
        repository: ContactsRepository
    ): AddressBookContactsProvider = repository

    @Provides
    @Singleton
    fun provideAddressBookSearchProvider(
        resolver: AddressBookResolver
    ): AddressBookSearchProvider = resolver
}
