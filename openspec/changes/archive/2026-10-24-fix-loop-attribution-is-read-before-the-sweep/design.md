# Design — reading the writer while it is alive, and saying what a writer is

## The two decisions

**D1 — Where the attribution reads the process table.** `outside_write_seen` (`tools/loop/run.sh`) now reads the
child's own process tree first, one kernel call per level, reads the working directory of the few pids that
answers with, and only then takes the host-wide sweep whose result is the note's holder list.

**D2 — What makes a process a writer.** A process of the child's tree counts only when it *left the worktree*: it
stands in the project (cwd inside `$LOOP_ROOT`, outside `$LOOP_WORKTREE`) and one of its ancestors worked in the
worktree. The child itself and the shells `run_child` starts for it are excluded, because the run starts them
**in the project**.

The root cause fixes D1's shape (measured: a sweep is 0.96 s for 731 processes, the first tick that sees a
difference is ≤1 s after the write, and the deciding answer must land inside the writer's life), and D2 is forced
by what the run does to start a child — measured, a peer's difference blocked an item when the child chain was
marked (`evidence/peer-probe.log`).

## Alternatives the root-cause evidence eliminated

| alternative | eliminated by |
|---|---|
| Keep the sweep, run it once instead of twice (derive the holders and the attribution from one pass) | The sweep is the cost, not its repetition: one pass ends ~1.0 s after the difference is read, and the case's own numbers show the first pass already ends at the writer's death (`evidence/timestamps.log`: sweep 1 ends 344.11, the writer dies 344.11) |
| Read the candidates from the sweep, but at the tick, before the state | Sweep 2 of the base lists **none** of the nine pids sweep 1 listed, 1.1 s earlier (`evidence/sweeps.log`) — by the time it runs, the child chain has left the project's tree, which is why the mechanism was inert. Candidate *and* answer have to be read from something that does not need the host |
| Widen the case's writer instead (`sleep 2` → longer) | It hides the defect: the item is about the *run* naming a writer that is alive when it reads the difference, and the engine would keep mis-recording a 2 s writer. Adjusting the case to go green is what this project refuses |
| Mark the child's own standing chain (the obvious reading of the shipped sentence) | Proved wrong by measurement, not by taste: `run_child` starts the child in the project, so that chain stands there for the whole item, and a **peer's** write — the child's work untouched — is then attributed and blocks the item (`evidence/peer-probe.log`). It re-makes #274's verdict, whose own case `run#childWriteOutsideTheWorktreeIsNoted` expects `done` |
| Distinguish a writer by timing instead of by place (mark a process that appeared after the child started) | The run's own shells appear after the child starts too, so it separates nothing. Place is the property that does separate them: the run started its chain in the project, and a writer had to walk into the project from the worktree |
| Let the file system report the writer (`inotify`, `fanotify`) | `inotify` reports the event but no pid; `fanotify` reports the pid but needs `CAP_SYS_ADMIN`, which the loop does not have |
| Read the writer from the transcript's own process ids at exit | A writer is gone by the time the transcript is written — measured while #286 added the in-time reading (`guidelines/Backlog.md`, section 7 "The loop") |

## The walk, and what it keeps

`loop_tree_pids <pid>` asks the kernel twice per level: `pgrep -g "$child"` for the group the run started the
child in (`set -m` makes the child its leader) and `pgrep -P "$pid"` for every level below it. The walk stops on
the pids it has already seen, so it is bounded by the number of pids on the host and terminates. Both promises of
the removed `pid_in_child_tree` are kept: a process that joined the child's group is found by `pgrep -g`, and a
descendant that gave itself its own session — the survivor #272 is about — by the `pgrep -P` walk.

`pid_left_the_worktree <pid> <worktree>` walks the candidate's ancestors with `pid_ppid` (a `sed` over
`/proc/<pid>/stat`, no fork), bounded at 64 hops, and returns 0 as soon as one of them works in the worktree. An
ancestor that is already gone answers nothing and the walk continues, because what is being read is the chain.

## Risk

- **A child that writes into the project's tree from its own process is not placed** — recorded, and the item
  keeps its outcome. Stated in the proposal's "Not in this change"; the safe direction, and
  `--strict-outside-write yes` is the run's answer for an exclusive tree.
- **`pgrep` becomes a hard dependency of the run.** Same procps package as `ps`, which the engine already uses;
  it is added to the suite's required-tool list so a host without it is refused rather than silently attributing
  nothing.
- **The note's holder list is read after the attribution**, ~1 s later than the moment of the difference. It is
  context, its two lines and the `attributed=` field are unchanged, and no case asserts its membership.
- **A process that joins the child's group but is not the child's** is still placed. #286 accepted that
  possibility in its own risk table.

## Threading, lifecycle, verification

No thread, no lifecycle, no dispatcher: shell functions inside the loop's heartbeat subshell, in the same process
tree the change is about. The engine has no Android component.

Verification:

- **The owed red test**: `run#aWriterTheRunCanPlaceBlocksTheItem` — red on HEAD (`expected 'blocked', got 'done'`,
  4 of 4 on main's tip), green after the fix (8 of 8 across the runs recorded in `evidence/`).
- **The guard the narrow rule needs**: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` — green after the
  fix, and red when the narrow rule is mutated away (record 12.11).
- **The neighbours**: `run#childWriteOutsideTheWorktreeIsNoted`, `run#strictOutsideWriteBlocksTheItem`,
  `run#strictOutsideWriteDiscardsTheReviewRound`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`.
- **The revert-check**: six records, `12.3 12.4 12.9 12.10 12.11 12.12` — each named case red, restored green
  (`evidence/revert-check.md`).
- **The whole engine suite**: owed for a change that owns `tools/loop/**`. No device, no emulator, nothing is
  rendered, so no on-device measurement applies.
