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
      fetch names the scan workflow and its artifact, that the exit-3 branch writes `scan-ready=false` while any
      other status still fails the tick, and that the row's step is gated on `scan-ready` beside the build's
      `ready`. Spec `backlog-sensors`, "The record is fetched from its own pipeline, and an unfinished one
      selects no row". Verification:
      `bash tools/sensor-selftest.sh --case workflow#theScanRecordIsFetchedBeforeItsRowRuns` fails on the base
      with `the fetch does not ask the scan workflow` (`evidence/red-on-HEAD.log`, 2026-11-03T19:43Z).

## 2. The publisher

- [x] 2.1 `tools/build-inputs.sh` gains `--scan-result --status <conclusion> [--steps <json>]`, writing
      `scan-result.json` at the workspace root (`job`, `step`, `command`, `result`); the first step that neither
      succeeded nor was skipped is the cause, and a step that never ran is not one. Spec `backlog-sensors`,
      "A red scan is one condition". Verification: the record case of
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
      `scan-result.json` (the workspace root: a single explicit upload path is archived under its basename),
      `if: always()`, `if-no-files-found: error`. Verification: `bash tools/check-sonar-workflow.sh` exits 0 and
      `bash tools/check-action-runtimes.sh` exits 0 (the pin's floor is recorded at v6).

## 4. The row and its condition

- [x] 4.1 Declare `broken-scan` in `tools/sensors/sensors.json`: `boolean`, threshold 1, `consecutive` 1,
      `class` `bug`, category `build-and-harness`, `file+close`, input `scan-result.json`,
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
      otherwise answer for the setting) — and that the declared path is one the upload's root can carry (no
      directory, exactly that one uploaded path), read from the registry rather than repeated in the check.
      Verification: `bash tools/check-sonar-workflow-selftest.sh` is green over all ten cases, and the plants in
      the two new cases redden the check.
- [x] 6.2 `guidelines/BacklogTooling.md` §9 names `broken-scan` among the declared rows;
      `guidelines/BacklogTooling.recipes.md` §9 states how a pipeline the build does not own publishes its
      record under its own readiness. Verification: `bash tools/check-doc-routes.sh` and
      `bash tools/check-todo-references.sh` exit 0.

## 7. Verification and evidence

- [x] 7.1 Copy the red runs into `evidence/`: `evidence/red-on-HEAD.log` (six cases, base `55b5d658`,
      `tests=6 failures=6`) and `evidence/red-on-pre-correction.log` (the corrections' cases red on `18748b88`,
      the revision review round 1 read).
- [x] 7.2 Run the focused suites for the touched pathspec, then the project's gate (`./tools/loop/gate.sh`),
      copying the suites' tallies it writes into this change's `evidence/gate/`. It ran three times, on three
      trees: after the code and case corrections (227 s), after the documents and evidence those corrections
      changed (315 s), and on the merged tree that lands (206 s, `--base 47ec83e5`, the run `evidence/gate.txt`
      quotes) — the last alone describes the tree a reader can check out. Verification: the verdict line
      reports no failure and no error over 28 suites, and the 28 per-suite tallies under `evidence/gate/` are
      the ones that line sums.
- [x] 7.3 Quote `./tools/loop/measure.sh --change 2026-11-03-fix-a-red-scan-files-an-item`'s `LOOP-MEASURE` block
      into the change's evidence rather than re-deriving a number by hand: `LOOP-MEASURE-GATE seconds=206
      suites=28`, `LOOP-MEASURE-CHANGE … scenarios=5 cases=5 unnamed=0` (`evidence/gate.txt`).

## 8. Review round 1's corrections

The answer recorded on the item was "apply the additive corrections and re-gate" (the reviewer's recommendation
over merging the branch as it stood). The four blocking findings and their answers are in `design.md`, "Review
round 1".

- [x] 8.1 Move the record to the path the artifact's root can carry: `scan_result="scan-result.json"` in
      `tools/build-inputs.sh`, `path: scan-result.json` in `sonar.yml`'s upload, `"input": "scan-result.json"`
      in the row. Verification: `inputs#theScanRecordIsTheScanPipelinesOwn` asserts the manifest's path and the
      row's `input` are one string, and `tools/build-inputs.sh --check` pairs them.
- [x] 8.2 `check-sonar-workflow.sh` reads the declared path from `tools/sensors/sensors.json` and refuses a
      declaration with a directory, an empty `path:`, or an upload that lists anything but that one path. New
      case `sonar-workflow#theRecordPathIsTheUploadsOwn` with both plants. Verification:
      `bash tools/check-sonar-workflow-selftest.sh` (ten cases) and the pre-correction red run
      (`evidence/red-on-pre-correction.log`: `the row declares 'app/build/scan-result.json' with a directory`).
- [x] 8.3 `sensors#aFailedScanIsItsOwnCondition` asserts the item names the step and the command, and that a
      second failing step is a second item. Verification: the case passes; its pre-correction red run fails on
      the row's declared input.
- [x] 8.4 `sensors#aGreenScanClearsItsCondition` asserts the green-with-nothing-recorded row-level clear — which
      found the empty-`step` identity the runner refuses; the clear now carries the record's `the-run` sentinel.
      Verification: the case fails on `18748b88` with `malformed finding: field:step(missing)`
      (`evidence/red-on-pre-correction.log`), and passes now.
- [x] 8.5 `workflow#theScanRecordIsFetchedBeforeItsRowRuns` asserts the exit-3 branch (`[ "$status" -eq 3 ]`,
      `scan-ready=false`) and that any other status still exits non-zero. Verification: the case passes, and its
      base run fails on the fetch (`evidence/red-on-HEAD.log`).
- [x] 8.6 Revert-check: mutate `scan_result` to `app/build/scan-result.json`, `inputs#theScanRecordIsTheScanPipelinesOwn`
      reddens for the right reason, restore, forced green (`evidence/revert-check.log`).
- [x] 8.7 Re-gate once, after the corrections (`./tools/loop/gate.sh`), and refresh `evidence/gate.txt` and
      `evidence/gate/`.
