# The attribution is read while the writer is alive, and the writer is one that left the worktree

## Why

`run#aWriterTheRunCanPlaceBlocksTheItem` (added by #286, `f5f2a2a7`) fails on a clean checkout of main's tip:
`a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'`. Measured
2026-10-24 at `9e77cd69cc2a0fc6fa51d68f145c5f22594f558e`, clean project tree: 4 of 4 runs red, with and without
the untracked probe file (`tools/issues/zz-dirty-probe`) the item used to make it flip — the flip the item
reports is the *margin*, not a second cause. Open item: #288.

**Root cause** (`tools/loop/run.sh`, `outside_write_seen`): the pids the attribution tested were the result of a
*second* host-wide sweep, and by the time that sweep ran the processes it was looking for had left the project's
own tree. Sweep 1 — the reading at the tick — lists nine pids standing in the project; sweep 2, ~1.1 s later,
lists none of them (`evidence/sweeps.log`). So `attributed` was **structurally always empty**: the "a placed
difference decides the item" half of #286 has never fired on this tree, only `--strict-outside-write yes` has, and
the case could not pass however often it was run.

**And the reading was 2.2 s late** (`evidence/timestamps.log`): a sweep of `/proc` is one `readlink` per process
on the host — 0.96 s for 731 of them (`evidence/sweep-cost.log`) — and `outside_write_seen` took two before it
decided, so the writer it was about died inside the reading:

```
write            1792825342.06   the child's write into the project's own tree
sleeper forked   1792825342.11   the child's own `sleep 2` (dies 344.11)
heartbeat tick   1792825343.05   the run sees the difference (?? demo-owned.txt)
state read       1792825343.06
sweep 1 ends     1792825344.11   ← the writer dies here
decision         1792825345.25   attributed=none → the item keeps 'done'
```

**The narrower rule is the other half of the root cause.** Reading the child's own tree in time is not enough:
the run starts the child **in the project** (`run_child`: `cd "$LOOP_ROOT" && $LOOP_AGENT_CMD …`), so the child
itself and every shell it was started through stand in the project's own tree for the whole item. Marking those
makes *every* difference in that tree decide the item. Measured with a temporary case (its own probe, reverted):
a **peer** writes and restores the project's tree while a child sleeps — the child's work untouched — and an
attribution that marks the child chain records `attributed=…` and blocks the item
(`evidence/peer-probe.log`). That is #274/#286's verdict, made from the run's own child. The shipped sentence
("marks the ones that belong to this child's own process tree") is vacuously true for that chain, so the rule
itself has to say what a *writer* is.

**The fix, then, has two parts**: read the child's own tree at the tick, from the kernel instead of from a
sweep, and mark only a process that **left the worktree** — one whose working directory is inside the project and
outside the worktree while the tree it came from works in the worktree. The sleeper is one (its parent, the
child's own shell, works in the worktree); the child and the shells the run started for it are not, because
nothing above them ever worked in the worktree.

## What Changes

- `tools/loop/run.sh`: `outside_write_seen` reads the child's own tree first (`loop_tree_pids`: `pgrep -g` for
  the group the child leads, `pgrep -P` down its descendants) and each of those few pids' working directory
  immediately; only then the host-wide sweep whose result is the note's holder list, which is context for a
  reader and no longer decides.
- The mark needs `pid_left_the_worktree`: an ancestor of the candidate worked in the worktree. `pid_ppid` reads
  the parent from `/proc/<pid>/stat`, so the walk forks nothing.
- `pid_in_child_tree` (per-pid `ps` + a 64-hop ancestor chain over swept pids) is replaced by that walk: it is
  bounded by the pids it has seen and keeps its two promises — the child's process group, and descendants that
  left it (the `setsid` survivor of #272).
- `pid_stands_outside_worktree` holds the working-directory test the note's list and the attribution both ask.
- `tools/loop-cases.sh`: a new case, `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` — a difference a peer
  made while a child stands in the project is recorded and the item keeps its outcome. It is the guard for the
  narrow rule: 12.11 (below) turns it red.
- `tools/loop-mutations.sh`: two records, `12.11` (drop the "left the worktree" test → the new case red) and
  `12.12` (read the candidates from the sweep again → the writer's case red).
- `tools/loop-selftest.sh`: `pgrep` joins the required tools, so a host without procps refuses the suite instead
  of attributing nothing silently.
- `guidelines/Backlog.md`, section 7 "The loop": the paragraph states the writer rule and records both measurements.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, "Each item is worked by a fresh process, reviewed by a
different one": the scenario *A difference is attributed while the writer is alive* extended by what a writer is
and by the reading order, plus one added scenario, *The run's own child chain is not a writer*. Every other
scenario of the requirement is repeated verbatim; the requirement's body is unchanged.

Additive, no interface change: the recorded lines, the block reason, `--strict-outside-write`, the default `off`
and the landing rule are untouched. Rollback path: revert the change; the item's red case is red again, as it is
today.

## Impact

| path | what changes |
|---|---|
| `tools/loop/run.sh` | `loop_tree_pids`, `pid_stands_outside_worktree`, `pid_ppid`, `pid_left_the_worktree`, the reading order and the mark in `outside_write_seen`; `pid_in_child_tree` removed |
| `tools/loop-cases.sh` | the peer case |
| `tools/loop-mutations.sh` | records 12.11, 12.12 |
| `tools/loop-selftest.sh` | `pgrep` in the required-tool list |
| `guidelines/Backlog.md` | the mechanism paragraph in section 7, "The loop" |
| `openspec/specs/agent-issue-loop/spec.md` | the two scenarios, after archive |

No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched: the delta is the loop engine, its
suite and the documents that route them. No submodule patch and no bridge override.

## Not in this change

- **A child that writes into the project's tree from its own process** (standing in the project, nothing above it
  in the worktree) is recorded and the item keeps its outcome. That is the price of the narrow rule and it is the
  safe direction: `--strict-outside-write yes` is the run's answer for a tree that is exclusive.
- The host-wide sweep for the note's holder list still costs ~1 s and is still taken after the attribution. It is
  context, and it no longer decides.
- The second red case of #287 (the pre-screen announcing no phase) is another item's.
