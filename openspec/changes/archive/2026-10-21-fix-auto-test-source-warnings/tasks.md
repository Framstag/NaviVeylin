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

- [x] 3.1 `AutoTestSourceWarningBaselineTest` green: focused run `BUILD SUCCESSFUL in 2s` (23:19Z), and the
      versioned copy is the gate's own `:app:testMobileDebugUnitTest` XML — `tests=3 failures=0`
      (`evidence/TEST-AutoTestSourceWarningBaselineTest-focused-green.xml`, identical to the mobile gate
      copy).
- [x] 3.2 `guidelines/Build.md` §4, the "Warnings" bullet: one sentence naming the guard. This delta touches
      no compiled file, so its verification is `tools/check-doc-routes.sh` and the change's own gate run —
      not a separate build.
- [x] 3.3 Falsification (`revert-check`): restore each of the three sites one at a time and confirm the
      owning case goes red, then restore the fix — `evidence/revert-check.txt`.

## 4. The gate

- [x] 4.1 `./gradlew test -PforceTests --no-build-cache` once → `tests=5707 failures=0`
      (`evidence/gate-tally.txt`, per-module XMLs copied into `evidence/`).
