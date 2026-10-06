# Proposal — Speed up test iteration

**Change:** `speed-up-test-iteration`
**Date:** 2026-10-06
**Scope:** build/test infrastructure for all Kotlin modules and both distribution flavors. No
user-facing behaviour, no Auto/AAOS-specific capability, no UI change. Production code is touched only
to make an existing time window injectable.

## Why

An iteration still pays 2–5 minutes for a suite whose cost is neither setup nor parallelism but
*which classes execute*, and part of that cost is wall-clock waiting inside tests that the suite's own
specification already forbids.

Measured on the last full `:app` suite (`app/build/test-results/testAutomotiveDebugUnitTest`,
2026-10-06 05:00, 220 classes / 1635 tests):

| bucket | classes | class time | share |
|---|---|---|---|
| `>= 5 s` | 14 | 113 s | 45 % |
| `2–5 s` | 15 | 50 s | 20 % |
| `1–2 s` | 24 | 36 s | 14 % |
| `< 1 s` | 167 | 53 s | 21 % |
| **total** | **220** | **252 s** | 100 % (135 s wall at 2 forks) |
| plain JVM (no Robolectric) | 46 | 1 s | 0.4 % |

1. **The suite is not tiered, and ordering it would buy nothing.** The only cheap tier is 1 s of
   252 s, so "fast unit tests first" is not the lever. The lever is *not running* the classes a change
   cannot affect: an in-flight change's own artifacts name 1–9 test classes (median 6) — 0.5–4 % of the
   suite — and a 6-class pass costs ~30–45 s against 135 s.
2. **Part of the heavy bucket is wall-clock waiting the spec forbids.**
   `unit-test-suite-runtime` already requires that "a unit test controls the time its subject waits on"
   and that "a module's failure set is independent of host load and concurrency". Measured violations:
   `MapCanvasViewModelBrowseReCenterTest` spends `Thread.sleep(RECENTER_DWELL_MS + 150)` (1.15 s) in
   three methods (2.32 s each), `MapCanvasViewModelViewportRestoreTest` polls a
   `System.currentTimeMillis()` deadline (one test 5.49 s), `MapCanvasViewModelAutoZoomCommitTest`
   sleeps 230 ms per step (3.71 s + 3.04 s), `MapCanvasViewModelFixQualityTest` runs three deadline
   loops. In total **22 test files contain `Thread.sleep`** (~40 call sites) and **7+ helpers loop
   until a `System.currentTimeMillis()` deadline**. Those waits also make the suites contend: the same
   suites measured 307 s / 252 s inside one four-module invocation against 122 s isolated, and a 4-fork
   run already failed to preserve the failure set (`guidelines/Build.md` §6, `TODO.md` §132).

The first is a new iteration procedure; the second is an **unenforced existing requirement**, so it
gets a build check rather than another rule nobody measures.

## What Changes

- **Iteration pre-gate.** The documented iteration procedure gains a first pass that runs the cases a
  change *declares*: the test classes its own artifacts name, the test classes its diff touches, and
  the test classes whose source mentions a changed production type. The module suite is unchanged and
  still owed at completion. **BREAKING (procedure):** the focused-run recipe changes from an ad-hoc
  `--tests` package filter to the declared-case pass, and the pre-gate is explicitly *not* evidence.
- **Selection tool.** A deterministic, device-free tool derives that filter set from a change name and
  a `git diff` and prints the classes plus their measured class time, so the operator sees what will
  run before it runs.
- **Wall-clock waits are refused.** A build-time scan of test sources fails on `Thread.sleep`, on a
  teardown/await loop driven by `System.currentTimeMillis()`, and on sleep-based pacing, with **no
  exception list**: a site that must wait for real time is converted, not declared — the fake that
  simulates native latency becomes a test-controlled gate and the timing helper reads the seam.
- **The measured violators are converted.** The classes in the heavy bucket that owe their time to a
  wait now advance an injected time source or the test scheduler instead: the recenter dwell window,
  the fix-quality poll, the follow throttle and the viewport-restore await. The seam is not new —
  `:core` already carries `EngineTimeSource` (production value: the wall clock) and `EngineDispatchers`
  (`Production` = `Dispatchers.Default`/`IO`) as uncommitted work of an in-flight change, cited against
  the existing `unit-test-suite-runtime` scenario "Injected time instead of a real clock". This change
  extends that seam to the view-model windows and converts every remaining wait site.
- **Guidelines.** `guidelines/Build.md` documents the pre-gate recipe, and the wait rule that already
  exists (`guidelines/Build.md` §4/§6, `guidelines/Design.md` §11 and §40.31: never pace a unit test off
  a timer, never compute a deadline from the wall clock) gains the reference to its build check.

## Capabilities

### New Capabilities

None. Both changes are behaviours of capabilities the project already owns.

### Modified Capabilities

- `build-test-gate`: adds an iteration pre-gate requirement — for a change in flight, the declared
  cases run first, the selection is derived from the change's own artifacts and diff, and the pre-gate
  is a local iteration form that never counts as gate evidence or as the completion gate.
- `unit-test-suite-runtime`: the existing time-control requirement gains enforcement — wall-clock waits
  and system-clock deadline loops in test sources are refused by a build check, and every declared
  exception carries its reason and justification; the load-independence requirement gains the same
  guard, since a wait is what makes a result depend on host load.

## Impact

**Test sources (measured violators, 22 files with `Thread.sleep`, ~40 sites)**

- `app/src/test/java/com/naviveylin/ui/map/`: `MapCanvasViewModelBrowseReCenterTest` (`:144`, `:358`),
  `MapCanvasViewModelViewportRestoreTest` (`awaitHeldBlock` `:495`, `:111`, `:260`, `:352`–`:374`,
  `:500`), `MapCanvasViewModelFollowModeTest` (7 × 250 ms), `MapCanvasViewModelFixQualityTest`
  (three deadline loops `:142`–`:169`), `MapCanvasViewModelVehicleAnchorTest` (`awaitFollowThrottle`
  `:581`, deadline loop `:606`), `MapCanvasViewModelAutoZoomCommitTest` (`:152`),
  `MapCanvasViewModelSingleFollowCenterTest` (`:119`), `MapCanvasViewModelModeTest` (`:180`–`:274`),
  `MapCanvasViewModelInitThreadingTest` (deadline loop `:114`), `MapCanvasViewModelNavEndRestoreTest`
  (`:109`), `MapCanvasViewModelSpeedWidgetTest` (`:243`)
- `app/src/test/java/com/naviveylin/ui/mapmanager/BasemapViewModelTest` (`await` `:52`),
  `app/src/test/java/com/naviveylin/NaviVeylinAppStartupLoggingTest` (`:39`),
  `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient` (`:272`, simulated native latency)
- `auto/src/test/java/com/naviveylin/`: `AutoMapRendererTest` (`:1713`), `MapScreenTest` (`:168`),
  `AutoInitialViewportTest` (`:94`), `TemplateFaultIsolationTest` (`:264`, `:280`),
  `StartupScreensTest` (`:121`), `DiagnosticsScreenTest` (`:67`)
- `core/src/test/java/com/naviveylin/`: `DiagnosticsLogTest` (`:91`), `DiagnosticsLogWritePathTest` (`:63`)

**Production seams (existing, uncommitted; extended — not introduced here)**

- `core/src/main/java/com/naviveylin/core/EngineTimeSource.kt`, `EngineDispatchers.kt` — the seam as it
  exists in this working tree; this change consumes it and extends it to the map-view-model windows
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the recenter dwell window
  (`RECENTER_DWELL_MS`, `:3481`), the fix-quality tick, the follow throttle, the viewport-restore path
- `app/src/main/java/com/naviveylin/location/`, `app/src/main/java/com/naviveylin/ui/mapmanager/`,
  `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` — only where a measured violator needs a
  window the seam does not yet cover (`DiagnosticsLogTest:91` sleeps inside `DiagnosticsLog.time { }`,
  so the elapsed measurement reads the same seam)

**Coordination (in-flight work in the same tree):** `EngineTimeSource`/`EngineDispatchers` are untracked
files of a concurrent in-flight change, and 57 test files of this tree are already modified. This change
must not edit a file owned by an in-flight change without coordinating first (`AGENTS.md`: one builder
per working tree), and its design states how it lands relative to them.

**Build and tooling**

- `buildSrc/src/main/kotlin/` — a new test-source scanner plus its unit tests
  (`./gradlew -p buildSrc test`), following the `CoordinateLogScanner` precedent
- `app/build.gradle.kts`, `auto/build.gradle.kts`, `core/build.gradle.kts` — `preBuild` wiring for that
  check, next to `checkNoCoordinatesInLogs` / `checkHardcodedStrings`
- `tools/` — the pre-gate selection tool, with a self-test that needs no device
  (`tools/measure-highlight.py` is the precedent)
- `guidelines/Build.md` §2/§4/§6/§7 (pre-gate recipe, evidence rules, fork budget note),
  `guidelines/Design.md` §4 (injected time source), `AGENTS.md` iteration-loop rule 2
- `.pi/skills/run-tests` — local wrapper only; every rule a fresh clone needs also lands in
  `guidelines/Build.md` and `AGENTS.md`

**Specifications at risk but not modified:** `unit-test-suite-runtime` (the fork-budget and
parallelism requirements stay as they are; the change must not require more forks to get green),
`kover-aggregate-report` and `test-coverage` (a shorter iteration pass must not feed a report),
`ci-unit-test-jni` (CI keeps the unchanged full recipe).

**Native / JNI:** no submodule patch and no bridge-module override. No C++ source, JNI signature, CMake
target or host-stub change.

**Additive or breaking:** additive to code and app behaviour (the injected time source defaults to the
real clock); one procedural **BREAKING** change, the iteration recipe. **Rollback:** revert the
commits — the scanner wiring is one line per module, the pre-gate is documentation plus a tool with no
runtime artifact, and the injected seam keeps its real-clock default, so no runtime or data state is
involved.

## Decisions to confirm before design

**1. Guard strictness (large consequence).** **Confirmed 2026-10-06: Option 2 — refuse outright, with
no allowlist; every wait site is converted.** The two candidate exemptions were both convertible
(`FakeOSMScoutClient.renderWithRouteAndPoisDelayMs` has one user, `RenderModeSwitchTest:145`, and
`DiagnosticsLogTest:91` sleeps inside `DiagnosticsLog.time { }`, which is that measurement's own
subject), so no exemption has a subject left to describe.

- *Option 1 (rejected):* refuse in test sources, allow declared exceptions in a single allowlist file
  whose entries each carry a reason and the measurement that keeps them. Rejected because each entry is
  the place the next load-dependent flake enters, and the rule then needs a reviewer to audit the file
  instead of reading one check's output.
- *Option 2 (chosen):* refuse `Thread.sleep` in test sources outright and convert every site — the fake's
  simulated latency becomes a test-controlled gate (deterministic, and it can hold the render exactly at
  the decision point instead of for 400 ms) and the timing helper reads the injected seam.

**2. Pre-gate reach (large consequence).** A backtest over 8 archived changes shows a
symbol/import-derived selection misses 14 of the 51 test classes those changes' own artifacts named
(27 %) — mostly other-module (`:auto` screen tests), sibling-package (`NavigationEngineTest` for a
`ui.route` diff), composable-level screen tests, and a test file the change itself adds.

- *Option 1 (assumed here):* the pre-gate selects only what the change declares and what the diff
  touches, and is bounded by the completion rule — the unfiltered suite is still owed before the change
  is called complete, exactly as `build-test-gate` already requires.
- *Option 2:* extend the selection with a mined co-change map and an always-run cross-cutting set,
  which closes most of the 27 % but adds a map, a miner and a completeness guard whose own maintenance
  cost is unbounded.

The design settles both before any build script or production seam is touched.
