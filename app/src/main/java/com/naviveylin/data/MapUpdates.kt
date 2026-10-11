package com.naviveylin.data

import android.util.Log
import com.framstag.libosmscout.client.InstalledMaps
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.mapsource.MapUpdateCheckResult
import com.naviveylin.core.mapsource.MapUpdateChecker
import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.RepositoryFetcher
import com.naviveylin.core.mapsource.RepositoryUrlPlanner
import com.naviveylin.core.mapsource.SlotInstall
import com.naviveylin.core.mapsource.UpdateState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects newer map data in the active repository source, for the whole process.
 *
 * Two entry points, one comparison: [checkIfDue] runs the daily check on the app-start path, and
 * [checkNow] is the user asking for it. The result — when the last check completed and what it found — is
 * persisted, so a restart shows the same state without probing on the user's behalf, and it is visible to
 * both surfaces because the instance is app-scoped (spec `map-updates` — "The app checks for newer
 * repository map data once a day", "The user can run the check on demand", "The check result survives a
 * restart").
 *
 * The instance owns a process-lifetime scope with its own fault handler: the application is its owner and
 * never cancels it, and a failing check must not reach the main thread's uncaught handler and take the
 * process down (`guidelines/Design.md` §4). Every check runs on the IO dispatcher — a check reads files
 * and a network, so nothing here may run on the main thread, and the map manager only ever reads the
 * published state.
 *
 * @param settingsStorage where the result and the last completed check are persisted
 * @param sourceRegistry the source the check covers; the built-in provider has no comparable baseline, so
 *        it reports no update state and is never probed (spec `map-updates` — "The check covers the active
 *        repository source only")
 * @param storageManager the maps root, where the installations to compare live
 * @param fetcher the repository network access
 * @param timeSource the clock the check's timing decisions read, so a test moves time instead of waiting
 */
@Singleton
class MapUpdates @Inject constructor(
    private val settingsStorage: SettingsStorage,
    private val sourceRegistry: MapSourceRegistry,
    private val storageManager: MapStorageManager,
    private val fetcher: RepositoryFetcher,
    private val timeSource: EngineTimeSource
) {

    /** Dispatcher for the check's work; swapped to a test dispatcher in unit tests. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow(MapUpdatesState())

    /** The update state the map manager shows; collect it instead of asking for a check. */
    val state: StateFlow<MapUpdatesState> = _state.asStateFlow()

    /**
     * The process-lifetime fault handler: a check that throws is recorded and the process survives it
     * (`guidelines/Design.md` §4).
     */
    private val faultHandler = CoroutineExceptionHandler { _, fault ->
        Log.e(TAG, "the update check failed", fault)
        DiagnosticsLog.log(TAG, "update check failed: ${fault.javaClass.simpleName}")
    }

    /**
     * The process-lifetime scope. The application owns it and never cancels it, and every launch names
     * [ioDispatcher] so a test schedules the check on its own scheduler.
     */
    private val scope = CoroutineScope(SupervisorJob() + faultHandler)

    /** One check at a time, whichever entry point asked for it. */
    private val checkMutex = Mutex()

    /**
     * Run the daily check unless one completed within the last day, and publish the last result.
     *
     * Called from the app-start path, which only starts the work: this returns immediately and the check
     * runs on a background dispatcher (spec `map-updates` — "The app checks for newer repository map data
     * once a day"). Publishing the stored result first is what makes a start within the day show the
     * state the last check produced without probing again.
     */
    fun checkIfDue() {
        scope.launch(ioDispatcher) {
            publishStored()
            sweepStagingLeftovers()
            val lastCompleted = settingsStorage.load().mapUpdateCheckAt
            val sinceLast = timeSource.nowMillis() - lastCompleted
            if (lastCompleted > 0L && sinceLast < CHECK_INTERVAL_MS) {
                Log.d(TAG, "checkIfDue: last check was ${sinceLast}ms ago, nothing to do")
                return@launch
            }
            runCheck()
        }
    }

    /**
     * Run the check now, whatever the daily throttle says, and count it as the day's check.
     *
     * The user's request must not be answered with "already checked today" (spec `map-updates` — "The user
     * can run the check on demand").
     */
    fun checkNow() {
        scope.launch(ioDispatcher) {
            publishStored()
            runCheck()
        }
    }

    private suspend fun runCheck() {
        val source = sourceRegistry.activeSource()
        if (!source.isRepository) {
            Log.d(TAG, "runCheck: the built-in provider reports no update state")
            return
        }
        if (!checkMutex.tryLock()) {
            Log.d(TAG, "runCheck: a check is already running")
            return
        }
        try {
            _state.update { it.copy(isChecking = true, lastAttemptFailure = null) }

            val installed = installedDatabaseDirectories()
            val result = MapUpdateChecker(fetcher, sourceRegistry.databaseFormatVersion).check(
                source = source,
                installedDirectories = installed,
                basemapDirectory = installedBasemapDirectory(),
                language = Locale.getDefault().toLanguageTag()
            )

            when (result) {
                is MapUpdateCheckResult.Failed -> reportFailedAttempt(result)
                is MapUpdateCheckResult.Compared -> reportCompleted(result.items, result.leftoverStaging)
            }
        } finally {
            _state.update { it.copy(isChecking = false) }
            checkMutex.unlock()
        }
    }

    /** A comparison that could not run at all: the day's check stays due, and the report stands. */
    private suspend fun reportFailedAttempt(result: MapUpdateCheckResult.Failed) {
        reportCheck(emptyList(), emptyList(), result.failure)
        _state.update { it.copy(lastAttemptFailure = result.failure) }
    }

    /** A completed comparison: persist it, which replaces whatever the previous one found. */
    private suspend fun reportCompleted(items: List<MapUpdateItem>, leftoverStaging: List<String>) {
        val updates = items.filter { it.state is UpdateState.UpdateAvailable }
        val checkedAt = timeSource.nowMillis()
        settingsStorage.update {
            it.copy(
                mapUpdateCheckAt = checkedAt,
                mapUpdates = updates.mapNotNull { item -> item.toStored() }
            )
        }
        reportCheck(items, leftoverStaging, null)
        _state.update { it.copy(report = MapCheckReport.Completed(checkedAt, updates)) }
    }

    /**
     * Remove what an earlier process left behind in the maps tree.
     *
     * Called from the app-start path and nowhere else, and that is the whole safety argument: before the
     * first screen exists no transfer of this process can be in flight, while a manual check can run
     * beside one — so removing staging data from [checkNow] could delete the directory a running transfer
     * is writing into, and losing a swap's `.backup` mid-swap could lose the installation it protects.
     */
    private fun sweepStagingLeftovers() {
        val mapsRoot = storageManager.mapsRootDir
        val leftovers = mapsRoot.toFile().listFiles()
            ?.filter { it.isDirectory && SlotInstall.isStaging(it.name) }
            ?.map { it.name }
            .orEmpty()
        leftovers.forEach { name ->
            DiagnosticsLog.log(
                TAG,
                "update cleanup: removing leftover staging $name for ${SlotInstall.stagingTargetOf(name)}"
            )
            SlotInstall.deleteRecursively(mapsRoot.resolve(name))
        }
    }

    /** Publish the persisted result, dropping anything that is no longer installed. */
    private suspend fun publishStored() {
        val stored = settingsStorage.load()
        val updates = stored.mapUpdates.mapNotNull { record -> record.toItem() }.filter { isInstalled(it) }
        if (updates.size != stored.mapUpdates.size) {
            // A map the user deleted must not keep a row in the settings file either.
            settingsStorage.update { it.copy(mapUpdates = updates.mapNotNull { item -> item.toStored() }) }
        }
        _state.update {
            it.copy(
                report = if (stored.mapUpdateCheckAt > 0L) {
                    MapCheckReport.Completed(stored.mapUpdateCheckAt, updates)
                } else {
                    MapCheckReport.NeverChecked
                }
            )
        }
    }

    /** Whether the installation a stored update names is still on the device. */
    private fun isInstalled(item: MapUpdateItem): Boolean = when (item.kind) {
        MapUpdateKind.REGION -> Files.isDirectory(storageManager.mapsRootDir.resolve(item.directoryName))
        MapUpdateKind.BASEMAP ->
            Files.isDirectory(storageManager.mapsRootDir.resolve(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME))
    }

    /** Every installed database directory, the basemap's excluded — it is compared as its own kind. */
    private fun installedDatabaseDirectories(): List<Path> {
        val mapsRoot = storageManager.mapsRootDir
        return InstalledMaps
            .findDatabaseDirectories(
                mapsRoot.toString(),
                mapsRoot.resolve(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME).toString()
            )
            .map { Paths.get(it) }
    }

    /** The installed basemap directory, or null when no basemap is installed. */
    private fun installedBasemapDirectory(): Path? =
        storageManager.mapsRootDir.resolve(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME)
            .takeIf { Files.isDirectory(it) }

    /**
     * The lines one check reports: one per compared installation, one per leftover staging directory, then
     * the check's own outcome.
     *
     * Identity only — a kind, a directory name, a state, a size — never a coordinate
     * (`guidelines/Logging.md` §3). Returned rather than logged so the wording is testable without a device.
     */
    internal fun updateCheckLines(
        items: List<MapUpdateItem>,
        leftoverStaging: List<String>,
        failure: RepositoryFailure?
    ): List<String> =
        items.map { item ->
            "update check: ${item.kind.name.lowercase()} ${item.directoryName} -> ${stateName(item.state)}"
        } +
            leftoverStaging.map { target -> "update check: leftover staging data of $target" } +
            listOf(
                if (failure == null) {
                    "update check completed: ${items.size} compared, " +
                        "${items.count { it.state is UpdateState.UpdateAvailable }} with an update"
                } else {
                    "update check did not complete: $failure"
                }
            )

    /** Record the check in the diagnostics (`guidelines/Logging.md` §3). */
    private fun reportCheck(
        items: List<MapUpdateItem>,
        leftoverStaging: List<String>,
        failure: RepositoryFailure?
    ) {
        updateCheckLines(items, leftoverStaging, failure).forEach { line -> DiagnosticsLog.log(TAG, line) }
    }

    private fun stateName(state: UpdateState): String = when (state) {
        UpdateState.UpToDate -> "up to date"
        is UpdateState.UpdateAvailable -> "update available, ${state.downloadBytes} bytes"
        UpdateState.NoLongerOffered -> "no longer offered"
        is UpdateState.Unknown -> "unknown"
    }

    private companion object {

        const val TAG = "MapUpdates"

        /** How long a completed check keeps the next one from running: one day. */
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
