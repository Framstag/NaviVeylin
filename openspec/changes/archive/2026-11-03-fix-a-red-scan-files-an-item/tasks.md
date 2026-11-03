# Tasks — fix-a-red-scan-files-an-item

No compiled file is touched, so every verification is a tooling check (`guidelines/Build.md` §2, the scope
block): the sensor, build-inputs and sonar-workflow shell suites, plus the portability and routing checks the
touched paths owe. No Gradle task, no device, no emulator.

## 1. The red case

- [x] 1.1 Add `sensors#aFailedScanIsItsOwnCondition` and `sensors#aGreenScanClearsItsCondition` to
      `tools/sensor-cases.sh` (list, dispatch and body), asserting the row's declaration, the condition's two
      directions and that a red scan files one condition under `job`+`step`. Spec `backlog-sensors`, "A red scan
      is one condition" and "A green scan clears the condition it recorded". Verification:
      `bash tools/sensor-selftest.sh --case sensors#aFailedScanIsItsOwnCondition` fails on the base with
      `the registry declares no broken-scan row` (`evidence/red-on-HEAD.log`).
- [x] 1.2 Add `inputs#theScanRecordIsTheScanPipelinesOwn` to `tools/build-inputs-cases.sh`. Spec
      `backlog-sensors`, "The publisher pairs the path without writing it". Verification:
      `bash tools/build-inputs-selftest.sh --case inputs#theScanRecordIsTheScanPipelinesOwn` fails on the base
      because the manifest does not name the scan's record (`evidence/red-on-HEAD.log`).
- [x] 1.3 Add `sonar-workflow#theRecordIsPublishedOnEveryOutcome` to `tools/check-sonar-workflow-selftest.sh`,
      with the two plants (a renamed artifact, a conditional record step). Spec `backlog-sensors`, "The
      workflow publishes its record for every outcome". Verification: the case fails on the base because the
      check's new assertion reports `no step writes the scan's record` (`evidence/red-on-HEAD.log`).
- [x] 1.4 Add `workflow#theScanRecordIsFetchedBeforeItsRowRuns` to `tools/sensor-cases.sh`, asserting that the
      fetch names the scan workflow and its artifact, and that the row's step is gated on `scan-ready`. Spec
      `backlog-sensors`, "The record is fetched from its own pipeline, and an unfinished one selects no row".
      Verification: `bash tools/sensor-selftest.sh --case workflow#theScanRecordIsFetchedBeforeItsRowRuns` fails
      on the base.

## 2. The publisher

- [x] 2.1 `tools/build-inputs.sh` gains `--scan-result --status <conclusion> [--steps <json>]`, writing
      `app/build/scan-result.json` (`job`, `step`, `command`, `result`); the first step that neither succeeded
      nor was skipped is the cause, and a step that never ran is not one. Spec `backlog-sensors`, "A red scan is
      one condition". Verification: the record case of
      `inputs#theScanRecordIsTheScanPipelinesOwn` passes for both a red and a green record.
- [x] 2.2 Declare the path in `foreign_paths()`, include it in `published_paths`, and keep `--write`'s
      completeness loop on `local_paths()` (written + produced). Spec `backlog-sensors`, "The publisher pairs the
      path without writing it". Verification: `bash tools/build-inputs.sh --check` exits 0 with the new row,
      and `--write` on a workspace without the file exits 0.
- [x] 2.3 Update the script's header and usage block (and the `-h` line range). Verification:
      `bash tools/build-inputs.sh --help` prints the new verb.

## 3. The scan's own record

- [x] 3.1 Give `sonar.yml`'s `Run unit tests`, `Generate coverage reports` and `Scan` steps ids, and add the
      `Write the scan's result record` step (`if: always()`) calling `tools/build-inputs.sh --scan-result` with
      the run's conclusion and the three step outcomes. Spec `backlog-sensors`, "The workflow publishes its
      record for every outcome".
- [x] 3.2 Add the `Publish the scan's record` step: `actions/upload-artifact@v7`, artifact `scan-inputs`, path
      `app/build/scan-result.json`, `if: always()`, `if-no-files-found: error`. Verification:
      `bash tools/check-sonar-workflow.sh` exits 0 and `bash tools/check-action-runtimes.sh` exits 0 (the pin's
      floor is recorded at v6).

## 4. The row and its condition

- [x] 4.1 Declare `broken-scan` in `tools/sensors/sensors.json`: `boolean`, threshold 1, `consecutive` 1,
      `class` `bug`, category `build-and-harness`, `file+close`, input `app/build/scan-result.json`,
      fingerprint fields `[job, step]`, cron `30 */2 * * *`. Spec `backlog-sensors`, "A red scan is one
      condition". Verification: `bash tools/sensors/guard.sh --json` reports a complete registry, and
      `bash tools/build-inputs.sh --check` pairs the input.
- [x] 4.2 Add `tools/sensors/conditions/broken-scan.sh` beside `broken-main.sh`: a red record is a violation
      under `job`+`step`, a green one clears every condition the row's issues record, an unreadable record is an
      error. Spec `backlog-sensors`, "A red scan is one condition" and "A green scan clears the condition it
      recorded". Verification: the two condition cases pass.
- [x] 4.3 Move the "clear what this row recorded" loop into `tools/sensors/lib.sh`
      (`sensors_clear_recorded`) and use it from `broken-main.sh` and `broken-scan.sh`, so one mechanism owns
      the closing. Spec `backlog-sensors`, "A green run closes what its own record names". Verification:
      `sensors#brokenMainGreenReportsClear` and `sensors#aRecordedConditionParsesToItsObject` still pass.

## 5. The tick

- [x] 5.1 Extend the existing record fetch in `.github/workflows/sensors.yml` to fetch `sonar.yml`'s
      `scan-inputs` artifact with the same read-only workflow token, reporting `scan-ready` (exit 3 = no
      completed scan of this commit carries the record yet; anything else fails the step). Spec
      `backlog-sensors`, "The record is fetched from its own pipeline…". Verification:
      `bash tools/sensor-selftest.sh --case workflow#onlyTwoTriggers` still passes — the workflow's own token is
      still used exactly once, in the one fetch step.
- [x] 5.2 Add the gated `Run the scan's condition row` step (`ready && scan-ready`, `--rows broken-scan`, the
      tick's `--since`). Spec `backlog-sensors`, "The record is fetched from its own pipeline…". Verification:
      `bash tools/sensor-selftest.sh --case workflow#theScanRecordIsFetchedBeforeItsRowRuns` passes and
      `--case workflow#theTickDeclaresItsWindow` still passes.

## 6. The structural check and the documentation

- [x] 6.1 `tools/check-sonar-workflow.sh`: assert that the scan writes its record and uploads it on
      `if: always()`, under the artifact name the checks' run fetches, carrying the path the row declares —
      reading each step's *own* `if:` line, never the string anywhere in the block (the block's prose would
      otherwise answer for the setting). Verification: `bash tools/check-sonar-workflow-selftest.sh` is green
      over all nine cases, and the two plants in the new case redden the check.
- [x] 6.2 `guidelines/BacklogTooling.md` §9 names `broken-scan` among the declared rows;
      `guidelines/BacklogTooling.recipes.md` §9 states how a pipeline the build does not own publishes its
      record under its own readiness. Verification: `bash tools/check-doc-routes.sh` and
      `bash tools/check-todo-references.sh` exit 0.

## 7. Verification and evidence

- [x] 7.1 Copy the red run's log and tally into `evidence/` (`evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`, tests=3 failures=3, 2026-11-03T06:47Z).
- [x] 7.2 Run the focused suites for the touched pathspec, then the project's gate exactly once
      (`./tools/loop/gate.sh`), copying the suites' tallies it writes into this change's `evidence/gate/`.
      Verification: the verdict line in `evidence/gate.txt` (197 s) reports no failure and no error over 25
      suites, and the 25 per-suite tallies under `evidence/gate/` sum to the case count that line states.
- [x] 7.3 Quote `./tools/loop/measure.sh --change fix-a-red-scan-files-an-item`'s `LOOP-MEASURE` block into the
      change's evidence rather than re-deriving a number by hand: `LOOP-MEASURE-GATE seconds=197 suites=25`,
      `LOOP-MEASURE-CHANGE … scenarios=5 cases=5 unnamed=0` (`evidence/gate.txt`).
