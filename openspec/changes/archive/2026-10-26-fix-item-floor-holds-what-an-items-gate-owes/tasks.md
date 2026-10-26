# Tasks — fix-item-floor-holds-what-an-items-gate-owes

## 1. The red case (requirement: *Both clocks a run refuses by hold what one item's gate owes*)

- [x] `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes` in `tools/loop-cases.sh`: it reads
  `LOOP_ITEM_FLOOR_MINUTES` and `LOOP_ITEM_GATE_SECONDS` from the profile, refuses a non-numeric floor and a floor
  below the gate cost, and passes a project that records no gate key at all. Verify: `bash -n tools/loop-cases.sh`,
  and red on the base's profile (`the item floor (LOOP_ITEM_FLOOR_MINUTES=45, 2700 s) is below what one item's gate
  owes (3900 s) …`, `evidence/red-on-base.log`).

## 2. The recorded value

- [x] `LOOP_ITEM_FLOOR_MINUTES=65` in `tools/loop/loop.conf`, with the measurement and the relation to
  `LOOP_ITEM_GATE_SECONDS` beside it. Verify: the case passes, and `state#floorWithNoHistoryIsTheConfiguredMinimum`
  and `state#floorIsDerivedFromTheFinishedItemsWallTimes` still pass — they read the configured value rather than
  naming it.

## 3. The invariant, one mutation per check

- [x] Record `64.2` in `tools/loop-mutations.sh`: the floor back at 45 min reddens the case. Verify:
  `bash tools/loop-mutations.sh --check --files tools/loop/loop.conf` and `--only "64.1 64.2"`
  (`evidence/focused-mutations.log`).

## 4. The guideline and the spec

- [x] `guidelines/Backlog.md`, "The loop": the floor's paragraph states the relation. Verify:
  `tools/check-doc-routes.sh` and `tools/check-portability.sh`.
- [x] The delta: one added requirement with two scenarios — the child's bound (shipped case) and the floor (this
  change). Verify: `openspec validate fix-item-floor-holds-what-an-items-gate-owes --strict`.

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root, every XML copied into `evidence/`. Verify:
  `LOOP-GATE: tests=… failures=0 errors=0`, `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .`
  passes. Note in the evidence if `run#aWriterTheRunCanPlaceBlocksTheItem` (flaky, filed as #673) reddens the run.

## 6. Archive and review

- [x] Archive the change, confirm the delta merged, then hand it to an independent read-only reviewer.
