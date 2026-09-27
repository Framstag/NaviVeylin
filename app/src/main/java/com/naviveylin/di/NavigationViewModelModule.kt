package com.naviveylin.di

import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.navigation.NavigationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the process-scoped [NavigationEngine] to the [NavigationViewModel]
 * interface so that [com.naviveylin.core.AutoEntryPoint] and the notification
 * path inject the one engine (spec: `navigation-engine` — Exactly one navigation
 * engine per process).
 * Also binds the process-scoped car-session-presence signal (spec:
 * `car-session-presence`), published by the car session and read by the phone UI.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationViewModelModule {

    @Binds
    abstract fun bindNavigationViewModel(
        impl: NavigationEngine
    ): NavigationViewModel

    @Binds
    abstract fun bindCarSessionPresence(
        impl: CarSessionPresenceImpl
    ): CarSessionPresence
}
