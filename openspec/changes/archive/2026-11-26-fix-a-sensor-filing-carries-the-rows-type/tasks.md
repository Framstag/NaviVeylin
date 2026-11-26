# Tasks: fix-a-sensor-filing-carries-the-rows-type

## 1. The payload site

- [x] 1.1 `tools/sensors/run.sh` (`:282-287`): `--arg type "$(sensors_row_type "$row")"` beside `--arg class`, and
      `type: $type` in the object literal. Verified by `run#aFilingCarriesTheRowsType` — the recorded `gh` write
      carries `--type Bug` (`evidence/green-aFilingCarriesTheRowsType.log`).
- [x] 1.2 `guidelines/BacklogTooling.md` `## 9. Checks`: the row rule states that the item a check files carries
      the kind, area and type its row declares, so the filing site — not only the registry — is where a reader is
      sent. Verified by `tools/look.sh 'the item a check files carries'` naming the section.

## 2. The witness

- [x] 2.1 `tools/sensors/tests/sensor-cases.sh`: `run#aFilingCarriesTheRowsType` — the name in `SENSOR_CASES`
      (`:113`), the dispatch line beside `run#aFilingSetsTheBaseline` (`:146`), and the case body after
      `case_run_aFilingSetsTheBaseline` (`:1180`). Two scenarios: a `bug`-class row files `--type Bug`, and a row
      whose registry entry declares `type: Feature` files that instead. Verified red on the base
      (`evidence/red-on-HEAD.log`, `'--type Bug' not found in: gh issue create …`) and green after 1.1.
- [x] 2.2 `tools/sensors/tests/sensor-mutations.sh`: record `8.37` — `--arg type "$(sensors_row_type "$row")"`
      becomes `--arg type ""`, which must redden `run#aFilingCarriesTheRowsType`. Verified by
      `bash tools/sensors/tests/sensor-mutations.sh --only 8.37` (`evidence/mutation-8.37.log`).

## 3. The check the change owes

- [x] 3.1 `bash tools/sensors/tests/sensor-selftest.sh` from the change root — the whole sensor self-test is the
      gate's own suite for this delta (`evidence/sensor-selftest.log`).
- [x] 3.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-a-sensor-filing-carries-the-rows-type --root .`
      after the evidence above is in place, and the change archived with `openspec archive --yes`.
