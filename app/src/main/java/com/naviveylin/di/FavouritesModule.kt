package com.naviveylin.di

import com.naviveylin.core.port.FavouritesStore
import com.naviveylin.data.FavoriteRepositoryFavouritesStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Selects the favourites store's one implementation.
 *
 * The port lives in `:core` and holds abstraction only (spec `package-structure` — port holds
 * abstraction only); the choice of implementation is shell's, which is why the binding sits here rather
 * than next to either side (spec `package-structure` — shell is the only kind that hosts several
 * features).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FavouritesModule {

    @Binds
    @Singleton
    abstract fun bindFavouritesStore(store: FavoriteRepositoryFavouritesStore): FavouritesStore
}
