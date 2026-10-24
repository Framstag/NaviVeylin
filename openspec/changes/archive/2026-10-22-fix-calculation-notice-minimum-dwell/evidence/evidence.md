# Evidence — fix-calculation-notice-minimum-dwell

Every artifact below was copied out of the build directory at the moment it was produced, and every
timestamp is the XML's own.

| artifact | what it shows |
|---|---|
| `red-HEAD-focused.log` | the red run **on the tree the change starts from** (HEAD `e1c9b74`): `> Task :auto:testDebugUnitTest FAILED`, `3 tests completed, 3 failed`, each of `aRouteArrivingInsideTheDwellKeepsTheNoticeUp`, `theDriversOwnAnswerIsNeverDelayedByTheDwell`, `aNoticeThatOutlastedItsDwellIsReplacedAtOnce` failing with `the calculation notice's removal consults no minimum visible dwell …`. That run's XML was overwritten by the first green run; the XML's header read `tests="3" failures="3"` at `2026-10-22T10:59:03.025Z`. |
| `red-seam-absent.xml` / `.log` | the same three failures, reproduced on the **fixed** tree with the seam's name withheld (`calculationNoticeDwellRemainingMsWithheld`), so the red artifact survives the fix: `tests="3" failures="3"`, `2026-10-22T11:00:16.405Z`. |
| `mutation-dwell-zero.xml` / `.log` | the falsification of the rule itself: with `CALCULATION_NOTICE_MIN_DWELL_MS = 0L` (the pre-fix behaviour) the band case fails `expected:<257> but was:<0>` and the boundary case `expected:<1> but was:<0>` — `tests="3" failures="2"`, `2026-10-22T11:00:21.781Z`. The third case is the cancellation case, which a zero dwell also answers by design. |
| `green-restored-focused.log`, `green-CalculationNoticeDwellTest.xml`, `green-CalculationNoticeDecisionTest.xml`, `green-SessionScreenStackTest.xml` | the forced green after the byte-identical restore (md5 `b803e5e1fb7a23dfa5117c7b11edc2e3`): `3/0` (`2026-10-22T11:00:25.208Z`), `8/0` and `16/0`. |
| `green-focused.log` | the first green run of the new cases beside the existing decision cases (10:59:21Z). |
| `green-citations.xml`, `green-citations.log` | the one case re-run after three citation-only edits (`issue #133` → `§133, issue #84` in the new test's KDoc, the seam's KDoc and the `UI.md` bullet): `tests="3" failures="0"`, `2026-10-22T11:17:44.394Z`. No compiled statement changed; the gate run below is the item's one gate run. |
| `structural-checks.txt`, `post-archive.txt` | the structural checks before and after the archive (strict validation, `--specs` 177/0, 120 doc-route references resolve, classification exit 0), and that the delta merged into `openspec/specs/route-calculation-feedback/spec.md`. |
| `measure-block.txt` | the change's own `LOOP-MEASURE` block: `scenarios=3 cases=3 unnamed=0`, the `surface:` line, and the checks the pathspec owes. |
| `gate.log`, `gate-tally.txt`, `gate-*.xml` | the one gate run, 2026-10-22T13:00:59+02:00 → 13:16:37+02:00, exit 0: `LOOP-GATE: tests=481 failures=0 (…)` for the shell suites, and the compiled half of the same run read from its own XMLs — `tests=5772 failures=0` over `testMobileDebugUnitTest` 2101, `testAutomotiveDebugUnitTest` 2101, `:auto` 880, `:core` 642, `:osmscout-client-java` 48. The engine's per-suite tally for the compiled command is `tests=0` because Gradle prints no `all N cases passed` line; that is why the XML aggregation is carried beside it. |
