# fix-merge-committed-branch-before-closing

## Why

Issue #229, verified in the tree on base `2e732f0e00fa771d04569483ffe80bc2ca3fa974` (2026-10-21).

**Observed.** A run closed an item it had worked and reviewed, and the item's fix never reached the base.
Measured 2026-10-20 on `#201`: the run committed two commits (`4faeef2`, `3211d4d`) on
`fix/issue-201-create-sh-requires-and-validates-the-fin`, the reviewer passed it in one round, and the run
reported `item #201: closed (…) · 1 review round(s)`; `main` stayed at the run's base `db1fe8e` and a person
fast-forwarded it by hand 35 minutes later.

**Root cause, one sentence.** The success path commits the item's work on the item's own branch
(`tools/loop/run.sh:723`, `worktree.sh end-item`) and then closes the item (`tools/loop/run.sh:941-943`,
`labels.sh close`) without ever merging that branch, and the loop's only merge — `worktree.sh adopt-item`
(`tools/loop/worktree.sh:230-274`) — is reachable solely from the pre-screen (`run.sh:212`) and from a later
`start-item` refusal (`run.sh:653`), both of which need the item to be visited again; a closed item is never
picked, so its branch is stranded by construction.

**Red test on HEAD** — case `run#mergesItsCommittedBranchBeforeClosing` (`tools/loop-cases.sh`), the loop's
own gate for `tools/loop/**`:

```
$ bash tools/loop-selftest.sh --case 'run#mergesItsCommittedBranchBeforeClosing'
  the item was closed with its fix on fix/issue-21-an-eligible-bug-whose-cited-path-still-e, which the base does not have
FAIL run#mergesItsCommittedBranchBeforeClosing
exit=1                                            (2026-10-21T06:51:30Z)
```

The run's own gate is a shell suite, so the tally is one case run and one case failed (`tests=1 failures=1`)
rather than a JUnit XML tally; the transcript is `evidence/red-on-HEAD.txt`.

**Owning requirement.** `agent-issue-loop` — "Version control belongs to the run's script, not to the agent"
(the branch and the working tree are the run's) and "Closure follows a passing review and carries evidence"
(`status/fixed-by` must mean the fix is in the tree). Owning guideline section: `guidelines/Backlog.md` §7
"The loop" (with §4's invariants I8 and I10).

**Contradiction, stated as a WHEN/THEN.** WHEN a run's item passed its review with an archived change and a
green verification, THEN its commits are on the base before the item is closed — false on HEAD (nothing
merges them).

## What Changes

The run merges the branch it committed, on the base it recorded, before it closes the item:

- `worktree.sh merge-item <issue> <branch>` — one recorded action that fast-forwards the base onto the item's
  branch, refusing when the project tree is not clean or when the branch is not a descendant of the recorded
  base (a replay onto an unreviewed tree is `adopt-item`'s decision, not this one). It prints one line,
  `merged: branch <name> -> <sha> · commits <a,b>`.
- `run.sh` — a `closed` outcome merges its own branch through that action before the closure: the base the run
  recorded advances to the merge (`state.sh set-base`), the branch and its commits are recorded in the item's
  state (`merged.branch`, `merged.commits`) and named in the closure's evidence comment. A refused merge turns
  the item into `blocked` with the refusal as its reason — the item stays open with its branch escalated,
  which is what `tools/issues/close.sh`'s "a refused write leaves the item open" already promises.

Additive to the loop's contract, breaking to nothing: the only behaviour that changes is that a closed item's
commits are now on the base. Rollback: revert the change (`fix-*` commits) — the branches a run created are
untouched by the fix, so a revert returns the tooling to today's behaviour, with the merge gone and nothing
else affected.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: adds one requirement — the item is closed on a base that carries its fix — with two
  scenarios (`run#mergesItsCommittedBranchBeforeClosing`, `worktree#refusesToMergeOverADirtyProjectTree`);
  review round 1 added the record/evidence assertions and the two cases
  `run#doesNotCloseOnAMergeTheBaseCannotTake` and `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom`.

## Impact

- `tools/loop/worktree.sh` — one command (`merge-item`) and its dispatch entry.
- `tools/loop/run.sh` — one helper (`merge_committed_branch`) and its call on the `closed` outcome.
- `tools/loop-cases.sh` — four cases: the red case, the command-level refusal, the run-level refusal at closure
  and the non-descendant arm.
- `guidelines/Backlog.md` §7 — what the run does with the branch it committed.
- `openspec/specs/agent-issue-loop/spec.md` — merged at archive.
- No compiled file, no Android component, no native boundary: the change is shell tooling plus its suite.
