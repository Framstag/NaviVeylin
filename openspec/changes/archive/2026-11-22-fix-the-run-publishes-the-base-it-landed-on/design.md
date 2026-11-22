# Design — the base a landing went into is published with the branch

## Root cause

`tools/loop/run.sh:2801-2805` is the run's whole publishing step: with `--push yes` it pushes
`fix/issue-$issue-$slug` and nothing else. The item's branch is already merged into the project tree's base and
released by then (`tools/loop/worktree.sh:772`, `git merge --ff-only`), so the one ref the flag publishes is the
one ref whose work is already in a base that no push carries. The remote therefore describes the tree as of the
last human push: measured 2026-11-22, `## main...origin/main [ahead 29]`, and every CI-driven sensor reads that
remote commit rather than this tree (issue #1018).

## The single fix

The base is published by the same function, from the same block:

```sh
  if [ "$push" = yes ]; then
    push_branch "fix/issue-$issue-$slug"
    push_branch "$(loop_base_ref)"
  fi
```

- `push_branch` (`tools/loop/leftovers.sh:414`) already refuses to hand git a refspec whose local ref is gone and
  already reports a failed push on one line (`publish-failed`, `tools/loop/events.tsv:104`) with no retry, which is
  the shape the decision records for the base too. The second caller changes nothing about it: the base always
  exists in a run that landed, so its `publish-nothing` arm cannot fire from here.
- The push runs from `$worktree`, a linked worktree of the project tree (`git worktree add`,
  `tools/loop/worktree.sh:154`), so `refs/heads/<base>` and `origin` are the project tree's own.
- The base ref is `loop_base_ref` (`tools/loop/lib.sh`), the name the rest of the engine already reads — not the
  recorded `run.base` commit, which is what the run started from.

The publishing failure and skip lines, the events table and `shared.tsv` are untouched: no new function, no new
event.

## Blast radius

Three files of the engine, and the two places that assert the old shape:

- `tools/loop/run.sh` — the second call site, with the comment that says why the base travels with the branch.
- `tools/loop/leftovers.sh` — the paragraph above `push_branch`, which said "the only push" without naming the base;
  it now states that both refs leave through this one function.
- `tools/loop/tests/loop-cases.sh` — the new case `worktree#publishesTheBaseItLandedOn` (`:4503`), and
  `worktree#noPushWithoutTheFlag`'s one-call-site assertion (`:4461`), which now counts two call sites and still
  asserts that the call site is guarded by the flag (`:4463`).
- `openspec/specs/agent-issue-loop/spec.md:2248` — the requirement restated, both scenarios repeated verbatim and
  one added.

`worktree#noPushWithoutTheFlag` is the arm that must keep passing, and does: with `--push no` the block is not
entered, so git is handed no push at all (its `GIT_TRACE` count stays 0, run 2026-11-22T15:09Z,
`evidence/no-push-case.log`). `worktree#pushSkipsABranchItAlreadyMerged` keeps passing for the item's branch
(`evidence/push-skips-case.log`).

## Alternatives the evidence eliminated

The item's decision records the fork and answers it, so the alternatives here are the ones that answer eliminated:

- **Name the drift instead of publishing it** (report the local landings, the count against the remote and the
  commit the last CI run measured): rejected because the remote stays stale while #1011's reader reads CI, not a
  report — the sensor's input is the remote commit.
- **Name the drift and owe a publish command**: rejected because it names the repair without making it.

A run that lands something red now publishes that red base. That is the cost the decision took, and it is smaller
than a remote nobody can trust: the drift made `tools/sensors/guard.sh`'s fifth guard refuse and made #1011's report
a fact about a six-hour-old tree.

## Rollback

`git revert` of the one hunk in `tools/loop/run.sh` restores the single call site; the spec delta and the case are
reverted with it, and the assertion in `worktree#noPushWithoutTheFlag` goes back to `1`.
