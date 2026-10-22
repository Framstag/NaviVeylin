# Tasks — fix-calculation-notice-minimum-dwell

## 1. The red case

- [x] 1.1 `auto/src/test/java/com/naviveylin/auto/CalculationNoticeDwellTest.kt` (new): three cases —
      `aRouteArrivingInsideTheDwellKeepsTheNoticeUp` (a notice seen for 443 ms owes 257 ms; a notice just
      pushed owes 700 ms), `theDriversOwnAnswerIsNeverDelayedByTheDwell` (0 ms for a cancellation or a
      failure) and `aNoticeThatOutlastedItsDwellIsReplacedAtOnce` (1 ms at 699 ms, 0 ms at 700 ms and
      beyond, never negative). The rule is reached by reflection, because on HEAD the seam does not exist
      and a direct call would fail the *compile* — a run with no result to compare; a signature change
      makes these cases fail loudly instead of silently.
      Verify: on HEAD (`e1c9b74`) `./gradlew :auto:testDebugUnitTest --tests
      "com.naviveylin.auto.CalculationNoticeDwellTest" -PforceTests --no-build-cache` reports
      `3 tests completed, 3 failed` (`evidence/red-HEAD-focused.log`), each failing with
      `the calculation notice's removal consults no minimum visible dwell …`; the XML read
      `tests="3" failures="3"` at `2026-10-22T10:59:03.025Z` before the first green run overwrote it.
- [x] 1.2 Keep the red artifact reproducible after the fix: withhold the seam's name
      (`calculationNoticeDwellRemainingMsWithheld`) in the one production file, run the same filter, copy
      the XML, restore.
      Verify: `evidence/red-seam-absent.xml`, `tests="3" failures="3"`, `2026-10-22T11:00:16.405Z`,
      restored to `md5 b803e5e1fb7a23dfa5117c7b11edc2e3`.

## 2. The seam

- [x] 2.1 `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`: `CALCULATION_NOTICE_MIN_DWELL_MS =
      700L` (with the 2026-10-05 measurement in its KDoc) and
      `calculationNoticeDwellRemainingMs(shownForMs, replacedByNavigation)` — the rest of the dwell, zero
      once it has run out, and zero when the replacement is not the navigation view.
      Verify: `evidence/green-CalculationNoticeDwellTest.xml`, `tests="3" failures="0"`,
      `2026-10-22T11:00:25.208Z`; the three cases assert 257/700/0/0 rather than "longer than before".

## 3. The wiring

- [x] 3.1 `NavigationSession`: record when the notice landed (`calculationNoticeShownAtMs`, monotonic),
      replace the immediate removal in `applyCalculationNotice()` with `scheduleCalculationNoticeRemoval()`
      (one cancellable `calculation-notice-dwell` job, removing by identity), cancel that job when a live
      calculation takes the notice over, clear the record when the notice leaves, and treat a landed root
      pop as the owner of the notice's departure.
      Verify: `:auto` compiles inside every focused run above; the notice's decisions and its bookkeeping
      are green beside it (`evidence/green-CalculationNoticeDecisionTest.xml` `tests="8" failures="0"`,
      `evidence/green-SessionScreenStackTest.xml` `tests="16" failures="0"`) and the gate compiles the
      module for `:app`'s consumers.

## 4. Guideline

- [x] 4.1 `guidelines/UI.md` §3c: a "Minimum visible dwell" bullet before "Never outlives its work" — the
      constant, the 443 ms measurement it answers, the decision seam, the cancellable job, and that a
      cancellation and a failure are never delayed.
      Verify: `bash tools/check-doc-routes.sh` → `all 120 section references resolve, all 5 skill
      references resolve`, exit 0 (`evidence/structural-checks.txt`).

## 5. Spec

- [x] 5.1 `openspec/changes/fix-calculation-notice-minimum-dwell/specs/route-calculation-feedback/spec.md`:
      one ADDED requirement ("A notice the navigation view replaces stays visible for a minimum dwell",
      484 characters) with three scenarios, each carrying its own `**Case**:` line. The shipped requirement
      "The car notice never outlives its calculation" and its five scenarios are not modified: the
      navigation view still replaces the notice.
      Verify: `openspec validate fix-calculation-notice-minimum-dwell --strict` → `Change
      'fix-calculation-notice-minimum-dwell' is valid`, and `openspec validate --specs` → `Totals: 177
      passed, 0 failed` (`evidence/structural-checks.txt`).

## 6. Verification

- [x] 6.1 Falsification (revert-check): `CALCULATION_NOTICE_MIN_DWELL_MS = 0L` — the pre-fix behaviour —
      must turn the band case red for the right reason; restore, forced green.
      Verify: `evidence/mutation-dwell-zero.xml`, `tests="3" failures="2"`, `2026-10-22T11:00:21.781Z`,
      `expected:<257> but was:<0>` and `expected:<1> but was:<0>`; the cancellation case passes under the
      mutation by design (a zero dwell also answers "no wait"); the restored file's md5 is
      `b803e5e1fb7a23dfa5117c7b11edc2e3` and the forced green is `evidence/green-*.xml` above.
- [x] 6.2 Structural checks, quoted in `evidence/structural-checks.txt`:
      `openspec validate fix-calculation-notice-minimum-dwell --strict` (valid),
      `openspec validate --specs` (`Totals: 177 passed, 0 failed`, the one `zoom-transition-scaling`
      long-text warning is pre-existing), `bash tools/check-doc-routes.sh` (120 references resolve),
      `bash tools/gen-feature-list.sh --check-classification` (exit 0, `stale: 0`).
- [x] 6.3 The documented gate, once: `./tools/loop/gate.sh`, 2026-10-22T13:00:59+02:00 → 13:16:37+02:00, exit 0 —
      `LOOP-GATE: tests=481 failures=0 (./gradlew test -PforceTests --no-build-cache,
      tools/loop-selftest.sh, tools/loop-mutations-selftest.sh, tools/issue-tooling-selftest.sh,
      tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh,
      tools/gen-feature-list-selftest.sh, bash tools/gen-feature-list.sh --check-classification,
      tools/check-doc-routes.sh)`. The engine's count covers the shell suites; the compiled half of the same
      run is read from its own XMLs (`tests=5772 failures=0` over `testMobileDebugUnitTest` 2101,
      `testAutomotiveDebugUnitTest` 2101, `:auto` 880, `:core` 642, `:osmscout-client-java` 48).
      `evidence/gate.log`, `evidence/gate-tally.txt`.
- [x] 6.4 `./tools/loop/measure.sh --change fix-calculation-notice-minimum-dwell --base e1c9b74`:
      `LOOP-MEASURE-CHANGE fix-calculation-notice-minimum-dwell scenarios=3 cases=3 unnamed=0` and
      `LOOP-MEASURE-SURFACE surface: 3 cases added, 0 rewritten, 0 fixtures realigned …`
      (`evidence/measure-block.txt`).
