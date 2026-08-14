package com.naviveylin.ui.mapmanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.AvailableMapEntry
import com.framstag.libosmscout.client.MapDownloadListener
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.data.MapStorageManager
import com.naviveylin.service.MapDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of a single map entry in the unified tree. */
data class MapEntryState(
    val entry: AvailableMapEntry,
    val downloadState: DownloadState = DownloadState.Available,
    val progress: Int = 0,       // 0-100
    val statusText: String = "",
    val downloadHandle: String? = null
)

enum class DownloadState {
    Available,
    Downloading,
    Installed,
    Error
}

/** Top-level state for the MapManagerScreen. */
data class MapManagerUiState(
    val availableEntries: List<AvailableMapEntry> = emptyList(),
    val activeDownloads: List<MapEntryState> = emptyList(),
    val installedMapPaths: Set<String> = emptySet(),
    val downloadingNames: Set<String> = emptySet(),
    val progressMap: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MapManagerViewModel @Inject constructor(
    private val application: Application,
    private val downloadManager: MapDownloadManager,
    private val storageManager: MapStorageManager,
    private val defaultProvider: MapProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapManagerUiState())
    val uiState: StateFlow<MapManagerUiState> = _uiState.asStateFlow()

    /** Map from map name to active download state. */
    private val downloadStates = mutableMapOf<String, MapEntryState>()

    init {
        refreshInstalledMaps()
    }

    fun refreshAvailableMaps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val entries = downloadManager.fetchAvailableMaps(defaultProvider)
                _uiState.value = _uiState.value.copy(
                    availableEntries = entries,
                    isLoading = false
                )
                // Merge installed maps after fetch
                refreshInstalledMaps()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to fetch map list"
                )
            }
        }
    }

    fun refreshInstalledMaps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dirs = downloadManager.installedMaps
                val pathSet = dirs.toSet()

                // Build synthetic entries for installed maps, preferring existing entry names for proper casing
                val currentEntries = _uiState.value.availableEntries
                val currentByName = currentEntries.groupBy { it.name.lowercase() }

                val synthetic = dirs.mapNotNull { dir ->
                    try {
                        val dirName = java.nio.file.Paths.get(dir).fileName.toString()
                        // Use existing entry name if available (preserves provider casing)
                        val existing = currentByName[dirName]
                        if (existing != null) {
                            existing.first()
                        } else {
                            AvailableMapEntry(dirName, emptyList(), "Installed map",
                                defaultProvider, 0L, "", 0L, -1)
                        }
                    } catch (_: Exception) { null }
                }

                // Merge: synthetic entries replace provider entries with same name (case-insensitive)
                val nonInstalled = currentEntries.filter { entry ->
                    val targetPath = storageManager.targetDirForMap(entry.name).toString()
                    targetPath !in pathSet
                }
                val merged = (synthetic + nonInstalled).distinctBy { it.name.lowercase() }

                _uiState.value = _uiState.value.copy(
                    installedMapPaths = pathSet,
                    availableEntries = merged
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to refresh installed maps: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun downloadMap(entry: AvailableMapEntry) {
        val mapName = entry.name
        Log.d("MapManagerVM", "downloadMap: $mapName")
        val targetDir = storageManager.targetDirForMap(mapName)
        Log.d("MapManagerVM", "targetDir: $targetDir")

        val entryState = MapEntryState(
            entry = entry,
            downloadState = DownloadState.Downloading,
            progress = 0,
            statusText = "Starting",
            downloadHandle = null
        )
        downloadStates[mapName] = entryState
        updateActiveDownloads()

        // Start foreground service on first download
        if (downloadStates.values.any { it.downloadState == DownloadState.Downloading }) {
            MapDownloadService.start(application)
        }

        val handle = downloadManager.downloadMap(entry, targetDir,
            object : MapDownloadListener {
                private var lastLogTime = 0L

                override fun onProgress(m: String, bytes: Long, total: Long) {
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 2000) {
                        Log.d("MapManagerVM", "onProgress: $m $bytes/$total")
                        lastLogTime = now
                    }
                    val pct = if (total > 0) {
                        val raw = (bytes * 100 / total).toInt()
                        if (raw >= 100) 99 else raw
                    } else 0
                    val existing = downloadStates[m] ?: return
                    downloadStates[m] = existing.copy(
                        downloadState = DownloadState.Downloading,
                        progress = pct,
                        statusText = if (total > 0) "$pct%" else "$bytes bytes"
                    )
                    // Update progress map directly in UI state for reactive updates
                    _uiState.value = _uiState.value.copy(
                        progressMap = _uiState.value.progressMap + (m to pct)
                    )
                    updateActiveDownloads()
                }

                override fun onComplete(m: String, dir: String) {
                    Log.d("MapManagerVM", "onComplete: $m at $dir")
                    downloadStates.remove(m)
                    _uiState.value = _uiState.value.copy(
                        progressMap = _uiState.value.progressMap - m
                    )
                    updateActiveDownloads()
                    refreshInstalledMaps()
                }

                override fun onError(m: String, msg: String) {
                    Log.e("MapManagerVM", "onError: $m - $msg")
                    val existing = downloadStates[m]
                    if (existing != null) {
                        val isCancelled = msg == "Download cancelled"
                        downloadStates[m] = existing.copy(
                            downloadState = if (isCancelled) DownloadState.Available else DownloadState.Error,
                            progress = 0,
                            statusText = if (isCancelled) "Cancelled" else "Error: $msg",
                            downloadHandle = null
                        )
                        if (isCancelled) downloadStates.remove(m)
                        _uiState.value = _uiState.value.copy(
                            progressMap = _uiState.value.progressMap - m
                        )
                        updateActiveDownloads()
                    }
                }
            })

        Log.d("MapManagerVM", "download handle: $handle")
        downloadStates[mapName]?.let { existing ->
            downloadStates[mapName] = existing.copy(downloadHandle = handle)
        }
        updateActiveDownloads()
    }

    fun cancelDownload(mapName: String) {
        val state = downloadStates[mapName] ?: return
        state.downloadHandle?.let { handle ->
            downloadManager.cancelDownload(handle)
        }
    }

    fun deleteMap(mapName: String) {
        val path = storageManager.targetDirPath(mapName)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = downloadManager.deleteMap(path)
                if (deleted) {
                    refreshInstalledMaps()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete map: $mapName"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete map: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    /** Dismiss a download error task; the entry returns to the available state. */
    fun dismissError(mapName: String) {
        downloadStates.remove(mapName)
        _uiState.value = _uiState.value.copy(
            progressMap = _uiState.value.progressMap - mapName
        )
        updateActiveDownloads()
    }

    fun isMapInstalled(entry: AvailableMapEntry): Boolean {
        val targetPath = storageManager.targetDirForMap(entry.name).toString()
        return targetPath in _uiState.value.installedMapPaths
    }

    fun isMapDownloading(entry: AvailableMapEntry): Boolean {
        return entry.name in _uiState.value.downloadingNames
    }

    fun getDownloadState(entry: AvailableMapEntry): MapEntryState? {
        return downloadStates[entry.name]
    }

    /** Get the filesystem path for an installed map. */
    fun getMapPath(mapName: String): String = storageManager.targetDirPath(mapName)

    private var lastServiceUpdateMs = 0L

    private fun updateActiveDownloads() {
        val active = downloadStates.values
            .filter {
                it.downloadState == DownloadState.Downloading ||
                    it.downloadState == DownloadState.Error
            }
            .sortedBy { it.entry.name }
        _uiState.value = _uiState.value.copy(
            activeDownloads = active,
            downloadingNames = downloadStates.keys.toSet()
        )
        // Throttle service notification updates to avoid spam
        val now = System.currentTimeMillis()
        if (now - lastServiceUpdateMs < 1000) return
        lastServiceUpdateMs = now
        // Update foreground service notification
        if (active.isNotEmpty()) {
            MapDownloadService.update(application, active.size)
        } else {
            MapDownloadService.stop(application)
        }
    }
}
