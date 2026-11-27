# Tasks: fix-a-record-issue-carries-the-rows-type

## 1. The payload site

- [x] 1.1 `tools/sensors/run.sh` (`sensors_file_record`, `:341-345`): `--arg type "$(sensors_row_type "$row")"`
      beside the record's `--arg claim`, and `type: $type` in the object literal. Verified by
      `run#aRecordIssueCarriesTheRowsType` — the recorded `gh` write carries `--type Bug`
      (`evidence/green-aRecordIssueCarriesTheRowsType.log`), and the same case failed before the change with
      `'--type Bug' not found in: gh issue create --title sensor record: fixture-emit …`
      (`evidence/red-on-HEAD.log`).
- [x] 1.2 `guidelines/BacklogTooling.md` `## 9. Checks`: the row rule's pointer names both payload sites, so the
      rule that an item a check files carries what its row declares names the record beside the condition. Verified
      by `tools/look.sh 'the item a check files carries'` naming the section.

## 2. The witness

- [x] 2.1 `tools/sensors/tests/sensor-cases.sh`: `run#aRecordIssueCarriesTheRowsType` — the name in `SENSOR_CASES`
      (`:114`), the dispatch line after `run#aFilingCarriesTheRowsType` (`:149`), and the case body after
      `case_run_aFilingCarriesTheRowsType` (`:1222`). One scenario: a clear finding files the row's record and the
      stub's `issue create` line carries `--type Bug`. Verified red on the base (`evidence/red-on-HEAD.log`) and
      green after 1.1 (`evidence/green-aRecordIssueCarriesTheRowsType.log`), with the cases that own the record's
      neighbouring behaviour green in `evidence/affected-cases.log`.
- [x] 2.2 `tools/sensors/tests/sensor-mutations.sh`: record `8.38` — the record's
      `--arg claim "$claim" --arg type "$(sensors_row_type "$row")"` becomes `--arg claim "$claim" --arg type ""`,
      which must redden `run#aRecordIssueCarriesTheRowsType`. Verified by
      `bash tools/sensors/tests/sensor-mutations.sh --only 8.38` (`evidence/mutation-8.38.log`).

## 3. The check the change owes

- [x] 3.1 `bash tools/sensors/tests/sensor-selftest.sh` from the change root — the whole sensor self-test is the
      gate's own suite for this delta (`evidence/sensor-selftest.log`, `evidence/gate-sensor-selftest.xml`).
- [x] 3.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-a-record-issue-carries-the-rows-type --root .`
      after the evidence above is in place, and the change archived with `openspec archive --yes`.
