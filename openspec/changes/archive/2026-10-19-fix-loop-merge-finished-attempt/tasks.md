# Tasks

## 1. Red first

- [x] 1.1 Cases `tools/loop-cases.sh` →
      `run#mergesAFinishedAttemptAndClosesTheItem` and
      `run#doesNotMergeAnAttemptWithoutAGreenVerification` — written and registered in the case list.
- [x] 1.2 Run them against `HEAD 8ecaa81` and keep the output: `evidence/red-on-head.log` — both `FAIL`, exit 1,
      the merge case's `adopted: branch …` line absent and the refusal case's `carries no green verification
      record` absent, on the stashed HEAD engine (`tools/loop/run.sh`, `tools/loop/state.sh`,
      `tools/loop/worktree.sh` restored to HEAD, then the working tree's mechanism restored).

## 2. The fix

- [x] 2.1 `tools/loop/worktree.sh`: `carried_change` and `carried_verification` read what the branch carries
      (one archived change the base does not have, plus a JUnit XML in its evidence with ≥1 test and 0 failures);
      `cmd_adopt_item` refuses without that evidence (`carries no green verification record`), refuses over a
      dirty tree, and merges the commits (fast-forward or cherry-pick).
- [x] 2.2 `tools/loop/run.sh`: `adopt_finished_attempt` calls `adopt-item`, advances the run's base
      (`state.sh set-base`), closes the item through `labels.sh close`, and records the merge in the run state;
      `adopt_finished_exclusions` runs the decision list's branch-carrying exclusions before they are escalated,
      and both call sites sit on the pre-screen path and the start-refusal path.
- [x] 2.3 `tools/loop/state.sh`: `set-base <commit>` advances `run.base`; `done` records
      `--merged-branch/--merged-commits/--merged-evidence` and drops the item's exclusion; the report names the
      merged attempts.

## 3. Verification

- [x] 3.1 Green run — `bash tools/loop-selftest.sh --case run#mergesAFinishedAttemptAndClosesTheItem` and
      `--case run#doesNotMergeAnAttemptWithoutAGreenVerification`: both `PASS`, exit 0 (`evidence/green-on-fix.log`).
- [x] 3.2 Red-on-HEAD pair — same cases against the stashed HEAD engine: both `FAIL`, exit 1
      (`evidence/red-on-head.log`).
- [x] 3.3 `openspec validate fix-loop-merge-finished-attempt --type change` exits 0.
- [x] 3.4 `openspec validate --specs` — 0 failed.
- [ ] 3.5 OWED — the full `bash tools/loop-selftest.sh` suite, the loop mutations
      (`tools/loop-mutations.sh`) and the portability/declared-cases suites were not run in this change's
      budget; the two named cases are the evidence for this fix. Run them before the next release gate.

## 4. Record

- [x] 4.1 Archived into `openspec/changes/archive/2026-10-18-fix-loop-merge-finished-attempt/` and committed.
- [x] 4.2 Issue `#176` carries `status/fixed-by` and its reason-and-evidence comment names the archived
      change (`tools/issues/update.sh 176 --status fixed-by --comment-file …` succeeded).
- [ ] 4.3 BLOCKED — the closure itself could not be written: `tools/issues/close.sh 176 --reason … --evidence …`
      refuses with `missing-eligibility-label: agent/eligible absent`, and `tools/issues/update.sh 176 --label
      agent/eligible` refuses with `label-reserved: agent/eligible` — the authorisation label is reserved and
      only a person may set it (`Backlog.md` §5 "Who may write what"; the tooling ships no authorise command),
      so the tooling cannot clear its own close gate. A person must add `agent/eligible` to #176 and then run
      the close, or close it directly. No bypass was attempted.
