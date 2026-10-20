# fix-loop-process-group-lifetime

## Why

Measured on the developer host on 2026-10-20 (issue #213, `class/bug`, `category/build-and-harness`): five
`tools/loop/run.sh --items 1 --minutes 90 --review off` processes were found still running 55 to 100 min after
the suites that had started them had ended, each with one `sleep 1` child, `readlink /proc/<pid>/cwd` pointing
into a `…/.pi/tmp/loop-selftest.<rand>/case.<rand>/repo` that had already been deleted. They were ended by hand
and nothing in the tooling had noticed them.

**Root cause, in one sentence.** No layer of the loop owns the *process group* of what it starts — the suite's
traps end the suite's own pid, the run's traps end the run's own pid, and the leftover report reads a
directory's age and nothing else — so a signal that ends a layer leaves the process it was waiting for
running, and a `SIGKILL`ed suite leaves processes that no command can see.

The evidence, in the tree at base `648d2bc`:

- `tools/loop-selftest.sh:65-68` traps `EXIT`, `HUP`, `INT` and `TERM`, and each handler removes `$tmp` and
  exits — the case itself runs in a foreground subshell (`tools/loop-selftest.sh:100`, `:115`) that the traps
  never name. Measured with this change's case: 10 s after `TERM` to the driver, the driver is *still running
  its case* (`driver alive: 1`) and the case's own two processes are alive (`… left 2 process(es) of it
  running: 3681780 3681781`) — bash defers a trap until the foreground command returns, so an interrupted suite
  honours the signal only when its case has finished anyway.
- `tools/loop/run.sh:370` (`on_exit`) releases the lock and unpicks the in-progress label; the child is started
  under `set -m` (`:514`) so the wall-clock cap can end it *and its descendants* (`:523`), which means the child
  has a process group of its own — and `on_exit` does not name it. A run ended by `TERM` therefore leaves its
  child working: `the run ended but the child it started (pid 3684598) is still running`.
- `tools/loop/loop-clean.sh:81-100`: the only signal the report reads is the age of a directory (`:88`, "Age is
  the only signal a killed suite leaves"). The report of a stale tree that a process is standing in prints
  `would remove loop-selftest.stale (4096 B)` and says nothing about the process.

**The three cases this change adds fail on HEAD `648d2bc`** (a shell gate has no JUnit XML; the case's own
message and exit status are the record — `evidence/red/`, produced by running these cases against
`git archive HEAD tools` with this change's cases file):

| case | red on HEAD | timestamp |
|---|---|---|
| `clean#interruptedSuiteEndsItsProcessGroup` | `10 s after TERM the suite was still running its case (driver alive: 1) and left 2 process(es) of it running: 3681780 3681781 — a suite ended by a signal must end the case and everything the case started, at once` (exit 1) | 2026-10-20T11:35:37Z |
| `clean#reportsAndEndsAProcessLeftInAScratchTree` | `the report names the process standing inside the tree it would remove: 'would end process 3665104' not found in: … would remove 1 path(s), 4096 B — nothing removed (pass --apply)` (exit 1) | 2026-10-20T11:35:38Z |
| `run#interruptedRunEndsTheChildItStarted` | `the run ended but the child it started (pid 3684598) is still running — an interrupted run must end its child` (exit 1) | 2026-10-20T11:35:45Z |

**The owning requirement and the owning rule.** Capability `agent-issue-loop`: this delta ADDS three
requirements beside the two the #210 change added ("A suite ended by a signal removes its scratch tree", "One
command reports and removes what the loop left") — the tree and the bytes those two own are not re-decided
here; what is added is the process that was still standing in them. The rule that owns the behaviour today is
`.pi/skills/fix-loop/SKILL.md`, §"What the loop leaves on disk" (its owner table's `loop-clean.sh` row), with
the facts in `.pi/skills/fix-loop/REFERENCE.md`, §"What a kept worktree keeps, and what the loop leaves on
disk"; invariant 3 of the same skill already states that the cap ends a child "together with everything it
started", and this change makes the run's own traps obey the same rule.

**Why it matters.** A leftover is indistinguishable from a real hang when the next session triages a loop that
"does nothing", and each one holds the case's fixture state, its worktree and its stub child. A leftover of a
*real* run is worse than noise: an `agent/in-progress` label on an item and a held run lock are exactly the
state a person then has to reason about by hand.

## What Changes

- `tools/loop/lib.sh` — two helpers, the one implementation of "end what I started": `loop_end_group <pgid>`
  (`TERM` the group, a bounded grace, `KILL` what is left) and `loop_end_pid <pid>` (the same, for one
  process). `TERM` first so a member's own trap still runs — the run's in-progress label and its lock, the
  child's own cleanup.
- `tools/loop-selftest.sh` — the case runs in a process group of its own (`set -m` before it, `run_case`), the
  traps leave through one `on_exit` that ends that group and then removes the scratch tree, and the case's stdin
  is closed for the same reason `run_child` closes its child's.
- `tools/loop/run.sh` — `active_child` names the process group of the child that is running, and `on_exit` ends
  it before it writes the item's outcome. The cap, the poll, the report, the state, the floor, the label
  transitions and the closure are untouched.
- `tools/loop/loop-clean.sh` — the report reads each stale scratch tree's processes (`/proc/<pid>/cwd`), names
  the ones standing inside a tree it is about to remove, and `--apply` ends exactly those before removing the
  tree. A process inside a tree that is kept is a live suite's and is never signalled; the path count and the
  byte total it already printed are unchanged, and a process count joins them only when there is one.
- `tools/loop-cases.sh` — the three cases, registered in the `--list` array and in the dispatch map.
- `tools/loop-mutations.sh` — three mutations (27.1–27.3), one per invariant, for `tools/loop-mutations.sh
  --only 27`.
- `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md` — the owner table's `loop-clean.sh` row, §"What the loop leaves
  on disk" and the reference section say what ends a case and its processes, and what the report names.

## Cases and evidence

- `clean#interruptedSuiteEndsItsProcessGroup` — this project's own driver, driven by a cases file whose one case
  starts a `sleep` and writes down its own pid and the child's, then waits. `TERM` to the driver: the driver is
  gone inside 10 s, its exit status is not 0, the scratch tree is gone (the #210 case still holds that) and
  neither pid survives.
- `clean#reportsAndEndsAProcessLeftInAScratchTree` — two processes of this case's own, one standing in a stale
  scratch tree and one in a fresh one: without `--apply` the report names the stale tree's process and ends
  nothing; with `--apply` that process is gone, the fresh tree's process and tree are untouched, and the summary
  counts the one process it ended.
- `run#interruptedRunEndsTheChildItStarted` — a run whose stub child blocks on a fifo (a child in the middle of
  its phase), `TERM` to the run's process group: the run reports `143`, and the child the run started is gone.

Red on HEAD `648d2bc`: `evidence/red/`. Green on this change: `evidence/green/`, which also holds the cases on
these two surfaces that already existed (`clean#interruptedSuiteRemovesItsScratchTree`,
`clean#reportsWithoutApplyAndRemovesWithIt`, `labels#trapRemovesInProgressOnSignal`,
`run#childOutlastingTheWallClockIsBlocked`, `run#mergesAFinishedAttemptAndClosesTheItem`).

## Impact

| file | module / area |
|---|---|
| `tools/loop/lib.sh` | the shared helpers every loop script sources |
| `tools/loop/run.sh` | `on_exit` and `run_child` only — what the run's traps end |
| `tools/loop/loop-clean.sh` | the leftover report: processes as well as trees |
| `tools/loop-selftest.sh` | the loop's offline gate: the case's process group and its traps |
| `tools/loop-cases.sh` | the gate's cases (three new) |
| `tools/loop-mutations.sh` | three mutations for the revert-check |
| `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md` | the skill that owns the loop's rules and its facts |

No Android module, resource, native source, JNI bridge, manifest, Gradle file or prompt skeleton is touched, so
the parity, native-build and configuration-portability rules do not arise: `loop_end_group` names no project
value, the cases name no project fact, and `tools/check-portability.sh` reads the same configuration as before.
`guidelines/*.md` says nothing about the loop's processes (checked: the loop's rules live in
`.pi/skills/fix-loop/`), so no guideline is superseded — the skill's own section is where the rule belongs.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **ADDED** three requirements: "A suite ended by a signal ends the
  case it was in and its processes", "An interrupted run ends the child it started", and "The leftover report
  names the processes, not only the trees". No existing requirement, scenario or wording is modified: the tree
  half (#210) keeps its requirement and its case, and the run's interruption requirement ("The run survives
  interruption") keeps the state, the lock and the in-progress mark it already owns.

## Additive or breaking, and rollback

**Additive.** Two new helpers, one new `EXIT` path in each of two scripts, one extra reader in the report, and
three new cases. Every flag, exit code, report line, state field, branch, label and archive step is unchanged:
the report's `N path(s), B B` line keeps its shape and gains `and ended N process(es)` only when it ended one.

Rollback: revert this commit. The suite's traps go back to removing the tree only, a run's trap goes back to
leaving its child, the report goes back to reading a directory's age, and the three cases fail again. Nothing
on disk needs repairing: a tree removed by an interrupted run is what the next run expects, and a process this
change ended was a leftover.
