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
  fingerprint, count). Verified: `report#aFiledRowIsReported` passes, and the unchanged report cases still pass
  (`evidence/focused-sensor-selftest.log`, all 53 cases).
- [x] 5. Falsify: mutation 8.20 (the helper stops reading body markers) reddens `report#aFiledRowIsReported`;
  mutation 8.21 (`run.sh` ignores the marker for its baseline) reddens `run#aFilingSetsTheBaseline`. Verified:
  `evidence/mutation-8.20.log`, `evidence/mutation-8.21.log`.
- [x] 6. Whole suite scoped to the delta: `bash tools/sensor-selftest.sh` — all 53 cases passed, exit 0.
- [x] 7. Gate, once, from the change root: `./tools/loop/gate.sh` — `LOOP-GATE: tests=654 failures=0 errors=0`;
  every tally it wrote is copied into `evidence/` with the `gate-` prefix (`evidence/evidence.md`).
- [x] 8. Evidence and archive: `bash tools/loop/evidence-check.sh openspec/changes/fix-report-reads-a-filings-marker
  --root .` — PASS; `openspec validate fix-report-reads-a-filings-marker --strict` — valid; archived.
