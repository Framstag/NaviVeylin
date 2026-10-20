# Tasks

## 1. Guard first (red)

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/AutoTestSourceWarningBaselineTest.kt` with the three
      cases, each with its positive control. Verification: run
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.AutoTestSourceWarningBaselineTest"
      -PforceTests --no-build-cache` on HEAD → `tests=3 failures=3` (2026-10-20T23:17:57.607Z,
      `evidence/TEST-AutoTestSourceWarningBaselineTest-red.xml`), findings `RendererTestRule.kt: T :
      AutoMapRenderer`, `RenderLoopSupervisorTest.kt: runIteration(loop) { error("native render failed") } ==
      null`, `TestCarContext.kt: as Array<Any?>`.
- [x] 1.2 Record the compiler's own measurement of HEAD:
      `./gradlew :auto:compileDebugUnitTestKotlin --offline --rerun-tasks` → three non-opt-in `w:` lines
      (`evidence/head-compile-warnings.log`).

## 2. Name the type at the three sites (green)

- [x] 2.1 `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`: `track` takes and returns
      `AutoMapRenderer`. Verification: the case of 1.1 for this file passes, and `RendererTestRuleTest` still
      passes (it spies a renderer and overrides `shutdown`).
- [x] 2.2 `auto/src/test/java/com/naviveylin/auto/RenderLoopSupervisorTest.kt`: `boom` calls
      `runIteration<Unit>(…)`, documented in its KDoc. Verification: this file's cases pass
      (`aFaultSkipsTheIterationAndTheLoopKeepsRunning`, `theFaultRecordCarriesIdentityOnlyAndIsThrottled`, …).
- [x] 2.3 `auto/src/test/java/com/naviveylin/auto/TestCarContext.kt`: the unchecked cast becomes a
      star-projection `as?` cast copied into `Array<Any?>`. Verification: `:auto`'s string-resolving screens
      pass — the strings they resolve are unchanged.
- [x] 2.4 Verification of 2.1–2.3 together:
      `./gradlew :auto:testDebugUnitTest -PforceTests --no-build-cache` → **866 tests, 0 failures, 0 errors**;
      re-compile of the `:auto` test sources reports **0** non-opt-in, non-deprecation warnings
      (`evidence/fixed-compile-warnings.log`).

## 3. Guard green and the record

- [x] 3.1 `AutoTestSourceWarningBaselineTest` green in the gate's own `:app:testMobileDebugUnitTest` run —
      `tests=3 failures=0` (`evidence/gate-mobile-TEST-AutoTestSourceWarningBaselineTest.xml`, the copy also
      carried as `TEST-AutoTestSourceWarningBaselineTest-focused-green.xml`); the focused green run is 3.3's
      `evidence/revert-check-restored-green.xml` (`tests=3 failures=0`, 23:28:48.245Z).
- [x] 3.2 `guidelines/Build.md` §4, the "Warnings" bullet: one sentence naming the guard. This delta touches
      no compiled file, so its verification is `tools/check-doc-routes.sh` — `evidence/check-doc-routes.log`,
      all 89 section references resolve — and not a separate build.
- [x] 3.3 Falsification (`revert-check`): each of the three sites restored to its HEAD form **one at a time**,
      the guard run after each — the owning case red naming its own file, the other two green — then the fix
      restored and the guard green: `evidence/revert-check.txt` with the four runs' XMLs
      (`revert-check-bound-site1.xml` `23:27:28.744Z`, `revert-check-throwing-site2.xml` `23:28:37.462Z`,
      `revert-check-cast-site3.xml` `23:28:43.812Z`, `revert-check-restored-green.xml` `23:28:48.245Z`).

## 4. The gate

- [x] 4.1 `./gradlew test -PforceTests --no-build-cache` once → `tests=5706 failures=0`
      (`evidence/gate-tally.txt`: 2075 + 2075 + 866 + 642 + 48; `evidence/gate-per-module.txt` records the
      recount from the run's XML trees, four of which still reproduce their line).
