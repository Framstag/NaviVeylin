# Design — fix-the-hotspot-bound-fits-churn

## The defect

`hotspot`'s score is a product, `size_in_bytes × times_changed` (`tools/sensors/hotspot-report.sh:26-37`), and its
bound is 5 000 with `greater-than` (`tools/sensors/sensors.json:50`). A product is passed by *either* factor, so at
5 000 the row fires on a path that changed **once** and is over 5 kB: over a 200-commit window that is nearly every
path the tree holds. Measured on the base `642c0278`, by the row's own command: **358** paths past 5 000 against
**7** past 1 000 000, with the largest tracked file at 535 814 bytes
(`openspec/changes/fix-the-hotspot-bound-fits-churn/evidence/probe-hotspot-bounds.log`). The row therefore reported
existence, not heat, and one tick wrote 337 items (issue #665).

## The fix (one)

`tools/sensors/sensors.json`'s `hotspot` row: `threshold` 5 000 → **1 000 000**, plus one clause in the registry's
own comment naming the rule the number obeys. Nothing else in the engine moves: the script takes its bound from
`SENSORS_THRESHOLD`, so the number is one edit in the registry (design D9, D10 of the sensor change), and the metric,
the fingerprint (`path`), the finding text and the clear path all keep their meaning.

Why 1 000 000 is a *fit* rather than a magnitude: it is above the largest file the row watches (535 814 bytes), so
`size × changes > 1 000 000` requires at least **two** changes of any path this tree holds — the bound is now a bound
on churn; and it is below the seventh real hotspot (1 504 404), so no path the tree actually churns is dropped. Both
halves are measured, and the case asserts the first as a property (`threshold > largest tracked file`) rather than as
a literal, so a refit stays an edit in the registry and a tree that grows a larger file reddens the suite.

The rule then lands in the two places that own it: a requirement of `backlog-sensors` (the spec) and one sentence of
`guidelines/Backlog.md` §9, which until now said only that "a file nobody touches scores nothing however large it
is" — true, and not the half that was wrong.

## Blast radius

- `tools/sensors/sensors.json` — one value, one comment clause. Every row's threshold is read by the same field
  reader (`Registry#everyRowIsComplete`, `sensors#engineNamesNoProjectValue` unchanged and green).
- `tools/sensor-cases.sh` — one case added: `hotspot#theBoundIsAboveOneChangedFile`. The existing
  `hotspot#ranksBySizeTimesChanges` passes `SENSORS_THRESHOLD=1` explicitly, so it asserts the ranking and is
  unaffected by the value.
- `tools/sensor-mutations.sh` — one record, `8.31`: the bound back at 5 000, which reddens the new case.
- `guidelines/Backlog.md` §9 and the spec delta. No engine file, no fixture registry (no fixture carries a `hotspot`
  row), and no compiled file — so no Gradle suite is owed.

## Alternatives the evidence eliminated

- **Normalise the score by the window** (`size × changes / window`). The inequality is unchanged at
  `threshold × window`: a 1 000 000 bound over 200 commits *is* 5 000 bytes churned per commit. It buys no
  discrimination, rewrites the number the finding text prints and the case that names the metric, and a retune would
  still move with history length only if the window changed — which is not the defect.
- **Scope the row to the source roots** (drop `openspec/changes/archive/**`, `.pi/**`, evidence logs). The seven paths
  past 1 000 000 are `tools/loop-cases.sh`, `openspec/specs/agent-issue-loop/spec.md`, `guidelines/Backlog.md`,
  `tools/loop-mutations.sh`, `tools/loop/run.sh`, `guidelines/Build.md` and `tools/sensor-cases.sh` — a source-root
  scope would drop most of them, so the row would stop reporting the churn it exists to report. `source-size` owns
  "this file is too big"; that is why this row owns churn only, not why it may not watch prose that churns.
- **Refuse the row (`sensors_fail_row`) when it is over its bound.** A failed row writes nothing, so 358 conditions
  would leave no trace of what was measured — exactly the reading `broken-main` and the clear path exist to prevent.

None of these is left as a fork: the measurement names which one is the fix.

## Rollback

Revert the branch. The registry returns to 5 000, the guideline sentence to its earlier half, and the case and its
mutation record disappear with them; no other file depends on the number.
