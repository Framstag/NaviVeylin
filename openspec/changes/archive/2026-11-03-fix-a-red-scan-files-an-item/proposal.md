# Proposal

## Why

The scan of `main` was red on every push and nothing noticed. `.github/workflows/sonar.yml` had failed on
`41522676864`, `41523024883` and `41585715648`, and the tracker received no item of any kind; the one red
pipeline row that existed, `broken-main`, reported a unit-test failure of the *build* pipeline instead (#747).

The gap is structural, verified on this tree at `55b5d658`:

- `.github/workflows/sonar.yml` publishes no record of its own outcome — `grep -c 'upload-artifact\|scan-result'
  .github/workflows/sonar.yml` returns `0`, so a scan that never once succeeded leaves nothing behind;
- no row reads the scan: `jq -r '.rows[].id' tools/sensors/sensors.json` lists fourteen rows, and none of them
  is a scan row;
- the one record the checks' pipeline fetches is the build's: `tools/fetch-build-inputs.sh` defaults to
  `--workflow build.yml` and `.github/workflows/sensors.yml:106` never names another workflow, so the scan
  pipeline is outside the loop in every direction.

A pipeline that has never once succeeded therefore reads as healthy: `broken-main` reads
`app/build/gate-result.json` out of the `sensor-inputs` artifact the build publishes, and the scan publishes
nothing at all.

**The red case.** `sensors#aFailedScanIsItsOwnCondition`, `inputs#theScanRecordIsTheScanPipelinesOwn` and
`sonar-workflow#theRecordIsPublishedOnEveryOutcome` fail on `55b5d658`
(tests=3 failures=3, 2026-11-03T06:47Z, `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`):

- the registry declares no `broken-scan` row and no condition script sits beside `broken-main.sh`;
- the publisher's manifest does not name a scan record, so a row that declared one would be refused;
- the scan workflow neither writes nor uploads a record — the structural check's new assertion reports
  `no step writes the scan's record` at `.github/workflows/sonar.yml:1`.

**Owning capability and guideline.** The delta belongs to `backlog-sensors` — "A row's declared input is
published, checked against its producer, and read for the commit under test" is the requirement's family, and
this change adds the case that a *second* pipeline publishes one of those inputs. The rules it touches are
`guidelines/BacklogTooling.md` §9 "Checks" (the declared rows and the credential) and its body
`guidelines/BacklogTooling.recipes.md` §9 (how a row's input reaches the checks' run).

## What Changes

- **The scan publishes its own record.** `tools/build-inputs.sh` gains `--scan-result --status <conclusion>
  --steps <json>`: it writes `scan-result.json` at the workspace root holding the run's result and, for a red
  run, the step that broke and the command it ran. `.github/workflows/sonar.yml` calls it on `if: always()` and
  uploads the file as the `scan-inputs` artifact on `if: always()` as well — a red scan is exactly when a reader
  needs it. The three steps that can fail carry ids, so the record names its cause instead of only its
  conclusion. The path carries no directory because `upload-artifact` archives a single explicit file under its
  basename: a record written under `app/build/` would be restored at the workspace root, where a row declaring
  the directory reads nothing (review round 1).
- **The publisher pairs the path with its producer.** `tools/build-inputs.sh` declares
  `scan-result.json` as a path a *foreign* pipeline publishes: `--manifest` and `--check` count it,
  so a row may declare it, and `--write` neither writes it nor requires it — the build and the scan run beside
  each other, and a build that demanded the scan's record would fail on every commit whose scan is unfinished.
- **One row.** `tools/sensors/sensors.json` declares `broken-scan`
  (`tools/sensors/conditions/broken-scan.sh`, `boolean`, threshold 1, `class: bug`, category
  `build-and-harness`, `file+close`, input `scan-result.json`, fingerprint fields `[job, step]`).
  One issue per failing step, closed when a green scan clears it — the shape `broken-main` gives the build.
- **The tick learns when the scan has finished.** The checks' pipeline fetches the scan's artifact with the
  same fetcher and the same read-only workflow token, and reports a second readiness (`scan-ready`): exit 3
  means no completed scan of this commit carries the record yet, so `broken-scan` is selected by no tick and
  no failed check is reported. The row runs in a step of its own, gated on that readiness, and runs for a red
  build too — the scan measures the commit, not the build's verdict.
- **The condition is one mechanism, shared.** `tools/sensors/lib.sh` gains
  `sensors_clear_recorded`, which clears every condition a row's issues record; `broken-main` uses it instead
  of its own copy of the loop, so "a green run closes what its own record names" lives in one place.
- **Two bounds hold it.** `tools/check-sonar-workflow.sh` asserts that the scan writes and uploads its record
  on `if: always()`, that the artifact is named `scan-inputs`, and that it carries the path the row declares —
  renaming either side, or moving the record under a directory the archive's root cannot carry, is now a red
  check rather than a row that silently reads nothing. The workflow's own
  `workflow#theScanRecordIsFetchedBeforeItsRowRuns` holds the other end: the fetch asks the scan
  workflow for that artifact, reports the exit-3 branch that writes `scan-ready=false`, and the row's step is
  gated on that readiness as well as the build's.
- **Documentation gains the second pipeline.** `guidelines/BacklogTooling.md` §9 names `broken-scan` among the
  declared rows, and `guidelines/BacklogTooling.recipes.md` §9 states how a pipeline the build does not own
  publishes its record under its own readiness.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `backlog-sensors`: one ADDED requirement, "A pipeline outside the build publishes its own record, and its
  row waits for it", with five scenarios (a red scan is one condition; a green scan clears it; the record is
  published by the pipeline that measures; the publisher pairs the path and never writes it; an unfinished
  scan selects no row). No existing requirement is changed: `broken-main` keeps its own contract, and
  `sonar-analysis` still requires exactly what it required — the scan's triggers, boundary, coverage inputs
  and report-only gate — this change adds a *publisher* to that workflow, not a requirement of it.

## Impact

**New files**: `tools/sensors/conditions/broken-scan.sh`; the change's own documents and evidence.

**Modified files**: `.github/workflows/sonar.yml` (three step ids, one record step, one upload step),
`.github/workflows/sensors.yml` (the scan fetch inside the existing record step, one gated run step),
`tools/build-inputs.sh` (`--scan-result`, the foreign path in the manifest), `tools/sensors/sensors.json` (one
row), `tools/sensors/lib.sh` (one helper), `tools/sensors/conditions/broken-main.sh` (uses it),
`tools/sensor-cases.sh` (two cases), `tools/build-inputs-cases.sh` (one case),
`tools/check-sonar-workflow.sh` and its self-test (three assertions, two cases),
`guidelines/BacklogTooling.md` §9, `guidelines/BacklogTooling.recipes.md` §9. The shipped
`openspec/specs/backlog-sensors/spec.md` gains the same requirement on archive, and the change's own documents
record the review round's corrections.

**Not touched**: no Gradle build file, no module source, no test source, no CMake or vcpkg input, no
`sonar-project.properties`. The scan's trigger set, its token scope and its exclusion boundary are unchanged,
so `tools/check-sonar-workflow.sh`'s existing assertions and `guidelines/Backlog.md` §9's credential scope hold
as they stand. The tracker write stays in `sensors.yml`: the scan publishes a record and files nothing.

**Scope**: the checks' pipeline and CI tooling, both distribution variants at once — the scan covers `app`
(both flavors), `auto` and `core`, and no product behaviour changes.

**Classification**: additive, non-breaking. **Rollback**: revert the change's commit; the row, its condition
script, the workflow steps and the publisher's foreign path disappear together, and the next `sensors` run
finds no `broken-scan` row to select. No build input, no persisted state and no shipped artifact depends on
the scan's record.

**Residual, stated**: a scan workflow that never uploads reads as "no record yet" for ever, exactly as an
unpublished build record would. The structural check is what makes that a red gate rather than a silent one;
the fetcher cannot tell a workflow that forgot from one that has not finished.

surface: 5 cases added (2 in `tools/sensor-cases.sh`, 1 in `tools/build-inputs-cases.sh`, 2 in
`tools/check-sonar-workflow-selftest.sh`), 0 rewritten, 1 row added, 2 workflows touched, 1 published path
declared — `grep -c 'broken-scan' tools/sensor-cases.sh tools/build-inputs-cases.sh tools/sensors/sensors.json`
and `grep -c 'scan-inputs' .github/workflows/sonar.yml .github/workflows/sensors.yml`.
