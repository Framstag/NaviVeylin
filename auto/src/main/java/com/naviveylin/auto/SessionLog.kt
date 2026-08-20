package com.naviveylin.auto

import android.content.Intent
import com.naviveylin.core.DiagnosticsLog

/**
 * Centralizes [NavigationSession] event logging so the exact log lines are
 * unit-testable without a host-provided [androidx.car.app.CarContext].
 * [NavigationSession] delegates every session-event log call here.
 */
object SessionLog {

    const val SESSION_TAG = "SESSION"
    const val WARMUP_TAG = "WARMUP"

    /** Logged once when the [NavigationSession] object is constructed. */
    fun sessionCreated() =
        DiagnosticsLog.log(SESSION_TAG, "Session created")

    /** Logged when the background warmup coroutine actually starts running. */
    fun warmupStarted() =
        DiagnosticsLog.log(WARMUP_TAG, "Warmup started on thread=${Thread.currentThread().name}")

    /** Logged when the warmup coroutine resumes on the main thread after the Default block. */
    fun warmupBlockDone(elapsedMs: Long) =
        DiagnosticsLog.log(WARMUP_TAG, "Warmup block returned to main thread (+${elapsedMs}ms) thread=${Thread.currentThread().name}")

    fun onCreateScreen(intent: Intent?, warmupCompleted: Boolean? = null, sinceSessionMs: Long? = null) =
        DiagnosticsLog.log(
            SESSION_TAG,
            "onCreateScreen action=${intent?.action} data=${intent?.data}" +
                (warmupCompleted?.let { " warmupCompleted=$it" } ?: "") +
                (sinceSessionMs?.let { " sinceSessionCreate=${it}ms" } ?: "") +
                " thread=${Thread.currentThread().name}"
        )

    fun onNewIntent(intent: Intent?, sinceSessionMs: Long? = null) =
        DiagnosticsLog.log(
            SESSION_TAG,
            "onNewIntent action=${intent?.action} data=${intent?.data}" +
                (sinceSessionMs?.let { " sinceSessionCreate=${it}ms" } ?: "") +
                " thread=${Thread.currentThread().name}"
        )

    fun destroyed() =
        DiagnosticsLog.log(SESSION_TAG, "Session destroyed")

    fun warmupComplete() =
        DiagnosticsLog.log(SESSION_TAG, "Warmup complete")

    fun warmupCancelled() =
        DiagnosticsLog.log(SESSION_TAG, "Warmup cancelled (session destroyed)")

    fun warmupDuration(ms: Long) =
        DiagnosticsLog.log(WARMUP_TAG, "Hilt entry point + native client warmup took ${ms}ms")

    /**
     * Log a warmup step marker under the [WARMUP_TAG].
     *
     * "Before" markers are appended synchronously before the corresponding
     * native call, so a crash during that call leaves the log ending at the
     * last "before" marker — localizing the failure to that step.
     *
     * @param elapsedMs when >= 0, appends the wall-clock delta since the previous step.
     */
    fun warmupStep(step: String, elapsedMs: Long = -1) {
        val delta = if (elapsedMs >= 0) " (+${elapsedMs}ms)" else ""
        DiagnosticsLog.log(WARMUP_TAG, "$step$delta thread=${Thread.currentThread().name}")
    }

    /** Log a ScreenManager push with the concrete screen class name. */
    fun push(screen: String) =
        DiagnosticsLog.log(SESSION_TAG, "Push $screen thread=${Thread.currentThread().name}")

    /** Log a ScreenManager popToRoot. */
    fun popToRoot() =
        DiagnosticsLog.log(SESSION_TAG, "Pop to RootScreen")

    fun errorOverlay(message: String) =
        DiagnosticsLog.log(SESSION_TAG, "Showing error overlay: $message")

    fun retry() =
        DiagnosticsLog.log(SESSION_TAG, "Retry requested")

    fun failed(call: String, e: Throwable) =
        DiagnosticsLog.logThrowable(SESSION_TAG, "$call failed", e)
}
