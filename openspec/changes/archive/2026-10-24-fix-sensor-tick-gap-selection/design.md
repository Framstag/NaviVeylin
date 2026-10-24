# Design — fix-sensor-tick-gap-selection

## Context

Motivation, the root cause and the measurements are in `proposal.md` (issue #256). The state this design
works within, read from the tree at HEAD:

- `tools/sensors/lib.sh`'s `sensors_cron_due <cron> <epoch> [<minutes>]` returns 0 when the expression
  matches any of the `<minutes>` minutes from `epoch` backwards; `tools/sensors/run.sh`'s selection loop
  asks that question per row (the `--within` flag, default 1).
- `.github/workflows/sensors.yml` schedules `0,30 * * * *` and passes `--within 30` on both row steps. A
  tick GitHub fires late within 30 minutes is covered; a tick GitHub skips entirely is not, because its
  minutes fall outside the next delivered tick's 30-minute window.
- The row steps are gated on the record's presence (and, for the build's rows, on a `success` conclusion).
- `sensors_cron_minute` matches one minute; `sensors_time_fields` reads the UTC fields of an epoch (with the
  BSD fallback).

## Goals / Non-Goals

**Goals**

- A tick GitHub skipped leaves its rows for the next tick, whatever the gap, so a row that is due is
  measured by the next tick that runs it.
- The selection is still a declared bound, and two consecutive ticks select no row twice for one cadence minute.
- The search cost is bounded by the gap's days, not by its minutes, so a dispatch after a long gap is not a
  per-minute loop.

**Non-Goals**

- Changing the schedule's cadence, the registry's rows, their bounds or their `consecutive` counts: the fix
  is *when a row is selected*, not which checks exist.
- Handling a row whose step was skipped by the red-build gate: that is the deliberate #168 behaviour, and
  its occurrence is recovered by the next interval in which the row's step runs only if the interval's lower
  bound is that step's own last run — state the pipeline does not carry. The issue's evidence and bound name
  the skipped tick, not the gate.
- Per-row last-run state (a tracker marker, a cache, an artifact): a clear run with no issue records
  nothing, so the tracker cannot answer "when did this row last run"; the previous tick's start is the one
  lower bound every row can share.

## Decisions

### D1 — The interval is `(previous tick's start, now]` (the fix's shape)

`run.sh --due --since <time>` selects a row when its cron matches any minute in the interval. The workflow
computes `<time>` from the previous Sensors run's `createdAt`. The lower bound is exclusive, so a row the
previous tick's interval already covered is outside this tick's interval; the intervals tile time exactly.

- *Alternatives the evidence eliminated*:
  (a) **Keep `--within` and pass a wider fixed window** — impossible: any fixed window is a guess, and a
  wider one double-measures (a daily row selected by every tick within the window), while a narrower one
  still loses ticks; the measured gaps reached six hours and were not bounded by the cadence.
  (b) **Make the schedule denser** — impossible for the same reason #166 recorded: lateness and skips are
  not bounded by granularity, and a run every minute multiplies the guard and preflight cost.
  (c) **A per-row last-run marker in the tracker** — impossible: a clear run with no issue records nothing
  (`sensors_outcome_clear`'s "nothing filed, nothing to clear"), so the newest marker is stale for every row
  that never filed, and a stale lower bound makes the row due on every tick.
- *Why this one*: one lower bound every row shares, computed from GitHub's own run history, with no state
  the pipeline has to carry and no repository write.

### D2 — The previous tick's start is read from the Actions API in the record step

The record-fetch step already runs with the workflow's own read-only token (`GH_TOKEN: ${{ github.token }}`,
`actions: read`). It adds `gh run list --workflow sensors.yml --branch main --status completed --limit 1
--json createdAt --jq '.[0].createdAt // empty'` and emits it as the `since` output. With no previous run,
the fallback is `date -u -d '30 minutes ago'`, the schedule's own cadence.

- *Alternatives the evidence eliminated*:
  (a) **A new step with its own token** — impossible: `workflow#onlyTwoTriggers` asserts `github.token`
  appears exactly once, between the fetch and the first tracker step, so a second step would fail the case.
  (b) **The runner queries the API itself** — impossible: the engine names no project value
  (`sensors#engineNamesNoProjectValue`, `check-portability.sh`), and the runner would have to be handed the
  workflow's token, widening what it holds.
  (c) **Read the scheduled event's nominal time from the payload** — impossible: a `schedule` event carries
  the cron expression, not the nominal instant, and a skipped tick's nominal time is exactly what is missing.
- *Why this one*: the token and the step already exist, the case's count is preserved, and the engine stays
  project-free.

### D3 — The interval predicate walks days, not minutes

`sensors_cron_since <cron> <since-epoch> <now-epoch>` validates the expression, aligns to minutes, and walks
backwards a day at a time from the now-day to the since-day. For a matching day it enters only the hours at
or below the relevant hour and the minutes the minute field names (`sensors_cron_minutes`, newest first). A
five-field expression matches at least once in four years (29 February is the coarsest day), so the walk is
bounded by four years and a day.

- *Alternatives the evidence eliminated*:
  (a) **The per-minute loop `sensors_cron_due` uses** — impossible: measured on this host, a ~three-year gap
  with no match (a 29-February cron) ran past the two-minute probe limit; the workflow's previous run can be
  months old after a disabled schedule.
  (b) **Hour-by-hour backwards** — impossible: a one-year gap costs 8,760 `date`+`awk` calls per row, and a
  daily row over a long gap would spend them to reach a match that a day walk finds in one step.
- *Why this one*: the measured cost is ~4 s for the worst case (a three-year gap that does not match) and
  ~0.1 s for the normal case, with no repository write and no new dependency.

### D4 — `--since` takes precedence, and a `--within` caller is unchanged

`run.sh` keeps `--within` for a caller that wants it (the punctual one-minute default and the existing
cases), and uses `sensors_cron_since` only when `--since` is given. A `--since` that is not an instant is a
usage refusal (exit 2), like an unreadable `--now`.

- *Alternatives the evidence eliminated*:
  (a) **Replace `--within` entirely** — impossible: the released cases and a manual `--within` call would
  stop working, and the one-minute default is the same question `sensors_cron_due` has always asked.
  (b) **Narrow a bad `--since` to the default** — impossible: that reproduces the defect inside its own fix,
  the same reason #166 refused to narrow `--within`.
- *Why this one*: the pipeline gets the new bound, and nothing that worked stops working.

## Risks / Trade-offs

- *A row's step skipped by the red-build gate loses the occurrence that fell before the skipped tick* →
  accepted: it is the #168 behaviour, and the next tick in which the build is green covers the interval from
  the previous tick, which may start after the occurrence. The issue's bound names the skipped tick, not the
  gate.
- *The previous-run query is an extra API call in the fetch step* → accepted: it is one `gh run list` with
  the token the step already holds, and the case asserts the token is still used once.
- *A `--since` older than four years does not find a 29-February match that would fall outside the walk* →
  accepted: a five-field expression matches within four years, so the walk's bound covers it; the bound is
  one comparison, not a per-minute loop.
- *`--since` is an interface the pipeline now depends on* → held by `workflow#theTickDeclaresItsWindow`
  (every scheduled `--due` invocation passes `--since "$SENSORS_SINCE"` and the workflow reads its own run
  history) and by mutation `8.13`.

## Migration Plan

1. Land the change (the interval predicate in `lib.sh`, `--since` in `run.sh`, the previous-run query and
   `--since` in `sensors.yml`, the new case and the reworked workflow case, the mutation), with the sensor
   suite, its mutation harness and the structural checks green.
2. Archive: sync the delta into `openspec/specs/backlog-sensors/spec.md`, move the change under
   `openspec/changes/archive/<date>-fix-sensor-tick-gap-selection/`.
3. Observation after the merge: the next scheduled tick on `main` reports the rows the previous interval
   covered; a *skipped* tick cannot be produced on demand, so the end-to-end record is the first run whose
   interval is wider than the schedule's cadence.
4. Rollback: revert this change's commit — `--since` disappears, `--within 30` returns, and the red test's
   HEAD failure reproduces.

## Open Questions

None that change this design.
