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
import com.naviveylin.data.BasemapAdmission
import com.naviveylin.data.BasemapDataFormat
import com.naviveylin.data.MapStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Path
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

        // Raster POI icons: the stylesheets request them by name, and the renderer resolves the
        // name against this directory (`path + name + ".png"`). Without it every icon-carrying
        // style fails to load, and the name-only ones draw nothing (TODO.md §85, spec `map-render`).
        var iconsDir = ""
        DiagnosticsLog.time("icon sync") {
            iconsDir = assetCopier.ensureIcons()
        }
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Icons synced to $iconsDir")

        val metrics: DisplayMetrics = context.resources.displayMetrics
        val physicalDpi = metrics.densityDpi.toDouble()
        Log.d("MapDownloadModule", "densityDpi=$physicalDpi, xdpi=${metrics.xdpi}, ydpi=${metrics.ydpi}")

        // Class init of OSMScoutClientBuilder triggers System.loadLibrary —
        // bracket it so a slow/failing dlopen shows up in the warmup log.
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Loading native library (OSMScoutClientBuilder class init)")
        val builder = OSMScoutClientBuilder()
        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Native library loaded, builder created")

        val basemapLookupDir = admittedBasemapLookupDirectory(storageManager.mapsRootDir.resolve("basemap"))
        if (basemapLookupDir != null) {
            Log.d("MapDownloadModule", "basemap found at $basemapLookupDir")
        }

        configureClient(
            builder = builder,
            mapsDir = mapsDir,
            stylesheetsDir = stylesheetsDir,
            iconsDir = iconsDir,
            physicalDpi = physicalDpi,
            basemapLookupDir = basemapLookupDir
        )

        DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "Starting native build()")
        logNativeClientBuildStart()
        var client: OSMScoutClient? = null
        DiagnosticsLog.time("native build") {
            client = builder.build()
        }
        logNativeClientBuildDone(Thread.currentThread().name)
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

/**
 * The basemap lookup directory the client may be built with, or null when there is nothing it could open.
 *
 * A basemap whose `types.dat` names another data format version is refused here rather than handed to the
 * native builder, which would fail its open at every start with the only trace in a native error line — the
 * directory still looks installed while borders, coastlines and country names silently vanish (issue #23,
 * [BasemapDataFormat]). The refusal is recorded once, as a diagnostics line naming both versions.
 *
 * @param basemapDirectory the basemap lookup directory (`{mapsDir}/basemap`)
 * @param expectedVersion the data format version this build reads
 */
internal fun admittedBasemapLookupDirectory(
    basemapDirectory: Path,
    expectedVersion: Int = MapDownloadManager.DATABASE_FORMAT_VERSION
): String? =
    when (val admission = BasemapDataFormat.admit(basemapDirectory, expectedVersion)) {
        BasemapAdmission.Absent -> null
        is BasemapAdmission.Rejected -> {
            BasemapDataFormat.logRejection(admission)
            null
        }
        is BasemapAdmission.Admitted -> admission.directory.toString()
    }

/** Map font size in millimetres (libosmscout's stylesheet unit). */
private const val FONT_SIZE_MM = 2.5

/** Name of the basemap's own stylesheet (the file name without the `.oss` postfix). */
private const val BASEMAP_STYLE_SHEET = "basemap-render"

/** Synthetic POI types the renderer draws through the stylesheets' route/marker includes. */
private val CUSTOM_POI_TYPES = listOf(
    "_favorite",
    "_search_selected",
    "_route_start",
    "_route_end",
    "_track"
)

/**
 * Applies the whole client configuration to [builder] and returns it.
 *
 * Extracted from [MapDownloadModule.provideOSMScoutClient] so the two directory seams — the
 * stylesheet directory and the icon directory the renderer resolves its named POI icons against
 * (`TODO.md` §85, spec `map-render`) — are testable without the native `build()` step, which a JVM
 * test cannot execute. [mapsDir], [stylesheetsDir] and [iconsDir] are the on-device
 * (internal-storage) directories; [basemapLookupDir] is `null` when no basemap is installed.
 */
internal fun configureClient(
    builder: OSMScoutClientBuilder,
    mapsDir: String,
    stylesheetsDir: String,
    iconsDir: String,
    physicalDpi: Double,
    basemapLookupDir: String?
): OSMScoutClientBuilder {
    builder
        .withMapLookupDirectories(mapsDir)
        .withPhysicalDpi(physicalDpi)
        .withFontSizeMm(FONT_SIZE_MM)
        .withStyleSheetDirectory(stylesheetsDir)
        .withIconDirectory(iconsDir)
        .withBasemapStyleSheet(BASEMAP_STYLE_SHEET)

    for (poiType in CUSTOM_POI_TYPES) {
        builder.withCustomPoiType(poiType)
    }

    if (basemapLookupDir != null) {
        builder.withBasemapLookupDirectory(basemapLookupDir)
    }

    return builder
}

/**
 * Record the start of the native client build **with the thread running it** (spec:
 * car-host-fault-isolation — Host interaction is diagnosable): the build syncs the
 * stylesheets, dlopens the native library and builds the client, so which thread ran it
 * is the first thing a host-crash report needs (the car-app host thread is the defect
 * this change removes).
 */
internal fun logNativeClientBuildStart() {
    DiagnosticsLog.log(
        DiagnosticsLog.WARMUP_TAG,
        "native build start thread=${Thread.currentThread().name}"
    )
}

/** Record the end of the native client build on [threadName] (duration: [DiagnosticsLog.time]). */
internal fun logNativeClientBuildDone(threadName: String) {
    DiagnosticsLog.log(DiagnosticsLog.WARMUP_TAG, "native build done thread=$threadName")
}
