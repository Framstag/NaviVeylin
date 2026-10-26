# Design — fix-a-first-exercise-reports-its-count

## Root cause

`sensors_run_row` reads the row's baseline (`tools/sensors/run.sh:792-794`) and hands it to the check's
environment and to the issue body, but no branch turns it into a decision: a row whose issues carry no
measurement marker takes the filing branch of `sensors_outcome_violation` (`tools/sensors/run.sh:472`) exactly
like a row with a long history. The state is already known — `tools/sensors/report.sh:69` prints
`stale row=<id> no measurement on record` — it is simply not consulted where the writing happens.

## The single fix

A row's run is its **first exercise** when two things hold: the trigger the pipeline names is `schedule`, and the
baseline is empty. `sensors_run_row` sets `SENSORS_FIRST_EXERCISE`, the finding loop reports each finding instead
of dispatching it to `sensors_outcome`, and the row ends with one line naming the count it would have filed. No
write is reachable from that state — not a condition item, not the record issue, not a summary — because the
write paths are all below the branch.

The trigger comes from the environment (`SENSORS_TRIGGER`), which `.github/workflows/sensors.yml` fills with
GitHub's own `event_name` beside the `SENSORS_MODE` expression that already distinguishes the two triggers
(`.github/workflows/sensors.yml:57`). An absent value is the run a person started: every hand run and every case
in `tools/sensor-cases.sh` names none.

This is the item's recorded decision, option 2, implemented as recorded: *the gate applies to the scheduled tick
only, and a dispatch with `apply=true` is the acknowledgement that bootstraps the row*.

## Alternatives the evidence eliminated

- **A row with no measurement on record runs `--dry-run` whatever the mode says.** Eliminated by the gate's own
  predicate: a dry run posts no marker comment — every measurement goes through `sensors_record`
  (`tools/sensors/run.sh:371`) and the record filing's own dry-run branch (`tools/sensors/run.sh:324`), so
  `sensors_last_marker` keeps returning empty (`tools/sensors/lib.sh:539`) and the row would report its first
  exercise forever: the row never starts, and `report.sh` prints `stale` for the life of the bound. The declared
  case `run#dryRunTouchesNothing` is the same statement from the other side — a dry run makes no `gh issue` call.
- **The first run files at most the filing cap.** Not this item: #666 already landed the bound as
  `maxFilings`/`SENSORS_MAX_FILINGS`, and a bound governs what a run *may* write. It does not answer the question
  the tick has to answer — whether it may write at all — and it makes the first exercise visible only as an item.
- **Nothing is written until a person records a measurement by hand.** Eliminated by the failure #257 fixed: a
  bound that can only start with a manual step silently never files, which is the silence-read-as-a-verdict class
  the record issue exists to close.
- **Recording the count on the row's own record issue instead of filing conditions** (the item's other candidate
  and the decision's option 1). Not taken because the decision records option 2 and its cost is the one a
  scheduled run may not spend: the first exercise writes a tracker item before anybody has seen the count.

## Blast radius

One script, one workflow env line, two cases, two mutation records, one guideline paragraph, one spec
requirement. The gate paths: `tools/sensors/run.sh`, `.github/workflows/sensors.yml`, `tools/sensor-cases.sh`,
`tools/sensor-mutations.sh`, `guidelines/Backlog.md`, `openspec/changes/**` — no compiled file. The 38 `--apply`
call sites in the existing cases (`grep -c -- '--apply' tools/sensor-cases.sh`) assert the settled path and name
no trigger, so they are untouched; the two new cases are the only places a trigger is named.

What cannot regress silently: a gate keyed on the baseline alone (mutation `8.29` reddens
`run#aDispatchedFirstExerciseRecordsItsMeasurement`) and a gate that never fires (mutation `8.30` reddens
`run#aFirstExerciseOnATickReportsInsteadOfFiling`).

## Rollback

Revert the branch. A run whose caller names no trigger behaves exactly as before, so there is no state to
migrate: the gate reads one environment value and, when it is absent, does nothing.
