package com.naviveylin.di

import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.navigation.NavigationStateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [NavigationStateProvider] to the [NavigationViewModel] interface
 * so that [com.naviveylin.core.AutoEntryPoint] can inject it via SingletonComponent.
 * Also binds the process-scoped car-session-presence signal (spec:
 * `car-session-presence`), published by the car session and read by the phone UI.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationViewModelModule {

    @Binds
    abstract fun bindNavigationViewModel(
        impl: NavigationStateProvider
    ): NavigationViewModel

    @Binds
    abstract fun bindCarSessionPresence(
        impl: CarSessionPresenceImpl
    ): CarSessionPresence
}
