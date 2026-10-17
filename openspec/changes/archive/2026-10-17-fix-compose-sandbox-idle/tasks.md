# Tasks

## 1. Identify the sandbox state behind the cascade

- [x] 1.1 Build a three-case reproduction in one sandbox (Compose case → plain case that writes a Compose snapshot
  state → Compose case) as a `@RunWith(Suite)` class, so one Gradle test class means one fork and a fixed order,
  and dump the sandbox state from each case. Verify: on the pre-fix tree the third case fails at `setContent`
  with `AppNotIdleException: Compose did not get idle after 809987 attempts in 60 SECONDS`, while the first is
  green and the dump shows `trampolineScheduled=true frameScheduled=true trampolinedQueue=1` from the plain
  case's write onward. **Done 2026-10-16:** reproduced in 70 s; the dispatcher, its `Choreographer` and the
  `Looper` are the *same* objects in all three cases (so this is not the stale-`Choreographer` variant known
  upstream) and the flags survive the case boundary.
- [x] 1.2 Name the mechanism: `AndroidUiDispatcher.Main` is a per-sandbox static and `dispatch()` posts its
  two draining callbacks (main `Handler` message, `Choreographer` frame callback) that Robolectric's per-test
  reset drops. Verify: the composed sources of `AndroidUiDispatcher.android.kt` (public `choreographer`,
  `scheduledTrampolineDispatch`/`scheduledFrameDispatch` guard), `ShadowPausedLooper.idle()`/
  `ShadowPausedChoreographer.resetState()` (queue and callback-queue resets) and
  `RobolectricIdlingStrategy`/`ComposeIdlingResource` (what "did not get idle" reads) agree with every measured
  number, including the ~25 µs per idling attempt (100 pumped frames per iteration).
- [x] 1.3 Record the candidates this excludes, so they are not re-tried: the `MemoryPressureResponder` 30 s poll
  (§195 had falsified it), the "loaded host" reading, a changed `Choreographer` identity, and the *class* in
  front of the first failure (the same slice fails on different classes in different runs).

## 2. Repair it

- [x] 2.1 `app/src/test/java/com/naviveylin/test/ComposeSandboxDispatcher.kt`: drop the stranded queues and clear
  both scheduled-dispatch flags when they are set — the state a fresh sandbox is in — confined so the repair can
  never fail a case. Verify: the three-case probe goes green (0.5 s for the third case) and its dump reads
  `trampolineScheduled=false frameScheduled=false trampolinedQueue=0`.
- [x] 2.2 `app/src/test/java/com/naviveylin/test/ComposeSandboxRule.kt` + the declaration in all 67 Compose test
  classes (`@get:Rule(order = 0) val composeSandbox = ComposeSandboxRule()` before the Compose rule). Verify:
  `tools/check-compose-sandbox-rule.sh` reports `68 Compose test classes carry it`; `:app` mobile compiles and
  the 134 cases of the previously hanging classes (`ui.mapmanager.*`, `NextTurnOverlayTest`,
  `NavigationStateOverlayComposeTest`, `RouteSummaryStepSelectionTest`) run **green in 24 s** where the recorded
  runs spent ~20 minutes in 60 s timeouts.
  **Rejected during this task (measured):** running the dispatcher's own `dispatchCallback` instead of dropping
  the work. It revives a finished case's continuation and fails the next case with
  `Detected multithreaded access to SnapshotStateObserver: previousThreadId=49, currentThread={…}`.
- [x] 2.3 Keep the repair honest about its scope: the mechanism was **not** fixed by disabling the
  `MemoryPressureResponder` loop (§195's falsified candidate) and does not explain the whole cascade — a
  `ui.map.*` + `ui.mapmanager.*` slice still reproduces 24 cases with the dispatcher clean. Recorded in the
  dispatcher's own doc, `guidelines/Build.md` §6 and `TODO.md` §195.

## 3. Guard the invariant

- [x] 3.1 Turn the probe into `app/src/test/java/com/naviveylin/test/ComposeSandboxIsolationTest.kt`: one class,
  three steps in `NAME_ASCENDING` order (Compose → plain write → Compose), asserting the third step idles and
  sees the state the plain step wrote. Verify: red without the rule (60 s `AppNotIdleException`) and green with
  it (`tests=3 failures=0`, 48.7 s of which 45.4 s is the first Robolectric case in a fresh, loaded JVM).
- [x] 3.2 `tools/check-compose-sandbox-rule.sh` + `tools/check-compose-sandbox-rule-selftest.sh`: fail the run
  when a Compose test class omits the rule or omits `order = 0`. Verify: selftest 4/4 (`ok` on the carrying
  class, `FAIL` on the missing rule, `FAIL` on the unordered rule, `ok` on a tree with no Compose class) and
  the checker green on the tree, naming 68 classes. Text files only: no build, no device, seconds of run time.
- [x] 3.3 Wire the checker into `.github/workflows/build.yml` beside the other text-only checks, and state the
  rule, its rationale and its residue in `guidelines/Build.md` §6 ("Test constraints").

## 4. OpenSpec artifacts

- [x] 4.1 `proposal.md`, `design.md` and this change's `specs/unit-test-suite-runtime/spec.md` carry the why, the
  mechanism, the measured evidence, the rejected alternatives and the modified requirement.
- [x] 4.2 Retire `openspec/changes/fix-memory-responder-poll-leak/`, whose README names the cause `TODO.md` §195
  had already falsified, so the backlog keeps one owner per entry.

## 5. Residue — named and fixed 2026-10-17, tracked in `TODO.md` §195

- [x] 5.1 Name the surviving never-idle condition, measured at the failure. **Measured 2026-10-17** (probe over the live
  `AndroidComposeUiTestEnvironment`): the deciding input is `hadSnapshotChanges=true` (`hadRecomposerChanges=false`,
  `hadAwaitersOnMainClock=false`, `pendingLayout=false`, recomposer `Idle`, `frameAwaiters=0`); the global snapshot holds one
  state that stays modified (`modified=1`) while the sandbox's `GlobalSnapshotManager` never consumes its notification
  (`started=true sent=true`). Already excluded (2026-10-16): the dispatcher flags/queues, the snapshot right after the check's own
  `sendApplyNotifications()`, a live writer (pending stays false after a 50 ms frame idle), the layout side of the root, and host
  load. **Named 2026-10-17** by a second probe (write observer + apply-observer ring + reflection into the global snapshot): the
  `modified` element is a `ParcelableSnapshotMutableState` written through the *current global snapshot* during the failing case's
  own `setContent` view-attach (`BasemapSectionComposeTest.downloadingShowsCompactStatus`, `PhoneWindow.setContentView`), so no
  pinned or never-applied mutable snapshot owns it; the case's dispatcher flags are clean, the main looper is idle and the
  thread-local snapshot is null at the failure, while the sandbox already reads `started=true sent=true` at that case's start.
- [x] 5.3 Fix the notification half in the sandbox repair: `ComposeSandboxDispatcher.repair()` applies what is pending
  (`Snapshot.sendApplyNotifications()`) and then replaces the manager's once-per-sandbox notification with a latch-free kick —
  every global write posts one main-looper message (coalesced through `hasCallbacks`) that runs `sendApplyNotifications()`, so a
  kick the per-test reset drops is re-posted by the next write. Verify (`revert-check`):
  `ComposeSandboxSnapshotPipelineTest` sets the measured `sent=true` wedge, and its one case fails on a tree without the kick with
  `AppNotIdleException: Compose did not get idle after 2490270 attempts in 60 SECONDS` and passes with it in seconds; the
  `ui.map.*` + `ui.mapmanager.*` slice (941 tests) was red 2 of 2 runs with the probe and no kick (same case, 60 s each) and
  green twice with the kick (`0 AppNotIdleException`, 1m 24s and 1m 21s).
- [x] 5.2 A green both-flavor `:app` gate on HEAD. **Done 2026-10-17:** `./gradlew :app:testMobileDebugUnitTest
  :app:testAutomotiveDebugUnitTest -PforceTests --no-build-cache` → **BUILD SUCCESSFUL in 4m 34s, 0 `AppNotIdleException`**,
  275 classes / 2059 tests / 0 failures / 0 errors per flavour (the same two tasks were ~40 minutes and red with 31 mobile /
  38 automotive `AppNotIdleException` before). The mobile slice with the notification repair was green 2026-10-17 (941 tests,
  0 failures, 1m 21s).
