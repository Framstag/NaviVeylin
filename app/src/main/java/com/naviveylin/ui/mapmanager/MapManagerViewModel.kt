package com.naviveylin.ui.mapmanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.AvailableMapEntry
import com.framstag.libosmscout.client.MapDownloadListener
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.DatabaseMetadata
import com.naviveylin.core.mapsource.DatabaseMetadataResult
import com.naviveylin.core.mapsource.DownloadOutcome
import com.naviveylin.core.mapsource.IndexRegion
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.core.mapsource.RegionIndexResult
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryDownloadPlanResult
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapSourceSwitchPlan
import com.naviveylin.data.MapSourceSwitchUseCase
import com.naviveylin.data.MapSourceSwitcher
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
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
import kotlinx.coroutines.launch
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

@HiltViewModel
class MapManagerViewModel @Inject constructor(
    private val application: Application,
    private val downloadManager: MapDownloadManager,
    private val storageManager: MapStorageManager,
    private val defaultProvider: MapProvider,
    private val settingsStorage: SettingsStorage,
    private val sourceRegistry: MapSourceRegistry,
    private val basemapReloadNotifier: BasemapReloadNotifier,
    private val basemapRegistrar: BasemapRegistrar
) : ViewModel() {

    /** Plans and performs a source switch (spec `map-source-selection`). */
    private val switcher = MapSourceSwitcher(storageManager.mapsRootDir)

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

    init {
        refreshInstalledMaps()
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
                    error = "Failed to refresh installed maps: ${e.message ?: "unknown error"}"
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
                statusText = if (isCancelled) "Cancelled" else "Error: $msg",
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
                runCatching { downloadManager.deleteMap(directory.toString()) }
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
                        error = "The repository's region index could not be read"
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
