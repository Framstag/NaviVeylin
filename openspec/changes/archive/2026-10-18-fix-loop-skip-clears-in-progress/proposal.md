# Take the in-progress mark off an item the run skipped

## Why

Measured on the real run of 2026-10-18 (`tools/loop/run.sh --items 1 --minutes 90`, item #109, skipped as
`stale`): the item came out of the run carrying **`agent/in-progress` and `status/on-hold`** at the same
time, with no run holding it:

```
$ gh issue view 109 --json labels
{"labels":["agent/eligible","agent/in-progress","category/car","class/bug","prio/p2",
           "source/todo-import","status/on-hold","status/open"]}
```

**Root cause**: `run.sh` picks the item (`agent/in-progress`) before the worker starts and takes the mark off
on two of its three exit paths — `close` removes it, the blocked path calls `unpick` — but the **skip path**
does not. It only applies the verdict's own label (`stale` → `status/on-hold`, `needs-decision` →
`agent/needs-decision`) and the next reader sees an item being worked by nobody. `labels.sh`'s own header
states the contract this violates: "`unpick` − agent/in-progress **(every exit path, incl. a signal)**".

## What changes

1. **`run.sh`'s skip path calls `loop_labels unpick` before it applies the verdict**, so every exit path
   clears the mark: closed (via `close`), blocked (already), skipped (now).
2. **A case** (`run#aSkipClearsTheInProgressMark`) and **a mutation** (`12.8`) — red on HEAD, where the gh
   record for a skipped item holds only `--add-label agent/in-progress` and the verdict's status.
3. **`tools/loop-mutations-selftest.sh` derives the expected set size from the set itself** instead of
   spelling a number: the set grew from twelve to nineteen across the loop fixes (twelve was itself a bump),
   and a guard that has to be re-bumped on every change reports the change rather than the harness. It now
   asserts that the harness *lists what it declares* (`MUTATIONS=(…)`), which is the fault worth catching —
   a record the parser drops — and `19` records are declared today.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | the skip verdict clears the in-progress mark |
| `tools/loop-cases.sh` | `run#aSkipClearsTheInProgressMark` |
| `tools/loop-mutations.sh` | `12.8` |
| `tools/loop-mutations-selftest.sh` | the expected set size is derived from the declared set, not spelled |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement modified: a skip is an exit path too |

No compiled file changes: the loop suite, the mutation set and the portability suite are the gates.

**Rollback**: the `unpick` line — the case then fails with
`a skipped item does not keep the in-progress mark: '--remove-label agent/in-progress' not found in: …`.

**Tracker repair, not part of the code fix**: issue #109 needs the stale mark taken off once
(`tools/issues/update.sh 109 --remove-label agent/in-progress`).
