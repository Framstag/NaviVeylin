# Tasks — the row's read covers the whole tracker

## 1. The red case, on the base

- [x] 1.1 Add `case_lib_theRowReadCoversTheWholeSet` and its two dispatch entries to
      `tools/sensors/tests/sensor-cases.sh`: a 401-item fixture whose row label sits on the oldest item only, the
      condition list asserted to return it, the fingerprint lookup asserted to find its record, and a count that
      answers short asserted to refuse. The fixture's own item is dated `2026-01-16` and its 400 fillers
      `2026-09-21`, so the read's window — not the fixture's own order — is what excludes the item the row owns.
      Verify: `bash tools/sensors/tests/sensor-selftest.sh --case 'lib#theRowReadCoversTheWholeSet'` fails on the base
      at the condition-list assertion, the read having answered an empty set (`evidence/red-on-base.log`).
- [x] 1.2 Copy the red run's log and its tally into `evidence/` as `red-on-base.log` and `red-on-base.xml`
      (`tests="1" failures="1"`).

## 2. The read

- [x] 2.1 `tools/sensors/lib.sh` — `sensors_issues_of_row` reads through `issues_set_window all` and
      `issues_set_covered "$((window - 1))" "$window" "$(… | jq 'length')"`, the shape `tools/issues/lib.sh` carries
      for every deciding reader. The label filter and the requested fields stay unchanged — `createdAt` among them,
      because one write produces the issue and its marker (#668).
      Verify: 1.1's case passes, and the row's own read over the whole-set fixture returns one item.
- [x] 2.2 `guidelines/BacklogTooling.md` §9 states that a check's own read is the tracker's whole set and that a fixed
      window narrows it and is refused when it fills.
      Verify: the section's rule names the row's read, and `tools/loop/evidence-check.sh` resolves the route id.

## 3. Evidence, archive, gate

- [x] 3.1 Run the engine's own suite — `bash tools/sensors/tests/sensor-selftest.sh` — and copy its log and tally into
      `evidence/` as `green-suite.log` and `green-on-change.xml` (`tests="100" failures="0"`).
- [x] 3.2 Run `bash tools/loop/evidence-check.sh <this change's directory> --root .` from the change root and fix what
      it reports (`evidence/evidence-check.log`).
- [x] 3.3 `openspec archive --yes` this change, with the delta it merges.
- [x] 3.4 `./tools/loop/gate.sh` run once, detached; its `LOOP-GATE:` line is the record.

LOOP-ROUTE: guidelines/BacklogTooling.md §9
