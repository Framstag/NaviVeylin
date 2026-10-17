# Proposal

## Why

The `:app` unit-test suites cascade into 60 s timeouts: 31 cases on mobile and 38 on automotive in one measured
gate run (`./gradlew test -PforceTests --no-build-cache`, `TODO.md` §195), every one of them
`androidx.test.espresso.AppNotIdleException: Compose did not get idle after ~2.4M attempts in 60 SECONDS` at
`setContent`, none an assertion failure. Each failing case burns its whole idling budget, so the two `:app` test
tasks — the only Compose users in the build (`:auto` and `:core` have no Compose test source) — turned a ~3-minute
suite into ~40 minutes, and a real Compose regression is indistinguishable from the noise.

The cascade had been read as a loaded-host artefact and then as a class-order property; neither reading produced
a cause. This change names one cause and removes it, with a two-second reproduction rather than a 20-minute one:

**`AndroidUiDispatcher.Main` is a per-sandbox static whose stranded dispatch survives Robolectric's per-test
reset.** The dispatcher is a `by lazy` on the class (`AndroidUiDispatcher.android.kt`), so it lives as long as the
sandbox's class loader; the two callbacks that would drain it — the main `Handler` message and the `Choreographer`
frame callback that `dispatch()` posts — are dropped by the reset (the main looper's queue is cleared,
`ShadowPausedChoreographer.resetState()` clears the frame callback queues). A test that writes a Compose snapshot
state and never drains the paused main looper therefore leaves the dispatcher believing a dispatch is in flight,
and the flags can never clear themselves. The next Compose case in that JVM cannot idle (`ComposeIdlingResource`
stays false until Espresso's 60 s policy expires). Every non-Compose `:app` test that mutates a ViewModel's
Compose state is such a test.

Measured with a three-case probe in one sandbox, on the pre-fix tree (2026-10-16):

```
Compose case  → clean;  opens the sandbox's snapshot pipeline
plain case    → writes a Compose state: trampolineScheduled=true frameScheduled=true trampolinedQueue=1
Compose case  → still trampolineScheduled=true frameScheduled=true trampolinedQueue=1
                setContent burns 60 s → AppNotIdleException
```

with the same shape reproducing 24 cases / 24 minutes inside one `:app` mobile slice (`ui.map.*` +
`ui.mapmanager.*`, 937 tests) and 31 cases in the full gate.

## What Changes

- `app/src/test/java/com/naviveylin/test/ComposeSandboxDispatcher.kt` repairs that sandbox state: it drops the
  stranded queues and clears both flags, which is the state a fresh sandbox is in. It deliberately does **not**
  run the dispatcher's own callback to revive the stranded work — a resumed continuation of a finished test
  touches that test's half-torn-down objects, measured as
  `Detected multithreaded access to SnapshotStateObserver: previousThreadId=49, currentThread={… "SDK 36 Main
  Thread"}` failing the *next* case.
- `app/src/test/java/com/naviveylin/test/ComposeSandboxRule.kt` runs the repair as a test rule, and all 68
  `:app` Compose test classes declare it as their first rule —
  `@get:Rule(order = 0) val composeSandbox = ComposeSandboxRule()` before the Compose rule, whose own setup is
  Compose work. Only Compose classes need it: a non-Compose case does not idle, so the leak cannot fail one.
- `app/src/test/java/com/naviveylin/test/ComposeSandboxIsolationTest.kt` is the guard: one class, three steps in
  one sandbox (Compose, then a plain Compose-state write, then Compose). It fails on a tree without the rule
  (60 s) and passes with it (seconds).
- `tools/check-compose-sandbox-rule.sh` (+ its selftest, + a step in `.github/workflows/build.yml`) fails the run
  when a Compose test class is added without the rule or without `order = 0`: the invariant cannot be asserted
  from inside a test, because the class without the rule is exactly the class that fails.
- `guidelines/Build.md` §6 states the rule, its rationale and the notification half; `TODO.md` §195 records the
  mechanisms, the measurements and what is left.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `unit-test-suite-runtime`: a module's suite currently owns its failure set, its fork budget and its
  parallelism. It gains the half that a shared Robolectric sandbox breaks: a Compose test case SHALL start from a
  repaired sandbox-wide Compose state, and a case that leaves the sandbox's Compose dispatcher armed SHALL NOT
  make the *next* case fail. The repository-side rule that carries it (every Compose test class repairs the
  sandbox before its Compose rule, enforced by a text-only check) is part of the requirement's scenario, not a
  convention.

## Impact

- Tests: `app/src/test/java/com/naviveylin/test/{ComposeSandboxDispatcher,ComposeSandboxRule,ComposeSandboxIsolationTest}.kt`
  (new) and the 67 Compose test classes that gain the rule declaration.
- Gates: `tools/check-compose-sandbox-rule.sh`, `tools/check-compose-sandbox-rule-selftest.sh` (new) and one step
  in `.github/workflows/build.yml`; `guidelines/Build.md` §6.
- Production code: **none** — the repair is test-only and the rule lives in test sources. No native, JNI,
  CMake, ABI, persistence or UI surface is touched, so no user-visible behaviour changes.
- Platform scope: JVM unit tests of `:app` (both flavors). `:auto`/`:core`/`osmscout-client-java` have no Compose
  test sources and are unaffected.
- Rollback: delete the three test-source files, the rule declaration in the 67 classes, the two `tools/` scripts
  and the CI step. Nothing ships, migrates or persists.
- **The residue was named and fixed too** (2026-10-17, `design.md` §D6): the sandbox's
  `GlobalSnapshotManager` — the only main-thread caller of `Snapshot.sendApplyNotifications()`, started once per
  sandbox on the same `AndroidUiDispatcher.Main` — loses its consumer to the same per-test reset, and its `sent`
  latch then keeps the runtime from ever applying another write, so a case that writes while it attaches its view
  cannot idle (`hadSnapshotChanges=true` in every iteration, ~3 M pumped frames per 60 s). The sandbox repair now
  applies what is pending and replaces that notification with a latch-free kick per global write, guarded by
  `ComposeSandboxSnapshotPipelineTest` (red without it, green with it; the `ui.map.*` + `ui.mapmanager.*` slice
  was red 2 of 2 runs without it and green twice with it). The earlier `fix-memory-responder-poll-leak` premise
  stays falsified: nothing measured in either half revives the 30 s poll.
