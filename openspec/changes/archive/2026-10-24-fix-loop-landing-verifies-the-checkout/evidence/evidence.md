# Evidence — fix-loop-landing-verifies-the-checkout (issue #302)

## Red on the base

`red-on-head.xml` (`tests="2" failures="2"`) and its transcript `red-on-head.log`: both new cases were run against
the engine at `db888dd8b7c509cfba82854a21427ae5c9c8e192` on 2026-10-24T20:12Z, with `tools/loop/worktree.sh` and
`tools/loop/state.sh` untouched.

```
  a project tree off the base branch refuses the merge: expected '5', got '0'
FAIL worktree#refusesToMergeIntoATreeOffTheBaseBranch
  a commit the base branch does not carry is refused: expected '5', got '0'
FAIL state#setBaseRefusesACommitTheBaseBranchDoesNotCarry
```

## Green after the fix

- `worktree#refusesToMergeIntoATreeOffTheBaseBranch` — PASS (exit 0).
- `state#setBaseRefusesACommitTheBaseBranchDoesNotCarry` — PASS (exit 0).
- The three cases beside them, unchanged: `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom`,
  `worktree#refusesToMergeOverADirtyProjectTree`, `state#recordsTheBaseBranchNotTheCheckout` — PASS.

## Falsified

`revert-check-57.1-57.2.log`: `bash tools/loop-mutations.sh --only '57.1 57.2'` → `selected 2 of 145 records`,
`RED 57.1` / `restore 57.1 … green again`, `RED 57.2` / `restore 57.2 … green again`,
`red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`. Mutation `57.1` replaces the checkout comparison
with `if false; then`, `57.2` replaces the ancestry test with `true ||`; each names the case above as the one that
must notice it.

## The gate

`gate.log` and the seven tallies beside it, from the delta's one gate run,
`./tools/loop/gate.sh` (base `db888dd8b7c509cfba82854a21427ae5c9c8e192`). Its own line, as printed, reads `397`
tests over seven suites with `1` failure and `0` errors, failing the loop suite; the tallies carry the same
numbers, one pair each: `tools/loop-selftest.sh --from-git db888dd8…` `tests="218" failures="1"`,
`bash tools/loop-mutations.sh --check` `tests="145" failures="0"`, `tools/loop-mutations-selftest.sh`
`tests="34" failures="0"`, and four structural checks with no case of their own.

The one failure is **not** a behaviour: `driver#everyCaseDeclaresTheFilesItCanReach` refused the two new cases'
`# files:` declarations, which named the engine paths but not the two the `new_project` helper copies
(`tools/pi-child-events.sh`, `tools/tooling.json`). The declarations were corrected and the same case re-run on the
corrected tree — `gate-failure-recheck.log`, PASS, exit 0 — the gate itself was not re-run: a second attempt at
one gate is not the worker's to make. The gate is red in this record and the item is reported `blocked` with that
decision, not closed on it.

## Off-base tally, for the same reason

The other 396 of the gate's 397 tests passed in that run, including both cases this change adds and every case of
the `state`, `worktree`, `run` and `driver` groups.
