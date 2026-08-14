package com.naviveylin.core

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-backed diagnostic log shared by [:app] and [:auto].
 *
 * Captures Android Auto session events, template failures, and fatal crashes
 * to `filesDir/diagnostics/app.log` so head-unit failures can be analyzed
 * without adb access. Bounded by size cap + rotation (keeps newest).
 *
 * Null-safe: before [init] (or [initForTest]) every call is a no-op, so
 * host-JVM unit tests and stub builds keep working.
 */
object DiagnosticsLog {

    const val DIR_NAME = "diagnostics"
    const val LOG_FILE = "app.log"
    const val ROTATED_FILE = "app.log.1"
    const val MAX_BYTES = 256 * 1024
    const val MAX_SHARE_CHARS = 50 * 1024

    private const val TAG = "DiagnosticsLog"

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val lock = Any()

    /** Overridable in tests to exercise rotation without writing 256 KB. */
    @Volatile
    internal var maxBytes: Int = MAX_BYTES

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Initialize with the application context. Safe to call multiple times. */
    fun init(appContext: Context) {
        synchronized(lock) {
            if (logFile == null) {
                val dir = File(appContext.filesDir, DIR_NAME)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                logFile = File(dir, LOG_FILE)
            }
        }
    }

    /** Test hook: point the log at an explicit file, no Android Context needed. */
    fun initForTest(file: File) {
        synchronized(lock) {
            logFile = file
        }
    }

    /** Test hook: forget the configured file (leaves any prior file on disk). */
    fun reset() {
        synchronized(lock) {
            logFile = null
        }
    }

    /** Append a tagged, timestamped log line (also mirrored to logcat). */
    fun log(tag: String, message: String) {
        Log.d("Diag/$tag", message)
        appendLine("$tag $message")
    }

    /** Append a tagged exception as a single log line (truncated stack). */
    fun logThrowable(tag: String, message: String, throwable: Throwable) {
        Log.e("Diag/$tag", message, throwable)
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString(" | ") { it.toString() }
        val detail = if (frames.isEmpty()) "" else " | $frames"
        appendLine("$tag $message: $throwable$detail")
    }

    /** Log the wall-clock duration of [block] under the [WARMUP] tag. */
    fun time(label: String, block: () -> Unit) {
        val start = System.currentTimeMillis()
        try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - start
            log(WARMUP_TAG, "$label took ${elapsed}ms")
        }
    }

    /** All log lines in file order (oldest first). Empty when uninitialized. */
    fun readEntries(): List<String> {
        val file = currentFile() ?: return emptyList()
        return try {
            file.readLines()
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            emptyList()
        }
    }

    /** Newest entries first, capped at [MAX_SHARE_CHARS] — for share sheets. */
    fun exportText(): String {
        return readEntries().joinToString("\n").takeLast(MAX_SHARE_CHARS)
    }

    /**
     * Install a default uncaught-exception handler that records the fatal
     * stack trace to the log, then delegates to the previous handler (which
     * on Android kills the process — preserved behavior).
     */
    fun installCrashHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logThrowable(CRASH_TAG, "Uncaught exception on thread ${thread.name}", throwable)
            } catch (_: Exception) {
                // Never let logging itself mask the crash
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Build a share intent carrying the diagnostics log as text. */
    fun shareIntent(): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NaviVeylin diagnostics log")
            putExtra(Intent.EXTRA_TEXT, exportText())
        }
    }

    private fun currentFile(): File? = logFile

    private fun appendLine(line: String) {
        val file = currentFile() ?: return
        synchronized(lock) {
            try {
                if (file.length() + line.length + 1 > maxBytes) {
                    rotate()
                }
                file.appendText(timestamped(line) + "\n")
            } catch (e: Exception) {
                Log.w(TAG, "append failed", e)
            }
        }
    }

    private fun rotate() {
        val file = currentFile() ?: return
        try {
            val rotated = File(file.parentFile, ROTATED_FILE)
            if (rotated.exists()) {
                rotated.delete()
            }
            if (file.exists()) {
                file.renameTo(rotated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "rotate failed", e)
        }
    }

    private fun timestamped(line: String): String =
        "[${timestampFormat.format(Date())}] $line"

    private const val MAX_STACK_FRAMES = 20

    const val CRASH_TAG = "CRASH"
    const val WARMUP_TAG = "WARMUP"
    const val SESSION_TAG = "SESSION"
    const val CAR_APP_TAG = "CARAPP"
    const val TEMPLATE_TAG = "TEMPLATE"
}
