package com.naviveylin.ui.mapmanager

import android.app.Application
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryFetcher
import com.naviveylin.core.mapsource.TextFetch
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.MapDataRegistrar
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.MapUpdates
import com.naviveylin.data.SettingsStorage

/**
 * Builds [MapManagerViewModel] with its update side wired to host-safe doubles.
 *
 * The view model's update dependencies (the basemap manager, the app-scoped update check and the map-data
 * registrar) are not what most of its tests exercise, so they are filled in here with an unreachable
 * repository and a fake native client: a test that only cares about the map tree should not have to wire
 * them, and a test that does cares passes its own [updates].
 *
 * @param updates the update check under test, or one whose repository is unreachable
 * @param client the native client double the map-data registrar re-points, so a test can assert the re-open
 */
internal fun mapManagerViewModelForTest(
    application: Application,
    downloadManager: MapDownloadManager,
    storageManager: MapStorageManager,
    provider: MapProvider,
    settings: SettingsStorage,
    registry: MapSourceRegistry,
    reloadNotifier: BasemapReloadNotifier = BasemapReloadNotifier(),
    basemapRegistrar: BasemapRegistrar = BasemapRegistrar(FakeOSMScoutClient(), reloadNotifier),
    updates: MapUpdates = mapUpdatesForTest(settings, registry, storageManager),
    client: FakeOSMScoutClient = FakeOSMScoutClient()
): MapManagerViewModel = MapManagerViewModel(
    application = application,
    downloadManager = downloadManager,
    storageManager = storageManager,
    defaultProvider = provider,
    settingsStorage = settings,
    sourceRegistry = registry,
    basemapReloadNotifier = reloadNotifier,
    basemapRegistrar = basemapRegistrar,
    basemapManager = BasemapManager(provider, storageManager.mapsRootDir),
    mapUpdates = updates,
    mapDataRegistrar = MapDataRegistrar(client, storageManager, reloadNotifier)
)

/** The update check with an unreachable repository, for the tests that do not exercise it. */
internal fun mapUpdatesForTest(
    settings: SettingsStorage,
    registry: MapSourceRegistry,
    storageManager: MapStorageManager,
    fetcher: RepositoryFetcher = OfflineRepositoryFetcher(),
    timeSource: EngineTimeSource = EngineTimeSource.System
): MapUpdates = MapUpdates(
    settingsStorage = settings,
    sourceRegistry = registry,
    storageManager = storageManager,
    fetcher = fetcher,
    timeSource = timeSource
)

/** A repository that answers nothing: every request fails as a transport failure. */
internal class OfflineRepositoryFetcher : RepositoryFetcher {

    override suspend fun text(url: String): TextFetch =
        TextFetch.Failed(RepositoryFailure.TransportFailed("offline"))

    override suspend fun download(url: String, onBytes: (ByteArray, Int) -> Unit): RepositoryFailure =
        RepositoryFailure.TransportFailed("offline")
}
