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

## 6. The gate, once (round 1)

- [x] 6.1 `./tools/loop/gate.sh` — run once from the change root after the round-1 fix, quoted, never retried:
  `LOOP-GATE: tests=676 failures=0 errors=0` (23 suites, 1373 s; `evidence/gate-round-1.txt` and
  `evidence/gate-round-1/`, plus its sum in `evidence/gate-round-1-summary.xml`), and the block it printed is
  `evidence/measure.txt`. Round 2 changes compiled sources and tests again, so its own gate supersedes this one —
  task 7.5.

## 7. Round 2 — the warmup re-push the first draft removed

Round 1's review blocked the change twice: the spec scenario's third `AND` was exercised by no case, and the
removal also removed the DB-ready re-push (a car screen can exist before the warmup opened the databases). Both
are closed here without restoring the second load per switch.

- [x] 7.1 `MapScreenTest`: new case `theFirstFlagPushWaitsForTheDatabasesAndLandsOnce` — a failing style load
  (the databases are closed) pushes no `daylight` flag, and the first load that succeeds pushes it exactly once.
  Verify: the case goes red on a tree with the `databasesReady` gate removed —
  `evidence/revert-check-mutated.xml`, `tests="7" failures="1"`, the failure being the case's own assertion
  (`no flag is pushed while the native side would drop it silently, got [(daylight, true)] expected:<0> but
  was:<1>` at `MapScreenTest.kt:274`), and green with the gate restored.
- [x] 7.2 `MapScreen.kt` / `NavigationScreen.kt`: `databasesReady`, the gate in `pushDark()`, and the one
  `pushDark()` call on the style apply's success branch.
  Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.MapScreenTest"
  --tests "com.naviveylin.auto.CarDaylightApplierTest" -PforceTests --no-build-cache` green —
  `evidence/green-after-round-2-map-screen.xml` (`MapScreenTest` `tests="7" failures="0"`) and
  `evidence/green-after-round-2-car-daylight-applier.xml` (`CarDaylightApplierTest` `tests="5" failures="0"`).
- [x] 7.3 The spec scenario's unexercised `AND` ("the map keeps the day/night variant that was in effect before
  the switch") is dropped: the native side keeps it, an app-side case cannot observe it, and what it asserted
  app-side is the second `AND`'s count already. The delta becomes a `MODIFIED` block, because the requirement
  already shipped with round 1's archive; the new scenario names case 7.1.
  Verify: `openspec validate fix-car-style-switch-costs-one-load --strict` -> valid.
- [x] 7.4 `guidelines/MapRendering.md` §15: the warmup bullet states the deferred first push and its proof
  (`DBInstance.cpp:35-37`), drops the wrong "the pre-ready push never reaches the native side" claim, and names
  issue #705 for the free-driving screen that has no proof.
  Verify: `bash tools/check-doc-routes.sh` and `bash tools/check-todo-references.sh` in the gate.
- [x] 7.5 The gate, once, over the round-2 tree: `./tools/loop/gate.sh` -> exit 0,
  `LOOP-GATE: tests=6 failures=0 errors=0` (8 steps; the tallied tool time sums to 7 s beside the Gradle step's
  397 s). The Gradle half — which the gate cannot count, because Gradle prints no `all N cases passed` line —
  sums to tests=6070 failures=0 errors=0 from its own JUnit XML, and the step printed
  `BUILD SUCCESSFUL in 6m 36s`. Evidence: `evidence/gate.txt` (the record, with that sum), `evidence/gate/` (the
  eight tallies) and `evidence/gate-summary.xml` (their sum: tests=6, time=404). Never retried.

## 8. Evidence and archive

- [x] 8.1 `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-car-style-switch-costs-one-load
  --root .` -> PASS (before the move: `C1` 12 §references, `C2` 11 pairs confirmed 0 drift, `C3` none, `C4` no
  mutation marker, `C5` 361 resolved and in range; after it the same five lines, C1/C2 report-only).
- [x] 8.2 Archived as `openspec/changes/archive/2026-10-29-fix-car-style-switch-costs-one-load/`; the delta's
  `MODIFIED` requirement replaced `openspec/specs/map-styles/spec.md:225-247` (both scenarios and their `Case`
  lines), verified with `bash tools/check-openspec-hygiene.sh` (exit 0).
