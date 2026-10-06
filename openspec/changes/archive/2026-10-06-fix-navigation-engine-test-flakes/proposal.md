# Proposal

## Why

Three `:app` unit-test cases fail intermittently in full-suite runs and pass alone (`TODO.md` §121,
recorded for `NavigationEngineTest` and `NavigationEngineRerouteTest` across both flavors since
2026-10-03). Triaging that entry found the mechanism behind the moving victims, and it is not a test
artefact: `NavigationEngine`'s init launches a staleness ticker on `Dispatchers.Default`
(`NavigationEngine.kt:194-211`) that publishes state with a non-atomic read-modify-write —

```kotlin
_state.value = _state.value.copy(currentSpeedKmH = 0.0)   // line 207
```

Two of the 27 assignment sites of `_state` run off the main thread, and both publish a whole snapshot:
the ticker above, and the road-info lookup's publish inside `scope.launch(Dispatchers.IO)`
(`NavigationEngine.kt:911`). Every other site publishes from `Dispatchers.Main` (`scope.launch(Dispatchers.Main)`
/ `withContext(Dispatchers.Main)`), which is the confinement the spec requires. The ticker is the one
that fires unconditionally once per second while navigating, so once the 3 s staleness window has
elapsed it can write back a snapshot it read *before* another writer's publication and revert that
writer's fields; the IO publish has the same shape at a lower rate.

The recorded failure `java.lang.AssertionError: expected:<100.0> but was:<NaN>` at
`NavigationEngineTest.kt:120` is that clobber, not a slow clock: line 120 asserts `maxSpeedKmH`, and
`core/NavigationState.kt:58` declares `maxSpeedKmH: Double = Double.NaN` — `100.0` can only become
`NaN` again by restoring a state snapshot taken before the write. The same shape explains the other
recorded victims (the `lane guidance mirrored` assertion twice, `instructions mirrored` once) and why
the victim moves between runs and flavors: whichever field is published inside the tick's
read-modify-write window is the one that is lost.

Two existing requirements are violated today, so this is a spec deviation and not just gate noise:

- `navigation-engine` — "Engine lifecycle and threading": *navigation state publication SHALL be
  main-thread confined*. Two publishers run off the main thread (the staleness tick and the road-info
  lookup), and both write non-atomic snapshots.
- `unit-test-suite-runtime` — "Unit-test parallelism is declared and result-preserving": a suite run
  with the declared concurrent JVMs must report *exactly the failing classes of the same suite run in a
  single JVM*. Under load it does not.

The test side has its own half of the problem: `NavigationEngineTest.awaitState:62-70` and
`startNavigating:72-80`, and `NavigationEngineRerouteTest.awaitState:69-78`, all wait on a wall-clock
`System.currentTimeMillis() + 5000` deadline with a `Thread.sleep(10)` pump, and
`staleSpeedTickerZeroesAStaleSpeed:183-194` depends on a real 1 s tick of the production loop, so its
outcome is a function of host load rather than of the behaviour under test.

Why now: a flaky shared suite makes every later full-suite verdict unreliable (the same class of
loss `TODO.md` §117 recorded for the viewport-restore hang) and costs a re-run plus triage each time it
fires, while the underlying race is real state corruption at ~1 Hz in production. It is also cheap
now: no device, no submodule, in-tree Kotlin only.

## What Changes

- **`NavigationEngine` publishes state atomically and on the main thread.** Every write of `_state`
  goes through `MutableStateFlow.update { }` instead of `_state.value = _state.value.copy(…)`, so no
  writer can revert a field it did not set. There are currently **zero** `_state.update` calls in the
  file: 26 sites use `_state.value = _state.value.copy(…)` and one (`:613`, the stop path) assigns a
  whole `NavigationState()`. The road-info lookup stops publishing from its IO coroutine: the native
  `getRoadAt` call stays off the main thread, the result is published inside
  `withContext(Dispatchers.Main)` — which is the confinement the spec already requires.
- **The time source and the dispatchers become injected seams.** The engine takes a time-source seam
  (default: the system clock) used by the staleness comparison and by the engine's own timestamps, and
  a dispatcher seam (defaults: `Dispatchers.Default` for compute, `Dispatchers.IO` for the native
  lookup) instead of naming the dispatchers inline. The ticker keeps a private `Dispatchers.Default`
  home (its endless `delay` loop must not land on a test scheduler) while its body becomes an internal
  step a test can drive in virtual time.
- **The engine test cases stop reading the wall clock.** `NavigationEngineTest` and
  `NavigationEngineRerouteTest` replace the `System.currentTimeMillis() + 5000` /
  `Thread.sleep(10)` waits with scheduler-driven awaits on the observable state, and the staleness case
  advances the injected clock instead of sleeping. No new test may pace itself off a timer or a
  debounce (`guidelines/Build.md` §4, `TODO.md` §40.16/§40.31).
- **New cases pin the invariant, not just the symptom.** One case proves a tick cannot revert a field
  published concurrently (the recorded `maxSpeedKmH 100.0 → NaN` shape), one proves the tick still
  decays the displayed speed once the injected clock passes the window, and the three formerly flaky
  cases gain their missing assertions (lane guidance, instructions, max speed) at the moment they are
  published.
- **A guardrail line records the rule** in `guidelines/Build.md` §4: a background tick that publishes
  shared state publishes through an atomic update, never a whole-snapshot `copy` write.

Not in scope: the reroute path's lease behaviour (`TODO.md` §126), the `LoadingScreen` dead code
(§127), and the `@Config(sdk = [34])` sandbox pin (§128) — all separate entries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `navigation-engine`: the "Engine lifecycle and threading" requirement gains the atomic-publication
  rule for its background writers and the injected time source, and "One navigation state shared by all
  surfaces" gains a scenario that a background tick preserves concurrently published fields.
- `unit-test-suite-runtime`: gains a requirement that a module's suite result is independent of host
  load and of the declared concurrency — a case may not decide its outcome from wall-clock time or from
  a real-thread wait, and the failure set at the declared concurrency equals the single-JVM failure set
  (the requirement the three flakes currently violate; the existing "The failure set is unchanged by
  concurrency" scenario stays and gains the load dimension).

No capability is removed.

## Impact

**Affected modules/files**

- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — the time-source and dispatcher
  seams, the ticker loop (`:194-211`) split into loop + body, the tick's publish (`:207`), the IO
  road-info publish (`:911`) hopped to the main thread, the engine's own timestamps (`:205`, `:280`,
  `:540`, `:713`, `:808`, `:894`), and the remaining `_state.value = _state.value.copy(…)` sites
  converted to `update { }` (26 sites; all converted so there is one shape, not two).
- `app/src/main/java/com/naviveylin/di/` — providers for the injected time source and the dispatcher
  pair (exact form is design decision D1; the engine is `@Inject constructor`-built and bound in
  `NavigationViewModelModule.kt`, so a new parameter needs a provider).
- `core/src/main/java/com/naviveylin/core/SpeedStaleness.kt` — the `EngineTimeSource` seam is added
  beside it; the existing predicate is unchanged (already takes `nowMs`, and is the seam the engine
  should have used).
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineTest.kt` — `awaitState`/`startNavigating`
  helpers, `staleSpeedTickerZeroesAStaleSpeed`, `listenerCallbacksDriveTheSharedState`,
  `roadInfoThrottleHoldsItsWindow` (last one also sleeps on a real clock).
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt` — `awaitState`,
  `advanceUntilIdleBlocking` pump, `startSession`.
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationCancelTest.kt`,
  `NavigationEngineCalculationStateTest.kt`, `NavigationEngineFaultIsolationTest.kt`,
  `NavigationEngineAcquisitionTest.kt` — every construction site of the engine must follow the new
  constructor signature; the ones that already use `runTest` + `advanceUntilIdle` are the pattern the
  two flaky classes are moved onto.
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineRoadInfoTest.kt` — the road-info cases
  follow the hopped publish (they call `updateRoadInfoFromPosition` directly today).
- `guidelines/Build.md` §4 — the guardrail bullet for background publishers: an atomic update, never a
  whole-snapshot `copy` write, and the component's injected time source for timing decisions.

**Android components**: no manifest, resource, permission, Service, Car App Library or AAOS surface is
touched; the engine is a Hilt singleton in the process, consumed by `MainActivity`'s UI tree, the car
session and `NavigationNotificationService`.

**Native/JNI**: none. No libosmscout submodule patch and no `:osmscout-client-java` override — the
change is app-module Kotlin plus tests. The `FakeOSMScoutClient` stubs and the Robolectric classloader
rule are unaffected (no `@Config` is added or removed).

**Guidelines referenced**: `guidelines/Design.md` §4 (threading: state publication is main-thread
confined) and §12 (single source of truth); `guidelines/Build.md` §4 (test evidence, restored-tree
rule, no timer-paced cases) and §6 (declared fork budget/parallelism — the numbers the flake
invalidated); `guidelines/UI.md` untouched.

**Previous specifications changed**: `navigation-engine`,
`unit-test-suite-runtime` (both deltas in this change's `specs/`).

**Change type and rollback**: additive, non-breaking — no public API, state schema, persisted file or
user-visible value changes; the only observable difference is that a concurrently published field is no
longer reverted by a tick. Rollback path: revert the commit (`git revert`) — the constructor seams have
defaults that match today's production behaviour, so reverting the wiring alone restores the previous
code path without touching the call sites. No version state (`app/release-version.properties`) and no
submodule gitlink is involved.

**Scope**: general (phone + Android Auto share the one engine); nothing here is car-only or
phone-only. Verification is JVM-only, so no device and no native build is required.
