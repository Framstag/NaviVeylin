package com.naviveylin.di

import com.naviveylin.core.NavigationViewModel
import com.naviveylin.navigation.NavigationStateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [NavigationStateProvider] to the [NavigationViewModel] interface
 * so that [com.naviveylin.core.AutoEntryPoint] can inject it via SingletonComponent.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationViewModelModule {

    @Binds
    abstract fun bindNavigationViewModel(
        impl: NavigationStateProvider
    ): NavigationViewModel
}
