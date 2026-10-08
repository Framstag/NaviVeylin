# Tasks

Parent spec for every task below: `rerouting-visual-feedback` (delta in
`specs/rerouting-visual-feedback/spec.md`) — scenarios are named per task. Design: `design.md`
(D1/D2 = the helper and the single update, D3 = off-route untouched, D4 = cancel path).

## 1. The engine clears the attempt's own flag

- [x] 1.1 In `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`, add the private
  helper `endRerouteAttempt()` (one `_state.update { it.copy(isRerouting = false) }`) and call it
  from the `onError` handler and the synchronous `catch` of `calculateAndStart` — after their
  `endCalculation(token, …)` guard, with the flag merged into the same update that publishes
  `errorMessage`/`errorOrigin` (design D1, D2) — and from `cancelAcquisition` when
  `_state.value.isRerouting` is true (design D4). Do not touch `onRouteInstructions`,
  `stopNavigation` or any `isOffRoute` writer. Verify: `./gradlew :app:assembleMobileDebug
  -Pandroid.injected.build.abi=arm64-v8a` → `BUILD SUCCESSFUL` with no `w:` warnings, and
  `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests
  "com.naviveylin.navigation.*"` stays green with the class counts quoted.
- [x] 1.2 Add `NavigationEngineRerouteTest.aFailedRerouteEndsTheReroutingState`
  (`app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt`, pattern of the
  existing `aFailedRerouteKeepsTheRunningNavigationLease`): hold the reroute delivery, fail it,
  then assert `isRerouting == false`, `isOffRoute == true`, `isNavigating == true`, the step list
  unchanged and the error still published; drive one further state change (a position tick) and
  assert `isRerouting` is still `false`. Exercises the scenarios *Failed reroute ends rerouting
  state*, *Off-route survives a failed reroute* and *No surface is told a reroute is running after
  it ended*. Verify: `--tests "com.naviveylin.navigation.NavigationEngineRerouteTest"`, quote the
  result XML's `tests`/`failures`.
- [x] 1.3 Add `NavigationEngineRerouteTest.aThrowingRouteCallEndsTheReroutingState`, using
  `FakeOSMScoutClient`'s synchronous-throw seam for `calculateRouteWithProfile`, asserting the same
  three flags plus the published error; it covers the second failure handler of the same scenario.
  Verify: the case appears in the XML with a distinct `time`, not skipped.
- [x] 1.4 Add `NavigationEngineCalculationCancelTest.aCancelledRerouteEndsTheReroutingState`
  (scenario *Cancelled reroute ends rerouting state*: cancel while the reroute is in flight →
  `isRerouting == false`, navigation still running) and the boundary
  `aCancelledSurfaceLessAcquisitionLeavesTheReroutingFlagFalse` (design D4: the rule is not "any
  cancel clears the flag"). Verify: `--tests
  "com.naviveylin.navigation.NavigationEngineCalculationCancelTest"` green with both cases
  executed.
- [x] 1.5 Scenario-to-case sweep for the paths this change leaves alone: confirm a case asserts
  `isRerouting == false` after stopping navigation while a reroute was in flight (scenario
  *Navigation stops during rerouting*) — add the assertion to `NavigationEngineStopPathTest` if it
  is missing — and confirm the existing `instructionListUpdatesAfterAReroute` still covers
  *Rerouting completes* and the reroute-begin cases cover *Rerouting begins*. Verify: each named
  case executed in the XML of its class run, listed in the task's report.
- [x] 1.6 Revert-check A (`revert-check` skill): name the triple — invariant "a failed reroute
  clears `isRerouting`", single mutation "delete the `endRerouteAttempt()` call from the `onError`
  handler", case `aFailedRerouteEndsTheReroutingState`. The mutation must fail *that* flag
  assertion (not a premise or a compile error). Restore, sweep the mutation marker, then force the
  green run (`--rerun-tasks`) and quote both runs.
- [x] 1.7 Revert-check B: same discipline for the cancel site — mutation "delete the
  `endRerouteAttempt()` call from `cancelAcquisition`", case
  `aCancelledRerouteEndsTheReroutingState`. Restore, sweep, forced green, both runs quoted.

## 2. The car template reports guidance after a failed reroute

- [x] 2.1 Add a case to `auto/src/test/java/com/naviveylin/auto/NavigationTemplateMapperTest.kt`:
  a state with `isNavigating = true`, `isRerouting = false`, a next instruction and a positive
  `etaMillis` maps through `NavigationTemplateMapper.tripFromState` to a trip carrying that step and
  its estimate, plus the negative control that the same state with `isRerouting = true` still maps
  to the loading trip. Scenario *Car guidance survives a failed reroute*, design D6 (no head unit
  needed: the mapper is a pure function). Verify: `./gradlew :auto:testDebugUnitTest --tests
  "com.naviveylin.auto.NavigationTemplateMapperTest"` green with both cases executed.

## 3. Gate, rule, and evidence

- [x] 3.1 Forced both-flavor gate (`guidelines/Build.md` §2 recipe: foreground, output redirected,
  generous timeout): `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest
  :core:testDebugUnitTest :auto:testDebugUnitTest --rerun-tasks` → 0 failures and 0 warnings, with
  the executed-task counts and elapsed time quoted from the log (a `BUILD SUCCESSFUL in 5s`
  up-to-date line is not evidence, `guidelines/Build.md` §4). Additionally build the mobile debug
  APK for all three ABIs and confirm each `libosmscout_client_java.so`/`libosmscout_client_javad.so`
  is packaged (this change adds no native code, so the APK should be byte-comparable in that area).
- [x] 3.2 `guidelines/Design.md` §4 carries the failed-attempt rule for the lease; extend it with
  the state half — *the attempt that set `isRerouting` clears it on every terminal outcome, and a
  superseded attempt must not clear a live one* — citing this change and spec
  `rerouting-visual-feedback` (design D1). Verify: the paragraph exists with its citation and no
  other section contradicts it (`grep -n 'terminal outcome' guidelines/Design.md`).
- [x] 3.3 Record the verification boundary in this change's report: **no device run is owed** — the
  state assertions are engine-level (1.2-1.4) and the car-visible consequence is a pure-function
  assertion (2.1); `adb devices` is empty (2026-10-07). If a unit/AVD is available, the optional
  check is a reroute that fails on a thin map set (the "No routable node near destination" shape),
  confirming the car template returns to guidance within one emission and
  `adb logcat -s NaviVeylin` shows the error line — a partial pass is recorded as partial
  (`guidelines/Build.md` §10).

## Verification record

- **Task 1.1** — `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` → `BUILD SUCCESSFUL in 17s`,
  0 warnings; `com.naviveylin.navigation.*` `-PforceTests --no-build-cache -PnoCoverage` →
  `BUILD SUCCESSFUL in 1m 12s`, 28 of 113 tasks executed, 0 warnings, 19 classes / **121 tests / 0
  failures** (result-XML timestamps 06:23:49, fresh for that run).
- **Task 1.2/1.3** — `NavigationEngineRerouteTest` `tests="11" skipped="0" failures="0" errors="0"`;
  the two new cases appear in the XML: `aFailedRerouteEndsTheReroutingState` (10.08 s — the known
  slow-route-await cost, `TODO.md` §143), `aThrowingRouteCallEndsTheReroutingState` (0.136 s).
- **Task 1.4** — `NavigationEngineCalculationCancelTest` `tests="5" ... failures="0" errors="0"` with
  `aCancelledRerouteEndsTheReroutingState` (0.121 s); the two existing cancel cases now also assert
  `isRerouting == false` after an acquisition cancel.
- **Task 1.5** — `NavigationEngineStopPathTest` `tests="6" ... failures="0" errors="0"` with
  `stoppingDuringARerouteClearsTheRerouteFlags` (8.66 s), which sets both flags through a real reroute
  before the stop, so the pre-stop assertions are non-vacuous. (It is *not* a revert-check target for
  this change: `stopNavigation`'s whole-state reset already passed it pre-change.)
- **Task 1.6 (revert-check A)** — mutation *delete the flag clear from the `onError` handler*:
  `NavigationEngineRerouteTest` `tests="1" failures="1"`, message `java.lang.AssertionError: a failed
  reroute must not keep the surfaces on a reroute` (`assertFalse`, `NavigationEngineRerouteTest.kt:326`).
  Restored, `grep -c 'REVERT-CHECK-MUTATION' NavigationEngine.kt` → **0**, forced green
  (`--rerun-tasks`) → `tests="11" failures="0"`, `BUILD SUCCESSFUL in 2m 44s`.
- **Task 1.7 (revert-check B)** — mutation *delete the flag clear from `cancelAcquisition`*:
  `tests="1" failures="1"`, message `java.lang.AssertionError: a cancelled reroute must not keep the
  surfaces on a reroute` (`NavigationEngineCalculationCancelTest.kt:232`). Restored, marker sweep → **0**,
  forced green → `tests="5" failures="0"`, `BUILD SUCCESSFUL in 2m 4s`.
- **Task 2.1** — `:auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationTripTest"` → `BUILD
  SUCCESSFUL in 14s`, 0 warnings, `tests="20" skipped="0" failures="0" errors="0"` with
  `guidanceAfterAFailedRerouteIsNotALoadingTrip` (0.046 s).
- **Task 3.1** — `:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :core:testDebugUnitTest
  :auto:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 8m 10s` (06:35:23 → 06:43:35), **185/185
  tasks executed**, mobile **1725** / automotive **1725** / core **447** / auto **782** tests, 0 failures,
  0 errors, 0 skipped. **127** warnings in that log — all from the pre-existing warning debt
  (`TODO.md` §44) and **0** from any file this change touches. `:app:assembleMobileDebug` → all three ABIs
  present (`lib/arm64-v8a`, `lib/armeabi-v7a`, `lib/x86_64`) with `libosmscout_client_javad.so` packaged per
  ABI (the debug variant's library name, `TODO.md` §86).
- **Device boundary** — **no device run is owed and none was attempted**: the state assertions are
  engine-level (1.2-1.4) and the car-visible consequence is a pure-function assertion (2.1), so a head unit
  would re-measure what the mapper case already decides. `adb devices` was empty on 2026-10-07. The optional
  on-device check (fail a reroute on a thin map set, watch the template return to guidance) stays in the
  task above for a session that has a unit.
- **Deviation from the task text, recorded** — (a) 2.1's case lives in `NavigationTripTest`, beside the
  existing `loadingTripWhileRerouting` negative control, instead of `NavigationTemplateMapperTest`;
  (b) 1.4's design-D4 boundary is asserted on the two existing cancel cases rather than in a dedicated
  case, because such a case could only assert that a flag stays `false` when nothing set it;
  (c) 3.2's verification pattern is `grep -n 'withoutRerouteAttemptFlag\|isRerouting' guidelines/Design.md`
  because the phrase "terminal outcome" wraps across two lines there.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, and verify the archived
  spec delta landed in `openspec/specs/rerouting-visual-feedback/spec.md`.
- Remove `TODO.md` §144 (and its `§126` cross-reference) once the change is archived, through the
  `cleanup-todo` skill — it is the archive that retires the entry, not this change.
