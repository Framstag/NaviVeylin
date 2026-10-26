# Tasks — fix-a-first-exercise-reports-its-count

Ordered; each task's verification is named beside it. The change touches no compiled file, so no Gradle suite is
owed and the gate that runs once at the end is the shell suites and the doc checks its pathspec selects
(`bash tools/loop/gate.sh --plan`).

- [x] 1. Write the red case `run#aFirstExerciseOnATickReportsInsteadOfFiling` — a scheduled tick, a row whose
      issues carry no measurement marker, three violation findings — asserting `condition_creates = 0`, no create,
      no comment, and `first exercise: row=fixture-emit findings=3` in the output. Declared in `SENSOR_CASES` and
      the dispatch table of `tools/sensor-cases.sh`.
      Verified: `bash tools/sensor-selftest.sh --case 'run#aFirstExerciseOnATickReportsInsteadOfFiling'` fails on
      the base — `expected '0', got '3'` (`evidence/red-on-head.log`,
      `evidence/red-on-head-aFirstExerciseOnATickReportsInsteadOfFiling.xml`, 2026-10-26T22:14:39Z).
- [x] 2. Write the case that pins the other half, `run#aDispatchedFirstExerciseRecordsItsMeasurement`: a dispatch
      with `apply=true` files all three findings and prints no `first exercise` line.
      Verified: green on the base by construction; `8.29` reddens it, which is its evidence.
- [x] 3. Read the trigger in `tools/sensors/run.sh` (`SENSORS_TRIGGER`, beside `SENSORS_DRY_RUN`) and pass it from
      `.github/workflows/sensors.yml` as `${{ github.event_name }}`.
      Verified: `grep -n SENSORS_TRIGGER tools/sensors/run.sh .github/workflows/sensors.yml` names both.
- [x] 4. Gate the first exercise in `sensors_run_row`: `SENSORS_FIRST_EXERCISE` when the trigger is `schedule` and
      the baseline is empty; the finding loop reports instead of filing; the row ends with the count line and
      writes nothing.
      Verified: `bash tools/sensor-selftest.sh --case 'run#aFirstExerciseOnATickReportsInsteadOfFiling'` passes;
      the full suite passes — `bash tools/sensor-selftest.sh` → `all 64 cases passed`.
- [x] 5. Record the falsification, one mutation per half: `8.29` (the gate ignores the trigger) and `8.30` (the
      gate never fires).
      Verified: `bash tools/sensor-mutations.sh --only 8.29` and `--only 8.30` each report
      `red under the mutation, green after the restore` (`evidence/mutations.log`).
- [x] 6. State the rule in `guidelines/Backlog.md` §9, beside the filing bound.
      Verified: `bash tools/check-doc-routes.sh` (the gate's own check for the guideline tree).
- [x] 7. Add the requirement *A row's first exercise is reported, not spent* with its two scenarios.
      Verified: `openspec validate fix-a-first-exercise-reports-its-count --strict` → valid; body 302 characters.
- [x] 8. Run the gate once, `./tools/loop/gate.sh`, and copy every tally it cites into `evidence/`.
      Verified: `evidence/gate.log` and `evidence/gate-*.xml`.
- [x] 9. Check the pointers mechanically and archive.
      Verified: `bash tools/loop/evidence-check.sh openspec/changes/fix-a-first-exercise-reports-its-count --root .`.
