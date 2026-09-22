package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import com.naviveylin.core.DiagnosticsLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The single confinement seam for car-facing work (spec: car-host-fault-isolation —
 * No fault escapes into the host path; design D1/D2).
 *
 * The car-app library dispatches every host callback on the app's main thread and
 * **rethrows** an app exception there (`RemoteUtils.dispatchCallFromHost`,
 * car-app 1.7.0: `sendFailureResponseToHost(callback, callName, e)` followed by
 * `throw new RuntimeException(e)`), so one throw inside a template-row click listener
 * kills the app process — and an app process that dies while a car session is live is
 * what takes the templates host down with it (TODO.md §51).
 *
 * Every screen-stack mutation (push, pop, popToRoot, remove) therefore runs through
 * [guardedHostCall], and every scope that can raise such a mutation carries a
 * [carFaultHandler]: the seam confines a synchronous fault inside a host callback,
 * the handler confines one inside a coroutine body. One without the other leaves a
 * path open, which is how the click path and the session's own observers stayed
 * unguarded while the host callbacks were already covered.
 */

/** Diagnostics tag for what the app sent the car host (sibling of `SCREEN`/`MAP`/`SESSION`). */
internal const val CAR_HOST_DIAG_TAG = "HOST"

/** Logcat tag of the seam itself; a rejection is always logged, never swallowed silently. */
private const val GUARD_LOG_TAG = "CarHostGuards"

/**
 * Run one car-facing mutation, confining a fault to it.
 *
 * Never rethrows: a rejected mutation degrades to a logged no-op, which is the only
 * behaviour the main thread of a live car session can afford. Every rejection is
 * recorded under the diagnostics [tag] **without throttling** — a repeat means the
 * condition is still there, and a device run has to be able to count it
 * (`guidelines/Build.md` §10).
 *
 * @param what names the mutation in the log (e.g. `push DetailsScreen`), so a
 *   diagnostics extract says what the host refused
 * @param tag diagnostics tag; [CAR_HOST_DIAG_TAG] for a host mutation, `SESSION` for a
 *   session-level step that is not itself a host call
 * @return true when [block] completed, false when it threw
 */
internal inline fun guardedHostCall(
    what: String,
    tag: String = CAR_HOST_DIAG_TAG,
    block: () -> Unit
): Boolean = try {
    block()
    true
} catch (t: Throwable) {
    reportRejectedCarWork(what, t, tag)
    false
}

/**
 * Record a rejected car-facing mutation. Runs on the failing thread and only logs:
 * `android.util.Log` plus one buffered [DiagnosticsLog] entry that carries the first
 * stack frame, so a logcat extract localizes the rejection without the whole trace.
 */
internal fun reportRejectedCarWork(
    what: String,
    throwable: Throwable,
    tag: String = CAR_HOST_DIAG_TAG
) {
    Log.w(GUARD_LOG_TAG, "$what rejected — degraded", throwable)
    DiagnosticsLog.log(tag, "$what rejected: $throwable${firstFrameSuffix(throwable)}")
}

/**
 * A [CoroutineExceptionHandler] for a scope that owns host-mutating work (spec:
 * car-host-fault-isolation — No fault escapes into the host path; design D3).
 *
 * Without it an exception from a coroutine body reaches the main thread's uncaught
 * handler and kills the process. With it the fault ends that child of the scope's
 * `SupervisorJob` and its siblings keep running — the granularity the car screens
 * already get from [CarScreenObservations], extended to the session's own scope and
 * to each screen's scope (the two that were missing it).
 *
 * @param noun what kind of work failed, used verbatim in the log line
 *   (`observation`, `session work`, …) so existing extracts keep matching
 * @param diagTag diagnostics tag to record the fault under
 * @param logTag logcat tag of the owning component
 */
internal fun carFaultHandler(
    noun: String,
    diagTag: String,
    logTag: String
): CoroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
    val key = context[CoroutineName]?.name ?: UNKNOWN_CAR_WORK
    Log.w(logTag, "$noun '$key' failed — confined", throwable)
    DiagnosticsLog.log(diagTag, "$noun '$key' failed: $throwable${firstFrameSuffix(throwable)}")
}

/**
 * The scope of one car session (spec: car-host-fault-isolation — No fault escapes into
 * the host path; design D3/D7).
 *
 * The session observes shared navigation state, publishes the host trip, shows error
 * notices and mutates the screen stack from this scope. Extracted as a seam so the
 * scope's fault confinement is testable without a host-provided `CarContext` (a
 * [NavigationSession] cannot be constructed in Robolectric — same reason
 * `SessionHostGate`/`FreeDrivingRestoreGate` are pure).
 *
 * @param dispatcher the session's dispatcher; the main dispatcher in production
 */
internal fun carSessionScope(dispatcher: CoroutineDispatcher = Dispatchers.Main): CoroutineScope =
    CoroutineScope(
        SupervisorJob() + dispatcher + carFaultHandler(
            noun = "session work",
            diagTag = SESSION_DIAG_TAG,
            logTag = "CarSession"
        )
    )

/** Diagnostics tag of the session's own events (`SessionLog` writes the same one). */
internal const val SESSION_DIAG_TAG = "SESSION"

/** Diagnostics tag for a fault in a car screen's own background work. */
internal const val CAR_SCREEN_DIAG_TAG = "SCREEN"

/**
 * The scope of one car screen (spec: car-host-fault-isolation — No fault escapes into the
 * host path; design D3/D7).
 *
 * A screen's own scope carries its renderer initialization, settings loads, surface-DPI
 * pushes, stylesheet pushes and the screen-stack mutations of its click actions. Every one
 * of them is host-facing, and an exception from any of them used to reach the main
 * thread's uncaught handler and kill the process — a car session that loses its process
 * takes the templates host down with it (TODO.md §51).
 *
 * The scopes live in the screens (they are cancelled in `onDestroy`), so the guarantee is
 * provided by construction: every screen builds its scope through this function instead of
 * writing `CoroutineScope(SupervisorJob() + Dispatchers.Main)` itself.
 *
 * @param name logcat tag for the screen's faults (the screen's own class name)
 * @param dispatcher the screen's dispatcher; the main dispatcher in production
 */
internal fun carScreenScope(
    name: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
): CoroutineScope = CoroutineScope(
    SupervisorJob() + dispatcher + carFaultHandler(
        noun = "screen work",
        diagTag = CAR_SCREEN_DIAG_TAG,
        logTag = name
    )
)

/** Key reported when car-facing work faults without a [CoroutineName] in its context. */
private const val UNKNOWN_CAR_WORK = "?"

/**
 * Arm a screen push from a host click action (spec: car-host-fault-isolation — Host
 * callbacks answer promptly, "Template row action opens a screen"; design D6).
 *
 * The host callback only arms the push and returns: the car-app library dispatches it on
 * the app's main thread and rethrows an app exception there, and constructing a car screen
 * is not answering-path work. The build and the push run on [scope] afterwards — main
 * thread, no extra hop beyond the dispatch — and the push is confined by
 * [guardedHostCall].
 *
 * [scope] is the screen's own scope, which is cancelled in `onDestroy`: an armed push of a
 * screen that is being popped away dies with it instead of landing on the new stack. A
 * failing build abandons the push and is confined by the scope's fault handler.
 *
 * Generic in the screen type so the sequencing is testable without a host-provided
 * `CarContext`.
 *
 * @param what names the target in the diagnostics (e.g. `FavoritesScreen`)
 */
internal fun <T : Screen> armScreenPush(
    carContext: CarContext,
    scope: CoroutineScope,
    what: String,
    buildScreen: () -> T
) {
    scope.launch {
        val screen = buildScreen()
        guardedHostCall("push $what") {
            carContext.getCarService(ScreenManager::class.java).push(screen)
        }
    }
}

/**
 * Arm the "show on map" screen swap from a host click action (spec:
 * car-host-fault-isolation — Host callbacks answer promptly, "Template row action opens a
 * screen"; design D6).
 *
 * Like [armScreenPush], with the swap the details screen needs: the target map replaces the
 * details screen rather than stacking on it, so the stack is popped back to the root first
 * and each mutation is confined on its own. The `popToRoot` is deliberate — the details
 * screen can sit on top of the browse map *or* of the navigation view, and the swap must
 * always end on the single map root rather than stacking a second map.
 *
 * Generic in the screen type for the same reason as [armScreenPush].
 */
internal fun <T : Screen> armShowOnMapSwap(
    carContext: CarContext,
    scope: CoroutineScope,
    what: String,
    buildScreen: () -> T
) {
    scope.launch {
        val screen = buildScreen()
        guardedHostCall("popToRoot ($what)") {
            carContext.getCarService(ScreenManager::class.java).popToRoot()
        }
        guardedHostCall("push $what") {
            carContext.getCarService(ScreenManager::class.java).push(screen)
        }
    }
}

/**
 * Auto-dismiss one error notice (spec: car-host-fault-isolation — Host screen-stack
 * mutations are balanced, "Deferred mutation after the session ended"; design D5).
 *
 * Two rules, both of which the session's previous `popToRoot()` dismissal broke:
 *
 * - the pop is scoped to **this notice** ([removeNotice] — `ScreenManager.remove` of the
 *   screen the session pushed, never a stack pop that takes the navigation view down with
 *   it while the session still believes it is shown);
 * - a host mutation that would run after the session stopped or ended is **discarded**, and
 *   its removal is re-applied once the session is started again ([onDeferred]).
 *
 * The local error state is cleared **before** the host decision and unconditionally: a
 * skipped host call must never pin the error. Extracted as a suspend seam so both halves
 * are testable without a host-provided `CarContext` (a [NavigationSession] cannot be
 * constructed in Robolectric).
 *
 * @param clearLocalError clears the app's error state (runs whatever the host decision is)
 * @param isSessionUsable false when the session was destroyed or this notice is no longer
 *   the one on the stack
 * @param hostMutationAllowed false while the session is not started
 * @param removeNotice performs the host removal; returns whether it landed
 * @param onRemoved called when the notice was actually removed
 * @param onDeferred called when the removal was skipped for a not-started session
 * @param onSkipped called when the removal was skipped for any other reason
 */
internal suspend fun dismissErrorNotice(
    notice: Any,
    delayMs: Long,
    clearLocalError: () -> Unit,
    isSessionUsable: () -> Boolean,
    hostMutationAllowed: () -> Boolean,
    removeNotice: (Any) -> Boolean,
    onRemoved: () -> Unit,
    onDeferred: () -> Unit,
    onSkipped: (String) -> Unit
) {
    delay(delayMs)
    // Unconditional: the notice's time is up either way, and a state that stays set
    // because a host call was skipped would re-show the error on the next state emission.
    clearLocalError()
    if (!isSessionUsable()) {
        onSkipped("session gone or notice superseded")
        return
    }
    if (!hostMutationAllowed()) {
        onDeferred()
        return
    }
    if (removeNotice(notice)) {
        onRemoved()
    }
}

/**
 * First stack frame of [throwable] as a log suffix, or an empty string when the
 * throwable carries no frames. The full trace is in logcat; the diagnostics line stays
 * one line so the ring buffer holds many faults.
 */
internal fun firstFrameSuffix(throwable: Throwable): String =
    throwable.stackTrace.firstOrNull()?.let { " at $it" } ?: ""
