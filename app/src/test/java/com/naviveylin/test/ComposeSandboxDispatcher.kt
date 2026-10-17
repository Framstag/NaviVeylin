package com.naviveylin.test

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.os.HandlerCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Repairs the Robolectric sandbox's Compose plumbing before a test runs: the dispatcher and the
 * snapshot-apply notifications ([repair] does both). Each half states the defect and the
 * measurement, `TODO.md` §195.
 *
 * **Half one: the dispatcher.** `AndroidUiDispatcher.Main` is a *per-sandbox* static — a `by lazy`
 * on the class — so it outlives Robolectric's per-test reset. The two callbacks that would drain it
 * do **not** survive that reset: `dispatch()` posts `dispatchCallback` to the main `Handler` *and*
 * to the `Choreographer`, and the reset drops both (the main looper's queue is cleared,
 * `ShadowPausedChoreographer.resetState()` clears the frame callback queues). A test that writes a
 * Compose snapshot state — every ViewModel state write in a non-Compose test — and never drains the
 * paused main looper therefore leaves the dispatcher believing a dispatch is in flight, and the
 * flags can never clear themselves:
 *
 * ```
 * probe, before the fix          trampolineScheduled=true frameScheduled=true trampolinedQueue=1
 * probe, next Compose case       trampolineScheduled=true frameScheduled=true trampolinedQueue=1
 *                              → setContent never idles, AppNotIdleException after 60 s
 * probe, with this repair        trampolineScheduled=false frameScheduled=false trampolinedQueue=0
 * ```
 *
 * The stranded work belongs to a test that has already finished, so the repair drops it and clears
 * both flags — the state a fresh sandbox is in. It deliberately does **not** run the dispatcher's
 * own callback to revive that work: a resumed continuation of a finished test touches that test's
 * half-torn-down objects, which is measured — running it here made a later Compose case fail with
 * `Detected multithreaded access to SnapshotStateObserver: previousThreadId=49, currentThread=…`
 * (2026-10-16). Anything the repair raises is confined: it runs on the path of every test, and a
 * repair must never be able to fail one.
 *
 * **Half two: the snapshot-apply notifications** (the residue `TODO.md` §195 tracked; measured and
 * fixed 2026-10-17). The dispatcher repair drops stranded dispatch work, and one stranded dispatch
 * is not work but a *consumer*: `GlobalSnapshotManager` starts **once per sandbox** (from the first
 * Compose view) a coroutine on that same `AndroidUiDispatcher.Main`, and that coroutine is the only
 * caller of `Snapshot.sendApplyNotifications()` on the main thread. It is guarded by an
 * `ensureStarted` latch (`started`) and a once-per-write latch (`sent`). When the reset drops its
 * dispatch, `sent` stays `true` forever, so the manager never sends another notification in that
 * sandbox — and the runtime learns about snapshot changes *only* from apply notifications
 * (`Recomposer` registers an apply observer, never a global write observer).
 *
 * Measured at a `:app` failure (`ui.map.*` + `ui.mapmanager.*`, `BasemapSectionComposeTest.
 * downloadingShowsCompactStatus`): the case's own `setContent` writes a state while it attaches its
 * view, nothing applies that write, and `ComposeIdlingResource.isIdleNow` then reads
 * `hadSnapshotChanges=true` in **every** iteration — the other idling inputs are clean (dispatcher
 * `false false 0 0`, `choreographerIsCurrent=true`, main looper idle, `threadLocal=null`, no pinned
 * mutable snapshot, recomposer `Idle`, `frameAwaiters=0`) — so `shouldPumpTime()` stays true and each
 * iteration pumps its 100 frames (~25 µs, ~3 M iterations in the 60 s budget) without ever
 * converging. The record's `modified` entry is a `ParcelableSnapshotMutableState` written through
 * the *current global snapshot*; the sandbox is what is wedged, not the record. Clearing the
 * manager's `started`/`sent` flags so the next Compose view re-arms the consumer was implemented and
 * **falsified** (same 24 cases; `started=true sent=true` again at the failure): the failing case arms
 * a fresh pipeline during its own `setContent` and that consumer's dispatch is lost the same way.
 *
 * The repair therefore replaces the manager's notification with one that cannot wedge: every global
 * write posts **one** main-looper message (coalesced through `hasCallbacks`) that runs the
 * framework's own `sendApplyNotifications()`. It keeps no latch, so a kick the reset drops is
 * re-posted by the next write, and a kick that runs applies exactly what the manager would have. It
 * first applies whatever an earlier test left unapplied, so no case starts with a pending record it
 * did not write.
 *
 * Every Compose test class must carry [ComposeSandboxRule], and
 * `tools/check-compose-sandbox-rule.sh` (CI) fails the run when a Compose test class does not: the
 * sandbox, not the test, is what leaks, so a class without the rule inherits whatever a previously
 * finished test left behind. `ComposeSandboxIsolationTest` guards the dispatcher half and
 * `ComposeSandboxSnapshotPipelineTest` the notification half.
 */
object ComposeSandboxDispatcher {

    /** Make the sandbox's Compose dispatcher and snapshot notifications look like a fresh one's. */
    fun repair() {
        repairDispatcher()
        repairSnapshotNotifications()
    }

    /**
     * The notification half of [repair] — the object's doc holds the defect and the measurement.
     * Safe to call at any time.
     */
    private fun repairSnapshotNotifications() {
        runCatching {
            // What `GlobalSnapshotManager` would have done with an unapplied write: it is the only
            // other caller of this on the main thread, and it can be dead (below).
            Snapshot.sendApplyNotifications()

            if (!kickInstalled.compareAndSet(false, true)) return
            val handler = HandlerCompat.createAsync(Looper.getMainLooper())
            Snapshot.registerGlobalWriteObserver {
                // Coalesced, and deliberately without a latch of its own: a kick the reset drops is
                // re-posted by the next write, so the sandbox cannot wedge a second time.
                if (!handler.hasCallbacks(snapshotKick)) handler.post(snapshotKick)
            }
        }
    }

    /** Make the sandbox's Compose dispatcher look like a fresh one. Safe to call at any time. */
    private fun repairDispatcher() {
        runCatching {
            val dispatcher = mainDispatcher()
            val cls = dispatcher.javaClass
            val trampolineScheduled = flag(dispatcher, "scheduledTrampolineDispatch")
            val frameScheduled = flag(dispatcher, "scheduledFrameDispatch")
            if (!trampolineScheduled && !frameScheduled) return

            // The stranded work can only belong to a finished test, and the callbacks that would
            // have run it were dropped by Robolectric's per-test reset — so the flags would stay
            // set forever unless they are cleared here.
            listOf("toRunTrampolined", "toRunOnFrame", "spareToRunOnFrame").forEach { field ->
                runCatching {
                    val queue = cls.getDeclaredField(field).apply { isAccessible = true }.get(dispatcher)
                    queue.javaClass.getMethod("clear").invoke(queue)
                }
            }
            setFlag(dispatcher, "scheduledTrampolineDispatch", false)
            setFlag(dispatcher, "scheduledFrameDispatch", false)
        }
    }

    private fun mainDispatcher(): AndroidUiDispatcher {
        val context: CoroutineContext = AndroidUiDispatcher.Main
        var found: AndroidUiDispatcher? = null
        context.fold(Unit) { _, element -> if (element is AndroidUiDispatcher) found = element }
        return found ?: error("no AndroidUiDispatcher in $context")
    }

    private fun flag(dispatcher: AndroidUiDispatcher, name: String): Boolean =
        dispatcher.javaClass.getDeclaredField(name).apply { isAccessible = true }.getBoolean(dispatcher)

    private fun setFlag(dispatcher: AndroidUiDispatcher, name: String, value: Boolean) {
        dispatcher.javaClass.getDeclaredField(name).apply { isAccessible = true }
            .setBoolean(dispatcher, value)
    }

    private val kickInstalled = AtomicBoolean(false)

    /** One pending kick at a time; it applies exactly what the sandbox's manager would have. */
    private val snapshotKick = Runnable { Snapshot.sendApplyNotifications() }
}
