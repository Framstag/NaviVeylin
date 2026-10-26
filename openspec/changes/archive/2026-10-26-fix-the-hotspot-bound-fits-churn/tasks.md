# Tasks — fix-the-hotspot-bound-fits-churn

## 1. The red test, before the fix

- [x] 1.1 `hotspot#theBoundIsAboveOneChangedFile` in `SENSOR_CASES` and the dispatch table —
  `tools/sensor-cases.sh`. Red at the base bound: `a path changed once is not a hotspot, whatever its size:
  '"path":"once.txt"' unexpectedly present in: hotspot: score=1400378 path=churned.txt size=200054 changes=7 /
  hotspot: score=6000 path=once.txt size=6000 changes=1`; tally
  `evidence/red-on-head-hotspot#theBoundIsAboveOneChangedFile.xml` (`tests="1" failures="1"`), log
  `evidence/red-on-head.log` (2026-10-26T22:40:03Z). The run is the change's final case over the base's bound
  (5 000, i.e. mutation `8.31`): the case's first version ran on the base commit itself at 22:39:01Z and failed on
  the same assertion, then its fit half was reworked (its `wc` ran from the fixture directory, not from the tree) and
  re-taken — the log says both.
- [x] 1.2 The case asserts the property, not the number: it reads the row's bound from the registry and requires it
  to be above the largest tracked file of the tree the row watches, so a refit stays one edit in the registry.
- [x] 1.3 The measurement the fit rests on — the row's own command over this tree, `threshold=5000 violations=358`,
  `threshold=1000000 violations=7`, largest tracked file `535814 tools/loop-cases.sh`:
  `evidence/probe-hotspot-bounds.log` (2026-10-26T22:38:19Z).

## 2. The bound, in the registry

- [x] 2.1 `tools/sensors/sensors.json` — the `hotspot` row's `threshold` 5 000 → `1000000`, and the registry's own
  comment states the rule the number obeys (a churn row's bound sits above the largest file it watches). Verified by
  `hotspot#theBoundIsAboveOneChangedFile` green and by `Registry#everyRowIsComplete` — the whole suite:
  `sensor-selftest: all 65 cases passed (JDK 27, offline, no token)` (`evidence/focused-existing-cases.log`).
- [x] 2.2 No engine file changed: `hotspot-report.sh` reads `SENSORS_THRESHOLD` (its own case asserts it holds no
  threshold constant), so the metric, the fingerprint and the clear path keep their meaning.

## 3. The documents

- [x] 3.1 `guidelines/Backlog.md` §9 "Checks" — the `hotspot` sentence gains the fit, its measurement, and the old
  bound's cost (358 paths past it, 337 items in one tick). Verified by `tools/check-doc-routes.sh` and
  `tools/check-todo-references.sh` in the gate.
- [x] 3.2 `openspec/changes/fix-the-hotspot-bound-fits-churn/specs/backlog-sensors/spec.md` — one added requirement,
  one scenario naming exactly one case. Verified by `openspec validate fix-the-hotspot-bound-fits-churn --strict` →
  `Change 'fix-the-hotspot-bound-fits-churn' is valid`.

## 4. Falsification

- [x] 4.1 Mutation `8.31` — the row's bound back at 5 000: reddens `hotspot#theBoundIsAboveOneChangedFile`, restore
  green — `evidence/mutation-8.31.log` (`1 behaved, 0 did not`).
- [x] 4.2 The whole mutation set, not only the new record: `bash tools/sensor-mutations.sh` — `31 behaved, 0 did
  not` in 2m09s (`evidence/mutations.log`).
- [x] 4.3 `bash tools/sensor-mutations.sh --list` lists `8.31` against `tools/sensors/sensors.json` (inside the gate).

## 5. The gate, once

- [x] 5.1 `./tools/loop/gate.sh`, run once from the change root, exactly as the run's configuration states it — no
  compiled path changed, so the plan is the shell suites alone: 686 tests, 0 failures and 0 errors over 29 suites
  (the gate's own summary line, `evidence/gate.log`), 397s of them the loop's own self-test. The sensor suite's own
  tally is
  `tests="65" failures="0"` (`evidence/gate-tallies/tools_sensor-selftest.sh.xml`), and one tally per suite sits
  beside it.

## 6. The evidence and the archive

- [x] 6.1 Every cited run is copied into `evidence/` — the red tally and its log, the probe, the surface counts, the
  focused green log, both mutation logs, the gate log and its tallies — and a copy of each report in the run's item
  directory.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-hotspot-bound-fits-churn --root .` — every
  `file:line`, `tests=`/`failures=` pair and `§N` resolves.
- [x] 6.3 Archive the change: `openspec archive fix-the-hotspot-bound-fits-churn`, and confirm the delta's
  requirement merged into `openspec/specs/backlog-sensors/spec.md`.
