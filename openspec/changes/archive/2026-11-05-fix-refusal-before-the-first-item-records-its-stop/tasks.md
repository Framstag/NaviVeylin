# Tasks — fix-refusal-before-the-first-item-records-its-stop

No compiled file is touched: the change edits two shell files and one spec delta, so the build task this list
would otherwise carry is replaced by the structural checks named below and no Gradle task is run "for
completeness".

## 1. The red case

- [x] Add `run#theDecisionCapRecordsItsStopReason` to `tools/loop-cases.sh` and register it twice — the case list
      (`run#…` entries) and the dispatch (`case_run_…`).
- [x] Verify, on the tree before the fix: `bash tools/loop-selftest.sh --case
      run#theDecisionCapRecordsItsStopReason` is **red** at its first assertion, `run.stop.reason` empty, and the
      JUnit tally is copied into the item's evidence directory as it is produced.

## 2. The three refusals record their reason

- [x] `tools/loop/run.sh:758` (decision cap): build the candidate list once, print it, and give `stop_run` a reason
      carrying both counts and that list; keep `report.sh` and `exit "$LOOP_REFUSE"` after it.
- [x] `tools/loop/run.sh:721` (kept worktree a previous run left dirty): `stop_run` naming the refusal and the item
      the branch marker names, before the report it already prints.
- [x] `tools/loop/run.sh:692` (`--reset-worktree` that cannot clean the tree): `stop_run` in place of `loop_refuse`,
      then the report and the exit.
- [x] Verify: `bash tools/loop-selftest.sh --case run#theDecisionCapRecordsItsStopReason` is green, and the tally is
      copied into the evidence directory at the moment it is produced.

## 3. The two neighbouring refusals keep their record

- [x] Add the one assertion to `run#dirtyKeptWorktreeIsARecordedRefusal` and to
      `run#resetWorktreeNamesTheDirtItCannotDiscard` that each refusal is `run.stop.reason`.
- [x] Verify: `bash tools/loop-selftest.sh --case 'run#dirtyKeptWorktree*'` and `--case
      run#resetWorktreeNamesTheDirtItCannotDiscard` are green; `bash tools/loop-selftest.sh --case
      'run#decisionCap*'` is green.

## 4. The record is what the report prints

- [x] Verify the shipped behaviour of the case from task 1 end to end: after the run's process is gone, `report.sh` prints
      `stop: <reason>` and neither `recorded no stop` nor `it was killed`.
- [x] Verify the mutation harness still applies to this file: `bash tools/loop-mutations.sh --check --files
      tools/loop/run.sh` — record `9.6` anchors on the cap's comparison line, which this change keeps verbatim.

## 5. Before archive (the change owns `tools/loop/**`)

- [x] `bash tools/loop-selftest.sh` — the whole set, not a file-scoped selection. Taken by the gate, which ran it
      as `tools/loop-selftest.sh --from-git b114e2d04b9f643949358ac4a5db606666dd4d1d` and reported it the line the
      gate prints: `tests="319" failures="0" errors="0"` in
      `evidence/tools_loop-selftest.sh_--from-git_b114e2d04b9f643949358ac4a5db606666dd4d1d.xml`, `all 319 cases
      passed` at line 1161 of `evidence/gate.log`. The gate is `evidence/gate-summary.txt`, run once.
- [x] `bash tools/loop-mutations.sh --files tools/loop/run.sh` — the records that name this file go red and their
      restore goes green. `red-when-mutated: 92 · not-noticed: 0 · restore-failures: 0`, `2026-11-05T22:21Z`
      (`evidence/revert-check-run.sh.txt`); the new record `9.12` is the one that carries this change's guard — the
      cap's refusal removed, `run#theDecisionCapRecordsItsStopReason` red at `run.stop.reason`, restored green.
- [x] `bash tools/check-change-readiness.sh openspec/changes/fix-refusal-before-the-first-item-records-its-stop`
      prints `LOOP-CHANGE-READY: ok`, and the surface line in `proposal.md` still names the count this change
      touched. `LOOP-CHANGE-READY: ok` (`evidence/checks.txt`, re-taken on the final tree 2026-11-05T22:42Z).
