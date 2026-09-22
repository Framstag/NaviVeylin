package com.naviveylin.auto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Observation lifetime of one car screen (spec: auto/screen-observation; design
 * D1/D2/D5).
 *
 * A car screen observes shared app state through a fixed set of observations, and
 * the host stops and starts that screen many times in a session — on every
 * background round trip and on every push/pop of another screen. This class owns
 * *how long* those observations live: one child [CoroutineScope] per started
 * period, so `start()` establishes the observations and `stop()` cancels every one
 * of them. A screen therefore runs exactly one instance of each observation no
 * matter how often it is stopped and started, which a per-collector job list
 * cannot guarantee (that is how the defect this replaces arose:
 * `MapScreen`, `NavigationScreen` and `FreeDrivingScreen` each cancelled only the
 * single job they tracked, and re-launched the rest on every start).
 *
 * **Main-thread only.** Every method is called from a screen's lifecycle callbacks
 * and screen functions, which the car-app library dispatches on the main thread.
 * Neither this class nor [observe] starts work on the main thread beyond the
 * coroutine itself: an observation does its native or file work inside its own
 * `withContext(Dispatchers.Default)`, exactly as it did before.
 *
 * **Nothing runs while stopped.** [observe] before [start], and any emission after
 * [stop], is a no-op: a stopped screen must not touch the renderer or the host
 * (spec: auto/screen-observation — A stopped screen performs no renderer or host
 * work). The screen's own constructor-time work (renderer construction, one-shot
 * loads) does not belong here and stays on the screen's own scope.
 *
 * **A fault stays inside its observation** (spec: auto/screen-observation — An
 * observation fault is confined to its observation): the period's scope carries a
 * [CoroutineExceptionHandler], so an exception from a collector body is logged with
 * the observation's key and ends that observation only. Without it the exception
 * reaches the main thread's uncaught handler — the process dies, and a car session
 * that loses its process is what takes the host down with it (TODO.md §51/§59).
 *
 * @param dispatcher dispatcher the observations run on; the main dispatcher by
 *   default, injectable so a unit test can share one test scheduler with the
 *   class under test
 */
internal class CarScreenObservations(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    /** The scope of the current started period, or null while stopped. */
    private var scope: CoroutineScope? = null

    /** Running observations by key, so a key cannot be observed twice at once. */
    private val running = LinkedHashMap<String, Job>()

    /** True between [start] and [stop]. */
    val isRunning: Boolean
        get() = scope != null

    /** How many observations are currently running (0 while stopped). */
    val liveObservationCount: Int
        get() = running.values.count { it.isActive }

    /**
     * Enter a started period. Idempotent: a second call without an intervening
     * [stop] keeps the existing period (and its observations) instead of starting a
     * second set.
     */
    fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + dispatcher + handler)
    }

    /**
     * Leave the started period, cancelling every observation. Idempotent and safe
     * before any [start].
     */
    fun stop() {
        val current = scope ?: return
        scope = null
        running.clear()
        current.cancel()
    }

    /**
     * Run [block] as one observation of the current started period.
     *
     * A no-op while the screen is not started, and a no-op for a [key] that is
     * already running in this period (spec: auto/screen-observation — One instance
     * of each observation per started period). A key whose block completed on its own
     * may be observed again — the guarantee is one *running* instance per key.
     *
     * @param key stable identifier of the observation within its screen, unique per
     *   observed source
     */
    fun observe(key: String, block: suspend CoroutineScope.() -> Unit) {
        val current = scope ?: return
        if (running[key]?.isActive == true) return
        // The key rides the coroutine's context so the period's exception handler can
        // name the observation that failed.
        running[key] = current.launch(CoroutineName(key), block = block)
    }

    /**
     * Confines a fault to the observation that raised it (spec: auto/screen-observation
     * — An observation fault is confined to its observation): log it with the
     * observation's key and let the period's other observations keep running. The handler
     * itself runs on the failing coroutine's own thread, so it only logs — never works.
     *
     * Built by the module's shared [carFaultHandler] (spec: car-host-fault-isolation — No
     * fault escapes into the host path; design D3) so the session's scope and the screens'
     * scopes confine a fault the same way this seam does.
     */
    private val handler: CoroutineExceptionHandler =
        carFaultHandler(noun = "observation", diagTag = DIAG_TAG, logTag = TAG)

    private companion object {
        const val TAG = "CarScreenObservations"

        /** Diagnostics tag for what a car screen observed (sibling of the MAP/HOST tags). */
        const val DIAG_TAG = "SCREEN"
    }
}
