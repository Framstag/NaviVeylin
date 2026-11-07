# Design — the cap ends the tree as well as the group

## Root cause

`run_child` starts the child under `set -m` so it leads a process group of its own, and the cap ends it with one
signal to that group (`tools/loop/run.sh:695`, `:697`). A descendant that calls `setsid` — a mutation harness, a
detached daemon — is in a session and group the signal never names, so the run's own record ("the run ended it
and everything it started", `tools/loop/run.sh:641`) is true only of well-behaved descendants. Measured
2026-10-22 on #262: the child was ended at 23:48, the harness it had started kept mutating and restoring the
worktree until 23:55, and its last restore left the tree dirty against the commit the run had just made.

## The single fix

After the group signals, the cap reads the processes that are still working in the tree and ends them, naming
each one in the transcript:

```sh
      sweep=$(loop_end_pids_under "$LOOP_WORKTREE")
      if [ -n "$sweep" ]; then
        printf '%s\n' "$sweep" | sed 's/^/LOOP-ENDED: /' >> "$2"
      fi
```

`loop_end_pids_under` already exists (`tools/loop/lib.sh:296`) with the signal discipline the rest of the loop
uses — `TERM`, a grace, then `KILL` — and `loop_pids_under` is the tree test the self-test's own teardown and
`loop-clean.sh` report with. The transcript line is a second machine-readable prefix beside `LOOP-TIMEOUT` and
`LOOP-OUTSIDE-WRITE`, and it is written into the report the item is recorded `blocked` with, so a reader of that
item sees what the group signal could not reach.

Order: the sweep runs after the `LOOP-TIMEOUT` line is appended, so the transcript reads "the child was ended"
and then what else was ended, and before the item's outcome is recorded.

## Why the tree and not `pkill`-style descendants

A process that changed session keeps no parent link to the child the run holds — its ppid is reparented to
init as soon as the child dies — so ancestry cannot find it. The working directory is the fact that survives,
which is exactly the measurement in #262 (the survivor's `cwd` was the worktree) and the reason `loop_pids_under`
was written for #213 and #233.

## Blast radius

- `tools/loop/run.sh` — one call and three lines inside the cap branch of `run_child`. No other phase, no other
  caller, no change to `loop_end_group`, `loop_end_pid` or `loop_pids_under`.
- `tools/loop-cases.sh` — one case added, one mutation record's case unchanged.
- `tools/loop-mutations.sh` — one record (`44.1`) that removes the sweep, so the new invariant is falsified by
  a mutation like every other.
- `guidelines/Backlog.md`, the section "7. The loop" — the paragraph that already claims "everything it started" gains the mechanism and
  the measurement.
- The spec `agent-issue-loop` — one ADDED requirement.

Surface, counted before the plan was fixed
(`grep -ln 'loop_end_pids_under\|loop_end_group\|childOutlastingTheWallClock\|LOOP_CHILD_MAX_SECONDS' tools/loop/*.sh tools/loop-cases.sh tools/loop-mutations.sh tools/loop-selftest.sh tools/loop/prompts/*.md tools/loop/fixtures/pi`):
5 files carry the rule today — `run.sh`, `lib.sh`, `loop-selftest.sh`, `loop-cases.sh`, `loop-mutations.sh` —
with one case (`run#childOutlastingTheWallClockIsBlocked`) and one mutation record (`23.1`) asserting the cap.
**surface: 1 case added, 0 rewritten, 0 fixtures realigned, 1 mutation record added.**

## Rollback

Delete the call and its three lines; the tree then behaves exactly as it did at
`b1b8de08c6e1673601adf54638fda18c51a51258`, and `run#detachedProcessInTheWorktreeIsEndedByTheCap` goes red
again — which is mutation `44.1`, so the rollback is the falsification already recorded.

## Alternatives the evidence eliminated

- **Ending the descendants by ancestry** (`/proc/<pid>/task/*/children`): the child is already dead when the
  sweep runs, so its survivor is reparented to init; the walk finds nothing on the very case that matters.
- **Making the child's group the only session** (`setsid` around the child in `run.sh`): a descendant may still
  call `setsid` itself, which is what a mutation harness does; the fix would hold only for descendants that
  choose to stay.
- **Leaving it to `loop-clean.sh`**: that runs at the *next* run's start, which is the run that already refuses
  a dirty kept worktree (#271's finding, and the reason the next run refused to start on 2026-10-22). The tree
  has to be clean when *this* run's outcome is written.
