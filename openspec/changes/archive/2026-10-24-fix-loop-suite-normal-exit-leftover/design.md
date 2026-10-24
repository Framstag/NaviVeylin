# Design — the normal exit path, and why the other answers are impossible

A bug-fix change: the alternatives below are the ones the root-cause evidence eliminated, each named with why it
cannot be the fix. A list of *viable* options would mean the root cause is not established.

## The state that produced the defect

`tools/loop-selftest.sh`, `run_case`:

```
224: run_case() { # <case name> → the case's own status
226:   set -m
227:   ( loop_run_shell_case "$1" ) < /dev/null &
228:   case_pgid=$!
229:   set +m
230:   wait "$case_pgid"
232:   case_pgid=""
```

and `on_exit`:

```
104:   loop_end_group "$case_pgid"
105:   case_pgid=""
112:   loop_end_pids_under "$tmp"
113:   rm -rf "$tmp"
```

By the time a suite reaches its normal end, line 232 has already cleared `case_pgid`, so line 104 signals
nothing. A case that backgrounded a `run.sh` and returned leaves it in the case's group — a group no one
remembers — and `run_child`'s `set -m` grandchildren are in groups of their own in any case. Line 112 is what
finds them, by asking `/proc` for the working directory (`loop_pids_under`, `tools/loop/lib.sh:311`), and it
runs on every exit path because `EXIT` is trapped (line 116).

## Alternative 1 — change nothing, the fix landed

**Eliminated by**: mutation `53.11`. With line 112 removed the new case goes red, and the restore makes it green
again (`evidence/revert-check-53.11.log`). The harness builds each trial with `--case "$case_name"`, so that log
is about the record's own case and not about the other 209 — what it shows is that *this* case notices the missing
sweep. A shipped invariant that no case fails on is not a tested invariant, and the issue's own *Fix shape* names
the case as part of the fix.

## Alternative 2 — signal the last case's group after `wait`, in `run_case`

**Eliminated by**: the same state. `case_pgid` is cleared at line 232, before the driver's own end, and a case
that put its run in a group of its own (`set -m`, the shape `run_child` uses and the wall-clock cap needs) is not
in the case's group at all. A group signal reaches neither. The process that survives is only findable by where
it stands, which is what line 115 reads.

## Alternative 3 — trap `EXIT` inside the case subshell

**Eliminated by**: the process having *left* the case's group. A subshell's own `EXIT` trap runs in that subshell
and can only signal the group it led; the survivor is outside it. It would also multiply the teardown across
every case instead of keeping the one place (`on_exit`) that runs for every exit path — a signal, a failure, a
normal end, and an interrupted run.

## Alternative 4 — sweep the whole `LOOP_TMP` root instead of the suite's own tree

**Eliminated by**: concurrency. Two suites share `LOOP_TMP` (the loop's own gate runs one inside the kept
worktree, and `clean#reportsTheWorktreesScratchTreesToo` records exactly that), so a sweep of the root would
signal a live suite's processes. Every process the issue measured stood under the *suite's* tree — the case's
run works in the case's own scratch project — so the narrower root is both sufficient and safe. This is the
reading the shipped body keeps: the suite sweeps *its own* scratch root.

## What is deliberately not decided here

The engine is not touched. `loop_pids_under`'s `/proc` walk, the deleted-tree suffix, and the sweep's position
before `rm -rf` are `ec1f8b2b`'s and are already exercised by `clean#endsARunWhoseTreeIsAlreadyDeleted`. This
change adds the exit path that was missing, and nothing else.

## Verification

- The case, alone: `bash tools/loop-selftest.sh --case clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft`
  → `PASS`, ~6 s (`evidence/green-case.log`).
- Its permanent falsification: `bash tools/loop-mutations.sh --only 53.11` → `RED` for the named case, restore
  green, byte-identical tree (`evidence/revert-check-53.11.log`).
- The record applies: `bash tools/loop-mutations.sh --check --only 53.11`
  (`evidence/mutation-check-53.11.log`).
- The whole engine suite, owed for a change that owns `tools/loop/**`:
  `bash tools/loop-selftest.sh` (`evidence/full-suite.log`).
- The delta's own gate: `bash tools/loop/gate.sh --base <main's tip>` (`evidence/gate.log`).
- No device, emulator, logcat or pixel measurement: the delta touches no Android, native, Gradle or rendering
  path — it is a shell harness and its requirement.

## Threading and lifecycle

Unchanged: the driver forks one subshell per case and one nested driver per invocation of the case; the nested
suite's `EXIT` trap is the only lifecycle hook, and it ends what stands in its tree before the tree goes. Nothing
here runs on an Android dispatcher, holds a `Surface`, or touches the native boundary.
