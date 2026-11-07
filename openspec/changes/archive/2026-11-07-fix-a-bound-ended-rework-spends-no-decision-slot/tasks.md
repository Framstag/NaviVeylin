# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "A review round is a record on the branch, from its announcement to its answer", "A blocked attempt's
branch is kept under a name of the run's own, and its item is worked" and "A block records the mechanism that stopped
it" — scenarios "The rework of a refusal the bound ended is recorded beside the round", "The rework-cut branch is
queued as work and set aside", "A rework worker the run's bound ended spends no decision slot".

- [x] 1.1 Extend `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`: the item's worker commits, the reviewer
      refuses round 1, the rework worker outlasts the child clock, and the case asserts `result=cut`,
      `stopReason=bound`, no close — and now the branch: `review-round-1-rework-cut.md` beside the round's
      announcement, and `branch-state` reading `rework-cut`.
      Verify: on the run's base `9b3f9d5b` the case fails at that first branch assertion,
      `expected '1', got '0'` (`evidence/red-on-HEAD.log`, `2026-11-07T13:10:04Z`); the case's first line gains
      `tools/loop/report.sh`, which the extended assertions call
      (`driver#everyCaseDeclaresTheFilesItCanReach`).
- [x] 1.2 Add `prescreen#aReworkCutBranchIsWork`: a branch carrying an archived change, a green verification, round
      1 announced and refused, and `review-round-1-rework-cut.md` is read as `rework-cut`, queued as `eligible` with
      the branch as evidence, recorded in no exclusion list, and written with no `agent/needs-decision` label.
      Verify: on the base the case fails at the reading,
      `expected 'rework-cut', got 'review-refused'` (`evidence/red-on-HEAD.log`, `2026-11-07T13:10:12Z`).
- [x] 1.3 Add `worktree#setsAsideAReworkCutBranchAndStartsFresh`: `start-item` on such a branch succeeds, says
      `set-aside:`, starts the item at the run's base, and keeps the attempt under `loop/leftover/issue-21-*`.
      Verify: on the base it fails with the start refusing the branch (exit 5),
      `expected '0', got '5'` (`evidence/red-on-HEAD.log`, `2026-11-07T13:10:14Z`).

## 2. The engine

- [x] 2.1 `tools/loop/worktree.sh`, `cmd_record_review_round`: accept `rework-cut` and write
      `evidence/review-round-<n>-rework-cut.md` beside the announcement and the refusal (neither is withdrawn).
      Verify: `bash -n tools/loop/worktree.sh`; assertion on the file in 1.1.
- [x] 2.2 `tools/loop/worktree.sh`: `carried_review_state` reads that file as `<round> rework-cut` ahead of the
      refusal of the same round; `branch_state_of` reports `rework-cut`; `cmd_start_item` sets it aside with its own
      line; `cmd_adopt_item` refuses it.
      Verify: `bash -n tools/loop/worktree.sh`; assertions in 1.2 and 1.3.
- [x] 2.3 `tools/loop/prescreen.sh`: `unfinished|rework-cut` share the arm, the reason naming which of the two the
      branch carries, and a stated question of the item's own still outranking the branch.
      Verify: `bash -n tools/loop/prescreen.sh`; `prescreen#aReworkCutBranchIsWork` plus the unchanged
      `prescreen#itemAwaitingADecisionIsNotQueued`.
- [x] 2.4 `tools/loop/run.sh`: `record_rework_cut` beside `record_cut_round`, called by the rework-cut arm before it
      breaks.
      Verify: `bash -n tools/loop/run.sh`; assertions in 1.1.
- [x] 2.5 Re-anchor the two mutation records the shifted lines moved (`12.7` in `worktree.sh`, `33.2` in
      `prescreen.sh`), because a stale record is a record that no longer applies rather than a case that fails.
      Verify: `bash tools/loop-mutations.sh --check` — 272 of 272.
- [x] 2.6 Add the mutation record the new branch write owes: `94.1` removes `record_rework_cut "$round"`
      (`tools/loop/run.sh:2688`) under `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`; `80.2` quotes the
      reviewer's cut (`"$1" cut >&2`) and cannot reach a line that ends `"$1" rework-cut`.
      Verify: `bash tools/loop-mutations.sh --only 94.1` — `RED 94.1`, `restore 94.1 … green again`,
      `restore-failures: 0` (`evidence/mutation-94.1.log`).

## 3. The rule's own words

- [x] 3.1 `guidelines/Backlog.recipes.md`, section 7e names the rework worker's cut beside the reviewer's, and states the
      two branch records and the two `branch-state` readings they give.
      Verify: `bash tools/look.sh 'rework-cut' --path guidelines/Backlog.recipes.md` names the paragraph.

## 4. Verification

- [x] 4.1 Structural check of every shell file the diff touches: `bash -n tools/loop/run.sh`,
      `bash -n tools/loop/worktree.sh`, `bash -n tools/loop/prescreen.sh`, `bash -n tools/loop-cases.sh`,
      `bash -n tools/loop-mutations.sh` — all clean.
- [x] 4.2 The three cases green on the change — `evidence/green-on-change.log` / `.xml`
      (`tests=3 failures=0 errors=0 time=14`, `2026-11-07T13:09:45Z`–`13:09:59Z`).
- [x] 4.3 The rule's surface still passes — `evidence/focused-cases.log` / `.xml`
      (`tests=93 failures=0 errors=0`, `2026-11-07T13:17:37Z`–`13:19:42Z`): the `prescreen` group (31 cases), the
      `worktree` group (25), the `state` group (26), `run#*Cut*` (6), `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`,
      `run#reviewRoundsCapAtTwo`, `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`,
      `run#childPastItsContextBoundIsCut` (the third `run#*Cut*` case the record carries — this list named
      `run#treeOfAChildTheCapEndedIsNotCommitted`, which the record does not carry),
      `run#announcesAndRecordsTheReviewRound`,
      `run#recordsAFailedReviewOnTheBranch`, `driver#everyCaseDeclaresTheFilesItCanReach`.
- [x] 4.4 `bash tools/loop-mutations.sh --check` — 272 of 272 apply, and `tools/loop-mutations-selftest.sh` — 34 of
      34 pass, which the gate owes for a `tools/loop-mutations.sh` delta (`tools/loop/loop.conf`,
      `LOOP_GATE_SUITES`) and this change does not run. `tools/loop/measure.sh --change fix-a-bound-ended-rework-spends-no-decision-slot`
      — 15 scenarios, 15 cases, none unnamed (`evidence/measure.txt`; its `paths=`/`LOOP-MEASURE-PATH` block is the
      whole tree since `base=a3cefa93…`, the base the profile records, not this change's six files).
- [x] 4.5 The gate (`./tools/loop/gate.sh`) is not this child's: the run deferred it to a fresh child after the diff
      review, and that child ran it once and passed — its transcript is `evidence/gate.log`, whose last line is the
      run's own gate record.
