package com.naviveylin.core

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * File-backed diagnostic log shared by [:app] and [:auto].
 *
 * Captures Android Auto session events, template failures, and fatal crashes
 * to `filesDir/diagnostics/app.log` so head-unit failures can be analyzed
 * without adb access. Bounded by size cap + rotation (keeps newest).
 *
 * **The caller never touches the filesystem** (spec: auto-diagnostics — Logging
 * never blocks the caller). [log]/[logThrowable] mirror the line to logcat on
 * the calling thread and hand it to a bounded in-memory buffer; one daemon worker
 * thread owns the file (size check, append, rotation). That matters because the
 * car host calls some of these paths on the app's main thread — a template build
 * (`MapScreen.onGetTemplate`), a surface callback, the host navigation calls, a
 * notification post — and the car-app library treats a host callback that does
 * not answer promptly as a host problem.
 *
 * Bounds and delays (spec: auto-diagnostics — The in-memory diagnostic buffer is
 * bounded, Buffered entries reach the file within a bounded delay):
 * [MAX_PENDING_ENTRIES]/[MAX_PENDING_CHARS] cap the buffer (oldest entries are
 * dropped, and dropping is recorded once with a marker line), the worker flushes
 * at least every [FLUSH_INTERVAL_MS] and immediately at a high-water mark. A hard
 * process kill therefore loses at most the last flush interval, while an
 * uncaught exception is written **synchronously** ([installCrashHandler]) so its
 * trace always lands.
 *
 * Null-safe: before [init] (or [initForTest]) every call is a no-op — no buffer,
 * no worker — so host-JVM unit tests and stub builds keep working.
 */
object DiagnosticsLog {

    const val DIR_NAME = "diagnostics"
    const val LOG_FILE = "app.log"
    const val ROTATED_FILE = "app.log.1"
    const val MAX_BYTES = 256 * 1024
    const val MAX_SHARE_CHARS = 50 * 1024

    /** Flush deadline: a pending entry reaches the file within this bound. */
    const val FLUSH_INTERVAL_MS = 250L

    /** Pending-buffer capacity in entries. */
    const val MAX_PENDING_ENTRIES = 512

    /** Pending-buffer capacity in characters, so long lines cannot grow the heap. */
    const val MAX_PENDING_CHARS = 64 * 1024

    /** How long a reader waits for the worker to drain before reading the file. */
    const val READ_DRAIN_TIMEOUT_MS = 1_000L

    private const val TAG = "DiagnosticsLog"
    private const val WORKER_THREAD_NAME = "DiagnosticsLog-worker"
    private const val WORKER_STOP_TIMEOUT_MS = 2_000L
    private const val DROP_MARKER = "pending buffer full — dropped older entries"

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /** Protects the pending buffer, the drop bookkeeping and the worker handle. */
    private val bufferLock = ReentrantLock()
    private val bufferChanged = bufferLock.newCondition()

    /** Protects [logFile] and every file operation (append, rotate, direct crash write, read). */
    private val fileLock = ReentrantLock()

    /** Guards [timestampFormat] (SimpleDateFormat is not thread-safe). */
    private val formatLock = Any()

    private val pending = ArrayDeque<String>()
    private var pendingChars = 0
    private var droppedEntries = 0L

    /** True when the buffer has dropped entries that no flushed marker reports yet. */
    private var droppedSinceFlush = false

    /** Target the current batch was drained for — set under [bufferLock], read under [fileLock]. */
    @Volatile
    private var batchTarget: File? = null

    private var worker: Thread? = null

    /** True while the worker is moving a drained batch to the file. */
    private var flushing = false

    /** Overridable in tests to exercise rotation without writing 256 KB. */
    @Volatile
    internal var maxBytes: Int = MAX_BYTES

    /** Overridable in tests so the flush deadline can be checked quickly. */
    @Volatile
    internal var flushIntervalMs: Long = FLUSH_INTERVAL_MS

    /** Overridable in tests to exercise the buffer bound without 512 entries. */
    @Volatile
    internal var maxPendingEntries: Int = MAX_PENDING_ENTRIES

    /** Overridable in tests to exercise the buffer bound with short lines. */
    @Volatile
    internal var maxPendingChars: Int = MAX_PENDING_CHARS

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Initialize with the application context. Safe to call multiple times. */
    fun init(appContext: Context) {
        // Always re-point to the canonical app log file: the app's
        // onCreate must own this path. Without this, a leftover pointer
        // from an earlier process/classloader (e.g. a previous Robolectric
        // test's initForTest) makes the app's onCreate silently write to
        // the wrong file, and the app's own log never appears.
        val dir = File(appContext.filesDir, DIR_NAME)
        configureTarget(File(dir, LOG_FILE), dir)
    }

    /** Test hook: point the log at an explicit file, no Android Context needed. */
    fun initForTest(file: File) {
        configureTarget(file, file.parentFile)
    }

    /**
     * Test hook: forget the configured file and stop the worker (leaves any prior
     * file on disk). Returns the seam to its uninitialised state, so the next
     * [log] behaves like the first one in a fresh process. The retired worker is
     * joined (bounded), so no stale worker can flush another target's entries.
     */
    fun reset() {
        configureTarget(null, null)
        val stale = bufferLock.withLock {
            val current = worker
            worker = null
            current
        }
        if (stale != null) {
            stale.interrupt()
            try {
                stale.join(WORKER_STOP_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Point the log at [target] (creating [dir] when missing) and drop anything
     * still buffered: the file is the only write target, so lines queued for a
     * previous target must never land in the new one.
     */
    private fun configureTarget(target: File?, dir: File?) {
        if (target != null && dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        fileLock.withLock { logFile = target }
        bufferLock.withLock {
            pending.clear()
            pendingChars = 0
            droppedEntries = 0L
            droppedSinceFlush = false
            batchTarget = null
        }
    }

    /** Append a tagged, timestamped log line (also mirrored to logcat). */
    fun log(tag: String, message: String) {
        Log.d("Diag/$tag", message)
        enqueue("$tag $message")
    }

    /** Append a tagged exception as a single log line (truncated stack). */
    fun logThrowable(tag: String, message: String, throwable: Throwable) {
        Log.e("Diag/$tag", message, throwable)
        enqueue("$tag $message: $throwable${stackDetail(throwable)}")
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

    /**
     * All log lines in file order (oldest first). Empty when uninitialized.
     *
     * Waits (bounded by [READ_DRAIN_TIMEOUT_MS]) for the worker to move already
     * logged entries to the file and then reads it, so the caller sees a
     * consistent snapshot. Reads the file, so call it off the main thread —
     * see [readEntriesAsync].
     */
    fun readEntries(): List<String> {
        awaitDrained()
        val file = currentFile() ?: return emptyList()
        return fileLock.withLock {
            try {
                file.readLines()
            } catch (e: Exception) {
                Log.w(TAG, "read failed", e)
                emptyList()
            }
        }
    }

    /**
     * [readEntries] on [Dispatchers.IO]: use this from UI/Compose code so a log
     * read never blocks the thread that renders (spec: auto-diagnostics — Reading
     * diagnostics does not block the UI).
     */
    suspend fun readEntriesAsync(): List<String> = withContext(Dispatchers.IO) { readEntries() }

    /** Newest entries first, capped at [MAX_SHARE_CHARS] — for share sheets. */
    fun exportText(): String {
        return readEntries().joinToString("\n").takeLast(MAX_SHARE_CHARS)
    }

    /** [exportText] on [Dispatchers.IO] — the share path must not block the UI. */
    suspend fun exportTextAsync(): String = withContext(Dispatchers.IO) { exportText() }

    /**
     * Install a default uncaught-exception handler that records the fatal
     * stack trace to the log, then delegates to the previous handler (which
     * on Android kills the process — preserved behavior).
     *
     * This write is deliberately **synchronous** (spec: auto-diagnostics — Crash
     * capture does not depend on the logging worker): the process is dying, so a
     * queued entry may never be flushed. It takes the file lock for one append, so
     * at worst it waits for one flush in flight.
     */
    fun installCrashHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logThrowableDirect(CRASH_TAG, "Uncaught exception on thread ${thread.name}", throwable)
            } catch (_: Exception) {
                // Never let logging itself mask the crash
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Build a share intent carrying [logText] as text. */
    fun shareIntent(logText: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NaviVeylin diagnostics log")
            putExtra(Intent.EXTRA_TEXT, logText)
        }
    }

    /**
     * Ask the worker to flush the pending buffer now and wait for it (bounded by
     * [timeoutMs]). Returns false when the entries are still buffered.
     */
    internal fun flushNow(timeoutMs: Long = READ_DRAIN_TIMEOUT_MS): Boolean {
        bufferLock.withLock { bufferChanged.signalAll() }
        return awaitDrained(timeoutMs)
    }

    /**
     * Number of entries dropped because the pending buffer was full — 0 on a
     * healthy run. Exposed so a reader (and the tests) can tell a truncated log
     * from a complete one.
     */
    internal fun droppedEntryCount(): Long = bufferLock.withLock { droppedEntries }

    /** The worker thread, or null while nothing has been logged yet (test hook). */
    internal fun workerThreadOrNull(): Thread? = bufferLock.withLock { worker }

    /**
     * Wait until the worker has moved every buffered entry to the file. Returns
     * false when [timeoutMs] elapsed first (the entry is still buffered, not lost).
     */
    internal fun awaitDrained(timeoutMs: Long = READ_DRAIN_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        bufferLock.withLock {
            while (pending.isNotEmpty() || flushing) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                bufferChanged.await(remaining, TimeUnit.MILLISECONDS)
            }
            return true
        }
    }

    // ── caller-thread path (memory only) ──

    /**
     * Buffer one line. Never touches the filesystem: this is the only work any
     * logging caller does (spec: auto-diagnostics — Logging never blocks the caller).
     */
    private fun enqueue(line: String) {
        if (currentFile() == null) return
        val stamped = timestamped(line)
        bufferLock.withLock {
            ensureWorkerLocked()
            pending.addLast(stamped)
            pendingChars += stamped.length
            var dropped = false
            while (pending.size > maxPendingEntries || pendingChars > maxPendingChars) {
                val removed = pending.removeFirst()
                pendingChars -= removed.length
                droppedEntries++
                dropped = true
            }
            if (dropped) {
                // The marker travels with the DRAIN, never as a ring entry: an entry
                // would be evicted by the very bound it reports, and this ring stays
                // exactly at its capacity. One marker per flush that dropped lines.
                droppedSinceFlush = true
            }
            if (pendingChars >= maxPendingChars / 2) {
                bufferChanged.signalAll()
            }
        }
    }

    /** The crash path: logcat plus a direct write on the dying thread. */
    private fun logThrowableDirect(tag: String, message: String, throwable: Throwable) {
        Log.e("Diag/$tag", message, throwable)
        appendToFile(timestamped("$tag $message: $throwable${stackDetail(throwable)}"))
    }
    private fun stackDetail(throwable: Throwable): String {
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString(" | ") { it.toString() }
        return if (frames.isEmpty()) "" else " | $frames"
    }

    // ── worker path (filesystem) ──

    private fun workerLoop() {
        while (true) {
            var drained: List<String>? = null
            try {
                bufferLock.withLock {
                    if (pending.isEmpty()) {
                        bufferChanged.await(flushIntervalMs, TimeUnit.MILLISECONDS)
                    }
                    if (pending.isNotEmpty()) {
                        val buffered = ArrayList(pending)
                        pending.clear()
                        pendingChars = 0
                        batchTarget = currentFile()
                        drained = if (droppedSinceFlush) {
                            // The gap sits before the oldest buffered entry.
                            droppedSinceFlush = false
                            ArrayList<String>(buffered.size + 1).apply {
                                add(timestamped("$TAG $DROP_MARKER"))
                                addAll(buffered)
                            }
                        } else {
                            buffered
                        }
                        flushing = true
                    }
                }
            } catch (_: InterruptedException) {
                // reset() retired this worker: exit without leaving the flag set.
                return
            }
            val batch = drained ?: continue
            try {
                fileLock.withLock {
                    // A batch belongs to the target it was drained for: if the target
                    // was repointed meanwhile (init/reset), the batch is dropped
                    // instead of being written into another file.
                    val target = batchTarget
                    if (target != null && target === currentFile()) {
                        batch.forEach { line -> appendLineLocked(target, line) }
                    }
                }
            } finally {
                bufferLock.withLock {
                    flushing = false
                    bufferChanged.signalAll()
                }
            }
        }
    }

    private fun appendToFile(stamped: String) {
        val file = currentFile() ?: return
        fileLock.withLock { appendLineLocked(file, stamped) }
    }

    private fun appendLineLocked(file: File, stamped: String) {
        try {
            if (file.length() + stamped.length + 1 > maxBytes) {
                rotateLocked(file)
            }
            file.appendText(stamped + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "append failed", e)
        }
    }

    private fun rotateLocked(file: File) {
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

    private fun ensureWorkerLocked() {
        if (worker != null) return
        worker = Thread({ workerLoop() }, WORKER_THREAD_NAME).apply {
            // Daemon: the log must never keep a (test) JVM alive.
            isDaemon = true
            start()
        }
    }

    private fun currentFile(): File? = logFile

    private fun timestamped(line: String): String =
        synchronized(formatLock) { "[${timestampFormat.format(Date())}] $line" }

    private const val MAX_STACK_FRAMES = 20

    const val CRASH_TAG = "CRASH"
    const val WARMUP_TAG = "WARMUP"
    const val SESSION_TAG = "SESSION"
    const val CAR_APP_TAG = "CARAPP"
    const val TEMPLATE_TAG = "TEMPLATE"
}
