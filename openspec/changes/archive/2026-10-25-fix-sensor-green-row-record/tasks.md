# Tasks — fix-sensor-green-row-record

The delta touches no compiled file, so the build task is replaced by the structural check of the files it edits
(`guidelines/Build.md` §2, scope block). Every task names its own verification.

## 1. Reproduce the defect on HEAD

- [x] 1.1 Write `run#aClearWithNoIssueReachesTheRecord` in `tools/sensor-cases.sh` (with `lib#theRecordIsNotACondition`
  and `report#aGreenRowIsNotNever`) and register them in `SENSOR_CASES` and `sensor_run_case`. — parent spec
  `backlog-sensors`, *A row whose condition is simply absent says so*. Verification: with the engine restored to
  HEAD (`git stash push -- tools/sensors/lib.sh tools/sensors/run.sh`),
  `bash tools/sensor-selftest.sh --case 'run#aClearWithNoIssueReachesTheRecord'` fails with
  `the clear files the row's record issue: expected '1', got '0'`, and
  `bash tools/sensor-selftest.sh --case 'lib#theRecordIsNotACondition'` fails with
  `the row's record is not a condition: expected '[]', got '[{"number":9,…}]'`; `report#aGreenRowIsNotNever`
  passes (the report is already generic). Recorded in `evidence/red-on-head.log`.

## 2. The record path in the engine

- [x] 2.1 `tools/sensors/lib.sh`: `sensors_record_issue` finds the row's record by `<!-- sensor-record: <id> -->`;
  `sensors_conditions_json` excludes record issues; `sensors_last_marker` sorts the row's markers by instant;
  `sensors_registry_problems` refuses a missing or out-of-vocabulary `record.status`. — parent spec
  *A row's record is not a condition*. Verification: `bash tools/sensor-selftest.sh --case 'lib#theRecordIsNotACondition'`
  and `--case 'Registry#everyRowIsComplete'`.
- [x] 2.2 `tools/sensors/run.sh`: `sensors_record_body`, `sensors_file_record`, `sensors_record_number` (per-run
  cache), `sensors_record_clear`, `sensors_comment`; `sensors_labels_json` takes the record's state; and
  `sensors_outcome_clear` sends an absent or already-closed condition identity to the record. — parent spec
  *A row whose condition is simply absent says so*. Verification:
  `bash tools/sensor-selftest.sh --case 'run#aClearWithNoIssueReachesTheRecord'`.
- [x] 2.3 `tools/sensors/sensors.json`: the `record` block with `status: on-hold`. — parent spec
  *A row whose condition is simply absent says so*. Verification: `jq -e '.record.status' tools/sensors/sensors.json`
  and `bash tools/sensor-selftest.sh --case 'Registry#everyRowIsComplete'`.

## 3. Fixtures, porting and the report's case

- [x] 3.1 Add the `record` block to `tools/sensors/fixtures/registry.json`,
  `tools/sensors/fixtures/registry-no-consecutive.json`, `tools/sensors/fixtures/registry-no-threshold.json` and
  `tools/fixtures/foreign-project/tools/sensors/sensors.json`. Verification: `jq -e '.record.status'` on each and
  `bash tools/portability-cases.sh`.
- [x] 3.2 `PORTING.md`: the recipe names the record block among the registry's adaptation points. Verification:
  `grep -n 'record' PORTING.md`.
- [x] 3.3 Realign the four cases that counted any filing as a condition (`sensors#buildDurationRow`,
  `sensors#coverageRow`, `sensors#brokenMainGreenReportsClear`, `sensors#prConflictsNothingReportsClear`) and add
  the `condition_creates`/`record_creates` helpers. — parent spec *The report tells a green row from one that
  never ran*. Verification: `bash tools/sensor-selftest.sh --case 'sensors#coverageRow'` and the three others.
- [x] 3.4 `report#aGreenRowIsNotNever`: a record reads as a measurement and is not flagged, a row with none is
  still `never`. Verification: `bash tools/sensor-selftest.sh --case 'report#aGreenRowIsNotNever'`.

## 4. Falsification, checks and evidence

- [x] 4.1 `tools/sensor-mutations.sh`: mutation `8.18` removes the record call from `sensors_outcome_clear`. —
  revert-check for the invariant this fix introduces. Verification: `bash tools/sensor-mutations.sh --only 8.18`
  prints `red under the mutation, green after the restore`.
- [x] 4.2 Run the tooling's own gate: `bash tools/sensor-selftest.sh` — all 49 cases pass. No Gradle build or
  test suite is owed (no compiled file is touched; `guidelines/Build.md` §2, scope block).
- [x] 4.3 Portability: `bash tools/check-portability.sh` (47 engines name no project value) and
  `bash tools/portability-cases.sh` (exit 0).
- [x] 4.4 Copy the red-on-HEAD run, the green suite, the mutation and the structural checks into
  `evidence/` and verify the pointers. Verification: `evidence/red-on-head.log`,
  `evidence/structural-checks.log`.
