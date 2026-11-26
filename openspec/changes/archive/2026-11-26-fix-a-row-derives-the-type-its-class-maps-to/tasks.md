# Tasks: fix-a-row-derives-the-type-its-class-maps-to

## 1. The registry's mapping, and the engine's derivation

- [x] 1.1 `tools/sensors/sensors.json`: add the root `classTypes` mapping beside `labels` and `escalation`, with the
      comment a new root key carries — `bug` → `Bug`, `improvement` → `Task`, `feature` → `Feature`. Verified by
      `jq -r '.classTypes.improvement' tools/sensors/sensors.json` printing `Task`.
- [x] 1.2 `tools/sensors/tests/fixtures/registry.json`: the same key, so the offline cases see a registry shaped
      like the shipped one. Verified by `jq -r '.classTypes.bug' tools/sensors/tests/fixtures/registry.json`.
- [x] 1.3 `tools/sensors/lib.sh`: `sensors_row_type` beside `sensors_row_field` (`:126`) — the row's own `type`
      first, its class's `classTypes` entry otherwise, empty when neither names one. Verified by the case in 2.2.
- [x] 1.4 `tools/sensors/lib.sh`: `row-type-unmapped` in `sensors_row_problems` (`:236`), beside the
      class-vocabulary refusal, naming the class the mapping does not cover.
- [x] 1.5 `guidelines/BacklogTooling.md` `## 9. Checks`: the row rule states that a row's class maps to the tracker
      type in the registry, and that a type is the account's own name, so no engine may hold it. Verified by
      `tools/check-portability.sh` (no account value in an engine) and `tools/check-doc-values.sh`.

## 2. The witness

- [x] 2.1 `tools/sensors/tests/fixtures/registry-no-class-type.json`: the new fixture, modelled on
      `registry-no-record.json` — a mapping that covers `improvement` only, so every row's own `bug` class is
      unmapped.
- [x] 2.2 `Registry#everyRowIsComplete` (`tools/sensors/tests/sensor-cases.sh:604`) gains the refusal assertion
      (`row-type-unmapped`) and the derivation assertion (`Bug|Task|Feature` for class, class, and a row's own
      override). Verified green: `evidence/green-registry-case.log` (`PASS Registry#everyRowIsComplete`, exit 0);
      red on the run's base: `evidence/red-on-head.log`.
- [x] 2.3 Falsification: mutation record `8.36` (`tools/sensors/tests/sensor-mutations.sh:65`) drops the refusal
      and reddens 2.2, then restores it — `evidence/mutation-8.36.log` (`sensor-mutations: 1 behaved, 0 did not`).
- [x] 2.4 Realign the five fixture registries the new refusal reaches — `registry-bounded.json`,
      `registry-row-bound.json`, `registry-no-record.json`, `registry-no-consecutive.json`,
      `registry-no-threshold.json` — each carrying `classTypes`, so a fixture that exists to be incomplete in one
      root entry is incomplete in that entry only. Verified by the whole suite: nine cases red before the key,
      `sensor-selftest: all 94 cases passed` after (`evidence/sensor-selftest-all-cases.log`).

## 3. The change's own gate

- [x] 3.1 Copy the evidence the change cites into `evidence/` and into the item's own directory, then
      `bash tools/loop/evidence-check.sh <this change> --root .` reports no FAIL.
- [x] 3.2 Archive the change with the delta it merges (`openspec archive --yes`), before the gate, so the tree the
      gate measures is the tree the round reads.
- [x] 3.3 Run the project's gate once (`./tools/loop/gate.sh`) and copy its XMLs.
