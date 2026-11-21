# Tasks

## 1. The reading reaches the reason

- [x] 1.1 `tools/loop/run.sh`: `item_outside_write_clause` answers the qualification from `worker_write_note`, the gate
      child's or the tail child's reading, and nothing when the item's record carries none. — done: the case below
      passes focused.
- [x] 1.2 `tools/loop/run.sh`: the three arms that read a tree at the run's base (`empty_tree_reading`, the
      `uncommitted` block and the run's own "could not start" stop) append it to the sentence each already writes. —
      done: `run#anEmptyTreeWithAnOutsideWriteNamesThePaths` asserts the sentence and the clause together.

## 2. The case, and the half that keeps its sentence

- [x] 2.1 `tools/loop/tests/loop-cases.sh`: `run#anEmptyTreeWithAnOutsideWriteNamesThePaths` stages a child that reports
      success, commits nothing and writes `$LOOP_CHILD_ROOT/demo-owned.txt`, and asserts the block names the caller's
      tree and the recorded path. — done: red on HEAD
      (`evidence/red-on-HEAD-983.log`), green on the change (`evidence/focused-cases.log`).
- [x] 2.2 `tools/loop/tests/loop-cases.sh`: `run#childClaimWithoutCommitIsBlocked` asserts the negative — a record
      with no reading carries no claim about the caller's tree. — done: green, same log.

## 3. The mutation

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: record `983.1` drops `$(item_outside_write_clause)` from the arms that
      quote `nothing was committed`, so the case must fail on the arm's own sentence. — done: red → restore → green
      (`evidence/mutation-983.1.log`).

## 4. The gate

- [x] 4.1 `tools/loop/tests/loop-selftest.sh --files tools/loop/run.sh`: 260 of 260 cases green on the change
      (`evidence/gate-cases-run-sh.log`, 487.85 s at `--jobs 12`). The full set was not run: the tree carries #938's
      unfinished work (`tools/loop/prescreen.sh` and its `epic-stalled` fixture, uncommitted), so the whole-set run
      would judge that change's cases and not this one's delta.
- [x] 4.2 `tools/loop/tests/loop-mutations.sh --check --files …` over the three changed files: 159 of 433 records
      apply to the files they name.
- [x] 4.3 `openspec validate fix-an-empty-tree-names-the-outside-write --strict`: valid.
- [x] 4.4 `tools/loop/tests/loop-selftest.sh --jobs 14` over the **full** set, in a detached worktree at the commit
      (no other item's uncommitted work in it): 508 of 508 cases green (`evidence/full-suite-55589fc1.log`, 554.58 s),
      and `tools/loop/tests/loop-mutations.sh --check` over every record: 432 of 432 apply
      (`evidence/mutation-check-full-55589fc1.log`).

## 5. The review round

- [x] 5.1 An independent read-only reviewer read the diff and the arms around it:
      `evidence/review-round-1-report.md` — PASS with findings, 3 should-fix and 4 nit.
- [x] 5.2 Findings 1–3 applied (`evidence/review-round-1-answered.md`): the clause is conditional and places the
      difference on no child, the delta states that and stays within the body bound, and
      `guidelines/Backlog.recipes.md` states the clause and drops its stale "written twice" claim. Nits 4–7 are
      recorded as deferred, with the reason each one is a second change.
- [x] 5.3 The gate re-run over the **reviewed** tree (commit `15991246`), in a detached worktree: 508 of 508 cases
      green (`evidence/full-suite-15991246.log`, 575.41 s) and 432 of 432 mutation records apply
      (`evidence/mutation-check-full-15991246.log`); `openspec validate --strict` valid.
