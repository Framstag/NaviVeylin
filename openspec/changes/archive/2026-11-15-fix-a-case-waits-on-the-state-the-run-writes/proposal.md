# A signalled case's wait is decided by the state the run writes, under a deadline measured on its host

## Why

`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` (`tools/loop/tests/loop-cases.sh:15388`) went red in a full suite and
green when run alone, on the same tree and the same commit, whenever other suites were running on the host (issue
#928). Three independent children hit it in one afternoon (#921, #923, #925), and each reported the same shape: red in
the full set, `PASS` focused, nothing in the change touching the HUP path. The red was the case's own guard —
`a run that took HUP did not end inside 300 ticks (status 124)` — and #921's report quotes the stop it read as
`SIGTERM`, so the run **was** ending while the wait ran out.

The cause is a figure priced on the wrong object. `CASE_WAIT_TICKS=300` is 30 s at `0.1 s` per tick, and it was priced
as thirty times the `1 s` `TERM`→`KILL` grace a run's trap gives its child — a statement about the **run**, spent as
one about the machine. Every case in the suite creates git repositories and forks a run, and a host carrying four
sibling suites stretches those same one-second operations past thirty seconds. The wait then reports a trap bug that
does not exist, and the only thing saying so is the case's message.

Two halves are the repair, and they are one property: the wait is decided by what the case asserts — the state the run
writes — and its deadline is measured on the host that keeps it.

- Measured 2026-11-15 on this repository's measured host with 103 sibling suites on it
  (`tools/loop/tests/loop-cases.sh:1314-1325`): the staging one HUP case does before it signals — throwaway project,
  worktree, pre-screen, item start, the child's start — took `16.3 s` and `21.5 s`, and the run's whole end, from the
  stop it recorded to the exit that answered the wait, took `3.3 s` and `2.0 s`, a fifth of the staging. The figure
  that ran out was the one with nothing left to spend it on.
- Raising `300` to `3000` is not the repair: the next four-way run is as slow as this one, and a five-minute hang
  inside a case is worse than the flake it hides.

## What Changes

1. **The bounds, each with the object it prices** (`tools/loop/tests/loop-cases.sh:1326-1331`): `STOP_WAIT_STAGE_FACTOR`
   (`10`) times the staging the case measured — fifty times the fifth of it the run's end took; `STOP_WAIT_TICK_FLOOR`
   (`300`, the 30 s a quiet host needs and the floor of every derivation); `STOP_WAIT_TICK_CEILING` (`1200`, the most a
   case may hold its slot for a run that has not ended); `STOP_WAIT_END_TICKS` (`50`, the 5 s grace the tail after the
   run's last state write gets); `STOP_WAIT_STATE_EVERY` (`3`, because two `jq` processes ten times a second is load the
   host this wait exists for cannot spare, and the two facts are written once).
2. **The derivation** (`:1334-1341`): `stop_wait_deadline <the ms this case's staging took>` answers the ticks the wait
   allows — the staging in ticks of the step the wait sleeps in, times the factor, floored and capped.
3. **The two facts, read off the state** (`:1346`, `:1347`): `run_stop_reason` and `run_item_result`, each answering
   empty when the read fails, because a state caught mid-replacement is the state of a run still working and the wait
   reads it again on its next tick.
4. **The wait** (`:1357-1384`): `wait_for_run_stop` polls the process and the state every third tick; once both facts
   are there the rest of the run's end — the in-progress label's removal, its item's record, the unlock, the exit —
   gets the tail grace instead of the whole deadline, and the wait answers with the status the process took; at the
   deadline the state is read once more, after the last thing the run writes, so a run that recorded everything and did
   not end answers `124` naming that state, a run that ended answers with its own status, and a trap that wrote nothing
   leaves the caller's assertion to name the hole.
5. **The HUP case waits on what it asserts** (`:15403-15446`): it times its own staging (`stage_started`,
   `stage_ms`), derives its deadline (`:15438`), and fails — when it must — with the deadline, the staging it was
   derived from, and the stop and item the wait read.
6. **The case that holds the derivation and the three readings**
   (`:15537-15595`): `run#theStopWaitIsDecidedByTheStateTheRunWrites` asserts `stop_wait_deadline` at `0`, `2.9 s`,
   `8 s` and `60 s`; a run that recorded its stop and its item's result and ended answers the wait with the status it
   took and the state it read, inside a fraction of the deadline; a run that ended having recorded neither answers at
   once with its own status and the empty state; and a run that recorded both and stood is answered `124` with the
   state it read. Registry entries at `:248` and `:993`.
7. **Two mutation records** (`tools/loop/tests/loop-mutations.sh:1350`, `:1354`): `928.1` restores the constant floor, so
   the derivation is gone and every host waits the 30 s priced on a quiet one — a staging of 8 s included;
   `928.2` removes the trap a signalled run answers HUP with, so the run genuinely never stops and the HUP case must
   still red at its derived deadline with an empty state named.
8. **The rule, where the backlog routes it** (`guidelines/Backlog.recipes.md:815-832`): one paragraph beside the bound it
   replaces — the clock a case keeps is measured on the host that keeps it — with the 2026-11-15 measurement, the
   derivation, the two readings the deadline tells apart and the two mutation records.
9. **The requirement** (`openspec/specs/agent-issue-loop/spec.md:2656`): "A self-test case is held to a bound of its own"
   gains a third paragraph — a case's own wait is decided by the state the run writes, under a deadline derived from
   that case's measured staging — and a fourth scenario. The shipped two paragraphs and three scenarios are carried
   verbatim, in order.

## Impact

- `tools/loop/tests/loop-cases.sh` — five bounds where one constant stood, two readers, one rewritten wait, the HUP
  case's staging and deadline, one new case, two registry entries. `tools/loop/tests/loop-mutations.sh` — two records.
  `guidelines/Backlog.recipes.md` — one paragraph. No engine file changes: `tools/loop/run.sh`, `tools/loop/loop.conf`
  and the driver are untouched, and the trap mutation `928.2` reads the engine only to prove the wait has teeth.
- `CASE_WAIT_TICKS` is no longer the clock any signalled run's case is held to; it stays as the default of
  `wait_for_run_stop`'s third argument, for a caller that passes no staging to derive from.
- Additive for every case that waits on a process it started: a quiet host keeps the 30 s it had, because the derivation
  floors there. Rollback is the revert of these three files; no Android module, native boundary or UI surface is
  touched, and no configuration value moves.

## Evidence

- The implementation: commit `c1fd406d`, merged into the base as `c4982d8b`. The change was written against the shipped
  tree, so the implementation landed first and this change names it.
- Cases: `run#theStopWaitIsDecidedByTheStateTheRunWrites`, and `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` for the
  wait it now spends.
- Mutations: `928.1`, `928.2`.
- Readings, anchors, the two loaded-host figures and the gate verdict this change can and cannot produce:
  `evidence/readings.md`.
