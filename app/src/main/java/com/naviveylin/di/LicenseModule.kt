package com.naviveylin.di

import android.content.Context
import com.naviveylin.ui.about.AssetLicenseInventorySource
import com.naviveylin.ui.about.LicenseInventorySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the license inventory the About dialog's license screen reads.
 *
 * The inventory is generated into the APK's assets at build time from the same
 * SBOM the license gate validates.
 */
@Module
@InstallIn(SingletonComponent::class)
object LicenseModule {

    @Provides
    @Singleton
    fun provideLicenseInventorySource(@ApplicationContext context: Context): LicenseInventorySource =
        AssetLicenseInventorySource(context)
}
