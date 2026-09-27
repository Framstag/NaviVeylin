package com.naviveylin.navigation

import com.naviveylin.core.DiagnosticsLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName

/**
 * Diagnostics tag of a confined engine fault. Lives with the handler instead of
 * `DiagnosticsLog`'s tag constants so the engine owns naming its own record; it is
 * greppable next to `WARMUP`/`HOST`/`SESSION` (spec: `auto-diagnostics` — A confined
 * fault is recorded like a fatal one).
 */
internal const val ENGINE_FAULT_TAG = "ENGINE_FAULT"

/**
 * Fault handler for the process-scoped navigation engine's coroutine scope (spec:
 * `navigation-engine` — Engine coroutine fault is confined; `car-host-fault-isolation`
 * — No fault escapes into the host path; `auto-diagnostics` — A confined fault is
 * recorded like a fatal one).
 *
 * A `SupervisorJob` stops a failing child from cancelling its siblings, but an
 * uncaught throwable still reaches the thread's uncaught-exception handler and kills
 * the process — and a process that dies while a car session is live takes the
 * templates host with it. This handler is the seam that keeps an engine fault inside
 * the engine: it records the fault (tag, origin, first stack frame — the diagnostics
 * log buffers the line, so the failing thread performs no filesystem work) and hands
 * it to [publish], with which the engine ends the affected attempt and raises an
 * engine-wide error on the main thread.
 *
 * The handler itself must never become a second fault: both steps are `runCatching`
 * wrapped, neither blocks, and the record happens before [publish] so a failing
 * publish cannot lose the evidence.
 *
 * @param publish invoked with the coordinate-free fault summary; must not block (the
 *   engine dispatches the state write to the main thread)
 */
internal fun engineFaultHandler(publish: (String) -> Unit): CoroutineExceptionHandler =
    CoroutineExceptionHandler { context, throwable ->
        val summary = faultSummary(context, throwable)
        runCatching { DiagnosticsLog.logThrowable(ENGINE_FAULT_TAG, summary, throwable) }
        runCatching { publish(summary) }
    }

/**
 * Coordinate-free summary of a confined fault: which coroutine faulted and the
 * throwable's class. The throwable's own message is deliberately left out — it can
 * carry anything, including a position — and reaches the log through the stack trace
 * [DiagnosticsLog.logThrowable] appends, which is what a reviewer reads for the
 * first frame (spec: `auto-diagnostics` — Diagnostics carry no coordinates).
 */
internal fun faultSummary(context: CoroutineContext, throwable: Throwable): String {
    val origin = context[CoroutineName]?.name ?: "unnamed coroutine"
    return "Navigation engine fault in $origin: ${throwable::class.java.name}"
}
