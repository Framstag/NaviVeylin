# Evidence — fix-report-reads-a-filings-marker (issue #668)

All artifacts here were produced in this tree on 2026-10-26 (UTC). The base commit is `a0b2a92f`.

## Red on the base

The two cases fail on the base commit for the reason the item names — the report's `last=never` line for a row
whose only marker is in a filing's body, and a comparison that quotes no baseline where the filing carries one.

- `red-on-head-report.log` — `bash tools/sensor-selftest.sh --case 'report#aFiledRowIsReported'` on the base:
  `row=fixture-emit last=never verdict=- measurement=- issue=-` plus `stale row=fixture-emit no measurement on
  record — the row has never run or never filed`; exit 1.
- `red-on-head-run.log` — `bash tools/sensor-selftest.sh --case 'run#aFilingSetsTheBaseline'` on the base:
  `build-duration: … verdict=clear measurement=542000 issue=121` where the filing's marker carries
  `measurement=100000` and the row's comparison is `baseline-multiple 1.5`; exit 1.
- `red-on-head.xml` — both red cases as one tally: `tests="2" failures="2" errors="0"`, taken
  `2026-10-26T13:55:42Z`.

## Green after the fix

- `focused-sensor-selftest.log` — `bash tools/sensor-selftest.sh`, taken `2026-10-26T13:57:10Z`:
  `sensor-selftest: all 53 cases passed (JDK 27, offline, no token)`, exit 0. The two new cases are among them;
  the three report and baseline cases the delta touches (`report#printsACountingLinePerRow`,
  `report#aGreenRowIsNotNever`, `sensors#buildDurationRow`) are among them too and were not rewritten.

## Falsification (`revert-check`)

- `mutation-8.20.log` — `bash tools/sensor-mutations.sh --only 8.20` (the helper stops looking at the issue
  body): `report#aFiledRowIsReported` red under the mutation, green after the restore.
- `mutation-8.21.log` — `bash tools/sensor-mutations.sh --only 8.21` (`sensors_run_row` ignores the marker for
  its baseline): `run#aFilingSetsTheBaseline` red under the mutation, green after the restore.

## Measurement block

- `measure.log` — `./tools/loop/measure.sh` from the change root, with the declaration of the paths this change
  touches and the checks they owe.

## The gate, run once

`./tools/loop/gate.sh` from the change root on `2026-10-26`, exit 0 (its last suite logged
`2026-10-26T14:14:44Z`):

```
LOOP-GATE: tests=654 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 239s, tools/loop-selftest.sh --from-git 6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 365s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 226s, tools/sensor-selftest.sh 42s, tools/sensor-mutations.sh --list 0s, tools/check-portability-selftest.sh 22s, tools/check-compose-sandbox-rule.sh 1s, tools/check-compose-sandbox-rule-selftest.sh 0s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 56s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The sensor suite's own tally is `gate-tools_sensor-selftest.sh.xml`: `tests="53" failures="0" errors="0"`, its
`system-out` carrying `all 53 cases passed`. Every tally the gate wrote is copied into this directory with the
`gate-` prefix.

## Evidence-check

`bash tools/loop/evidence-check.sh openspec/changes/fix-report-reads-a-filings-marker --root .` — `PASS`: 0 dangling
`§`, 1 tally pair confirmed against `red-on-head.xml`, no `/tmp` pointer, no surviving mutation marker, every
cited `file:line` in range.
