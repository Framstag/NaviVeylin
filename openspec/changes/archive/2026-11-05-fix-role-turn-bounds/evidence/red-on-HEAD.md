# Red on HEAD, 2026-11-05

Both new cases were run against a detached worktree of `f8eb72f3` (the tree's HEAD when the change was written),
with HEAD's engine and configuration and the change's `tools/loop-cases.sh` copied over it — so the case bodies
are the change's and everything they drive is HEAD's.

## `run#eachChildIsBoundedByItsOwnRolesTurns`

```
1. decide #21 — the run escalated it (blocked): the tail worker process exited 124 — the child reached the
   recorded turn bound of 10 turns (LOOP_ITEM_TURNS_MAX) after 10 turn(s) — the run ended it and everything it
   started
FAIL run#eachChildIsBoundedByItsOwnRolesTurns
```

HEAD ended the **tail** child at the item pot of ten — the worker's own ten turns — so the reviewer the case
measures never started. That is the defect in one line: the phase that inherited nothing is the one discarded.

## `run#aRoleWithNoRecordedTurnBoundRunsUnbounded`

```
the run's header says the worker's bound is off: 'child-turns-max=worker:0' not found in:
agent-issue-loop: … child-max-seconds=3900 item-turns-max=1 min-free-mib=1024 …
| #21 | … | blocked | … the worker process exited 124 — the child reached the recorded turn bound of 1 turns
  (LOOP_ITEM_TURNS_MAX) after 3 turn(s) — the run ended it and everything it started
FAIL run#aRoleWithNoRecordedTurnBoundRunsUnbounded
```

HEAD read the one turn the case recorded as an item pot and ended the worker, whose own key records `0` — a bound
the tree before this change cannot express.

## Green on the change

```
PASS run#childPastItsTurnBoundIsBlocked
PASS run#theRecordedZeroKeepsTheTurnBoundOff
PASS run#eachChildIsBoundedByItsOwnRolesTurns
PASS run#aRoleWithNoRecordedTurnBoundRunsUnbounded
```

The whole suite (`bash tools/loop-selftest.sh --jobs 8`, 316 cases) is **315 passed, 1 failed**, and the failure
is `gate#theMapCoversEveryPipelineStep` — "the suite map carries none of these commands the pipeline's Check
steps run: `tools/loop-selftest.sh --group \"$g\"`" — which fails identically on `f8eb72f3` in a detached worktree
with none of this change's files (measured the same day). It is not this change's.
