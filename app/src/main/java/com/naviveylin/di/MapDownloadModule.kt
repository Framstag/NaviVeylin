package com.naviveylin.di

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.MapStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Files
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapDownloadModule {

    private const val DEFAULT_PROVIDER_NAME = "karry.cz"
    private const val DEFAULT_PROVIDER_URI = "https://osmscout.karry.cz"
    private const val DEFAULT_PROVIDER_LIST_URI =
        "https://osmscout.karry.cz/latest.php?fromVersion=%1&toVersion=%2&locale=%3"

    private const val RENDER_WIDTH = 864

    @Provides
    @Singleton
    fun provideOSMScoutClient(
        storageManager: MapStorageManager,
        assetCopier: AssetCopier,
        @ApplicationContext context: Context
    ): OSMScoutClient {
        val mapsDir = storageManager.mapsRootDir.toString()
        var stylesheetsDir = ""
        DiagnosticsLog.time("stylesheet sync") {
            stylesheetsDir = assetCopier.ensureStylesheets()
        }
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Stylesheets synced to $stylesheetsDir")

        val metrics: DisplayMetrics = context.resources.displayMetrics
        val physicalDpi = metrics.densityDpi.toDouble()
        Log.d("MapDownloadModule", "densityDpi=$physicalDpi, xdpi=${metrics.xdpi}, ydpi=${metrics.ydpi}")

        // Class init of OSMScoutClientBuilder triggers System.loadLibrary —
        // bracket it so a slow/failing dlopen shows up in the warmup log.
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Loading native library (OSMScoutClientBuilder class init)")
        val builder = OSMScoutClientBuilder()
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Native library loaded, builder created")
        builder
            .withMapLookupDirectories(mapsDir)
            .withPhysicalDpi(physicalDpi)
            .withFontSizeMm(2.5)
            .withStyleSheetDirectory(stylesheetsDir)
            .withCustomPoiType("_favorite")
            .withCustomPoiType("_search_selected")
            .withCustomPoiType("_route_start")
            .withCustomPoiType("_route_end")
            .withCustomPoiType("_track")

        val basemapDir = storageManager.mapsRootDir.resolve("basemap")
        if (Files.isDirectory(basemapDir)) {
            Log.d("MapDownloadModule", "basemap found at $basemapDir")
            builder.withBasemapLookupDirectory(basemapDir.toString())
        }

        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Starting native build()")
        var client: OSMScoutClient? = null
        DiagnosticsLog.time("native build") {
            client = builder.build()
        }
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "native build() returned")
        return client!!
    }

    @Provides
    @Singleton
    fun provideBasemapManager(
        storageManager: MapStorageManager,
        defaultProvider: MapProvider
    ): BasemapManager = BasemapManager(defaultProvider, storageManager.mapsRootDir)

    @Provides
    @Singleton
    fun provideMapDownloadManager(client: OSMScoutClient): MapDownloadManager =
        client.mapDownloadManager

    @Provides
    @Singleton
    fun provideDefaultMapProvider(): MapProvider =
        MapProvider(DEFAULT_PROVIDER_NAME, DEFAULT_PROVIDER_URI, DEFAULT_PROVIDER_LIST_URI)
}
