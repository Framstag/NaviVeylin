# Proposal — fix-worker-own-shell-write-is-staged-and-guarded

## Why

**The condition, verified in the tree at base `27cf61c` (2026-10-30).** The run's mark places a process of the
child's own tree only when an *ancestor* of it worked in the worktree:

```
$ sed -n '927,932p' tools/loop/run.sh
  p=$(pid_ppid "$1")
  while [ -n "$p" ] && [ "$p" != "0" ] && [ "$p" != "1" ] && [ "$hops" -lt 64 ]; do
    # An ancestor that is already gone answers nothing; the walk continues, because what it stands for is the
    # chain, not that one process.
    cwd=$(readlink "/proc/$p/cwd" 2>/dev/null) || cwd=""
    case "${cwd% (deleted)}" in "$wt"|"$wt"/*) return 0 ;; esac
$ sed -n '990p' tools/loop/run.sh
    pid_left_the_worktree "$pid" "$LOOP_WORKTREE" || continue
```

`run_child` starts every child **in the project** (`tools/loop/run.sh:1140`, `( cd "$LOOP_ROOT" && … ) &`), so a
shell the child forks and never `cd`s out of stands in the project with no ancestor that worked in the worktree:
the walk finds it (`loop_tree_pids`, `:885`) and places it as nothing. The write is recorded and the item keeps
its outcome — deliberately, because marking that chain is the verdict #274/#286 refused (issue #288) — but
**no case stages that shape**, so nothing refuses a later change that starts deciding on it. The one case that
pins placement works for a reason the item names: the stub's action is run *after* a `cd "$LOOP_WORKTREE"`
(`tools/loop/fixtures/pi:84`), a cwd no real child has, so every writer the fixtures fork carries a worktree
ancestor and is placed.

**Root cause, one sentence.** `tools/loop/fixtures/pi:84` moves the stub into the worktree before running the
action, so the self-test cannot stage a shell of the child's own tree that stands in the project — the shape
`pid_left_the_worktree` cannot place — and the guard the item owes for that shape cannot be written as a case.

**The record half is already in the tree.** `guidelines/Backlog.md:322`–`:325` names both unplaceable shapes
(the worker's own shell, and a process whose parent chain has died), landed by `db37a3a8` on 2026-10-29 — the
same review pass that raised this item. What is owed there is only the pointer from that sentence to the case
that now pins one of them; the case itself is missing.

**The red case, measured on the base tree** (`evidence/red-on-head.sh`, which installs the base commit's
`tools/loop/fixtures/pi`, runs the one case the delta names, and restores the tree's own copy on exit):

```
$ bash evidence/red-on-head.sh --base
FAIL run#theWorkersOwnShellWriteIsRecordedAndNotPlaced — the difference is recorded, with the path that changed: 'demo-owned.txt' not found in:
tests=1 failures=1
```

`evidence/red-on-head-round2.xml` (2026-10-30T09:04:21Z): `tests="1" failures="1" errors="0"`. On the base the
fixture has no hook that forks a shell while the child still stands in the project, so the case's write never
happens and the difference it must record is never recorded — the tree cannot stage the shape, which is the defect.
Round 1's red (`evidence/red-on-head.xml`, 2026-10-30T08:51:41Z) is the same case before the correction round added
its last assertion.

**Owning capability and guideline section.** The capability is `agent-issue-loop`; the rule is its scenario *The
run's own child chain is not a writer* (`openspec/specs/agent-issue-loop/spec.md:442`–`:450`), whose `Case` line
names only `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` — a case whose writer is a peer outside the child's
tree, so the scenario's own WHEN (only the child's own shells stand in the project) is pinned by nothing.
`AGENTS.md:197`–`:200` routes the change to that spec and to `tools/loop-selftest.sh`. The guideline section is
`guidelines/Backlog.md`'s "The loop" section (`:244`), whose paragraph at `:310`–`:327` is the rule being qualified.

**Surface**, from the base tree:

```
$ grep -rn 'pid_left_the_worktree' tools/ | grep -v /archive/ | cut -d: -f1 | sort | uniq -c
      1 tools/loop-cases.sh        (this change's own comment)
      1 tools/loop-mutations.sh    12.11, the mutation that removes the call
      2 tools/loop/run.sh          the function and its one caller
$ grep -rln 'PI_STUB_ACTION' tools/ | grep -v /archive/
tools/loop/fixtures/pi
tools/loop-cases.sh
tools/portability-cases.sh
$ git show HEAD:tools/loop-cases.sh | grep -c 'demo-owned.txt'
13          (the six cases that write the project's own tree, on the base)
```

surface: 1 case added (`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`), 1 fixture hook added
(`tools/loop/fixtures/pi` gains `PI_STUB_PROJECT_SHELL`), 1 spec scenario added, 1 guideline sentence extended with
the case that pins it, 0 cases rewritten, 0 mutations renamed, 0 compiled files — the three commands above.

## What Changes

- `tools/loop/fixtures/pi`: a `PI_STUB_PROJECT_SHELL` hook, run **before** the `cd "$LOOP_WORKTREE"` and so in the
  project the child is started in, forking a shell of the child's own tree that never leaves it — the shape
  `pid_left_the_worktree` cannot place. Absent or unset, the fixture behaves exactly as before.
- `tools/loop-cases.sh`: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, which stages that shape, and asserts
  the difference is recorded with its path, the item is `done` and `closed`, the reading names the shell that wrote
  among the processes standing in the project (the found half), and it places no process of that tree
  (`attributed=none`).
- The spec delta restates the modified requirement whole and adds the scenario *A shell the child forks in the
  project is found and not placed*, naming exactly that case. The restatements also give four shipped scenarios the
  `Case` line they had none — *The item arrives as a prepared package*, *A process runs where the project's own
  policy applies*, *A difference the run cannot attribute does not decide the item* (two cases), and *The reviewer is
  not the writer*, which declares `Case: none` with the structural reason — because `openspec/config.yaml` requires
  every scenario to name the one case that exercises it.
- `guidelines/Backlog.md`'s "The loop" section: the sentence that names the two unplaceable shapes gains the case that pins the
  worker's-own-shell one, so a reader of the rule is routed to the test of it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — *A difference the run cannot attribute does not decide the item*: the shape the narrow rule
  cannot place gains the case that pins it, staged by a fixture that stands where a real child stands.

## Impact

`tools/loop/fixtures/pi`, `tools/loop-cases.sh`, `guidelines/Backlog.md`'s "The loop" section, the shipped spec. No engine script, no
`pid_left_the_worktree` behaviour, no Gradle case, no compiled file: the run's verdicts are unchanged and this
change is the record of what the deliberate narrow rule cannot place.
