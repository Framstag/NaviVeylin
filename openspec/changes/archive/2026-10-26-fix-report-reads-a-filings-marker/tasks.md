# Tasks — fix-report-reads-a-filings-marker

- [x] 1. Red: `report#aFiledRowIsReported` fails on the base — `bash tools/sensor-selftest.sh --case
  'report#aFiledRowIsReported'` prints `row=fixture-emit last=never` and exits 1 (`evidence/red-on-head-report.log`).
- [x] 2. Red: `run#aFilingSetsTheBaseline` fails on the base — the same invocation for the run case reports
  `verdict=clear` instead of `baseline=100000` and exits 1 (`evidence/red-on-head-run.log`).
- [x] 3. `tools/sensors/lib.sh` — ask `gh issue list` for `createdAt` in `sensors_issues_of_row`, and add
  `sensors_markers_json` (comments + the issue body's own marker, dated by the issue's creation, sorted oldest
  first); re-implement `sensors_last_marker` on it. Verified: 8.20's mutation of that helper reddens
  `report#aFiledRowIsReported`, and `run#aFilingSetsTheBaseline` gets the filing's number as its baseline.
- [x] 4. `tools/sensors/report.sh` — build the row entry from `sensors_markers_json` (last, previous by
  fingerprint, count). Verified: `report#aFiledRowIsReported` passes, and the unchanged report cases still pass.
- [x] 5. Falsify: mutation 8.20 (the helper stops reading body markers) reddens `report#aFiledRowIsReported`;
  mutation 8.21 (`run.sh` ignores the marker for its baseline) reddens `run#aFilingSetsTheBaseline`. Verified:
  `evidence/mutation-8.20.log`, `evidence/mutation-8.21.log`.
- [x] 6. Whole suite scoped to the delta: `bash tools/sensor-selftest.sh` — all 54 cases passed, exit 0
  (`evidence/focused-sensor-selftest.log`).
- [x] 7. Gate, once, from the change root: `./tools/loop/gate.sh` — `LOOP-GATE: tests=655 failures=0 errors=0`
  (`evidence/gate-summary.xml`, aggregated from the per-step tallies in this directory).
- [x] 8. Evidence and archive: `bash tools/loop/evidence-check.sh
  openspec/changes/archive/2026-10-26-fix-report-reads-a-filings-marker --root .` — PASS; archived.
- [x] 9. Red for the review's correction: `report#aTrendIsBetweenOneCondition` fails on the tree round 1 left —
  a record issue carrying two conditions' clears, its body naming no fingerprint, reports `trend=better` where
  the shipped report printed `trend=-`; exit 1 (`evidence/red-on-correction.log`,
  `evidence/red-on-correction.xml`, `tests="1" failures="1" errors="0"`, taken `2026-10-26T14:19:29Z`).
- [x] 10. `tools/sensors/lib.sh` — `sensors_markers_json` reads each marker's `fingerprint` from the marker, not
  from `$issue.body`, so the report's `previous` keeps selecting by condition identity. Verified:
  `report#aTrendIsBetweenOneCondition` passes, and `report#printsACountingLinePerRow`,
  `report#aGreenRowIsNotNever`, `report#aFiledRowIsReported`, `run#aFilingSetsTheBaseline` and
  `sensors#buildDurationRow` keep their printed lines (`evidence/focused-sensor-selftest.log`, all 54 cases).
- [x] 11. Falsify the corrected field: mutation 8.22 restores the issue-body fingerprint and reddens
  `report#aTrendIsBetweenOneCondition`; the file restores green (`evidence/mutation-8.22.log`).
- [x] 12. Gate again, once, after the correction: `./tools/loop/gate.sh` — `LOOP-GATE: tests=655 failures=0
  errors=0`; the sensor step's own tally is `tests="54" failures="0"` (`evidence/gate-tools_sensor-selftest.sh.xml`).
