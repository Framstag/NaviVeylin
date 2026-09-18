package com.naviveylin.auto

import android.util.Log
import androidx.car.app.Screen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recovers car settings screens whose content arrives via an async load plus
 * a template refresh (spec: auto/preferences — "Settings screens never wedge
 * on a loading placeholder").
 *
 * The car-app library's [Screen.invalidate] is a remote host call
 * (`IAppHost.invalidate`, contract `@throws HostException`): on real hardware
 * it can fail outright or be silently dropped while the host template
 * pipeline is busy. The previous pattern issued it exactly once with no
 * timeout, no retry, and no error state — a lost invalidate left the screen
 * stuck on "Loading..." forever. This guard owns only the *render
 * recovery*:
 *
 * - a one-shot watchdog re-invalidates when the placeholder is still showing
 *   after [loadTimeoutMs] (the first refresh may have been dropped),
 * - every invalidate is guarded so a host failure cannot kill the screen's
 *   load coroutine; the recovery nudges on with a shorter [retryDelayMs],
 * - recovery invalidates are capped ([maxInvalidateAttempts], mirroring
 *   `MapScreen`'s surface-refresh cap) — once exhausted the screen is told
 *   to render an explicit error state instead of an infinite placeholder,
 * - [onLifecycleStart] re-renders the screen on re-visibility, so a stale
 *   template from a previous visit can never come back to life,
 * - [onLifecycleStart]/[onLoadSucceeded] clear the error state if content
 *   actually arrived later.
 *
 * Host-free: the [invalidate] lambda is the only code path that touches the
 * car API, so the watchdog/cap/error decisions are unit-testable with a
 * test scheduler ([kotlinx.coroutines.test]).
 *
 * @param scope the screen's coroutine scope — the guard launches watchdog
 *   and retry jobs into it, and [Screen.observeSettingsLifecycle] cancels it
 *   on destroy so no stale job can fire after the screen is gone.
 * @param invalidate the guarded call that re-renders the current template.
 * @param contentLoaded true once the screen has data to render (the loading
 *   placeholder is gone); the guard skips recovery when the screen is fine.
 * @param loadTimeoutMs watchdog delay after [onLoadStarted] before the first
 *   recovery invalidate fires. Default [LOAD_TIMEOUT_MS].
 * @param retryDelayMs delay before re-attempting after a thrown invalidate.
 *   Default [RETRY_DELAY_MS].
 * @param maxInvalidateAttempts cap on recovery invalidates per load-attempt
 *   cycle. Default [MAX_INVALIDATE_ATTEMPTS].
 */
class SettingsLoadGuard(
    private val scope: CoroutineScope,
    private val invalidate: () -> Unit,
    private val contentLoaded: () -> Boolean,
    private val loadTimeoutMs: Long = LOAD_TIMEOUT_MS,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
    private val maxInvalidateAttempts: Int = MAX_INVALIDATE_ATTEMPTS,
    private val log: (String, Throwable?) -> Unit = { msg, t ->
        if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
    }
) {

    /**
     * True once recovery is exhausted and the screen should render its
     * explicit error state (error row + retry) instead of the placeholder.
     */
    var failed: Boolean = false
        private set

    private var watchdogJob: Job? = null
    private var retryJob: Job? = null
    private var invalidateAttempts = 0

    /**
     * Call when a load attempt starts and the placeholder is showing.
     * Resets the error state and the recovery budget, then arms the
     * watchdog. Also the entry point for the error row's Retry action.
     */
    fun onLoadStarted() {
        failed = false
        invalidateAttempts = 0
        nudge(loadTimeoutMs)
    }

    /**
     * Call when the load produced data (content is about to be rendered).
     * Cancels the stalled watchdog, delivers the content immediately, and
     * arms a single confirm refresh so a silently dropped success
     * invalidate still gets a second delivery attempt.
     */
    fun onLoadSucceeded() {
        failed = false
        watchdogJob?.cancel()
        watchdogJob = null
        refreshContent()
        if (!contentLoaded()) return
        watchdogJob = scope.launch {
            delay(loadTimeoutMs)
            if (failed) return@launch
            deliverSafely()
        }
    }

    /**
     * Call when the load attempt itself failed (the provider threw or
     * returned nothing usable). Transitions to the error state immediately —
     * no point waiting out a watchdog for data that will never come.
     */
    fun onLoadFailed() {
        watchdogJob?.cancel()
        watchdogJob = null
        failToError()
    }

    /**
     * Call from the screen's `onStart` lifecycle callback. Re-renders the
     * screen on re-visibility: content is re-delivered (a stale placeholder
     * template from a previous visit can never stick), otherwise recovery
     * resumes from the current stall point.
     */
    fun onLifecycleStart() {
        retryJob?.cancel()
        retryJob = null
        if (contentLoaded()) {
            refreshContent()
        } else {
            nudge(0L)
        }
    }

    /**
     * Success-path delivery: one immediate guarded invalidate, with a single
     * delayed retry when the remote call threw. Does not consume the
     * recovery budget (the data is there; only its delivery failed).
     */
    fun refreshContent() {
        if (!contentLoaded()) return
        if (!deliverSafely()) {
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(retryDelayMs)
                deliverSafely()
            }
        }
    }

    /**
     * One recovery step scheduled after [delayMs]: nothing to do once content
     * is showing or the error state is already up; otherwise a guarded,
     * capped re-invalidate. A thrown invalidate (host glitch) is retried with
     * the shorter [retryDelayMs] instead of consuming an attempt; a silently
     * accepted one consumes an attempt and re-arms the watchdog so the cap
     * eventually ends the stall with the error state.
     */
    private fun nudge(delayMs: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(delayMs)
            if (contentLoaded() || failed) return@launch
            if (invalidateAttempts >= maxInvalidateAttempts) {
                failToError()
                return@launch
            }
            val delivered = deliverSafely()
            if (delivered) {
                invalidateAttempts++
                nudge(loadTimeoutMs)
            }
            // delivered == false: deliverSafely is retried here via the
            // shorter retry path instead of consuming the budget.
        }
    }

    private fun failToError() {
        watchdogJob?.cancel()
        watchdogJob = null
        if (!failed) {
            failed = true
            log("Settings load recovery exhausted — showing error state", null)
        }
        deliverSafely()
    }

    /** True when the remote invalidate was accepted, false when it threw. */
    private fun deliverSafely(): Boolean {
        return try {
            invalidate()
            true
        } catch (t: Throwable) {
            log("invalidate failed", t)
            false
        }
    }

    private companion object {
        const val TAG = "SettingsLoadGuard"
        const val LOAD_TIMEOUT_MS = 2_000L
        const val RETRY_DELAY_MS = 500L
        const val MAX_INVALIDATE_ATTEMPTS = 2
    }
}

/**
 * Wires the settings-recovery lifecycle for a car [Screen]: `onStart` runs
 * [onReVisible] — a settings re-read when the screen carries one (so a value
 * changed in a pushed picker appears on re-visibility instead of a stale
 * snapshot) — then re-renders via [SettingsLoadGuard.onLifecycleStart];
 * `onDestroy` cancels [scope] so no guard job or observer callback can fire
 * after the screen is gone.
 */
fun Screen.observeSettingsLifecycle(
    scope: CoroutineScope,
    guard: SettingsLoadGuard,
    onReVisible: (() -> Unit)? = null
) {
    lifecycle.addObserver(settingsLifecycleObserver(scope, guard, onReVisible))
}

/**
 * The screen lifecycle observer wired by [observeSettingsLifecycle], exposed
 * so the re-read hook and scope cancellation are testable without a host
 * lifecycle (plain [androidx.lifecycle.LifecycleRegistry] in unit tests).
 */
internal fun settingsLifecycleObserver(
    scope: CoroutineScope,
    guard: SettingsLoadGuard,
    onReVisible: (() -> Unit)? = null
): DefaultLifecycleObserver = object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        // A throwing re-read hook must not prevent the guard's re-render
        // (the re-read is fire-and-forget; the recovery stays authoritative).
        runCatching { onReVisible?.invoke() }
        guard.onLifecycleStart()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
    }
}
