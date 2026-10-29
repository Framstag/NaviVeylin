# Tasks — fix-nothing-compares-a-tombstone-s-own-retir

## 1. The red case (first, before any production code)

- [x] 1.1 `check#aDisplacedSettledStateIsReported` in `tools/issues/check-metadata-selftest.sh`, over the new
      fixture `tools/issues/fixtures/metadata/tombstone-displaced.json` (#41: the record and `status/on-hold`;
      #42: no record and `status/open`), registered in `tools/issue-tooling-cases.sh`.
      *Verify*: `bash tools/issues/check-metadata-selftest.sh --case check#aDisplacedSettledStateIsReported`
      FAILs on HEAD at "a tombstone whose state is not settled exits non-zero: expected [1] got [0]"; recorded as
      `evidence/settled-record-red.xml` (`tests="1" failures="1" errors="0"`, 2026-10-28T20:17:31Z).

## 2. The predicate, in one place

- [x] 2.1 `tools/issues/lib.sh`: `issues_records_settled_state <body> <marker>`, and
      `tools/issues/resolve-stale.sh`'s `records_settled_state` delegates to it.
      *Verify*: `bash tools/resolve-stale-selftest.sh` — every case green, including
      `resolver#parkedTombstoneKeepsItsSettledState` and `resolver#cleanParkedItemIsReopened`.

## 3. The fix

- [x] 3.1 `tools/issues/check-metadata.sh`: resolve `roles.settledRecordMarker`, `roles.settledStatuses` and
      `issues_status_prefix` once; report `settled-record-displaced` for an item whose body carries the record and
      whose state is not settled; the header comment names the fifth fact.
      *Verify*: `bash tools/issues/check-metadata-selftest.sh` all cases green, and
      `bash tools/issues/check-metadata.sh` (self mode, no token) unchanged at `0 violation(s)`.
- [x] 3.2 `tools/sensors/metrics/metadata-invariant.sh`: the claim and clear strings name the item's record and
      its label, not the priority pair.
      *Verify*: `bash tools/sensor-selftest.sh --case sensors#metadataDriftRow` and
      `sensors#metadataDriftClearsUnderItsOwnIdentity` green, and `bash tools/check-portability.sh` green (the
      strings name no vocabulary value).
- [x] 3.3 `guidelines/Backlog.md` §9's `metadata-invariant` paragraph states the invariants the row re-reads.
      *Verify*: `bash tools/check-doc-routes.sh` and `bash tools/check-todo-references.sh` green.

## 4. The revert-check

- [x] 4.1 `tools/issues/metadata-mutations.sh`: mutation `settled-record-not-checked` removes the invariant and
      the probe is `check#aDisplacedSettledStateIsReported`.
      *Verify*: `bash tools/issues/metadata-mutations.sh --only settled-record-not-checked` reports the mutation
      noticed and the probe quiet after the restore; transcript copied to `evidence/revert-checks.md`.

## 5. The gate, once

- [x] 5.1 `bash tools/check-portability.sh` and `bash tools/check-portability-selftest.sh` green before the gate,
      so a project value leaked into the engine is found here rather than by the pipeline.
- [x] 5.2 `./tools/loop/gate.sh`, run once from the change root; its `LOOP-GATE` line and the per-suite tallies
      are copied into `evidence/gate.md`. No compiled file is touched, so the shell suites its pathspec names are
      the whole gate and no build is owed.

## 6. Evidence and archive

- [x] 6.1 Copy every cited XML into `<change>/evidence/` and into the item's evidence directory; write
      `evidence/measure.txt` from the one measurement of this change's own delta.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-nothing-compares-a-tombstone-s-own-retir --root .`
      reports no dangling pointer; re-run on the archived directory, its transcript is
      `evidence/evidence-check-archived.log` (`evidence-check: PASS`).
- [x] 6.3 `openspec archive` the change; confirm the directory exists under `openspec/changes/archive/` and both
      deltas merged into their capability specs (`issue-backlog`, `backlog-sensors`).
- [x] 6.4 Every scenario in both deltas names one case, and each of those cases is green in the recorded run
      (`LOOP-MEASURE-CHANGE … scenarios=5 cases=5 unnamed=0`); round 1 found the `backlog-sensors` scenario widened
      past the input its case supplies, and that scenario was narrowed back to the pair —
      `evidence/review-round-1-corrections.md`.
