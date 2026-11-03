# Design

## Root cause

One mechanism: **the sensor engine reads exactly one record, and that record is the build pipeline's.**
`tools/build-inputs.sh` writes the row inputs into the build job, `build.yml` uploads them as `sensor-inputs`,
`fetch-build-inputs.sh` fetches the artifact of `build.yml` for the commit under test, and every row that
declares an `input` reads a file out of it. `sonar.yml` sits outside that loop in every direction — it uploads
no artifact, `sensors.json` declares no row for it, and the fetch names no workflow but the build's — so a scan
that failed on every push left no trace in the tree the checks' run inspects, and a scan that never succeeded
read exactly like a healthy one (#751; the same shape made a red *build* visible only through
`broken-main`, #747).

## The single fix

Make the scan publish a record of its own, in the shape the loop already reads, and let one row read it:

1. **The record.** `tools/build-inputs.sh --scan-result --status <conclusion> --steps <json>` writes
   `app/build/scan-result.json`: `{job, step, command, result}`, where `job` is the workflow's job, `step` the
   first step that neither succeeded nor was skipped, and `command` the `run:`/`uses:` value that step carried.
   The script is the project's, not the engine's — it already is the one place that knows which paths the rows
   declare, so the record's path and the row's `input` stay paired by construction.
2. **The publisher.** The path joins `foreign_paths()`: `--manifest` and `--check` count it (a row may declare
   it), `--write` neither writes it nor requires it. That split is what keeps `--check` a real pairing while
   letting two pipelines produce into one manifest.
3. **The workflow.** `sonar.yml` gives the three failing-capable steps ids, writes the record on
   `if: always()` and uploads `app/build/scan-result.json` as `scan-inputs` on `if: always()`. The write is in
   the scan pipeline, never in the checks' pipeline, so the tracker token's scope (`Backlog.md` §9, the one
   workflow holding `issues: write`) is untouched.
4. **The readiness.** The checks' `run` fetches the scan's artifact with the same fetcher and the same
   read-only workflow token, and reports `scan-ready` beside `ready`: exit 3 means no completed scan of that
   commit carries the record yet. The row runs in its own step, gated on `ready && scan-ready`, and for a red
   build as well as a green one.
5. **The condition.** `broken-scan.sh` reads the record and emits one of the three outcomes: a violation
   identified by `job`+`step`, a clear for every condition its own issues record (through the shared
   `sensors_clear_recorded`, which `broken-main` now uses too), or an error when the record is unreadable.

## The decision the shape had to settle, and how the evidence settled it

The issue offered three shapes: a record published by the scan (chosen), a row reading the scan's run through
the API, and a `workflow_run`-triggered pipeline. The evidence eliminates the other two:

- **Reading the run through the API** puts a second network reader inside a row. Every condition row today
  reads a file (`broken-main`, `pr-conflicts` reads `gh pr list` — the one exception, and it is a *state* the
  tree cannot hold). A row that reads a run's conclusion would have to model "not finished yet" itself, which
  is exactly the distinction `fetch-build-inputs.sh` already models for the build with exit 3.
- **`workflow_run`** is a third trigger, and `sensors.yml`'s trigger set is capped at schedule and dispatch
  (`workflow#onlyTwoTriggers`), because the pipeline that holds the tracker token may not run near a
  contributor's tree. The scan may not file either: `check-sonar-workflow.sh` caps its triggers at push and
  dispatch and forbids `pull_request`, so a scan that filed would be a scan on a branch with `SONAR_TOKEN`
  in scope writing to the tracker.

There is no second viable mechanism, so no option list survives — gate 3 holds.

## Why the record is written by `tools/build-inputs.sh` and not inline in the YAML

An inline `jq` in the workflow cannot be gated: no shell suite can run a YAML step. The record's shape — which
step is the cause, what the command field carries, and what a green record says — is what
`inputs#theScanRecordIsTheScanPipelinesOwn` asserts, and it asserts it against the real script. The workflow
then only carries the facts: the run's conclusion and the per-step outcomes, which it already has.

## Blast radius

- **No product code**: no Gradle file, no Kotlin/Java source, no test source, no native input. The gate's
  suites for the touched pathspec are the shell suites (`tools/sensor-selftest.sh`,
  `tools/build-inputs-selftest.sh`, `tools/check-sonar-workflow-selftest.sh`, the portability and mutation
  listings, the document-routing checks).
- **The engine gains one helper** (`sensors_clear_recorded`) and one caller changes (`broken-main.sh`). Both
  clear paths are already covered by cases (`sensors#brokenMainGreenReportsClear`,
  `sensors#aRecordedConditionParsesToItsObject`, `sensors#aGreenScanClearsItsCondition`), so the refactor is
  held by existing evidence rather than by a new promise.
- **The checks' pipeline gains one gated step and one extra fetch.** The extra fetch is the same call the
  build's record already makes, under a different workflow and artifact; its exit-3 branch writes
  `scan-ready=false` and the rest of the run is unchanged.
- **A row's cadence**: `broken-scan` runs at minute 30 of every second hour, which the tick's `0,30` schedule
  covers (`workflow#tickMinutesCoverTheRegistryCrons`) and which leaves the scan ~30 minutes to finish after a
  push.

## Rollback

Revert the commit. The row, the condition script, the publisher's foreign path, the two workflow additions and
the shared helper go together; `--check` then pairs the manifest with the registry as before, and the next
`sensors` run selects no `broken-scan` row. Nothing persisted, nothing shipped, no state file, no artifact name
any other job reads.

## Open decisions

none

The issue offered three shapes and the tree eliminated two of them (see "The decision the shape had to settle"
above): reading the scan's run through the API, and a `workflow_run`-triggered pipeline, both refused by an
existing invariant rather than by a preference. Nothing here is left for a person to choose.

## What a reviewer should check first

1. `bash tools/build-inputs.sh --check` passes with the new row (the pairing is the change's load-bearing
   claim).
2. `bash tools/sensor-selftest.sh --case sensors#aFailedScanIsItsOwnCondition` and
   `--case sensors#aGreenScanClearsItsCondition` — the condition's two directions.
3. `bash tools/check-sonar-workflow-selftest.sh` — the workflow publishes the record on every outcome, and the
   mutated file reddens the check.
4. That no step of `sonar.yml` writes to the tracker: the scan publishes a record and files nothing.
