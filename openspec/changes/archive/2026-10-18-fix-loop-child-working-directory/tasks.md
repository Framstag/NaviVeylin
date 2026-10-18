# fix-loop-child-working-directory — tasks

## 1. The red cases (before the fix)

- [x] 1.1 Three cases in `tools/loop-cases.sh`: `run#childStartsInTheProjectRoot` (every child's recorded
      `cwd:` is the project root; both rendered prompts name the worktree as their change root),
      `run#childWriteOutsideTheWorktreeIsBlocked` (a worker that also writes in the project's tree blocks
      the item with "outside the worktree", and nothing is closed), `run#reviewerWriteInTheProjectIsDiscarded`
      (a reviewer that writes there discards the round).
- [x] 1.2 The stub child's action runs in `$LOOP_WORKTREE` and exports `LOOP_CHILD_ROOT`
      (`tools/loop/fixtures/pi`), so a case can write in either tree on purpose.
- [x] 1.3 Red on HEAD, before `run.sh` changed — verified:
      `bash tools/loop-selftest.sh --case run#childStartsInTheProjectRoot` → `FAIL … expected '', got
      'cwd: …/.pi/worktrees/loop'`; `… --case run#childWriteOutsideTheWorktreeIsBlocked` → `FAIL … expected
      'blocked', got 'done'`. Log: `evidence/red-on-head-two-new-cases.log`.
      The third case's red is the mutation that removes the digest clause (`12.4`, step 3.3 below).

## 2. The fix

- [x] 2.1 `run.sh`'s `run_child()` starts the child in `$LOOP_ROOT` and appends `LOOP-OUTSIDE-WRITE` to the
      child's report when the project's own tracked state changed while it ran; `project_state()` is the one
      reader of that state.
- [x] 2.2 The item loop reads the field: the worker's report blocks the item, and a round-2 worker's report
      blocks it too (named for what it is, not as a second failed review).
- [x] 2.3 The reviewer's read-only digest covers the project's tree as well as the worktree's.
- [x] 2.4 `prompts/worker.md` and `prompts/reviewer.md` name the change root as the directory to work in,
      and say the project's own tree is not theirs to write.

## 3. The mutations (one per new check)

- [x] 3.1 `9.9` re-pointed at the new `run_child()` line (its pattern had to follow the line it mutates).
- [x] 3.2 `12.2` `cd "$LOOP_ROOT"` → `cd "$worktree"` must turn `run#childStartsInTheProjectRoot` red.
- [x] 3.3 `12.3` the outside-write marker removed must turn `run#childWriteOutsideTheWorktreeIsBlocked` red;
      `12.4` the digest's `project_state` clause removed must turn
      `run#reviewerWriteInTheProjectIsDiscarded` red.

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` → 49 cases passed (JDK 27, offline): `evidence/loop-selftest-49-cases.log`.
- [x] 4.2 `bash tools/loop-mutations.sh` → `red-when-mutated: 15 · not-noticed: 0 · restore-failures: 0`:
      `evidence/loop-mutations-15-red.log`.
- [x] 4.3 `bash tools/check-portability-selftest.sh` → 21 cases passed, which is the suite that runs the loop
      against a foreign project and would catch a fix that only works in this tree:
      `evidence/portability-selftest-21-cases.log`.
- [x] 4.4 Re-run with the two later fixes that landed in the same engine (`53` cases, `19` mutations) — the
      suite, the mutation set and the portability suite are green with all three fixes in the tree:
      `evidence/loop-selftest-final.log`, `evidence/loop-mutations-final.log`,
      `evidence/portability-selftest-final.log`.

## 5. The records

- [x] 5.1 Issue #169 filed with the run's own output and the worker's report as evidence.
- [x] 5.2 This change: proposal, design, the delta above, tasks, and the four gate logs + the boundary
      measurement in `evidence/`.
- [x] 5.3 `.pi/skills/fix-loop/REFERENCE.md`: step 5 of the worktree recipe states where the children are
      started and where the run's state lives (the previous wording named the worktree for both).

## 6. Not done here, and named

- [ ] 6.1 The project's policy still answers `ask` for the `skill` tool on `build-app`, `run-tests` and
      `revert-check` and for `lean_ctx`; what a non-interactive child does with that refusal is not measured
      by this change (`design.md`, "Open question"). The owner's decision.
- [ ] 6.2 No item has been closed by a real run since the fix: the next `tools/loop/run.sh` is the end-to-end
      proof, and the worker's report is where a remaining policy refusal would show.
