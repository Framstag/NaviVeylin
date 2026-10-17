# Design

## D1 — The sandbox state, and why it is the dispatcher's flags

`AndroidUiDispatcher.Main` is `by lazy { AndroidUiDispatcher(Choreographer.getInstance(), HandlerCompat.createAsync(
Looper.getMainLooper())) }` (Compose `AndroidUiDispatcher.android.kt`), a **static on the class**, so it lives as
long as the sandbox's class loader — one sandbox per test configuration, shared by every test of that
configuration. `dispatch()` is the only way work reaches that dispatcher and it is guarded by
`scheduledTrampolineDispatch`:

```kotlin
override fun dispatch(context: CoroutineContext, block: Runnable) {
    synchronized(lock) {
        toRunTrampolined.addLast(block)
        if (!scheduledTrampolineDispatch) {
            scheduledTrampolineDispatch = true
            handler.post(dispatchCallback)                  // dropped by the per-test reset
            if (!scheduledFrameDispatch) { scheduledFrameDispatch = true; choreographer.postFrameCallback(dispatchCallback) }
        }
    }
}
```

Both posted callbacks are dropped when the test ends (main looper queue cleared; `ShadowPausedChoreographer
.resetState()` clears the frame callback queues), so nothing can ever clear those two flags again. The next
Compose case's composition and its `Recomposer` dispatch through that same dispatcher and are simply never run:
`ComposeIdlingResource.isIdleNow` stays false for Espresso's whole master idling policy (60 s) and the case fails
with `AppNotIdleException`. The trigger is any Compose snapshot write that reaches the global snapshot's apply
observer — which is armed sandbox-wide once any Compose view has been created — i.e. every ViewModel state write
in the ~150 non-Compose `:app` Robolectric classes.

## D2 — Measure it with three cases, not with a 20-minute suite

The three-case probe (Compose → plain write → Compose, one sandbox, `@RunWith(Suite)` so one Gradle class = one
fork and a fixed order) turned the failure into a 60 s reproduction on the pre-fix tree and a ~0.5 s green after
the repair, and it names the mechanism instead of a symptom:

```
[A: after setContent]      trampolineScheduled=false frameScheduled=false trampolinedQueue=0
[B: after write]           trampolineScheduled=true  frameScheduled=true  trampolinedQueue=1
[C: before setContent]     trampolineScheduled=true  frameScheduled=true  trampolinedQueue=1   ← pre-fix
[C: before setContent]     trampolineScheduled=false frameScheduled=false trampolinedQueue=0   ← repaired
```

`ComposeSandboxIsolationTest` is that probe, kept as the guard. Two earlier reproduction routes were wrong for
this purpose and are recorded so they are not retried: a class-order bisection of the suite (the boundary moves
with the order, and a `--tests` slice does not preserve the suite's order), and a thread dump at the hang (it
shows the idling loop, which is the *symptom* of a dispatcher that never drains, not the state behind it).

## D3 — Drop the stranded work; do not run it

The dispatcher's own `dispatchCallback` is the natural repair — running it performs the trampoline dispatch and
clears the frame flag when nothing is queued. It was implemented first and **rejected on measurement**: the
resumed continuation belongs to a finished test and observes that test's half-torn-down owner, so the *next*
Compose case failed with

```
Detected multithreaded access to SnapshotStateObserver: previousThreadId=49, currentThread={id=36, name="SDK 36 Main Thread"}
```

Dropping the queues and clearing the flags is the state a fresh sandbox is in, and has no such side effect. The
repair is wrapped so it cannot fail the case it protects (it runs before every test), and it is a no-op when the
dispatcher is not armed.

## D4 — Why the rule and not a `@Config`, a shadow or a fork budget

- **A fresh sandbox per class** (`@Config(sdk=…)`/`qualifiers=…`, or `forkEvery = 1`) works but is paid per class
  (~11 s of Robolectric bootstrap each, and a behaviour-changing config for 68 classes).
- **A custom shadow with `@Resetter`** (registered through `robolectric.properties` `shadows=`) is the
  Robolectric-native hook, but resetters are emitted by Robolectric's annotation processor into the generated
  `ShadowProvider`, and `:app` does not run that processor — the resetter would never be called.
- **A test `Application`** (`robolectric.properties` `application=`) is the cheapest global hook, but
  `NaviVeylinApp` is final and Hilt's `@HiltAndroidApp` owns it; opening it for tests is a production change for
  a test-only defect.
- A rule in the test source set is therefore the only option that repairs the exact state, needs no production
  change, and can be enforced by a text-only check.
- **No Gradle-level hook can do it**: the repair must run inside the sandbox class loader (it reflects on the
  sandbox's Compose classes), while `Test.beforeTest` and a `ServiceLoader` `RunListener` are loaded by the test
  worker's loader and cannot reach them.

## D5 — The invariant needs a check, not a convention

68 classes carry the rule, and the class that omits it is exactly the class that fails — a test cannot guard
itself. `tools/check-compose-sandbox-rule.sh` reads the test sources (no build, no device, seconds) and fails the
run that adds a Compose test class without `@get:Rule(order = 0) val composeSandbox = ComposeSandboxRule()`. It
ships with a selftest that runs the checker against a temporary tree, in the shape of the repository's other
checkers.

## D6 — Residue: the sandbox's snapshot notifications (named and fixed 2026-10-17)

After this change one `:app` mobile slice still reproduces a cascade (`ui.map.*` + `ui.mapmanager.*`, 937 tests;
24 cases × 60 s in one run, 1 case in another), and the failing case's sandbox state at the failure is **clean**:

```
thread=36 dispatcher=… trampolineScheduled=false frameScheduled=false trampolined=0 onFrame=0
choreographerIsCurrent=true snapshotPending=true snapshotPendingAfterApply=false
```

After this change one `:app` mobile slice still reproduces a cascade (`ui.map.*` + `ui.mapmanager.*`, 937 tests; 24 cases ×
60 s in three runs, 1 case in three others — same classes, same exception), and every idling input reachable from test sources
was measured clean at the failure:

| input | measured | verdict |
|---|---|---|
| dispatcher flags/queues, `choreographerIsCurrent` | `false false 0 0`, `true` | clean |
| `hasPendingChanges()` after the check's own `sendApplyNotifications()` | `false` | clean |
| a live writer: pending again after `sendApplyNotifications()` + `idleFor(50 ms)` (frames) | `false`, at every start and failure | no writer (a per-frame leak is falsified) |
| the root's layout inputs (`hasPendingMeasureOrLayout`, `isAttachedToWindow`, `rootView.parent != null`) | `attached=true hasParent=true pendingLayout=false busyAttaching=false` | layout side clean |
| `clock.hasAwaiters` / `mainRecomposer.hasPendingWork` | not reachable from test sources | **the surviving candidates** |

**Then measured (2026-10-17) through the framework's own recorded flags**, by a probe that reaches the live
`AndroidComposeUiTestEnvironment` (its `ComposeIdlingResource`, `Recomposer`, `FrameClock`): at a failure the deciding input is
**`hadSnapshotChanges=true`** with `hadRecomposerChanges=false hadAwaitersOnMainClock=false hadPendingSetContent=false
hadPendingMeasureLayout=false`, the recomposer `Idle` (`changeCount=0`), `frameAwaiters=0`, and the sandbox's
`GlobalSnapshotManager` never consuming its notification (`started=true sent=true`). The global snapshot therefore holds one
state that stays modified (`modified=1`), `shouldPumpTime()` is true in every iteration, and the loop pumps its 100 frames
(~25 µs) forever. **Clearing the manager's `started`/`sent` flags so the next Compose view re-arms the consumer was implemented
and falsified** (same 24 cases; `started=true sent=true` again at the failure) and reverted.

**Resolved.** A second probe named the record and the write that armed it (2026-10-17): the `modified` element is a
`ParcelableSnapshotMutableState` written through the **current global snapshot** during the failing case's own
`setContent` — its view-attach path (`BasemapSectionComposeTest.downloadingShowsCompactStatus`, `PhoneWindow.setContentView`)
— so no pinned or never-applied mutable snapshot owns it. The owning snapshot is therefore the global one, the case's own
dispatcher flags are clean (`false false 0 0`, `choreographerIsCurrent=true`) and the main looper is idle at the failure, and
the sandbox is already wedged when the case starts (`started=true sent=true`, 8 478 writes of history in that sandbox; the
sandbox that runs the neighbouring classes reads `sent=false` — the wedge is per sandbox, not per JVM).

The mechanism is the one cause #1 fixed, wearing a different hat: `GlobalSnapshotManager` starts **once per sandbox** the
coroutine that is the only main-thread caller of `Snapshot.sendApplyNotifications()`, on the same `AndroidUiDispatcher.Main`;
when Robolectric's per-test reset drops that coroutine's dispatch, `sent` stays `true` forever, so the manager never sends
again — and `Recomposer` learns about snapshot changes *only* from apply notifications, so nothing applies the write and the
state stays modified for the whole 60 s budget. **Fix** (test side, `ComposeSandboxDispatcher.repair()`): apply whatever is
pending, then replace the manager's notification with a latch-free kick — every global write posts one main-looper message
(coalesced with `hasCallbacks`) that runs `sendApplyNotifications()`, so a kick the reset drops is re-posted by the next write.
**Evidence:** `ComposeSandboxSnapshotPipelineTest` (the measured `sent=true` wedge, one case) fails without the repair with
`AppNotIdleException: Compose did not get idle after 2490270 attempts in 60 SECONDS` and passes with it in seconds; the
`ui.map.*` + `ui.mapmanager.*` slice (941 tests) was red 2 of 2 runs without the kick (same case, 60 s each) and green twice
with it (1m 24s, 1m 21s, 0 `AppNotIdleException`).

`fix-memory-responder-poll-leak` stays retired: its premise (`MemoryPressureResponder`'s 30 s poll) §195 had already
falsified, and nothing measured here revives it.
