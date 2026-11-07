# Tasks — fix-sensor-tick-gap-selection

## 1. The red case (proves the defect on HEAD)

- [x] 1.1 Write `run#aSkippedTickLeavesItsRowsForTheNextTick` in `tools/sensor-cases.sh`: a fixture clock
  with the previous tick at 01:00 and the next at 04:00 asserts the 03:00 row is selected by the next tick
  and that the previous tick's own minute is not selected again. Verify: on HEAD the case fails
  (`run.sh` refuses `--since`, `unknown argument --since`, exit 2), recorded in `evidence/red-on-head.log`.

## 2. The interval predicate

- [x] 2.1 Add `sensors_cron_minutes` and `sensors_cron_since` to `tools/sensors/lib.sh` (spec:
  `backlog-sensors`, *A skipped tick leaves its rows for the next tick*). Verify: the new case passes and
  `sensors_cron_since` answers 0/1/2 for the gap, exclusion, backwards and malformed probes.
- [x] 2.2 Add `--since <time>` to `tools/sensors/run.sh`: parse, refuse a non-instant, use
  `sensors_cron_since` when given and `sensors_cron_due` otherwise (spec: *The schedule fires without a
  person*). Verify: `bash tools/sensor-selftest.sh` — 46 of 46 cases.

## 3. The pipeline declares its interval

- [x] 3.1 Read the previous Sensors run's `createdAt` in the record-fetch step of
  `.github/workflows/sensors.yml` and pass `--since "$SENSORS_SINCE"` on both row steps (spec: *The pipeline
  declares the interval its ticks select over*). Verify: `yq -e '.' .github/workflows/sensors.yml` and
  `workflow#theTickDeclaresItsWindow`.
- [x] 3.2 Keep the workflow's own token used exactly once (the fetch step). Verify: `workflow#onlyTwoTriggers`.

## 4. Cases and falsification

- [x] 4.1 Rework `workflow#theTickDeclaresItsWindow` to assert every scheduled tick passes
  `--since "$SENSORS_SINCE"` and that the workflow reads its own run history (spec: *The pipeline declares
  the interval its ticks select over*). Verify: the case passes and mutation 8.13 makes it red.
- [x] 4.2 Update mutation 8.13 in `tools/sensor-mutations.sh` to remove `--since`. Verify:
  `bash tools/sensor-mutations.sh --only 8.13` — red under the mutation, green after the restore.

## 5. Structural checks (no compiled file)

- [x] 5.1 `bash tools/sensor-selftest.sh` — 46 of 46 cases.
- [x] 5.2 `bash tools/sensor-mutations.sh --only 8.12` and `--only 8.13` — both behaved.
- [x] 5.3 `bash tools/build-inputs-selftest.sh` (11 cases), `bash tools/build-inputs.sh --check` (4 rows),
  `bash tools/check-portability.sh` (47 engines). Verify: each green.
- [ ] 5.4 Not run, deliberately: no Gradle build or test suite (no compiled file is touched;
  `guidelines/Build.md`'s agent iteration protocol, its scope block).

## 6. Archive and observation

- [ ] 6.1 `openspec validate fix-sensor-tick-gap-selection --strict` at 0; then archive the change and
  confirm the delta merged into `openspec/specs/backlog-sensors/spec.md`.
- [ ] 6.2 After the merge, read the first scheduled `main` run whose interval is wider than the schedule's
  cadence and record the rows it selected.
