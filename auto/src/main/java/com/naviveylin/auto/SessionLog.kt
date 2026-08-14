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

    fun onCreateScreen(intent: Intent?) =
        DiagnosticsLog.log(SESSION_TAG, "onCreateScreen action=${intent?.action} data=${intent?.data}")

    fun onNewIntent(intent: Intent?) =
        DiagnosticsLog.log(SESSION_TAG, "onNewIntent action=${intent?.action} data=${intent?.data}")

    fun destroyed() =
        DiagnosticsLog.log(SESSION_TAG, "Session destroyed")

    fun warmupComplete() =
        DiagnosticsLog.log(SESSION_TAG, "Warmup complete")

    fun warmupDuration(ms: Long) =
        DiagnosticsLog.log(WARMUP_TAG, "Hilt entry point + native client warmup took ${ms}ms")

    /**
     * Log a warmup step marker under the [WARMUP_TAG].
     *
     * "Before" markers are appended synchronously before the corresponding
     * native call, so a crash during that call leaves the log ending at the
     * last "before" marker — localizing the failure to that step.
     */
    fun warmupStep(step: String) =
        DiagnosticsLog.log(WARMUP_TAG, step)

    fun push(screen: String) =
        DiagnosticsLog.log(SESSION_TAG, "Push $screen")

    fun popToRoot() =
        DiagnosticsLog.log(SESSION_TAG, "Pop to RootScreen")

    fun errorOverlay(message: String) =
        DiagnosticsLog.log(SESSION_TAG, "Showing error overlay: $message")

    fun retry() =
        DiagnosticsLog.log(SESSION_TAG, "Retry requested")

    fun failed(call: String, e: Throwable) =
        DiagnosticsLog.logThrowable(SESSION_TAG, "$call failed", e)
}
