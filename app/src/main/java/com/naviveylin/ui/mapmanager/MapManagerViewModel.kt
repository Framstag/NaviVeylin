package com.naviveylin.ui.mapmanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.AvailableMapEntry
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.MapDownloadListener
import com.naviveylin.core.mapsource.BasemapAvailability as RepositoryAvailability
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.DatabaseMetadata
import com.naviveylin.core.mapsource.DatabaseMetadataResult
import com.naviveylin.core.mapsource.DatabaseMetadataParser
import com.naviveylin.core.mapsource.DownloadOutcome
import com.naviveylin.core.mapsource.IndexRegion
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.RegionIndexResult
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryDownloadPlanResult
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.MapDataRegistrar
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapSourceSwitchPlan
import com.naviveylin.data.MapSourceSwitchUseCase
import com.naviveylin.data.MapSourceSwitcher
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.MapUpdates
import com.naviveylin.data.MapUpdatesState
import com.naviveylin.data.selectedMapSource
import com.naviveylin.data.withSelectedMapSource
import com.naviveylin.R
import com.naviveylin.service.MapDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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

/** What the repository URL test action found. */
sealed interface SourceTestOutcome {

    /** The URL serves a region index with [regionCount] top-level regions and [leafCount] leaves. */
    data class Success(
        val url: String,
        val regionCount: Int,
        val leafCount: Int
    ) : SourceTestOutcome

    /** The URL does not serve a usable region index; [failure] says why and [url] names what was asked. */
    data class Failure(val url: String, val failure: RepositoryFailure) : SourceTestOutcome
}

/** What is known about a repository leaf's database metadata. */
sealed interface LeafMetadataState {

    /** The metadata is being read. */
    data object Probing : LeafMetadataState

    /** The metadata was read. */
    data class Loaded(val metadata: DatabaseMetadata) : LeafMetadataState

    /** The leaf publishes no database for this app's database format version. */
    data class NotPublished(val databaseFormatVersion: Int) : LeafMetadataState

    /** The probe failed; [failure] says why. */
    data class Failed(val failure: RepositoryFailure) : LeafMetadataState
}

/** Top-level state for the MapManagerScreen. */
data class MapManagerUiState(
    val availableEntries: List<AvailableMapEntry> = emptyList(),
    val activeDownloads: List<MapEntryState> = emptyList(),
    val installedMapPaths: Set<String> = emptySet(),
    /**
     * Which source installed each installed map directory, keyed by directory path. An unmarked
     * directory is attributed to the built-in provider (spec `map-download-ui` — "Installed maps name
     * their source").
     */
    val installedSources: Map<String, MapSourceRecord> = emptyMap(),
    /** The sources the selector offers, the built-in provider first. */
    val sources: List<MapSource> = emptyList(),
    /** The active source. */
    val activeSource: MapSource = MapSource.BuiltInProvider,
    /** The repository base URL as the user has it in the field. */
    val repositoryUrlDraft: String = "",
    /**
     * True once the user reached for the repository source while it had no URL yet: the URL field is
     * revealed so it can be entered and tested before the switch runs (spec `map-source-selection` —
     * "Base URL of the repository source is validated before use").
     */
    val repositoryUrlRevealed: Boolean = false,
    /** The last test action's outcome, or null when none ran since the URL changed. */
    val sourceTestOutcome: SourceTestOutcome? = null,
    /** True while a test action is in flight. */
    val isTestingSource: Boolean = false,
    /** A source switch waiting for confirmation, with what it would delete. */
    val pendingSourceSwitch: MapSourceSwitchPlan? = null,
    /** The source a confirmed switch would activate. */
    val pendingSourceTarget: MapSource? = null,
    /** The active repository's leaves, in index order. */
    val repositoryLeaves: List<AvailableMapEntry> = emptyList(),
    /**
     * Localized names of the active repository's region nodes, keyed by their identifier path
     * (`europe/germany`). The tree builder labels a group row from its path segment, which for a
     * repository is an identifier — this map supplies the index's name for it instead (spec
     * `map-repository-source` — "Tree rendered with localized names").
     */
    val repositoryRegionLabels: Map<String, String> = emptyMap(),
    /** What is known about each repository leaf's metadata, keyed by its identifier path. */
    val leafMetadata: Map<String, LeafMetadataState> = emptyMap(),
    val downloadingNames: Set<String> = emptySet(),
    val progressMap: Map<String, Int> = emptyMap(),
    /**
     * The update transfers of this screen, keyed by the installation's directory name. Only the transfer's
     * own state is here; its progress is shown in the active-downloads section, like the basemap's
     * (spec `map-updates` — "Updating every listed item is a queue").
     */
    val updateTransfers: Map<String, UpdateTransferState> = emptyMap(),
    /** True while the update queue runs, so its actions are not taken twice. */
    val isUpdating: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) {

    /**
     * The plain-HTTP URL the map manager must mark as unencrypted, or null when there is none (spec
     * `map-source-selection` — "An unencrypted repository source is marked as such").
     *
     * The field's value while it holds one, because that is the URL the user is working with right
     * now; otherwise the active repository source's, so a source selected earlier is still marked when
     * the field has been cleared. Derived rather than stored: a stored copy would go stale on the next
     * keystroke.
     */
    val unencryptedUrl: String?
        get() {
            val draft = RepositoryUrlPlanner.normaliseBaseUrl(repositoryUrlDraft)
            val candidate = when {
                draft.isNotBlank() -> draft
                activeSource.isRepository -> RepositoryUrlPlanner.normaliseBaseUrl(activeSource.baseUrl)
                else -> ""
            }
            return candidate.takeIf { it.isNotBlank() && RepositoryUrlPlanner.isUnencrypted(it) }
        }
}

/**
 * The state of one update transfer this screen runs.
 *
 * The progress itself is shown in the active-downloads section, not in the updates row: one transfer is
 * presented once (spec `map-updates` — "The queue is visible as a download").
 */
sealed interface UpdateTransferState {

    /** The transfer runs; [progress] is the percentage done. */
    data class Transferring(val progress: Int) : UpdateTransferState

    /** The transfer did not complete; [message] says so in the user's words and stays until dismissed. */
    data class Failed(val message: String) : UpdateTransferState
}

@HiltViewModel
class MapManagerViewModel @Inject constructor(
    private val application: Application,
    private val downloadManager: MapDownloadManager,
    private val storageManager: MapStorageManager,
    private val defaultProvider: MapProvider,
    private val settingsStorage: SettingsStorage,
    private val sourceRegistry: MapSourceRegistry,
    private val basemapReloadNotifier: BasemapReloadNotifier,
    private val basemapRegistrar: BasemapRegistrar,
    private val basemapManager: BasemapManager,
    private val mapUpdates: MapUpdates,
    private val mapDataRegistrar: MapDataRegistrar
) : ViewModel() {

    /** Plans and performs a source switch (spec `map-source-selection`). */
    private val switcher = MapSourceSwitcher(storageManager.mapsRootDir)

    /**
     * The update check is process-scoped and this screen only reads its result, so the screen collects the
     * check's own state instead of the view model mirroring it: a copy taken here would lag the check by as
     * long as its collector waits, and the surface would show a state the check has already replaced
     * (spec `map-updates` — "The check result survives a restart").
     */
    val updates: StateFlow<MapUpdatesState> = mapUpdates.state

    private val _uiState = MutableStateFlow(MapManagerUiState())
    val uiState: StateFlow<MapManagerUiState> = _uiState.asStateFlow()

    /**
     * Dispatcher every fetch, delete and source switch runs on. A test may replace it, so the work is
     * driven step by step instead of raced against real thread pools (the seam `SettingsStorage` uses).
     */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Map from map name to active download state. */
    private val downloadStates = mutableMapOf<String, MapEntryState>()

    /** Repository downloads the user cancelled, by map name; polled by the running install. */
    private val cancelledRepositoryDownloads = mutableSetOf<String>()

    /** The update transfers of this screen, keyed by the installation's directory name. */
    private val updateTransfers = mutableMapOf<String, UpdateTransferState>()

    /** Updates the user cancelled, by installation directory name; polled by the running transfer. */
    private val cancelledUpdates = mutableSetOf<String>()

    /** True while the update queue runs, so the download service is held for the whole queue. */
    @Volatile
    private var isUpdating = false

    /** Serialises the update queue: one transfer at a time, whichever action asked for it. */
    private val updateQueueMutex = Mutex()

    init {
        refreshInstalledMaps()
    }

    /** Run the update check now, whatever the daily throttle says (spec `map-updates` — the manual check). */
    fun checkForUpdates() {
        mapUpdates.checkNow()
    }

    /** Update one installation the section lists. */
    fun update(item: MapUpdateItem) {
        enqueueUpdates(listOf(item))
    }

    /** Update every installation the section lists, one transfer at a time. */
    fun updateAll() {
        enqueueUpdates(updates.value.updates)
    }

    /** Cancel one running update transfer; its partial data is removed by the install path. */
    fun cancelUpdate(directoryName: String) {
        cancelledUpdates += directoryName
    }

    /** Dismiss a failed update's message; the row returns to its update action. */
    fun dismissUpdateError(directoryName: String) {
        updateTransfers.remove(directoryName)
        publishUpdateTransfers()
    }

    /**
     * Run [items] as a queue: one transfer at a time, each cancellable on its own, a failure reported for its
     * own item and never stopping the rest (spec `map-updates` — "Updating every listed item is a queue").
     *
     * The queue recomputes the list from the source when it is done: what was just installed is no longer an
     * update, and the check is what says so.
     */
    private fun enqueueUpdates(items: List<MapUpdateItem>) {
        if (items.isEmpty()) return
        // A fresh request is the user asking for these updates now, so a cancellation of an earlier attempt
        // does not apply to it.
        cancelledUpdates.clear()
        viewModelScope.launch(ioDispatcher) {
            updateQueueMutex.withLock {
                _uiState.update { it.copy(isUpdating = true) }
                isUpdating = true
                syncUpdateService()
                try {
                    items.forEach { item -> runUpdate(item) }
                } finally {
                    isUpdating = false
                    syncUpdateService()
                    _uiState.update { it.copy(isUpdating = false) }
                }
            }
            mapUpdates.checkNow()
        }
    }

    /** Run one update transfer and record its outcome. */
    private suspend fun runUpdate(item: MapUpdateItem) {
        val source = _uiState.value.activeSource
        if (!source.isRepository) return
        if (item.directoryName in cancelledUpdates) {
            // The user cancelled this one while it was still queued: it must not run at all, or a cancel would
            // be undone by the queue reaching the item it was meant to stop.
            updateTransfers[item.directoryName] =
                UpdateTransferState.Failed(application.getString(R.string.update_cancelled))
            publishUpdateTransfers()
            return
        }
        updateTransfers[item.directoryName] = UpdateTransferState.Transferring(0)
        publishUpdateTransfers()

        val outcome = when (item.kind) {
            MapUpdateKind.REGION -> installRegionUpdate(item, source)
            MapUpdateKind.BASEMAP -> installBasemapUpdate(source)
        }

        when (outcome) {
            DownloadOutcome.Completed -> updateTransfers.remove(item.directoryName)
            else -> updateTransfers[item.directoryName] = UpdateTransferState.Failed(updateFailureText(outcome))
        }
        publishUpdateTransfers()
    }

    /**
     * Replace an installed regional database with the version the repository publishes, and let the running
     * client render it.
     */
    private suspend fun installRegionUpdate(item: MapUpdateItem, source: MapSource): DownloadOutcome {
        val idPath = item.idPath ?: return DownloadOutcome.Failed(RepositoryFailure.NoFilesPublished)
        val outcome = sourceRegistry.databaseInstaller().install(
            source = source,
            idPath = idPath,
            targetDirectory = storageManager.mapsRootDir.resolve(item.directoryName),
            isCancelled = { item.directoryName in cancelledUpdates },
            onProgress = { done, total -> publishUpdateProgress(item.directoryName, done, total) }
        )
        if (outcome == DownloadOutcome.Completed) {
            // The installed directory keeps its name and its marker; the client re-opens the set and the
            // renderers drop what was drawn from the data that is gone.
            mapDataRegistrar.apply()
        }
        return outcome
    }

    /** Replace the installed basemap with the version the repository's manifest publishes. */
    private suspend fun installBasemapUpdate(source: MapSource): DownloadOutcome {
        val basemap = sourceRegistry.repositoryBasemap(source)
        val version = when (val availability = basemap.availableVersion()) {
            is RepositoryAvailability.Available -> availability.version
            RepositoryAvailability.Unavailable, is RepositoryAvailability.Failed ->
                return DownloadOutcome.Failed(RepositoryFailure.DatabaseNotPublished)
        }
        val basemapDirectory = basemapManager.getBasemapDirectory()
        val outcome = basemap.install(
            version = version,
            basemapDirectory = basemapDirectory,
            isCancelled = { basemapDirectory.fileName.toString() in cancelledUpdates },
            onProgress = { done, total ->
                publishUpdateProgress(basemapDirectory.fileName.toString(), done, total)
            }
        )
        if (outcome == DownloadOutcome.Completed) {
            basemapRegistrar.apply(basemapDirectory.toString())
        }
        return outcome
    }

    /** Publish one transfer's progress: the row's own state, the service notification and the shared state. */
    private fun publishUpdateProgress(directoryName: String, done: Long, total: Long) {
        val percent = if (total > 0L) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
        updateTransfers[directoryName] = UpdateTransferState.Transferring(percent)
        publishUpdateTransfers()
        MapDownloadService.update(application, 1)
    }

    private fun publishUpdateTransfers() {
        _uiState.update { it.copy(updateTransfers = updateTransfers.toMap()) }
    }

    /** Hold the download service for the whole queue rather than start and stop it per item. */
    private fun syncUpdateService() {
        if (isUpdating) {
            MapDownloadService.start(application)
        } else {
            MapDownloadService.stop(application)
        }
    }

    /** The user-actionable text for an update that did not complete. */
    private fun updateFailureText(outcome: DownloadOutcome): String = when (outcome) {
        DownloadOutcome.Cancelled -> application.getString(R.string.update_cancelled)
        is DownloadOutcome.Failed -> when (val failure = outcome.failure) {
            is RepositoryFailure.VerificationFailed ->
                application.getString(R.string.map_download_checksum_failed, failure.fileName)
            RepositoryFailure.DatabaseNotPublished ->
                application.getString(R.string.map_download_not_published)
            is RepositoryFailure.HttpStatus ->
                application.getString(R.string.map_download_http_failed, failure.status)
            RepositoryFailure.InstallFailed ->
                application.getString(R.string.update_install_failed)
            else -> application.getString(R.string.map_download_failed)
        }
        DownloadOutcome.Completed -> ""
    }

    fun refreshAvailableMaps() {
        viewModelScope.launch(ioDispatcher) {
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

    /**
     * Delete one installed map directory, finishing what the native delete leaves behind.
     *
     * `MapDirectory::DeleteDatabase()` removes the files in its own lists and then needs the directory to be
     * empty (`MapDirectory.cpp:144-174`). A repository installation always holds files those lists do not
     * know — the app's `db.json` ([DatabaseMetadataParser.METADATA_FILE_NAME]) and `.source.json`
     * ([MapSourceMarker.FILE_NAME]), and the mapgen slot's `ptroutes.dat` — so the native call reports
     * failure and leaves an invalid directory behind that the app can neither use nor download again
     * (issue #119). The app therefore removes the directory tree itself, the way the basemap's delete
     * already does, and asks the map manager once more: on an absent directory `DeleteDatabase()` succeeds
     * (`RemoveFile` treats a missing file as removed, `File.cpp:42-52`), which is what drops the map
     * manager's registration (spec `map-download-infrastructure` — "Delete removes map manager
     * registration").
     *
     * Only a directory inside the maps root is swept: a path outside it is not this app's to remove
     * recursively, so a native failure there stays a failure.
     *
     * @return whether the directory is gone; an already absent directory counts as deleted
     */
    private fun deleteMapDirectory(path: String): Boolean {
        if (downloadManager.deleteMap(path)) {
            return true
        }

        val directory = Paths.get(path)
        removeDirectoryTree(directory)
        if (!Files.exists(directory)) {
            downloadManager.deleteMap(path)
            return true
        }
        return false
    }

    /**
     * Remove [directory] and everything below it, when it lies inside the maps root.
     *
     * @return whether the directory is gone afterwards
     */
    private fun removeDirectoryTree(directory: Path): Boolean {
        val root = storageManager.mapsRootDir.toAbsolutePath().normalize()
        if (!directory.toAbsolutePath().normalize().startsWith(root) || !Files.isDirectory(directory)) {
            return !Files.exists(directory)
        }
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
        return !Files.exists(directory)
    }

    fun refreshInstalledMaps() {
        viewModelScope.launch(ioDispatcher) {
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
                    installedSources = dirs.associateWith { dir ->
                        provenanceOf(dir)
                    },
                    availableEntries = merged
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(
                        R.string.map_refresh_failed,
                        e.message ?: application.getString(R.string.unknown_error)
                    )
                )
            }
        }
    }

    /** The basemap directory to register after a switch: the installed one, or blank to unload it. */
    private fun survivingBasemapDirectory(): String {
        val basemapDirectory = storageManager.mapsRootDir.resolve(
            com.naviveylin.core.mapsource.RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME
        )
        return if (java.nio.file.Files.isDirectory(basemapDirectory)) basemapDirectory.toString() else ""
    }

    /**
     * Download a repository database: read its metadata, fetch and verify exactly the files it names,
     * register the directory, and record which source installed it.
     *
     * The directory is named after the index identifier path, never the localized display name (spec
     * `map-repository-source` — "A repository database's directory name comes from the index, not the
     * display name"). Progress, cancellation and the foreground service are the same as for a provider
     * download (spec `map-download-infrastructure` — "Repository downloads join the download
     * lifecycle").
     */
    private fun downloadRepositoryMap(entry: AvailableMapEntry) {
        val source = _uiState.value.activeSource
        val idPath = entry.serverDirectory.orEmpty().split("/").filter { it.isNotBlank() }
        if (idPath.isEmpty()) return
        val mapName = entry.name
        val targetDir = storageManager.mapsRootDir.resolve(
            RepositoryUrlPlanner.databaseDirectoryName(idPath)
        )

        cancelledRepositoryDownloads.remove(mapName)
        downloadStates[mapName] = MapEntryState(
            entry = entry,
            downloadState = DownloadState.Downloading,
            progress = 0,
            statusText = ""
        )
        updateActiveDownloads()
        MapDownloadService.start(application)

        viewModelScope.launch(ioDispatcher) {
            val version = sourceRegistry.databaseFormatVersion
            when (
                val planned = sourceRegistry.repositoryLister(source).planDownload(idPath, version)
            ) {
                is RepositoryDownloadPlanResult.Refused -> failRepositoryDownload(mapName, planned.failure)
                is RepositoryDownloadPlanResult.Planned -> {
                    val outcome = sourceRegistry.databaseDownloader().download(
                        plan = planned.plan,
                        metadataDocument = planned.metadataDocument,
                        targetDirectory = targetDir,
                        isCancelled = { mapName in cancelledRepositoryDownloads },
                        onProgress = { done, total ->
                            publishProgress(mapName, done, total)
                            MapDownloadService.update(application, 1)
                        }
                    )
                    when (outcome) {
                        DownloadOutcome.Completed -> {
                            MapSourceMarker.write(
                                targetDir,
                                MapSourceRecord(
                                    sourceKind = MapSourceKind.REPOSITORY,
                                    baseUrl = source.baseUrl,
                                    databaseVersion = version
                                )
                            )
                            downloadManager.registerMapDirectory(targetDir.toString())
                            publishComplete(mapName, targetDir.toString())
                        }
                        DownloadOutcome.Cancelled -> publishError(mapName, "Download cancelled")
                        is DownloadOutcome.Failed -> failRepositoryDownload(mapName, outcome.failure)
                    }
                }
            }
        }
    }

    /** Publish a failed repository download: the entry keeps its error until the user dismisses it. */
    private fun failRepositoryDownload(mapName: String, failure: RepositoryFailure) {
        val message = when (failure) {
            is RepositoryFailure.VerificationFailed ->
                application.getString(R.string.map_download_checksum_failed, failure.fileName)
            RepositoryFailure.DatabaseNotPublished ->
                application.getString(R.string.map_download_not_published)
            is RepositoryFailure.HttpStatus ->
                application.getString(R.string.map_download_http_failed, failure.status)
            else -> application.getString(R.string.map_download_failed)
        }
        publishError(mapName, message)
    }

    fun downloadMap(entry: AvailableMapEntry) {
        val mapName = entry.name
        if (_uiState.value.activeSource.isRepository && entry.serverDirectory != null) {
            downloadRepositoryMap(entry)
            return
        }
        Log.d("MapManagerVM", "downloadMap: $mapName")
        val targetDir = storageManager.targetDirForMap(mapName)
        Log.d("MapManagerVM", "targetDir: $targetDir")

        val entryState = MapEntryState(
            entry = entry,
            downloadState = DownloadState.Downloading,
            progress = 0,
            statusText = application.getString(R.string.download_status_starting),
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
                    publishProgress(m, bytes, total)
                }

                override fun onComplete(m: String, dir: String) {
                    publishComplete(m, dir)
                }

                override fun onError(m: String, msg: String) {
                    publishError(m, msg)
                }
            })

        Log.d("MapManagerVM", "download handle: $handle")
        downloadStates[mapName]?.let { existing ->
            downloadStates[mapName] = existing.copy(downloadHandle = handle)
        }
        updateActiveDownloads()
    }

    /**
     * Report download progress for [mapName].
     *
     * Shared by the provider path (through its listener) and the repository path, so a repository
     * download shows the same inline progress as a provider download.
     */
    private fun publishProgress(mapName: String, bytes: Long, total: Long) {
        val pct = if (total > 0) {
            val raw = (bytes * 100 / total).toInt()
            if (raw >= 100) 99 else raw
        } else 0
        val existing = downloadStates[mapName] ?: return
        downloadStates[mapName] = existing.copy(
            downloadState = DownloadState.Downloading,
            progress = pct,
            statusText = if (total > 0) "$pct%" else "$bytes bytes"
        )
        // Update progress map directly in UI state for reactive updates
        _uiState.value = _uiState.value.copy(
            progressMap = _uiState.value.progressMap + (mapName to pct)
        )
        updateActiveDownloads()
    }

    /** Report a finished download for [mapName] and refresh the installed list. */
    private fun publishComplete(mapName: String, dir: String) {
        Log.d("MapManagerVM", "onComplete: $mapName at $dir")
        downloadStates.remove(mapName)
        _uiState.value = _uiState.value.copy(
            progressMap = _uiState.value.progressMap - mapName
        )
        updateActiveDownloads()
        refreshInstalledMaps()
    }

    /** Report a failed or cancelled download for [mapName]; the error stays until dismissed. */
    private fun publishError(mapName: String, msg: String) {
        Log.e("MapManagerVM", "onError: $mapName - $msg")
        val existing = downloadStates[mapName]
        if (existing != null) {
            val isCancelled = msg == "Download cancelled"
            downloadStates[mapName] = existing.copy(
                downloadState = if (isCancelled) DownloadState.Available else DownloadState.Error,
                progress = 0,
                statusText = if (isCancelled) {
                    application.getString(R.string.download_status_cancelled)
                } else {
                    application.getString(R.string.download_status_error, msg)
                },
                downloadHandle = null
            )
            if (isCancelled) downloadStates.remove(mapName)
            _uiState.value = _uiState.value.copy(
                progressMap = _uiState.value.progressMap - mapName
            )
            updateActiveDownloads()
        }
    }

    fun cancelDownload(mapName: String) {
        // A repository download has no manager handle; it observes this flag instead.
        cancelledRepositoryDownloads.add(mapName)
        val state = downloadStates[mapName]
        state?.downloadHandle?.let { handle ->
            downloadManager.cancelDownload(handle)
        }
        publishError(mapName, "Download cancelled")
    }

    fun deleteMap(mapName: String) {
        val path = storageManager.targetDirPath(mapName)
        viewModelScope.launch(ioDispatcher) {
            try {
                val deleted = deleteMapDirectory(path)
                if (deleted) {
                    refreshInstalledMaps()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = application.getString(R.string.map_delete_failed, mapName)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(
                        R.string.map_delete_failed,
                        e.message ?: application.getString(R.string.unknown_error)
                    )
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

    /**
     * Which source installed [mapName]'s directory, for the installed row's label.
     *
     * @return the marker's record, or the built-in provider when the directory carries no marker (a
     *         map installed before this change) or an unreadable one
     */
    fun installedMapSource(mapName: String): MapSourceRecord =
        provenanceOf(storageManager.targetDirForMap(mapName).toString())

    /** Read a directory's source marker; a path that cannot be read counts as the built-in provider. */
    private fun provenanceOf(directory: String): MapSourceRecord =
        runCatching { MapSourceMarker.sourceOf(Paths.get(directory)) }
            .getOrElse { MapSourceRecord.BuiltInProvider }

    /**
     * The name of the source an installed map came from, for the row's label: the repository's base
     * URL, or the shipped provider's name.
     */
    fun installedMapSourceName(mapName: String): String {
        val source = installedMapSource(mapName).toSource()
        return if (source.isRepository) source.baseUrl else defaultProvider.name
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
        // Throttle the notification updates to avoid spam; stopping is not throttled, so the service
        // never lingers after the last download ended (spec `map-download-infrastructure` — "The
        // foreground service stops when downloads end").
        if (active.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastServiceUpdateMs < 1000) return
            lastServiceUpdateMs = now
            MapDownloadService.update(application, active.size)
        } else {
            MapDownloadService.stop(application)
        }
    }

    // ── Map source selection (spec `map-source-selection`, `map-repository-source`) ──────────

    /**
     * Read the offered sources, the active one and the stored URL.
     *
     * Called when the screen opens, so a selection made earlier is visible without a refresh.
     */
    fun refreshSources() {
        viewModelScope.launch(ioDispatcher) {
            val settings = settingsStorage.load()
            _uiState.value = _uiState.value.copy(
                sources = sourceRegistry.availableSources(),
                activeSource = settings.selectedMapSource(),
                repositoryUrlDraft = settings.mapRepositoryUrl
            )
        }
    }

    /**
     * Record the URL the user typed, so it is remembered per source and a later switch back does not
     * issue a request (spec `map-download-ui` — "URL field remembers the last value per source").
     */
    fun updateRepositoryUrlDraft(url: String) {
        _uiState.value = _uiState.value.copy(
            repositoryUrlDraft = url,
            sourceTestOutcome = null
        )
        viewModelScope.launch(ioDispatcher) {
            settingsStorage.update { it.copy(mapRepositoryUrl = url) }
        }
    }

    /**
     * Test the URL in the field: fetch the region index, validate its schema and count what it offers
     * (spec `map-source-selection` — "Base URL of the repository source is validated before use").
     *
     * The outcome is published in [MapManagerUiState.sourceTestOutcome]; the active source is never
     * changed by a test.
     */
    fun testRepositoryUrl() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = _uiState.value.copy(isTestingSource = true, sourceTestOutcome = null)
            val url = _uiState.value.repositoryUrlDraft
            val outcome = when (val result = sourceRegistry.repositoryLister(MapSource.repository(url))
                .availableRegions(localeLanguage())) {
                is RegionIndexResult.Loaded -> SourceTestOutcome.Success(
                    url = RepositoryUrlPlanner.regionIndexUrl(url),
                    regionCount = result.regions.size,
                    leafCount = result.leafCount
                )
                is RegionIndexResult.Unusable -> SourceTestOutcome.Failure(
                    url = RepositoryUrlPlanner.regionIndexUrl(url),
                    failure = result.failure
                )
            }
            _uiState.value = _uiState.value.copy(isTestingSource = false, sourceTestOutcome = outcome)
        }
    }

    /**
     * Select [target] as the active source.
     *
     * When maps or a basemap of another source exist, the switch is held as a pending plan the screen
     * confirms first; otherwise it is applied immediately (spec `map-source-selection` — "Switching
     * source is gated by a confirmation naming what will be deleted").
     */
    fun selectSource(target: MapSource) {
        viewModelScope.launch(ioDispatcher) {
            val stored = settingsStorage.load()
            // A repository with no URL yet is not something to switch to: reveal the URL field and let
            // the user enter and test it first. Persisting the selection here would be reverted by the
            // empty-URL fallback on the next start (found on device 2026-10-09).
            val resolved = when {
                !target.isRepository -> target
                target.baseUrl.isNotBlank() -> target
                stored.mapRepositoryUrl.isNotBlank() -> MapSource.repository(stored.mapRepositoryUrl)
                else -> {
                    _uiState.value = _uiState.value.copy(repositoryUrlRevealed = true)
                    return@launch
                }
            }
            val plan = switcher.plan(resolved)
            if (plan.isEmpty) {
                applySourceSwitch(resolved, plan)
            } else {
                _uiState.value = _uiState.value.copy(
                    pendingSourceSwitch = plan,
                    pendingSourceTarget = resolved
                )
            }
        }
    }

    /** Apply the held switch: it removes the other source's data and persists the new selection. */
    fun confirmSourceSwitch() {
        val target = _uiState.value.pendingSourceTarget ?: return
        val plan = _uiState.value.pendingSourceSwitch ?: MapSourceSwitchPlan(emptyList(), 0L, null, 0L)
        _uiState.value = _uiState.value.copy(
            pendingSourceSwitch = null,
            pendingSourceTarget = null
        )
        viewModelScope.launch(ioDispatcher) { applySourceSwitch(target, plan) }
    }

    /** Drop the held switch: nothing is deleted and the active source stays. */
    fun cancelSourceSwitch() {
        _uiState.value = _uiState.value.copy(
            pendingSourceSwitch = null,
            pendingSourceTarget = null
        )
    }

    /**
     * Remove the other source's data, then persist the new selection.
     *
     * Maps are unregistered through the map manager (not only removed from disk); the basemap's
     * deletion fires the shared reload signal inside [MapSourceSwitcher]; the selection is persisted
     * only once the deletion finished, so a crash cannot leave data deleted under the old source.
     */
    private suspend fun applySourceSwitch(target: MapSource, plan: MapSourceSwitchPlan) {
        val previous = _uiState.value.activeSource

        val failed = MapSourceSwitchUseCase(
            switcher = switcher,
            unregister = { directory ->
                runCatching { deleteMapDirectory(directory.toString()) }
                    .onFailure { Log.w(TAG, "could not unregister ${directory.fileName}") }
            },
            persist = { source -> settingsStorage.update { it.withSelectedMapSource(source) } },
            reloadNotifier = basemapReloadNotifier
        ).switchTo(target, previous, plan)

        // The client must stop using a basemap the switch deleted — and keep using the target's, when it
        // has one (spec `basemap-ui` — "Basemap status clears when its source's data is deleted").
        basemapRegistrar.apply(survivingBasemapDirectory())

        _uiState.value = _uiState.value.copy(activeSource = target)
        if (failed.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = application.getString(
                    R.string.source_switch_delete_failed,
                    failed.joinToString { it.fileName.toString() }
                )
            )
        }
        refreshSources()
        refreshInstalledMaps()
        if (target.isRepository) {
            refreshRepositoryRegions()
        } else {
            _uiState.value = _uiState.value.copy(repositoryLeaves = emptyList(), leafMetadata = emptyMap())
            refreshAvailableMaps()
        }
    }

    /**
     * Read the active repository's region index and publish its leaves as tree entries.
     *
     * A leaf's size is not known until its metadata was read, so leaves are published with size 0 and
     * probed on demand (spec `map-repository-source` — "Database metadata is fetched on demand per
     * leaf"; design D5).
     */
    fun refreshRepositoryRegions() {
        viewModelScope.launch(ioDispatcher) {
            val source = _uiState.value.activeSource
            if (!source.isRepository) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = sourceRegistry.repositoryLister(source).availableRegions(localeLanguage())) {
                is RegionIndexResult.Loaded -> {
                    val leaves = result.regions.flatMap { it.leaves() }.map { leaf ->
                        AvailableMapEntry(
                            leaf.displayName,
                            leaf.idPath.dropLast(1),
                            "",
                            null,
                            0L,
                            leaf.idPath.joinToString("/"),
                            0L,
                            -1
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        repositoryLeaves = leaves,
                        repositoryRegionLabels = regionLabelsOf(result.regions),
                        availableEntries = leaves + _uiState.value.availableEntries
                            .filter { entry -> entry.name !in _uiState.value.repositoryLeaves.map { it.name } }
                    )
                }
                is RegionIndexResult.Unusable -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = application.getString(R.string.repository_region_index_unreadable)
                    )
                }
            }
        }
    }

    /**
     * Read one leaf's metadata from its own version slot, once.
     *
     * @param idPath the leaf's index identifier path
     */
    fun probeLeaf(idPath: List<String>) {
        val key = idPath.joinToString("/")
        val current = _uiState.value.leafMetadata[key]
        if (current is LeafMetadataState.Probing || current is LeafMetadataState.Loaded) return
        val source = _uiState.value.activeSource
        if (!source.isRepository) return
        _uiState.value = _uiState.value.copy(
            leafMetadata = _uiState.value.leafMetadata + (key to LeafMetadataState.Probing)
        )
        viewModelScope.launch(ioDispatcher) {
            val state = when (
                val result = sourceRegistry.repositoryLister(source)
                    .metadataOf(idPath, sourceRegistry.databaseFormatVersion)
            ) {
                is DatabaseMetadataResult.Loaded -> LeafMetadataState.Loaded(result.metadata)
                is DatabaseMetadataResult.Unusable -> when (result.failure) {
                    RepositoryFailure.DatabaseNotPublished ->
                        LeafMetadataState.NotPublished(sourceRegistry.databaseFormatVersion)
                    else -> LeafMetadataState.Failed(result.failure)
                }
            }
            _uiState.value = _uiState.value.copy(
                leafMetadata = _uiState.value.leafMetadata + (key to state),
                repositoryLeaves = _uiState.value.repositoryLeaves.map { entry ->
                    if (entry.serverDirectory == key && state is LeafMetadataState.Loaded) {
                        AvailableMapEntry(
                            entry.name, entry.path, entry.description, entry.provider,
                            state.metadata.totalSizeBytes, entry.serverDirectory,
                            entry.creationTimestamp, state.metadata.typeConfigVersion
                        )
                    } else {
                        entry
                    }
                },
                availableEntries = _uiState.value.availableEntries.map { entry ->
                    if (entry.serverDirectory == key && state is LeafMetadataState.Loaded) {
                        AvailableMapEntry(
                            entry.name, entry.path, entry.description, entry.provider,
                            state.metadata.totalSizeBytes, entry.serverDirectory,
                            entry.creationTimestamp, state.metadata.typeConfigVersion
                        )
                    } else {
                        entry
                    }
                }
            )
        }
    }

    /** Probe every leaf below an expanded directory that has no metadata yet. */
    fun probeLeavesUnder(directoryKey: String) {
        val leaves = _uiState.value.repositoryLeaves.filter { entry ->
            val key = entry.serverDirectory.orEmpty()
            key.startsWith("$directoryKey/") && _uiState.value.leafMetadata[key] !is LeafMetadataState.Loaded
        }
        leaves.forEach { entry -> probeLeaf(entry.serverDirectory.orEmpty().split("/")) }
    }

    /** The metadata state of a repository leaf, for its row's status text. */
    fun leafStateOf(entry: AvailableMapEntry): LeafMetadataState? =
        entry.serverDirectory?.let { _uiState.value.leafMetadata[it] }

    /** The user's language, used for the repository's localized names. */
    private fun localeLanguage(): String =
        java.util.Locale.getDefault().toLanguageTag()

    /** Flatten an index tree into `identifier path -> localized name`, for the tree's group rows. */
    private fun regionLabelsOf(regions: List<IndexRegion>): Map<String, String> {
        val labels = mutableMapOf<String, String>()
        fun walk(region: IndexRegion) {
            labels[region.idPath.joinToString("/")] = region.displayName
            region.children.forEach(::walk)
        }
        regions.forEach(::walk)
        return labels
    }

    private companion object {
        private const val TAG = "MapManagerVM"
    }
}
