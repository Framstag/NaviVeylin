# Tasks — fix-parked-tombstone-keeps-its-settled-state

## 1. The red case (first, before any production code)

- [x] 1.1 `resolver#parkedTombstoneKeepsItsSettledState` in `tools/resolve-stale-cases.sh`: a tracker item parked
      the way this tool parked one (on-hold, `agent/excluded-stale`, the parking comment) whose body carries the
      migration's tombstone record, run through `resolve-stale.sh --apply --revisit-parked`.
      *Verify*: `bash tools/resolve-stale-selftest.sh --case resolver#parkedTombstoneKeepsItsSettledState` FAILs
      on HEAD at "a tombstone is not reopened into the queue: expected '0', got '1'"; the run is recorded as
      `evidence/parked-tombstone-red.xml` (`tests="1" failures="1" errors="0"`, 2026-10-27T20:39:14Z).
- [x] 1.2 `resolver#tombstoneRestoreNeedsASettledState` in the same file: a profile whose `retiredStatus` is
      not inside `settledStatuses`, asked for the reversal.
      *Verify*: red on HEAD (HEAD writes `status/open` and exits 0, where the case requires exit 2 and no write).

## 2. The declaration

- [x] 2.1 `tools/tooling.json`: `roles.settledRecordMarker` = the migration's provenance line, and the `roles`
      comment gains the role it now declares and names `resolve-stale.sh` as its reader.
      *Verify*: `jq -e '.roles.settledRecordMarker' tools/tooling.json` prints the value; the file still parses
      (`jq . tools/tooling.json >/dev/null`).
- [x] 2.2 `tools/fixtures/foreign-project/tools/tooling.json`: the same key declared empty, with its comment's
      "declared empty, never omitted" sentence extended.
      *Verify*: `jq -e '.roles | has("settledRecordMarker")' tools/fixtures/foreign-project/tools/tooling.json`.

## 3. The fix

- [x] 3.1 `tools/issues/resolve-stale.sh`: read `roles.settledRecordMarker`, `roles.retiredStatus` and
      `roles.settledStatuses`; refuse a profile whose marker is declared beside a `retiredStatus` outside
      `settledStatuses` (before the first write, through `issues_die`); reverse a body carrying the marker to
      `retiredStatus` and count it `restored` beside `reopened` (item record, summary line and `--json`).
      *Verify*: `bash tools/resolve-stale-selftest.sh` — every case green, including the three the delta names.
- [x] 3.2 `guidelines/Backlog.md`'s resolver paragraph states that a reversal restores the state a
      tombstone's own record keeps.
      *Verify*: `bash tools/check-doc-routes.sh` and `bash tools/check-todo-references.sh` green (the section
      names no new path).

## 4. The revert-check

- [x] 4.1 `tools/resolve-stale-mutations.json` mutation `5.17`: the marker guard replaced so that every reversal
      restores the settled state; `resolver#cleanParkedItemIsReopened` must go red.
      *Verify*: `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.17` reports
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`; transcript copied to
      `evidence/revert-checks.md`.

## 5. The gate, once

- [x] 5.1 `bash tools/check-portability.sh` (the marker is in the profile, not in an engine) and
      `bash tools/check-portability-selftest.sh` green before the gate, so a portability leak is found here
      rather than by the pipeline.
- [x] 5.2 `./tools/loop/gate.sh`, run once from the change root; its line is quoted into `evidence/gate.md` and
      the per-suite tallies are copied beside it. No compiled file is touched (`tools/**`, `guidelines/**`,
      `openspec/**` — `guidelines/Build.md` §2 scope block), so the structural checks stand in for a build and
      the gate's own path map decides whether the Kotlin suite is owed.

## 6. Evidence and archive

- [x] 6.1 Copy every cited XML into `<change>/evidence/` and into the item's evidence directory; write
      `evidence/measure.txt` from the one measurement of this change's own delta.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-parked-tombstone-keeps-its-settled-state --root .`
      reports no dangling pointer.
- [x] 6.3 `openspec archive` the change; confirm the directory exists under `openspec/changes/archive/` and the
      delta merged into `openspec/specs/stale-citation-resolution/spec.md`.
- [x] 6.4 The delta's every scenario names one case, and each of those cases is green in the recorded run — the
      archive's precondition (`openspec/config.yaml`, the `fix-*` archive rule).
