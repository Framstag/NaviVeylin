package com.naviveylin.ui.mapmanager

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.MapDownloadListener
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.service.MapDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Availability of the world basemap on the provider server. */
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
    val error: String? = null
)

/**
 * ViewModel for the world basemap: discovery, download/update, delete,
 * and reload of the native basemap database (basemap-loading spec).
 */
@HiltViewModel
class BasemapViewModel @Inject constructor(
    private val application: Application,
    private val basemapManager: BasemapManager,
    private val client: OSMScoutClient,
    private val basemapReloadNotifier: BasemapReloadNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasemapUiState())
    val uiState: StateFlow<BasemapUiState> = _uiState.asStateFlow()

    private var downloadHandle: String? = null

    init {
        refresh()
    }

    /** Probe the server + read installed state. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = basemapManager.getInstalledBasemapInfo()
            val archives = basemapManager.fetchAvailableBasemaps()
            val updateAvailable = installed != null && basemapManager.isUpdateAvailable()
            // Archives are sorted newest-first; keep the newest archive per
            // variant (full/minimal) so the UI never lists historical builds.
            val variants = archives.groupBy { it.isMinimal }
                .values
                .map { it.first() }
                .sortedBy { it.isMinimal } // full first, then minimal
            _uiState.value = BasemapUiState(
                availability = if (archives.isEmpty()) {
                    BasemapAvailability.Unavailable
                } else {
                    BasemapAvailability.Available
                },
                archives = archives,
                variants = variants,
                installedInfo = installed,
                updateAvailable = updateAvailable
            )
        }
    }

    /** Download (or update to) the given archive. */
    fun download(archive: BasemapManager.BasemapArchive) {
        if (_uiState.value.isDownloading) return
        Log.d("BasemapVM", "download: ${archive.fileName}")
        _uiState.value = _uiState.value.copy(isDownloading = true)
        MapDownloadService.start(application)
        downloadHandle = basemapManager.downloadBasemap(archive, createDownloadListener())
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
                Log.d("BasemapVM", "onComplete: $dir")
                downloadHandle = null
                _uiState.value = _uiState.value.copy(isDownloading = false, progress = 100)
                MapDownloadService.stop(application)
                // Register the installed directory for the running session and
                // reload, so the basemap shows without an app restart
                // (spec: basemap-loading — first-time installation + re-render).
                applyBasemapChange(dir)
                refresh()
            }

            override fun onError(m: String, msg: String) {
                Log.e("BasemapVM", "onError: $msg")
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
        client.setBasemapLookupDirectory(dir)
        client.reloadBasemap()
        basemapReloadNotifier.bump()
    }

    /** Download the newest available archive. */
    fun update() {
        val latest = _uiState.value.variants.firstOrNull() ?: return
        download(latest)
    }

    fun cancel() {
        downloadHandle?.let { basemapManager.cancelDownload(it) }
        downloadHandle = null
    }

    /** Delete the installed basemap. */
    fun delete() {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = basemapManager.deleteBasemap()
            if (deleted) {
                // Unload the basemap for the running session and re-render
                // (spec: basemap-loading — delete while running).
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
}