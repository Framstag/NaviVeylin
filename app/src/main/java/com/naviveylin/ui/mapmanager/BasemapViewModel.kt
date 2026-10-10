package com.naviveylin.ui.mapmanager

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.MapDownloadListener
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.BasemapAvailability as RepositoryAvailability
import com.naviveylin.core.mapsource.BasemapVersion
import com.naviveylin.core.mapsource.DownloadOutcome
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.service.MapDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Availability of the world basemap on the active source. */
enum class BasemapAvailability {
    Unknown,
    Available,
    Unavailable
}

/** State for the basemap section in the map manager screen. */
data class BasemapUiState(
    val availability: BasemapAvailability = BasemapAvailability.Unknown,
    val archives: List<BasemapManager.BasemapArchive> = emptyList(),
    /** Newest archive per variant (full/minimal) for display. */
    val variants: List<BasemapManager.BasemapArchive> = emptyList(),
    val installedInfo: BasemapManager.BasemapInfo? = null,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val updateAvailable: Boolean = false,
    val error: String? = null,
    /**
     * The active map source. A repository source publishes no version comparable with an installed
     * basemap, so it reports no update state (spec `basemap-discovery` — "Source publishes no
     * comparable version"; spec `basemap-ui` — "Source reports no update state").
     */
    val source: MapSource = MapSource.BuiltInProvider,
    /** The repository's basemap version offered for download, when the active source is a repository. */
    val repositoryVersion: BasemapVersion? = null
) {

    /** True when the active source is a repository rather than the shipped provider. */
    val sourceIsRepository: Boolean
        get() = source.isRepository
}

/**
 * ViewModel for the world basemap: discovery, download/update, delete,
 * and reload of the native basemap database (basemap-loading spec).
 *
 * Which protocol obtains the basemap follows the active source: the shipped provider publishes tar.gz
 * archives, a repository publishes a version slot (spec `basemap-download` — "A tar.gz archive is not
 * used by a repository source").
 */
@HiltViewModel
class BasemapViewModel @Inject constructor(
    private val application: Application,
    private val basemapManager: BasemapManager,
    private val basemapRegistrar: BasemapRegistrar,
    private val sourceRegistry: MapSourceRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasemapUiState())
    val uiState: StateFlow<BasemapUiState> = _uiState.asStateFlow()

    /**
     * Dispatcher every probe, download and delete runs on. A test may replace it, so a probe can be
     * driven step by step rather than raced against real IO (the seam `SettingsStorage` uses).
     */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var downloadHandle: String? = null

    /** The probe whose result may still arrive; a new probe or a source change retires it. */
    private var probeJob: Job? = null

    /** Set while a repository basemap install runs, so its cancel action is honoured. */
    @Volatile
    private var repositoryInstallCancelled = false

    init {
        refresh()
    }

    /**
     * Probe the active source + read installed state.
     *
     * A result from a source that is no longer active is dropped instead of published, so a switch
     * never shows the previous source's basemap state (spec `basemap-discovery` — "Probe follows the
     * source, not the stale one").
     */
    fun refresh() {
        probeJob?.cancel()
        probeJob = viewModelScope.launch(ioDispatcher) {
            val source = sourceRegistry.activeSource()
            val installed = basemapManager.getInstalledBasemapInfo()

            if (source.isRepository) {
                refreshFromRepository(source, installed)
                return@launch
            }

            val archives = basemapManager.fetchAvailableBasemaps()
            val updateAvailable = installed != null && basemapManager.isUpdateAvailable()
            val variants = newestPerVariant(archives)
            if (registryHasMovedOn(source)) return@launch
            _uiState.value = BasemapUiState(
                availability = if (archives.isEmpty()) {
                    BasemapAvailability.Unavailable
                } else {
                    BasemapAvailability.Available
                },
                archives = archives,
                variants = variants,
                installedInfo = installed,
                updateAvailable = updateAvailable,
                source = source
            )
        }
    }

    /** Read the repository's basemap manifest; a missing one is "unavailable", never an error. */
    private suspend fun refreshFromRepository(
        source: MapSource,
        installed: BasemapManager.BasemapInfo?
    ) {
        val availability = sourceRegistry.repositoryBasemap(source).availableVersion()
        if (registryHasMovedOn(source)) return
        _uiState.value = BasemapUiState(
            availability = when (availability) {
                is RepositoryAvailability.Available -> BasemapAvailability.Available
                RepositoryAvailability.Unavailable, is RepositoryAvailability.Failed ->
                    BasemapAvailability.Unavailable
            },
            installedInfo = installed,
            updateAvailable = false,
            source = source,
            repositoryVersion = (availability as? RepositoryAvailability.Available)?.version
        )
    }

    /** True when the active source changed after [source] was read, so its result must be dropped. */
    private suspend fun registryHasMovedOn(source: MapSource): Boolean =
        !mayPublish(source)

    /**
     * Whether a probe result for [source] may be published: only when that source is still the active
     * one, so a switch never shows the previous source's basemap state (spec `basemap-discovery` —
     * "Probe follows the source, not the stale one").
     */
    @VisibleForTesting
    internal suspend fun mayPublish(source: MapSource): Boolean =
        sourceRegistry.activeSource() == source

    /** The newest archive per variant (full/minimal), so the UI never lists historical builds. */
    private fun newestPerVariant(
        archives: List<BasemapManager.BasemapArchive>
    ): List<BasemapManager.BasemapArchive> =
        archives.groupBy { it.isMinimal }
            .values
            .map { it.first() }
            .sortedBy { it.isMinimal } // full first, then minimal

    /** Download (or update to) the given archive from the shipped provider. */
    fun download(archive: BasemapManager.BasemapArchive) {
        if (_uiState.value.isDownloading) return
        Log.d(TAG, "download: ${archive.fileName}")
        _uiState.value = _uiState.value.copy(isDownloading = true)
        MapDownloadService.start(application)
        downloadHandle = basemapManager.downloadBasemap(archive, createDownloadListener())
    }

    /**
     * Install the repository's published basemap version: same verified-download path as a regional
     * database, and the installed slot replaces an existing basemap only when it is complete.
     */
    fun downloadRepositoryBasemap(version: BasemapVersion) {
        if (_uiState.value.isDownloading) return
        val source = _uiState.value.source
        if (!source.isRepository) return
        Log.d(TAG, "download repository basemap version=${version.typeConfigVersion}")
        repositoryInstallCancelled = false
        _uiState.value = _uiState.value.copy(isDownloading = true, progress = 0)
        MapDownloadService.start(application)
        val basemapDirectory = basemapManager.getBasemapDirectory()
        viewModelScope.launch(ioDispatcher) {
            val outcome = sourceRegistry.repositoryBasemap(source).install(
                version = version,
                basemapDirectory = basemapDirectory,
                isCancelled = { repositoryInstallCancelled },
                onProgress = { done, total ->
                    val pct = if (total > 0L) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                    _uiState.value = _uiState.value.copy(progress = pct)
                    MapDownloadService.update(application, 1)
                }
            )
            MapDownloadService.stop(application)
            when (outcome) {
                DownloadOutcome.Completed -> {
                    // Record the installing source in the basemap directory, so a later switch knows
                    // whose data this is (spec `map-source-selection` — "Installed map data records
                    // the source it came from").
                    if (!MapSourceMarker.write(
                            basemapDirectory,
                            MapSourceRecord(
                                sourceKind = MapSourceKind.REPOSITORY,
                                baseUrl = source.baseUrl,
                                databaseVersion = version.typeConfigVersion
                            )
                        )
                    ) {
                        Log.w(TAG, "could not record the basemap's source in $basemapDirectory")
                    }
                    _uiState.value = _uiState.value.copy(isDownloading = false, progress = 100)
                    applyBasemapChange(basemapDirectory.toString())
                    refresh()
                }
                DownloadOutcome.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isDownloading = false, progress = 0)
                    refresh()
                }
                is DownloadOutcome.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        progress = 0,
                        error = failureMessage(outcome)
                    )
                    refresh()
                }
            }
        }
    }

    /**
     * The download listener driving UI state and the native reload. Extracted
     * so the [onComplete] path is unit-testable without running a real
     * download (which starts a Hilt foreground service + native build).
     */
    @VisibleForTesting
    internal fun createDownloadListener(): MapDownloadListener =
        object : MapDownloadListener {
            override fun onProgress(m: String, bytes: Long, total: Long) {
                val pct = if (total > 0L) ((bytes * 100) / total).toInt().coerceIn(0, 99) else 0
                _uiState.value = _uiState.value.copy(progress = pct)
                MapDownloadService.update(application, 1)
            }

            override fun onComplete(m: String, dir: String) {
                Log.d(TAG, "onComplete: $dir")
                downloadHandle = null
                _uiState.value = _uiState.value.copy(isDownloading = false, progress = 100)
                MapDownloadService.stop(application)
                // The shipped provider installed this basemap; record that, so a source switch can
                // tell whose data it is (design D4). A marker that cannot be written does not fail
                // the install; the reader then attributes the directory to the built-in provider.
                if (!MapSourceMarker.write(
                        java.nio.file.Paths.get(dir),
                        MapSourceRecord(
                            sourceKind = MapSourceKind.BUILT_IN_PROVIDER,
                            baseUrl = null,
                            databaseVersion = sourceRegistry.databaseFormatVersion
                        )
                    )
                ) {
                    Log.w(TAG, "could not record the basemap's source in $dir")
                }
                // Register the installed directory for the running session and
                // reload, so the basemap shows without an app restart
                // (spec: basemap-loading — first-time installation + re-render).
                applyBasemapChange(dir)
                refresh()
            }

            override fun onError(m: String, msg: String) {
                Log.e(TAG, "onError: $msg")
                downloadHandle = null
                MapDownloadService.stop(application)
                refresh()
            }
        }

    /**
     * Register a changed basemap directory for the running session, reload
     * the native basemap database, and notify renderers to re-render
     * (spec: basemap-loading — download/update/delete while the app runs).
     * An empty [dir] unloads the basemap.
     */
    @VisibleForTesting
    internal fun applyBasemapChange(dir: String) {
        basemapRegistrar.apply(dir)
    }

    /** Download the newest available archive from the shipped provider. */
    fun update() {
        val latest = _uiState.value.variants.firstOrNull() ?: return
        download(latest)
    }

    fun cancel() {
        downloadHandle?.let { basemapManager.cancelDownload(it) }
        downloadHandle = null
        repositoryInstallCancelled = true
    }

    /** Delete the installed basemap. */
    fun delete() {
        viewModelScope.launch(ioDispatcher) {
            val deleted = basemapManager.deleteBasemap()
            if (deleted) {
                // Unload the basemap for the running session and re-render
                // (spec: basemap-loading — delete while running; basemap-ui —
                // "Basemap status clears when its source's data is deleted").
                applyBasemapChange("")
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(error = "Failed to delete basemap")
            }
        }
    }

    /** Dismiss the error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** A message a user can act on: which file failed, or that the source did not answer. */
    private fun failureMessage(outcome: DownloadOutcome.Failed): String =
        when (val failure = outcome.failure) {
            is com.naviveylin.core.mapsource.RepositoryFailure.VerificationFailed ->
                "Download of ${failure.fileName} did not match the server's checksum"
            is com.naviveylin.core.mapsource.RepositoryFailure.DatabaseNotPublished ->
                "The source does not publish this basemap version for this app"
            else -> "Basemap download failed: ${failure.javaClass.simpleName}"
        }

    private companion object {
        private const val TAG = "BasemapVM"
    }
}
