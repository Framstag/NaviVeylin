package com.naviveylin.memory

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.core.TileCacheTrim
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The device's memory state, as the release decision sees it (spec: `native-tile-data-cache` —
 * Retention is released when the device is low on memory or the app stops using it).
 */
data class DeviceMemoryState(
    /** The system considers itself to be in a low memory situation. */
    val lowMemory: Boolean,
    /** Available memory on the system, as `ActivityManager` reports it. */
    val availMemBytes: Long,
    /** The availability below which the system considers memory low and starts killing. */
    val thresholdBytes: Long
)

/**
 * Releases retained tile data when the device is low on memory or when the app stops being the
 * surface that renders from it (spec: `native-tile-data-cache` — Retention is released when the
 * device is low on memory or the app stops using it).
 *
 * Without this the app holds its whole walked map area until the system starts killing processes: the
 * measured retention ceiling of a phone that has walked a wide area is ~220 MB (native heap 115 MB →
 * 337 MB, `TOTAL PSS` 377 MB → 600 MB, 2026-09-27), memory the app *could* give back but never did —
 * the capacity policy was raise-only and nothing reacted to the device's state.
 *
 * **Triggers** (design D1; the deprecation finding that decided it is recorded there):
 * - a 30 s poll of [ActivityManager.MemoryInfo] while the app is running — `lowMemory` halves the
 *   capacity, `availMem <= threshold / 2` takes it to the library default ([floorTargetFor]);
 * - the platform's two **still-delivered** levels — `TRIM_MEMORY_UI_HIDDEN` halves,
 *   `TRIM_MEMORY_BACKGROUND` floors — **only while no car session is live**, because with a car session
 *   the car surface keeps rendering from those caches and a release would buy a refetch for nothing.
 *
 * `TRIM_MEMORY_RUNNING_*`/`MODERATE`/`COMPLETE` are **not** referenced: the platform stopped delivering
 * them to apps in API 34 (AOSP `ComponentCallbacks2`), so a release built on them would never fire.
 * `onLowMemory()` is likewise inert.
 *
 * **Contract**: a release is [NativeTileDataCache.trim], the one lowering path of the capacity policy;
 * it evicts least-recently-used tile data and never changes rendered content (spec: cache sizing must
 * not change rendering output). Every release is recorded with its trigger and the capacity it released
 * to. A failure is recorded and keeps the capacity. Nothing native runs on a callback thread, and a
 * release never builds a client: without a configured capacity nothing is retained, and building the
 * client under pressure would be the opposite of the intent.
 *
 * **Threading** (`guidelines/Design.md` §4): the process-scoped [scope] runs the poll and every release
 * on [dispatcher] (`Dispatchers.Default`, the same rule as every other native call); the callback
 * thread only reads an `Int`/a boolean.
 *
 * **No fault escapes**: this responder runs on callbacks the platform and the process-wide dispatcher
 * own, so a throwable raised inside a release is confined and recorded rather than left to reach the
 * thread's uncaught-exception handler — in production that handler kills the app, and in a test JVM it
 * poisons whichever test runs next. A release is opportunistic work: it must never be able to take
 * anything down with it.
 */
@Singleton
class MemoryPressureResponder @Inject constructor(
    private val client: Lazy<OSMScoutClient>,
    private val carSessionPresence: CarSessionPresence,
    @param:ApplicationContext private val context: Context
) : ComponentCallbacks2 {

    /** Process-lifetime scope: the poll and the releases cannot outlive the process. */
    private val scope = CoroutineScope(SupervisorJob())

    /** Where the poll and the release run. Overridable in tests; production uses the render dispatcher. */
    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /** Test hook: stops the poll loop so a test's own reads/releases are deterministic. */
    @VisibleForTesting
    internal var pollEnabled: Boolean = true

    /** Test hook: the memory-state read (production reads `ActivityManager`). */
    @VisibleForTesting
    internal var memoryState: () -> DeviceMemoryState? = ::readDeviceMemoryState

    init {
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (!pollEnabled) continue
                // A poll must never be able to take anything down with it: a throwing read (a torn-down
                // context, a bridge fault, an unexpected platform state) is confined here, so the loop
                // survives and no exception escapes onto a process-wide dispatcher.
                runCatching { pollRelease() }
            }
        }
    }

    /** The device's memory state while the app runs: halves on a warning, floors in the severe band. */
    internal fun pollRelease() {
        // Nothing retained: no client exists, and a poll must not create one.
        if (!NativeTileDataCache.isConfigured()) return
        val state = memoryState() ?: return
        // A healthy device is not a trigger: only the low-memory situation releases anything.
        if (!state.lowMemory) return
        release(
            trigger = pollTrigger(state),
            severe = state.availMemBytes <= state.thresholdBytes / 2,
            detail = "avail=${state.availMemBytes / MIB}MB threshold=${state.thresholdBytes / MIB}MB",
            platformSignal = false
        )
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> release(
                trigger = UI_HIDDEN_TRIGGER,
                severe = false,
                detail = "level=${levelName(level)}",
                platformSignal = true
            )

            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> release(
                trigger = BACKGROUND_TRIGGER,
                severe = true,
                detail = "level=${levelName(level)}",
                platformSignal = true
            )

            // Every other value is either a level the platform stopped delivering (the deprecated
            // RUNNING_*/MODERATE/COMPLETE ladder) or an intermediate value Android reserves the right to
            // add: not a trigger for this app.
            else -> Unit
        }
    }

    /**
     * Deliberately inert: `onLowMemory()` is deprecated since API 35 and targets a background process,
     * which is not this app's pressure case (it is the *running* app whose retention is worth releasing
     * early). Kept explicit so the decision is visible rather than implied.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    /** Required by [ComponentCallbacks2]; a memory responder has nothing to do here. */
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    /**
     * Apply one release. Reads the client's current capacity and releases to the target of the
     * trigger's severity; the native call, the bookkeeping and the record happen on [dispatcher].
     *
     * @param platformSignal true for a platform lifecycle level, which is scoped to "no car session is
     *   live" (with one, the car surface renders from the same caches); false for the device-state poll,
     *   which is about the device and not about who is rendering
     */
    private fun release(trigger: String, severe: Boolean, detail: String, platformSignal: Boolean) {
        // A platform level is not a release trigger while the car renders from the same caches.
        if (platformSignal && carSessionPresence.active.value) return
        if (!NativeTileDataCache.isConfigured()) return

        scope.launch(dispatcher) {
            // Confined the way the poll is: a release runs on a process-wide dispatcher, where an
            // escaping throwable reaches the thread's uncaught-exception handler (in production the
            // app dies; in a test JVM the next test reports it as a leaked exception). The fault is
            // recorded, so the confinement does not hide it.
            runCatching {
                val client = client.get()
                val current = NativeTileDataCache.currentCapacity(client) ?: return@launch
                val target = if (severe) floorTargetFor(current) else halfTargetFor(current)
                when (NativeTileDataCache.trim(client, target)) {
                    TileCacheTrim.TRIMMED -> DiagnosticsLog.log(
                        MEMORY_TAG,
                        "retention released: trigger=$trigger ($detail) $current -> $target tiles/db"
                    )

                    TileCacheTrim.FAILED -> DiagnosticsLog.log(
                        MEMORY_TAG,
                        "retention release failed: trigger=$trigger ($detail) target=$target tiles/db " +
                            "(keeping $current)"
                    )

                    TileCacheTrim.UNCHANGED -> Unit
                }
            }.onFailure { fault ->
                // Only the throwable's class: a message can carry anything (spec: `auto-diagnostics`
                // — Diagnostics carry no coordinates).
                runCatching {
                    DiagnosticsLog.log(
                        MEMORY_TAG,
                        "retention release confined fault: ${fault::class.java.name}"
                    )
                }
            }
        }
    }

    /** The production memory-state read; null when the platform service is unavailable. */
    private fun readDeviceMemoryState(): DeviceMemoryState? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        return try {
            manager.getMemoryInfo(info)
            DeviceMemoryState(
                lowMemory = info.lowMemory,
                availMemBytes = info.availMem,
                thresholdBytes = info.threshold
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Diagnostics tag of a retention release; greppable next to `HOST`/`SESSION`/`WARMUP`. */
        const val MEMORY_TAG = "MEMORY"

        /** Poll interval: cheap check, no wakeups (the process is alive only while it is alive). */
        internal const val POLL_INTERVAL_MS = 30_000L

        private const val MIB = 1024L * 1024L
    }
}

/** Trigger labels of the diagnostics record: which signal released the retention. */
internal const val POLL_TRIGGER = "poll"
internal const val UI_HIDDEN_TRIGGER = "ui-hidden"
internal const val BACKGROUND_TRIGGER = "background"

/** The poll's trigger label, split by band so the record says what was seen. */
internal fun pollTrigger(state: DeviceMemoryState): String =
    if (state.availMemBytes <= state.thresholdBytes / 2) "$POLL_TRIGGER-severe" else "$POLL_TRIGGER-low"

/** A moderate release halves the working set, never below the library default. */
internal fun halfTargetFor(currentCapacity: Int): Int =
    maxOf(NativeTileDataCache.LIBRARY_DEFAULT_TILES, currentCapacity / 2)

/** A severe release floors the working set at the library default (and never raises it). */
internal fun floorTargetFor(currentCapacity: Int): Int =
    minOf(currentCapacity, NativeTileDataCache.LIBRARY_DEFAULT_TILES)

/** Human-readable level for the diagnostics record (identity, not a value). */
internal fun levelName(level: Int): String = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
    // The deprecated RUNNING_*/MODERATE/COMPLETE levels are deliberately not named: they are not
    // triggers, and referencing the constants would emit deprecation warnings.
    else -> "level=$level"
}
