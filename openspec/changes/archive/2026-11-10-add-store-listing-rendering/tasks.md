# Tasks — add-store-listing-rendering

Parent spec: `specs/store-listing-rendering/spec.md` (every task below names the requirement it serves).

## 1. The renderer, and the red case first

- [x] 1.1 Write section 10 of `tools/tests/gen-feature-list-selftest.sh` — its own fixture (six changed
      capabilities across two areas, one of them off the shortlist) and the 11 cases the delta's scenarios name.
      *Verification:* `bash tools/tests/gen-feature-list-selftest.sh` against the base commit's tool —
      `9 of 130 cases failed`, retained as `evidence/red-on-HEAD.xml` (`tests="130" failures="9" errors="0"`,
      `timestamp="2026-11-09T23:22:06Z"`).
      Requirement: every requirement of the delta; the trim case is the item's red case.
- [x] 1.2 Add `tools/feature-list/listing.awk`: parse the entry's bullets, rank them by the shortlist, place whole
      bullets against the budget, reserve the summary line, and write `shown`/`bullets`/`chars` for the tool.
      *Verification:* `bash /home/tim/projects/NaviVeylin/.pi/issue-loop/items/100/scratch/probe-fixture.sh` prints a
      block whose lines are the entry's bullets and whose last line is the summary at 4 of 6 changes
      (`evidence/real-run-probe.log` is the same probe over the real tree: `443 chars, budget 500, 5 of 229 changes
      shown`).
      Requirement: "The store listing is derived from the version entry", "An over-budget entry is trimmed by whole
      bullets and summarised".
- [x] 1.3 Declare `store_budget=500` and `store_summary` in `tools/gen-feature-list.sh`, with the figure's provenance
      beside the constant, and check a `storeShare` declared in `areas.json` against it before anything is written.
      *Verification:* `sed -n 's/^store_budget=//p' tools/gen-feature-list.sh` prints one line; the per-area case
      exits 1 naming `600` and `500`.
      Requirement: "A block fits the store field's character budget", "A per-area share composes to the entry's
      budget".
- [x] 1.4 Factor the note's versioned reassembly into `write_versioned_doc` and write the listing through it, with
      the rank file from `highlights.json` and the report line (`chars`, `budget`, `N of M changes shown`).
      *Verification:* the suite's `notes: re-running a version replaces its entry byte for byte` stays green, so the
      note's bytes did not move.
      Requirement: "The store listing is derived from the version entry".
- [x] 1.5 Classify the new capability in `tools/feature-list/specs.json` (`build-tooling`, internal), because the
      classification gate reads an open change's delta specs.
      *Verification:* `bash tools/gen-feature-list.sh --check-classification` reports `unclassified spec ids: 0`.
      Requirement: `guidelines/FeatureList.md` §4 "a capability is classified in the change that writes its delta".

## 2. The documents

- [x] 2.1 `guidelines/FeatureList.md` §1: the heading becomes "What the three documents are", the first line names
      all three, and the listing's bullet plus the rule "the store listing is the entry inside one field" carries the
      cases that enforce it; §9's never-edit line names the third document.
      *Verification:* `./tools/check-doc-routes.sh` and `./tools/check-rule-routes.sh` (the rule name is in
      `tools/doc-routes.json`).
      Requirement: `guidelines/FeatureList.md` §1's maintenance rule — "a rule names the case that enforces it".
- [x] 2.2 `README.md`'s document table and the `AGENTS.md` routing row name the third document, and the row's prices
      are re-measured (`./tools/check-doc-routes.sh --sizes`).
      *Verification:* `./tools/check-doc-routes.sh` resolves `§1 "What the three documents are"`; the row's figures
      equal the `--sizes` output for §1, §3, §4, §6 and §8.
      Requirement: `documentation-ownership`, "A routing row's cost is the measurement it claims".
- [x] 2.3 `tools/issues/citations.json` lists `STORE-LISTING.md` among the generated artefacts.
      *Verification:* `tools/look.sh 'STORE-LISTING' --path tools/issues/citations.json` names the line, and the
      issue-tooling suite (gate step for `tools/issues/*`) reads the file.
      Requirement: `stale-citation-resolution`, "an artefact the project produces is not missing".

## 3. The change's own record

- [x] 3.1 `evidence/red-on-HEAD.{log,xml}` (the base tool, the new cases), `evidence/after-change.{log,xml}` (the
      change applied) and `evidence/structural-checks.log` (the documentation checks and the classification gate).
      *Verification:* each file exists in `openspec/changes/add-store-listing-rendering/evidence/` and its XML's
      `tests=`/`failures=` match the log's summary line.
      Requirement: `agent-issue-loop`, "A green claim has a test result in the tree".
