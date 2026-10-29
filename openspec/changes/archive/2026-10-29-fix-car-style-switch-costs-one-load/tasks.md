# Tasks — fix-car-style-switch-costs-one-load

Each task names its own verification. The change touches no compiled native file, so no native build task: the
structural check of the Kotlin sources stands in where a build would sit (task 5).

## 1. The red case on HEAD

- [x] 1.1 Add `aStyleSwitchLoadsTheStylesheetSetOnce` to
  `auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt`: build the browse car map with the test dispatcher
  injected as `backgroundDispatcher`, await the startup (one `daylight` push, the persisted style loaded once),
  switch the shared style through the periodic settings re-read, and assert one load per style and no flag push
  after the switch.
  Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.MapScreenTest" -PforceTests
  --no-build-cache` is red on the base tree — `tests="6" failures="1"`, kept as `evidence/red-on-head.xml`
  (2026-10-29T13:32:20.231Z), failure message
  `the style load carries the daylight flag — the switch adds no flag application, got
  [(daylight, true), (daylight, true)] expected:<1> but was:<2>`.

## 2. The style apply carries no flag push

- [x] 2.1 `MapScreen.kt`: drop `pushedForStyleSheet`, `styleChanged` and `pushDark(force = …)`; the settings
  re-read applies the style and nothing else.
  Verify: the task 1.1 case passes for the browse map.
- [x] 2.2 `NavigationScreen.kt`: the same four lines.
  Verify: `grep -rn "force = styleChanged" auto/src/main/java` prints nothing (structural check of the identical
  edit in the second screen, whose `CarContext` harness has no case for a settings re-read).

## 3. The force parameter goes with its last caller

- [x] 3.1 `CarDaylightApplier.kt`: `apply(dark)`, `needsPush(dark)`; KDoc records that a load carries the flags.
- [x] 3.2 `RendererGate.kt`: `DaylightPush(dark, token)`, `requestDaylightPush(dark)`.
- [x] 3.3 `FreeDrivingScreen.kt`, `MapScreen.kt`, `NavigationScreen.kt`: `pushDark()`, `requestDaylightPush(dark)`,
  `daylightApplier.apply(request.dark)`.
  Verify: `./gradlew :auto:testDebugUnitTest -PforceTests --no-build-cache` passes for the `:auto` module —
  `evidence/green-after.xml` (`MapScreenTest` `tests="6" failures="0"`, 2026-10-29T13:33:31.598Z),
  `evidence/green-after-car-daylight-applier.xml` (`tests="5" failures="0"`),
  `evidence/green-after-renderer-gate.xml` (`tests="22" failures="0"`).
- [x] 3.4 Tests: `forceRepushesEvenAfterSuccess` removed with its subject; `needsPushReportsWhetherAPushWouldHappen`
  and the gate's daylight-push case rewritten without the force assertions.
  Verify: the three cases pass in the same `:auto` run.

## 4. The guideline that carried the assumption

- [x] 4.1 `guidelines/MapRendering.md` §15: replace "may apply the flag once more after a style load it
  performed … `TODO.md` §120 is the native check that would let that re-application go" with the native fact and
  its `file:line`; correct the warmup bullet to the mechanism that actually re-pushes the flag (the readiness
  push, skipped — not dropped — while no renderer exists).
  Verify: `bash tools/check-todo-references.sh` (the §120 citation stays resolving) and
  `bash tools/check-doc-routes.sh`.

## 5. Structural checks (in place of a build)

- [x] 5.1 No compiled native file changes: `git status --short` names no `*.cpp`/`*.h`/`CMakeLists.txt`.
- [x] 5.2 `auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt` keeps no wall-clock wait:
  `./gradlew :auto:checkNoWallClockWaits` passes (the case drives the injected test dispatcher).

## 6. The gate, once

- [x] 6.1 `./tools/loop/gate.sh` — run once from the change root after the fix, quoted, never retried:
  `LOOP-GATE: tests=676 failures=0 errors=0` (23 suites, 1373 s; `evidence/gate.txt`, and the block it printed is
  `evidence/measure.txt`).

## 7. Evidence and archive

- [x] 7.1 `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-car-style-switch-costs-one-load --root .`
  -> PASS (`C1` 10 §references, `C2` 6 test tallies confirmed, `C3` none, `C4` no mutation marker, `C5` 289
  `file:line` resolved, 0 unresolved).
- [x] 7.2 Archived as `openspec/changes/archive/2026-10-29-fix-car-style-switch-costs-one-load/`; the delta is
  merged into `openspec/specs/map-styles/spec.md:225-239` (requirement "A style switch loads the stylesheet set
  once" with its scenario and `Case` line), verified with `bash tools/check-openspec-hygiene.sh` (exit 0).
