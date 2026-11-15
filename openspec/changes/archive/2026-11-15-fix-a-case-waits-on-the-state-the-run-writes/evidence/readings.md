# Readings — a signalled case's wait is decided by the state the run writes

Every anchor below was taken from this tree, on the branch that carries commit `c1fd406d` (merge `c4982d8b`). The
change was written after the implementation landed, so these are readings of shipped code and of the shipped cases,
not a plan.

## R1 — the figure that ran out, and what it priced

- `tools/loop/tests/loop-cases.sh:1314-1325` — the measurement the derivation is built from, in the suite's own words:
  on this host with 103 sibling suites on it, the staging one HUP case does before it signals took `16.3 s` and
  `21.5 s`, and the run's whole end, from the stop it recorded to the exit that answered the wait, took `3.3 s` and
  `2.0 s`, a fifth of the staging.
- The replaced figure was `CASE_WAIT_TICKS=300` — 30 s at `0.1 s` per tick — priced as thirty times the `1 s`
  `TERM`→`KILL` grace a run's trap gives its child: a fact about the run, spent as one about the machine. The issue
  (#928) records the red as `a run that took HUP did not end inside 300 ticks (status 124)`, and #921's report as the
  stop it read being `SIGTERM` — the run was ending.

## R2 — the five bounds and the objects they price

`tools/loop/tests/loop-cases.sh`:

| line | bound | value | object |
|---|---|---|---|
| `:1326` | `STOP_WAIT_STAGE_FACTOR` | `10` | the staging this case measured — fifty times the fifth of it the run's end took |
| `:1327` | `STOP_WAIT_TICK_FLOOR` | `300` | 30 s, what a quiet host needs, and the floor of every derivation |
| `:1328` | `STOP_WAIT_TICK_CEILING` | `1200` | 120 s, the most a case may hold its slot for a run that has not ended |
| `:1329` | `STOP_WAIT_END_TICKS` | `50` | 5 s, the grace the tail after the run's last state write gets |
| `:1330` | `STOP_WAIT_STATE_EVERY` | `3` | how often the state is read: two `jq` processes ten times a second is load the host cannot spare |

## R3 — the derivation and the two readers

- `tools/loop/tests/loop-cases.sh:1334-1341` — `stop_wait_deadline <the ms this case's staging took>`: the staging in
  ticks of the step the wait sleeps in, times the factor, floored at `STOP_WAIT_TICK_FLOOR` and capped at
  `STOP_WAIT_TICK_CEILING`.
- `:1346` — `run_stop_reason` reads `.run.stop.reason`.
- `:1347` — `run_item_result` reads `.items[0].result`.
- Both answer empty on a failed read: a state caught mid-replacement is a run still working, and the wait reads it again
  on its next tick.

## R4 — the wait

`tools/loop/tests/loop-cases.sh:1357-1384` — `wait_for_run_stop <state file> <pid> [ticks]`:

- `:1362-1375` polls the process every tick and the state every third one; `:1367-1372` answers with the process's own
  status once both facts are there, after `STOP_WAIT_END_TICKS`, and re-reads the state before answering `124` when the
  process still stands;
- `:1376-1383` reads the state once more after the loop, so what the caller names is the state at the answer and not the
  one a tick before it; a process still standing answers `124`, an ended one answers with its own status.
- The contract in the comment (`:1349-1356`) is what the two loaded-host readings are told apart by: recorded and did
  not end (`124` after the grace) against a trap that never ran (empty at the deadline).

## R5 — the case that holds it

`tools/loop/tests/loop-cases.sh:15537` — `case_run_theStopWaitIsDecidedByTheStateTheRunWrites`:

- `:15543-15546` the derivation at four stagings: `0` and `2900` ms both floor at `300`, `8000` ms derives `800`,
  `60000` ms is held at `1200`.
- `:15551-15563` a run that recorded its stop and its item's result and ended: the wait answers with the status it
  ended with, names the stop and the item it read, and does it inside a fraction of the deadline.
- `:15567-15576` a run that ended having recorded neither: the wait answers at once with its own status and the empty
  state, so the caller's assertion names the hole rather than the wait.
- `:15581-15594` a run that recorded both and stood: answered `124` with the state it read, inside the tail grace
  instead of the deadline.
- Registry entries `:248` (the case set) and `:993` (the dispatcher arm).

## R6 — the HUP case spends it

`tools/loop/tests/loop-cases.sh:15388` — `case_run_aHupEndedRunRecordsItsStopAndUnpicksItsItem`:

- `:15403` times its own staging; `:15437-15438` derives the deadline from it;
- `:15439` waits on the state, not on a count;
- `:15445` fails, when it must, with the deadline, the staging it was derived from, and the stop and item the wait
  read — the loaded-host reading #928 reported was that wait running out for a run that had recorded its stop;
- `:15448-15453` then assert the status the run took, the stop it recorded and the item it recorded `blocked`, which is
  what the wait is decided by.

## R7 — the falsification the suite holds

`tools/loop/tests/loop-mutations.sh`, the two records this change adds, verbatim:

```
'928.1@-@tools/loop/tests/loop-cases.sh@run#theStopWaitIsDecidedByTheStateTheRunWrites@the floor that lets the derived figure through, so the derivation is gone and every host waits the 30 s priced on a quiet one — a staging of 8 s included, which is the loaded host this issue is about (issue #928)@s#^  \[ "\$ticks" -ge "\$STOP_WAIT_TICK_FLOOR" \] || ticks=\$STOP_WAIT_TICK_FLOOR$#  ticks=\$STOP_WAIT_TICK_FLOOR#'
'928.2@-@tools/loop/run.sh@run#aHupEndedRunRecordsItsStopAndUnpicksItsItem@the trap a signalled run answers HUP with, so the run goes on instead of stopping and only the deadline the case keeps for itself can answer it (issue #928)@s|^trap '\''on_signal 129 HUP'\'' HUP$|trap '\'': '\'' HUP|'
```

`928.1` is the derivation's absence — every host waits the quiet-host figure. `928.2` is the teeth: with the trap
removed the run genuinely never stops, and the HUP case must still red at its derived deadline with an empty state
named, rather than go green over a run that took no signal.

## R8 — where the rule is written for the loop's readers

- `guidelines/Backlog.recipes.md:815-832`: the paragraph "The clock a case keeps is measured on the host that keeps
  it" — the measurement above, the derivation and its floor and cap, the two readings the deadline tells apart, and the
  two mutation records.

## R9 — the gate verdict

The gate that landed `c1fd406d` is the loop's own run. Its tally is **not retained** in this checkout: the run record
is gitignored and machine-local, and this change was written after the run had ended. Nothing here quotes a figure it
cannot show — `tools/loop/evidence-check.sh`'s C2 refuses exactly that — and the bounds, the derivation, the three
readings in the case and the two mutation records above are the readings a reader can re-take.
