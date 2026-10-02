package com.naviveylin.auto

import com.naviveylin.core.DiagnosticsLog
import kotlinx.coroutines.CancellationException

/**
 * One of the car map renderer's continuing loops (spec: car-host-fault-isolation — No fault
 * escapes into the host path, "A fault in one frame keeps the frames coming"; design D1).
 *
 * A loop is work that never ends on its own: a fault in one iteration must skip that
 * iteration and leave the loop running, otherwise the car map is frozen for good — the
 * failure mode `TODO.md` §51 records.
 */
internal enum class RenderLoop(val label: String) {
    /** The render-signal consumer that turns a `requestRender()` into a drawn frame. */
    FRAME("frame"),

    /** The displayed-frame loop (extrapolate + blit between fixes). */
    EXTRAPOLATION("extrapolation"),

    /** The zoom-transition loop (eased magnification). */
    ZOOM_WALK("zoom-walk")
}

/**
 * Diagnostics tag for a confined renderer fault. Reuses the map tag (spec
 * `auto-diagnostics` — Diagnostics carry no coordinates; design D6), so no new tag and no
 * `AGENTS.md` logging-section edit.
 */
internal const val RENDER_FAULT_DIAG_TAG = "MAP"

/** Consecutive confined faults in one renderer that request a re-creation (design D2). */
internal const val CONSECUTIVE_RENDER_FAULT_THRESHOLD = 3

/** Window the consecutive faults must fall into; a later fault starts a new streak (design D2). */
internal const val RENDER_FAULT_WINDOW_MS = 60_000L

/** Re-creations allowed per started screen period before the degraded state is shown (design D7). */
internal const val MAX_RENDERER_RECOVERIES_PER_PERIOD = 2

/** At most one diagnostics record per window after the first fault of a streak (design D2). */
internal const val RENDER_FAULT_RECORD_INTERVAL_MS = 5_000L

/**
 * Bounds repeated faults in a car map renderer's frame work (spec: car-host-fault-isolation
 * — A repeatedly faulting car renderer recovers, then degrades visibly; design D1/D2/D7).
 *
 * One supervisor belongs to one **started screen period** and is shared by the renderer
 * instances the gate publishes during that period: the re-creation budget survives the
 * instance swap, which is exactly what makes "threshold reached again after the cap" mean
 * something. [startPeriod] re-arms it, so a driver who leaves and returns is not degraded by
 * a previous period's faults.
 *
 * Counters live on the renderer's own dispatcher (see `AutoMapRenderer`); the supervisor is
 * *not* a main-thread object and takes no lock. [runIteration] is the only entry point the
 * loops use, and it must not allocate on the happy path (spec `auto-map-renderer` — Marker
 * drawing allocates no per-frame objects).
 */
internal class RenderLoopSupervisor(
    private val threshold: Int = CONSECUTIVE_RENDER_FAULT_THRESHOLD,
    private val windowMs: Long = RENDER_FAULT_WINDOW_MS,
    private val maxRecoveries: Int = MAX_RENDERER_RECOVERIES_PER_PERIOD,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val record: (String) -> Unit = { DiagnosticsLog.log(RENDER_FAULT_DIAG_TAG, it) }
) {
    private var streak = 0
    private var streakStartedMs = 0L
    private var lastRecordMs = 0L
    private var recoveries = 0
    private var suspended = false
    private var degradedNotified = false

    private var recoveryCallback: ((attempt: Int) -> Unit)? = null
    private var degradedCallback: ((loop: RenderLoop, attempts: Int) -> Unit)? = null

    /** Re-creations requested so far in this period. */
    val recoveriesAttempted: Int get() = recoveries

    /** True once the period is over or the budget is exhausted: no further re-creation is asked for. */
    val isSuspended: Boolean get() = suspended

    /**
     * Called when the threshold is reached and a re-creation is still affordable. Runs on the
     * render thread; the consumer posts to the main thread (`postTemplateRefresh`, the same
     * pattern `onSurfaceFailed` uses) because the renderer gate is a main-thread object.
     */
    fun onRecoveryRequested(callback: (attempt: Int) -> Unit) {
        recoveryCallback = callback
    }

    /** Called once per period when the budget is exhausted and the driver has to be told. */
    fun onDegraded(callback: (loop: RenderLoop, attempts: Int) -> Unit) {
        degradedCallback = callback
    }

    /**
     * A fresh started screen period: streak, window, record throttle and re-creation budget
     * are reset, so a fault storm of the previous period does not keep the new one degraded
     * from its first frame (design D7).
     */
    fun startPeriod() {
        streak = 0
        streakStartedMs = 0L
        lastRecordMs = 0L
        recoveries = 0
        suspended = false
        degradedNotified = false
    }

    /**
     * Stop asking for re-creation — the screen is stopping, or the degraded state was reached
     * and a new renderer would only fault again.
     */
    fun suspend() {
        suspended = true
    }

    /** One iteration completed without a fault: the streak is broken (design D2). */
    internal fun noteCleanIteration() {
        streak = 0
        streakStartedMs = 0L
    }

    /** One iteration faulted: count it, record it (throttled) and escalate when due (design D1/D2). */
    internal fun noteFault(loop: RenderLoop, throwable: Throwable) {
        val now = nowMs()
        if (streak == 0 || now - streakStartedMs > windowMs) {
            streak = 1
            streakStartedMs = now
        } else {
            streak++
        }
        // Identity only: which loop, which throwable class, how far the streak is — never a
        // position, a throwable message or pixels (spec auto-diagnostics).
        if (streak == 1 || now - lastRecordMs >= RENDER_FAULT_RECORD_INTERVAL_MS) {
            lastRecordMs = now
            record(
                "renderer ${loop.label} fault #$streak: ${throwable.javaClass.simpleName} " +
                    "(recoveries=$recoveries)"
            )
        }
        if (streak < threshold) return
        streak = 0
        streakStartedMs = 0L
        if (suspended) return
        if (recoveries < maxRecoveries) {
            recoveries++
            record("renderer ${loop.label}: re-creating after $threshold consecutive faults (attempt $recoveries/$maxRecoveries)")
            recoveryCallback?.invoke(recoveries)
        } else if (!degradedNotified) {
            degradedNotified = true
            suspended = true
            record("renderer ${loop.label}: degraded after $recoveries re-creation(s), last fault ${throwable.javaClass.simpleName}")
            degradedCallback?.invoke(loop, recoveries)
        }
    }

    /** Test/diagnostic view of the current streak. */
    internal fun currentStreak(): Int = streak
}

/**
 * Run one iteration of a continuing loop, confining a fault to it (spec:
 * car-host-fault-isolation — No fault escapes into the host path).
 *
 * Returns the block's value, or `null` when the iteration faulted — the caller's loop
 * continues either way. `CancellationException` is rethrown: a cancelled scope must end the
 * loop (renderer shutdown, screen stop), and swallowing it would leave a loop running that
 * nothing can stop.
 *
 * `inline` so the happy path allocates nothing (the guarded body is a lambda only at compile
 * time): it is the frame path's hot loop.
 */
internal inline fun <T> RenderLoopSupervisor.runIteration(loop: RenderLoop, block: () -> T): T? =
    try {
        block().also { noteCleanIteration() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        noteFault(loop, t)
        null
    }
