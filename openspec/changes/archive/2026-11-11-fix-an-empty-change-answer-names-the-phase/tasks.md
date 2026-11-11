# Tasks — an empty change answer names the phase

- [x] Read the item's own record: `.pi/issue-loop/items/85/worker-report.md` 0 B, `events.jsonl` ending
      `auto_retry_end success=false`, `run.json` `.items[] | select(.issue==85)` = `blocked`, 0 review rounds, no gate
      step, and the log's `run-20261107T195343Z.log:1727-1735` going from the commit to `unpick #85`
- [x] Locate the two readings in `tools/loop/run.sh`: `:2405` enters the gate phase on `LOOP-DIFF-READY` alone, and
      the outcome arms read `LOOP-VERDICT`/`LOOP-GATE`, so a report with none is read as the item's tree
- [x] Add `change_answered()` beside `gate_answered()`: the report carries any line the arms read, a bound line
      included, so a bound stays the arm that owns it
- [x] Record the reading before the phase that would follow it: `phase_failure` names the change phase, the step
      `worker-answer` is recorded `fail` with the note `exit 0 and no answer`, and the run prints what it did not do
- [x] Keep the tree at the base on the run's own stop: that arm is reached first and is unchanged
- [x] Add the case `run#anEmptyAnswerOverACommitIsTheChangePhasesFailure` — one child, no gate child, no round before
      the gate, no tail, `blocked` with a reason that names the phase, and the branch carrying the run's commit
- [x] Add mutation `273.1`, which drops the reading and reddens the case on the run's printed line
- [x] Run the loop's gate: `LOOP-GATE: tests=999 failures=0 errors=0`; `loop-selftest` 400/400;
      `loop-mutations --check` 324/324; `loop-mutations --files tools/loop/run.sh` 118 records red-then-green;
      `loop-mutations --only 273.1` red, restore, green
- [x] Archive the change with its evidence
