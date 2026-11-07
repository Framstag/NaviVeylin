# Proposal — fix-sensor-tick-gap-selection

## Why

One defect, measured on `main`, issue #256: **a scheduled tick GitHub skips entirely leaves its minutes
unmeasured**, so the run is green, nothing is measured, and a check can go a whole day without running.

**Root cause (one).** The selection matched a row's cron against a fixed window ending at the delivered
tick's own minute (`tools/sensors/lib.sh`'s `sensors_cron_due`) and `.github/workflows/sensors.yml` declared
that window as the schedule's own cadence (`--within 30`, `0,30 * * * *`). GitHub delivered 17 of the ~148
scheduled ticks in a 74-hour span (issue #256), with gaps up to six hours: a row whose cron minute fell
inside a gap matched no delivered tick's window and was never selected, while the run stayed green with
`no rows selected`. In those 74 hours only `broken-main` (4 ticks), `package-size` (1) and `stale-citations`
(1) ever ran; `build-duration`, `coverage`, `source-size`, `hotspot` and `pr-conflicts` ran in none of them.

**The single resulting fix.** The interval a tick selects over is bounded by the **previous delivered tick's
start time**, not by a fixed cadence. `.github/workflows/sensors.yml` reads the previous Sensors run's
`createdAt` and passes it as `--since`; `tools/sensors/run.sh` selects a row when its cron matches a minute
in `(since, this tick's minute]` through the new `sensors_cron_since`. A skipped tick's minutes remain in
the next tick's interval, so a missing tick delays a measurement instead of losing it, and consecutive
ticks' intervals still tile time so no row is measured twice for one cadence minute.

**Red test.** `bash tools/sensor-selftest.sh --case 'run#aSkippedTickLeavesItsRowsForTheNextTick'` on HEAD:
`run.sh` refuses `--since` (`unknown argument --since`, exit 2) and the case fails; after the fix the case
passes (46 of 46 cases green). The case drives a fixture clock (previous tick 01:00, next tick 04:00) and
asserts the 03:00 row is selected by the next tick while the previous tick's own minute is not selected
again.

**The requirement the defect contradicts.** `backlog-sensors`'s scenario *The schedule fires without a
person* states that the rows whose declared cadence is due are selected and measured. With a fixed window a
tick GitHub skipped contradicts it; the requirement is **modified** — its description and every other
scenario stay byte-identical, the scenario above now names the interval, and two scenarios are added
(*A skipped tick leaves its rows for the next tick*, *The interval measures a row once*), each naming the
case that exercises it.

## What Changes

- **`tools/sensors/lib.sh`** — `sensors_cron_since <cron> <since-epoch> <now-epoch>` is the interval
  predicate: it walks backwards a day at a time, entering only the hours of a matching day and the minutes
  the minute field names, so a gap of months costs a bounded number of conversions rather than one per
  minute. `sensors_cron_minutes` lists the minutes a minute field names, newest first. `sensors_cron_due`
  is unchanged.
- **`tools/sensors/run.sh`** — `--since <time>` is the interval's exclusive lower bound, taking precedence
  over `--within`; a value that is not an instant is refused. The selection loop uses `sensors_cron_since`
  when `--since` is given and `sensors_cron_due` otherwise.
- **`.github/workflows/sensors.yml`** — the record-fetch step reads the previous Sensors run's `createdAt`
  (`gh run list --workflow sensors.yml --branch main --status completed --limit 1`) and emits it as `since`;
  both row steps pass `--since "$SENSORS_SINCE"` instead of `--within 30`. With no previous run the
  fallback is the schedule's own cadence.
- **`tools/sensor-cases.sh`** — the new case `run#aSkippedTickLeavesItsRowsForTheNextTick`, and
  `workflow#theTickDeclaresItsWindow` now asserts every scheduled tick passes `--since "$SENSORS_SINCE"`
  and that the workflow reads its own run history.
- **`tools/sensor-mutations.sh`** — mutation `8.13` now removes `--since` rather than `--within 30`.
- **`openspec/specs/backlog-sensors/spec.md`** — one requirement modified when this change archives.

**Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
resource — the tools, one workflow and one spec. The registry, its rows, their bounds and their cadences are
untouched. `--within` keeps its meaning for a caller that passes it, so the only behaviour that changes is
the one that was broken. Rollback path: revert this change's commit — `--since` disappears, the workflow
passes `--within 30` again, and the red test's failure on HEAD reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at
all, so no Gradle build and no test suite is owed (`guidelines/Build.md`'s agent iteration protocol, its
scope block); the verification
is the sensor tooling's own offline suite, its mutation harness and the structural checks named in
`tasks.md`.

**Guidelines.** `guidelines/Backlog.md`'s checks section owns the checks' doctrine and names a cadence only
as one of the registry's columns; nothing there contradicts the interval, and no guideline section states how
a due row is selected, so no guideline is edited (a rule goes where the measurement lives, and this
measurement is a case in `tools/sensor-cases.sh`).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: one requirement modified — `The checks run in a pipeline of their own, pinned and
  least-privileged`. Its shipped description is unchanged, nine of its scenarios are copied whole, *The
  schedule fires without a person* now holds for an interval, and three scenarios are added or reworded.
  The delta lives at `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine and its pipeline)
- `tools/sensors/lib.sh` — `sensors_cron_minutes`, `sensors_cron_since`
- `tools/sensors/run.sh` — `--since <time>`, its refusal, the selection that passes it
- `.github/workflows/sensors.yml` — the previous run's `createdAt`, `--since` on both row steps

Cases and their harness
- `tools/sensor-cases.sh` — one new case, and `workflow#theTickDeclaresItsWindow`
- `tools/sensor-mutations.sh` — mutation `8.13`

Docs and specs
- `openspec/specs/backlog-sensors/spec.md` — gains the modified requirement when this change archives

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's own gate (46 cases)
- `bash tools/sensor-mutations.sh` — the change-time falsification of the bound it adds
- `yq -e '.' .github/workflows/sensors.yml`, `bash tools/build-inputs-selftest.sh`,
  `bash tools/build-inputs.sh --check`, `bash tools/check-portability.sh`
- Not run, deliberately: no Gradle build or test suite (no compiled file is touched; `guidelines/Build.md`
  scope block), and no live `workflow_dispatch` of `sensors.yml` (its guard refuses every ref but `main`).
