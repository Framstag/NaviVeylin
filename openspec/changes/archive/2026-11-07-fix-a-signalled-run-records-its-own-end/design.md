# Design — the end a signalled run writes about itself

## Root cause

`tools/loop/run.sh:588`–`591` traps `EXIT`, `INT` and `TERM` and nothing else, and the branch that runs when a
signal ended the run (`:579`–`584`) writes only the *item's* outcome: it unpicks `agent/in-progress` and records the
item `blocked`, then `:585` releases the lock. The run's own record is written by one command, `loop_state stop`
(`tools/loop/state.sh:445`–`452`), and the only thing that calls it is `stop_run` (`tools/loop/run.sh:606`–`609`) —
the path every refusal ends through. A signal therefore ends a run by the one route that leaves `run.stop` empty, and
`HUP` is not on that route at all: the shell dies before any trap, so the label comes off nowhere, and `unlock` —
which removes the lock and nothing else (`tools/loop/state.sh:976`–`995`) — never runs either. `report.sh:137`–`139`
and `state.sh:263`–`268` then print "it recorded no reason", which is the shape of a crash rather than of a run a
person ended.

## The single fix

The signal path records the run's end, and `HUP` takes the same path as `INT` and `TERM`.

**The trap names the signal.** `on_signal` takes the status *and* the signal's name (`HUP`→129, `INT`→130,
`TERM`→143), keeps the name in one variable and exits with the status, exactly as it exited before. Decoding
`128+N` in `on_exit` would put a second copy of the same table in a second place, and the trap already knows which
signal it is.

**The record goes in the interrupted branch, before the unlock.** `loop_state stop "interrupted by SIG<NAME>"` is
the same command the refusals use, so the reason is one vocabulary and `report.sh` prints it with no edit of its own.
It sits before `loop_state unlock` because that is the last thing the trap does and the record must exist while the
state it is written into is still this run's. It is outside the `[ -n "$in_progress" ]` test, because the write does
not depend on an item being in flight; the two cases here assert the in-flight path, so that scope is the
implementation's shape and is not claimed as covered.

**Why the label and the item record stay where they are.** They are the item's facts, they are inside the
`in_progress` test, and `labels#trapRemovesInProgressOnSignal` and the `TERM` case already hold them; this change adds
nothing to that half and moves nothing in it.

**Why the fix is in the engine and not in a prompt.** The cleanup is a shell fact, not a thing a child can be asked
for: `HUP` is delivered to the run's own process, whose trap table is `run.sh`'s, and no prompt reaches it.

## Blast radius

- `tools/loop/run.sh` — one variable beside `interrupted`, one argument on `on_signal`, one trap line, and one command
  in the interrupted branch. Nothing else moves: the child's group, the heartbeat, `keep_leftovers`, `stop_run` and
  every refusal path are untouched.
- `tools/loop-cases.sh` — one case added (`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`: a run blocked on its
  child, `HUP` to the pid the state names, then status `129`, the stop reason, the in-progress unpick, the item's
  record and the lock), and one assertion added to `run#interruptedRunEndsTheChildItStarted` beside a wait for the run's
  own end — that case signals the run's whole process group, so the subshell it waits for is gone while the trap is
  still finishing, and the lock is released after the run's record is written. The run-case count moves from 128 to
  129 (`grep -c "^  'run#" tools/loop-cases.sh` on the base and in the change, 2026-11-07); the suite's own
  `--list` reads `349` in the change and `348` at the base `fca2c857`.
- `guidelines/Backlog.recipes.md` §7d — one paragraph, because that section owns what the run records when it ends.
  No routing row's byte figure changes: `AGENTS.md:62` prices `Backlog.md`'s §7a–§7e, not this file.
- `openspec/specs/agent-issue-loop` — the one requirement above gains `HUP` and the record; no other requirement is
  restated, so no second home for the same rule exists.
- `tools/loop-mutations.sh` — record 9.4 names the `loop_labels unpick` line the interrupted branch re-indents, so the
  record is realigned to the line as it now stands (the check's own instruction: fix the record, not the case).
- No compiled file is touched, so no build and no Gradle suite is owed; the gate is the loop's own suite
  (`tools/loop-selftest.sh`), scoped by the files this change touches (`BuildGate.md` §2a).

## Alternatives the evidence eliminated

- **Repairing the stale `agent/in-progress` mark in the pre-screen** (the issue's third direction: unpick a mark
  whose owner pid is gone, the test `state.sh`'s lease and `report.sh` already use). Eliminated as *this* change:
  it is the queue's mechanism, not the signal path's, and it repairs what runs before this fix already left. The
  case added here reads `.run.ownerPid` — the field that repair would test — so its absence is visible rather than
  silently assumed, and it is named in the proposal's "what this does not change" for a change of its own.
- **Recording the stop outside the trap** (a `stop_run` call on the normal exit path for every run). Eliminated: a
  run that ends by its budget or its queue already records its own end through `stop_run` or the state's own
  `deadline` reason, and the state's record has one writer by its own rule — `openspec/specs/agent-issue-loop/spec.md`,
  "The run's state SHALL have exactly one writer" — which is a requirement the interruption delta does not restate.
- **Leaving `HUP` untrapped and letting the next run clean up** (the pre-screen's takeover of a lock whose owner is
  dead). Eliminated: the lock is already taken over, so the *run* recovers; the *item* does not — its in-progress
  label is what the pre-screen excludes, which is the symptom measured on #733 a day after the run that left it, and
  the run's own history keeps the missing cause (#755's 50 of 105 runs).
- **A trap that only records and does not exit** (ignoring `HUP` to keep the run going). Eliminated: the terminal or
  harness that sent `HUP` is gone, and a run that outlives its reader keeps spending a budget nobody can watch — the
  shape `on_signal` was introduced for (issue #213).

## Rollback

Revert the commit: the traps return to three, and the interrupted branch records the item alone. Nothing else reads
`run.stop` differently — `report.sh` already prints the reason when it is there and its "recorded no reason" line
when it is not — so the tree returns to its behaviour before this change with no migration, no state rewrite and no
second release.
