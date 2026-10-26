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

## Red on the round-1 tree (the review's blocking finding)

The helper's first version took each marker's `fingerprint` from `$issue.body`, so every marker of one issue got
one identity and the report's `previous` — which `tools/sensors/report.sh:53` selects by that field — became "the
neighbouring marker on that issue". A record issue names no `fingerprint=` in its body while each clear posted on
it carries the cleared condition's own, so the trend was drawn across conditions. Case `report#aTrendIsBetweenOneCondition`
(`tools/sensor-cases.sh:960`) is that record: two conditions' clears, `trend=-` expected.

- `red-on-correction.log` — `bash tools/sensor-selftest.sh --case 'report#aTrendIsBetweenOneCondition'` on the
  round-1 tree: `verdict=clear measurement=512 … trend=better`, where the shipped report printed `trend=-`; exit 1.
- `red-on-correction.xml` — the same run as one tally: `tests="1" failures="1" errors="0"`, taken
  `2026-10-26T14:19:29Z`.

## Green after the fix and the correction

- `focused-sensor-selftest.log` — `bash tools/sensor-selftest.sh`: 54 `PASS` lines and
  `sensor-selftest: all 54 cases passed (JDK 27, offline, no token)`, exit 0. The three new cases are among them,
  and so are the cases the delta touches without rewriting (`report#printsACountingLinePerRow`,
  `report#aGreenRowIsNotNever`, `report#aFiledRowIsReported`, `run#aFilingSetsTheBaseline`,
  `sensors#buildDurationRow`).
- The corrected field is `tools/sensors/lib.sh:508` — `fingerprint: ((((.marker // "") | [scan("fingerprint=[^ ]+")] | .[0]) …`.

## Falsification (`revert-check`)

- `mutation-8.20.log` — `bash tools/sensor-mutations.sh --only 8.20` (the helper stops looking at the issue
  body): `report#aFiledRowIsReported` red under the mutation, green after the restore.
- `mutation-8.21.log` — `bash tools/sensor-mutations.sh --only 8.21` (`sensors_run_row` ignores the marker for
  its baseline): `run#aFilingSetsTheBaseline` red under the mutation, green after the restore.
- `mutation-8.22.log` — `bash tools/sensor-mutations.sh --only 8.22` (the fingerprint comes from `$issue.body`
  again, `tools/sensor-mutations.sh:51`): `report#aTrendIsBetweenOneCondition` red under the mutation, green
  after the restore. Each log is the harness's own summary line; the harness applies the mutation, runs the named
  case, restores the file and runs it again, and fails when either half does not behave, so the red half is
  attested by the harness rather than quoted in the log.

## Measurement block

- `measure.log` — `./tools/loop/measure.sh` from the change root, with the declaration of the paths this change
  touches and the checks they owe.

## The gate, run once per round

`./tools/loop/gate.sh` from the change root, exit 0, after the correction (started `2026-10-26T14:21:02Z`):

```
LOOP-GATE: tests=655 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 242s, tools/loop-selftest.sh --from-git 6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 376s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 226s, tools/sensor-selftest.sh 42s, tools/sensor-mutations.sh --list 1s, tools/check-portability-selftest.sh 22s, …)
```

The gate writes one JUnit tally per step into `app/build/test-results/loop-gate/`. Every one of those tallies is
copied into this directory with a `gate-` prefix (25 files), and `gate-summary.xml` is the aggregate **this
evidence** assembles from them — the gate itself writes no summary. The sensor step's own tally is
`gate-tools_sensor-selftest.sh.xml`: `tests="54" failures="0" errors="0"`, its `system-out` carrying
`all 54 cases passed`. The 25 tallies sum to 655 tests, which is the number the `LOOP-GATE:` line prints.

## Evidence-check

`bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-26-fix-report-reads-a-filings-marker --root .`
— see the round-2 worker report for its verdict; it checks every `§`, every quoted `tests=`/`failures=` pair,
every `/tmp` pointer, every surviving mutation marker and every `file:line`.
