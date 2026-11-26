# Proposal — a row's class derives the tracker type its item carries

## Why

**Root cause.** `sensors_row_problems` (`tools/sensors/lib.sh:170`) validated a row's `class` against the profile's
label vocabulary (`tools/tooling.json:18`) and stopped there, and the registry declared no mapping from a class to the
tracker account's type names: `tools/sensors/sensors.json:9` did not exist, so the root carried `namespace`, `labels`,
`escalation`, `record` and `maxFilings` and nothing about types. A row's kind therefore reaches its item as a label
only, never as a type — the untyped state `tools/issues/check-metadata.sh` reports and the one #1021 was cut on.

**The red case on the base** `fa45813e4fd4b79a8becf27b4ecc1ce07cf1a22e`: `Registry#everyRowIsComplete`
(`tools/sensors/tests/sensor-cases.sh:576`) with one fixture (`registry-no-class-type.json`) and two assertions added —
a registry whose `classTypes` does not cover a row's class must be refused as `row-type-unmapped`, and
`sensors_row_type` must answer a row's own `type` first and its class's mapping otherwise. On the base the first
assertion prints nothing: `'row-type-unmapped' not found in:` (`evidence/red-on-head.log`). After the change the case
is green (`evidence/green-registry-case.log`) and record `8.36` reddens it by dropping the refusal
(`evidence/mutation-8.36.log`).

**Surface, counted before the plan:**

```
$ tools/look.sh 'classTypes'
LOOK: 4 matches in 4 files … tools/sensors/lib.sh · tools/sensors/sensors.json ·
      tools/sensors/tests/fixtures/registry.json · tools/sensors/tests/fixtures/registry-no-class-type.json
$ tools/look.sh 'sensors_row_type'
LOOK: 5 matches in 2 files … tools/sensors/lib.sh (2) · tools/sensors/tests/sensor-cases.sh (3)
```

so the plan's files were 2 source files, 7 fixtures, 1 case file, 1 mutation record and 1 guideline section — the
fixture count is the one the plan missed: the refusal reaches every fixture registry, and five of the seven are
registries whose rows the mapping must cover for their own cases to stay about the entry they test.

## What changes

- `tools/sensors/sensors.json` gains the root `classTypes` mapping beside `labels` and `escalation`, carrying the
  comment a new root key carries; `tools/sensors/tests/fixtures/registry.json` carries the same key, and
  `tools/sensors/tests/fixtures/registry-no-class-type.json` is the witness for the refusal.
- The five other fixture registries (`registry-bounded.json`, `registry-row-bound.json`, `registry-no-record.json`,
  `registry-no-consecutive.json`, `registry-no-threshold.json`) carry the same key: they exist to exercise one
  missing root entry each, and a registry without the mapping would redden every case that reads them with a
  `row-type-unmapped` refusal none of those cases is about.
- `tools/sensors/lib.sh` gains `sensors_row_type` (`:126`) — a row's own `type` first, its class's mapping otherwise —
  and `row-type-unmapped` in `sensors_row_problems` (`:236`, beside the class-vocabulary refusal at `:231`).
- `Registry#everyRowIsComplete` (`tools/sensors/tests/sensor-cases.sh:604`) asserts both halves; record `8.36`
  (`tools/sensors/tests/sensor-mutations.sh:65`) is the falsification.
- `guidelines/BacklogTooling.md:15` states that a row declares the tracker type its class maps to.

## Impact

The registry's own data grows by one root key; the engine names neither a class nor a type. No filing is wired to the
derivation here — the two payload children of #1021 read it, which is why this change is theirs to build on and not a
filing change itself. A row that declares no `class` is unaffected; a row whose class the mapping does not cover is
refused before any filing rather than after one.
