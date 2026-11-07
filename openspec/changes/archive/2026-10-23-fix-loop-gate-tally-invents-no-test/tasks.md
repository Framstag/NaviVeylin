# Tasks — fix-loop-gate-tally-invents-no-test

- [x] 1. Red test on HEAD: `gate#aFailingSuiteWithNoSummaryInventsNoTest` in `tools/loop-cases.sh`, registered in
  `LOOP_SHELL_CASES` and in the name-to-function map.
  Verification: `bash tools/loop-selftest.sh --case 'gate#aFailingSuiteWithNoSummaryInventsNoTest'` → exit `1`,
  `FAIL`, with the gate's line reporting a failure for a suite that reported nothing; transcript
  `evidence/red-HEAD.log`, tally `evidence/red-HEAD.xml` (`tests="1" failures="1"`, 2026-10-23T09:38:16Z).
- [x] 2. `tools/loop/gate.sh`: remove the `f=1` and `n=1` substitutions, count a suite that reported no summary as
  an error, write it to the tally's `errors` attribute and to the gate's own line.
  Verification: task 1's case passes (2026-10-23T09:39Z) and the fixed gate against the same probe suite writes
  `evidence/fixed-tally.xml` (`tests="0" failures="0" errors="1"`), its own line reading the same three facts;
  `bash tools/loop-selftest.sh --case 'gate#aFailingSuiteIsReadFromTheSameStream'` still passes, so a reported
  count is unchanged.
- [x] 3. `tools/loop-cases.sh`: `gate#aFailingSuiteIsReadFromTheSameStream` asserts the tally of a suite that
  reported its numbers carries no error.
  Verification: that case passes; the four other gate cases (`theDeltaSelectsTheSuitesItTouches`,
  `anEnginePathOwesTheRecordCheck`, `theSuitesOutputReachesTheConsoleWhileItRuns`) pass unchanged.
- [x] 4. `tools/loop-mutations.sh`: record `47.1` restores the fabrication, naming task 1's case.
  Verification: `bash tools/loop-mutations.sh --check --files tools/loop/gate.sh` exits `0` (`selected 2 of 99
  records`), and `bash tools/loop-mutations.sh --only 47.1` reports `RED 47.1 …` with the mutation and
  `restore 47.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` —
  `evidence/revert-check-47.1.log`.
- [x] 5. `tools/loop/prompts/worker.md`: the `LOOP-GATE` field a worker reports carries `errors=<n>`.
  Verification: `grep -n 'LOOP-GATE' tools/loop/prompts/worker.md` names the new shape.
- [x] 6. `guidelines/Backlog.md`, section "7. The loop": the streamed-gate paragraph says a suite that reported
  no summary contributes an error rather than a case, with this item's measurement.
  Verification: the paragraph carries the rule and the 2026-10-23 measurement, and
  `tools/check-doc-routes.sh` (run by the gate) still resolves all 152 section references.
- [x] 7. The capability delta validates.
  Verification: `openspec validate fix-loop-gate-tally-invents-no-test --strict` → `Change
  'fix-loop-gate-tally-invents-no-test' is valid`, exit `0`.
- [x] 8. The project's gate, run once (this change touches `tools/loop/**`, so it owes the shell suites; the
  evidence XMLs pull the compiled half in as well through the profile's `*.xml` pattern).
  Verification: `./tools/loop/gate.sh` (2026-10-23T09:40:00Z) exits `0`; its line reports 311 cases, zero failures
  and zero errors over `./gradlew test -PforceTests --no-build-cache`, `tools/loop-selftest.sh`, `bash
  tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`, `bash tools/gen-feature-list.sh
  --check-classification` and `tools/check-doc-routes.sh`; the console is `evidence/gate.log` and the per-suite
  tallies are `evidence/gate-tallies/`.
- [x] 9. Evidence and archive: every cited XML under `openspec/changes/fix-loop-gate-tally-invents-no-test/evidence/`,
  pointers checked by `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-gate-tally-invents-no-test --root .`,
  the change archived and the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
