# Tasks — fix-a-tool-result-over-its-bound-is-trimmed

No compiled file is touched: the delta is one new extension script, one adapter, one recorded key, five cases, six
mutation records and one guideline paragraph. The build gate is therefore replaced by the structural checks of the
files edited (`tools/loop-selftest.sh`, `tools/loop-mutations.sh --check`, `tools/check-portability.sh`,
`tools/check-doc-routes.sh`) and by the one gate the run's configuration names.

- [x] 1. Write the red cases and run them on the base commit.
  Verification: `bash tools/loop-selftest.sh --group trim` → `loop-selftest: 3 of 3 cases failed`;
  `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: 2 of 3 cases failed`. Logs and tallies:
  `evidence/red-on-head-trim.log`/`.xml` (`tests="3" failures="3"`), `evidence/red-on-head-adapter.log`/`.xml`
  (`tests="3" failures="2"`), 2026-10-26T23:58:46Z.

- [x] 2. Measure the distribution the bound is placed against, instead of asserting a number.
  Verification: `python3 evidence/result-sizes.py` → `LOOP-RESULT-SIZES at=2026-10-27T00:17:42Z n=22901
  chars=59179014`, `p50=825`, `p90=7194`, `bound=8000 above=2052 share=9.0%` (`evidence/result-sizes.log`);
  `python3 tools/loop-spend.py` → `LOOP-SPEND-CAP8K saved=228988001` of `total=1663853998`
  (`evidence/loop-spend.log`).

- [x] 3. Land the rule (`tools/pi-trim-result.mjs`): the cut, the notice and the record, as a hook and as a CLI.
  Verification: `bash tools/loop-selftest.sh --group trim` → `loop-selftest: all 3 cases passed`; and the hook the
  child actually goes through, driven through the factory with a stand-in for pi's API:
  `node evidence/hook-probe.mjs` → `prefix=unchanged first 8000`, `tail=cut`, `notice=named`,
  `record=LOOP-TOOL-RESULT-TRIM: ctx_read chars=20005 kept=8000`, `undersized=untouched`, `bound-0=no bound`
  (`evidence/hook-probe.log`).

- [x] 4. Load it with every child and record the trim beside the child's event stream
  (`tools/pi-child-events.sh`), and record the bound (`tools/loop/loop.conf`).
  Verification: `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: all 3 cases passed`;
  `bash tools/loop-selftest.sh --case run#childStartsInTheProjectRoot` → `PASS`, the seam the tool policy is
  resolved on, unchanged.

- [x] 5. One mutation record per invariant (`tools/loop-mutations.sh`, records 67.1–67.4 for the cut, the notice, the
  record and the adapter's `-e`; round 2 adds 67.5 and 67.6).
  Verification: `bash tools/loop-mutations.sh --only "67.1 67.2 67.3 67.4 67.5 67.6"` → `RED` six times, `restore …
  green again` six times, `red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0` (`evidence/mutations.md`); and
  the gate's own cheap check, `bash tools/loop-mutations.sh --check` → `all 188 cases passed`.

- [x] 6. State the rule where it is owned (`guidelines/Backlog.md`, section 7 "The loop").
  Verification, structural and offline: `bash tools/check-doc-routes.sh` → every section reference resolves;
  `bash tools/check-portability.sh` → `OK`.

- [x] 7. Run the project's gate once, from the change root, and copy its tallies into `evidence/`.
  Verification, on the corrected tree of round 2: `./tools/loop/gate.sh` → one aggregate `LOOP-GATE:` line over 29
  steps — 703 cases, 0 failures, 0 errors, the loop suite the longest at 416 s — and its own tally
  `tools_loop-selftest.sh_--from-git_<base>.xml` reads `tests="247" failures="0" errors="0" time="416"`
  (`evidence/gate.log`, `evidence/gate-tallies/`); the block beside it reports the same run as
  `LOOP-MEASURE-GATE seconds=840 suites=29` (`evidence/measure-block.log`). Round 1's run of the same gate on the
  uncorrected tree reads 701 cases at 411 s, and that log is not what this change cites. The three structural
  checks and the evidence check were re-run after this round's last prose edit — `tools/check-doc-routes.sh` → all
  154 section references resolve, `tools/check-openspec-hygiene.sh` → consistent, `tools/check-portability.sh` → OK,
  `tools/loop/evidence-check.sh … --root .` → PASS — so no cited prose is older than the last run of the check that
  reads it.

- [x] 8. Archive the change and check its citations.
  Verification: `bash tools/loop/evidence-check.sh
  openspec/changes/archive/2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed --root .` → PASS, retained as
  `evidence/evidence-check.log` (round 1 quoted the PASS without one). The path quoted in
  round 1 was the pre-archive one and no longer resolved, which is why round 2 re-ran it against the archive, where
  the change lives; the archive directory exists under `openspec/changes/archive/` and
  `openspec/specs/agent-issue-loop/spec.md` carries the new requirement — the delta and the merged requirement are
  byte-identical from `### Requirement:` to the last `**Case**:` line.

- [x] 9. Align every quoted number with the evidence log, and re-verify what that touches.
  The archive grows while a run is in flight — this item's own session is written into it — so the probes were run
  once more at the end and the numbers quoted in the plan, the guideline and the two scripts are that log's
  numbers, not an earlier run's. Round 1 left three of them unsourced, and round 2 corrects them against the logs
  the change already carries (task 10). The affected suites were re-run scoped: `bash tools/loop-selftest.sh --group
  trim|adapter` → all green (3 and 3 cases), `bash tools/loop-mutations.sh --check` → `all 188 cases passed`,
  `tools/check-openspec-hygiene.sh`, `tools/check-doc-routes.sh`, `tools/check-portability.sh` → OK, and
  `bash tools/loop/evidence-check.sh <this archived change> --root .` → `PASS` (`evidence/evidence-check.log`).

- [x] 10. Round 2 — the review's corrections: the undefended clause, its record, and the numbers it could not check.
  The spec's "The trim is recorded for the item" names the record landing "beside the child's own event stream", and
  no case reached the adapter's export of that path — `grep -rn "TRIM_RECORD\|tool-result-trims" tools/` returned
  `tools/pi-child-events.sh` and `tools/pi-trim-result.mjs` alone, and deleting the export left every case green.
  `adapter#theChildIsStartedWithTheResultBound` now runs the adapter with `LOOP_CHILD_EVENTS` set and asserts that
  `LOOP_TOOL_RESULT_TRIM_RECORD` is `<its dirname>/tool-result-trims.log`, and that a child started by hand names no
  record at all; record 67.5 removes that export and turns the case red. Record 67.6 moves the pass-through's `<=`
  boundary to `<`, and `trim#aResultUnderTheBoundIsHandedOverWhole` drives a result of exactly 8 000 characters, so
  the scenario's own word "at most" is exercised rather than a result comfortably under it.
  Three quoted numbers are corrected against logs the change already carries: `LOOP-SPEND-TURNS n=20648` (was
  `20 540`), `p99=16149` with `bound=16000 above=281 share=1.2%` (16 000 was called "past p99"), and
  `LOOP-SPEND-READS calls=4798 duplicate=2174` in place of a before/after split around #246 that no recorded
  command produces. The purpose clause nothing enforces ("no claim is ever judged on a cut line") is gone from the
  requirement, the guideline and the scripts, and the THEN says "no character of the result past the bound", which
  is what the case asserts against the notice the same sentence requires.
  Verification: `bash tools/loop-selftest.sh --group trim` → `all 3 cases passed`, `--group adapter` → `all 3 cases
  passed`; `bash tools/loop-mutations.sh --only "67.1 67.2 67.3 67.4 67.5 67.6"` → `red-when-mutated: 6 ·
  not-noticed: 0 · restore-failures: 0` (`evidence/mutations.log`); `bash tools/loop-mutations.sh --check` → `all 188
  cases passed`; the gate re-run once on the corrected tree (task 7). The evidence artefacts the review could not
  resolve are repaired with the same pass: `evidence/hook-probe.mjs` imports the extension from the archived depth
  (its `../../../../` resolved to `openspec/tools/` and threw) and drives `at-the-bound` with exactly 8 000
  characters instead of ten (`evidence/hook-probe.log`), and the guideline paragraph and `loop.conf` name the
  archived change beside `evidence/result-sizes.py` rather than a path that resolves from nowhere. One assertion
  message was reworded after the gate (`trim#aResultAboveTheBoundIsTrimmedAndNamed` still asserts `sed -n`, and the
  message now says a reader can re-read the range instead of a purpose nothing enforces); the scoped re-run is
  `bash tools/loop-selftest.sh --group trim` → `all 3 cases passed`, `--case
  adapter#theChildIsStartedWithTheResultBound` → `PASS`, `bash tools/loop-mutations.sh --check` → `all 188 cases
  passed`.

