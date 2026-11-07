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
- The same case's detached-HEAD arm, added in round 2 (`tools/loop-cases.sh:4435`–`:4452`): `detached-arm.log`, PASS on
  the corrected tree, `2026-10-24T20:36Z` — with HEAD detached, both the merge and the pre-screen's adoption are
  refused, `a detached HEAD` and the base branch are named, and neither the base nor the item's commit moved.

## Falsified

`revert-check-57.1-57.2.log` (round 1) and `revert-check-57.1-57.2-round2.log` (round 2, after the detached-HEAD arm
was added: `bash tools/loop-mutations.sh --only '57.1 57.2'`, `HEAD 0b89f079`, `2026-10-24T20:37Z`):
`selected 2 of 145 records`, `RED 57.1` / `restore 57.1 … green again`, `RED 57.2` / `restore 57.2 … green again`,
`red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`. Mutation `57.1` replaces the checkout comparison
with `if false; then`, `57.2` replaces the ancestry test with `true ||`; each names the case above as the one that
must notice it. `57.1` guards both arms of the case — the tree switched to another branch and the detached HEAD —
because both are the one comparison it mutates.

## The gate

**Round 1** (`gate.log`, `db888dd8b7c509cfba82854a21427ae5c9c8e192`): red — its line reports one failing case of
397, and that case was **not** a behaviour: `driver#everyCaseDeclaresTheFilesItCanReach` refused the two new cases'
`# files:` declarations, which named the engine paths but not the two the `new_project` helper copies
(`tools/pi-child-events.sh`, `tools/tooling.json`). The declarations were corrected and the same case re-run on the
corrected tree — `gate-failure-recheck.log`, PASS, exit 0.

**Round 2** (`gate-round2.log`, `HEAD 0b89f079`, started `2026-10-24T20:37Z`, ended `20:56Z`): the reviewer refused
the round-1 record because no full-gate execution had the corrected tree as its subject, so the gate was run once
on that tree — which also carries the detached-HEAD arm added in round 2. Its final line (quoted from
`gate-round2.log`) reports **403 cases over ten suites, zero failures, zero errors**, the loop suite alone taking
`974s` of the run; `gate.log` keeps the round-1 line beside it.
The tallies beside it carry the same numbers, one pair each: `tools/loop-selftest.sh --from-git db888dd8…`
`tests="218" failures="0"`, `bash tools/loop-mutations.sh --check` `tests="145" failures="0"`,
`tools/loop-mutations-selftest.sh` `tests="34" failures="0"`, `tools/check-openspec-hygiene-selftest.sh`
`tests="6" failures="0"`, and six structural checks with no case of their own (`tests="0" failures="0"`).
218 + 145 + 34 + 6 = 403.

