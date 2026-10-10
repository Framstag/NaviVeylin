# Proposal — fix-shipped-spec-classification

## Why

Root cause: six spec ids are present in `openspec/specs/` but absent from `tools/feature-list/specs.json` —
the classification file was not updated when those capabilities shipped (`TODO.md` §161 recorded two of them
on 2026-10-09; the 2026-10-09 `fix-highlight-longest-run` archive (commit `3d3d99d`) added
`highlight-measurement`, and the 2026-10-10 land of the parallel workstream added `render-projection-dpi`,
`route-calculation-feedback` and `starred-ordering` the same way) — so the
classification gate reports every one of them as unclassified and exits 1.

Evidence:    `com.naviveylin.featurelist.ShippedSpecClassificationTest#every shipped spec id is classified`
fails on HEAD — XML `tests="1" failures="1"` (`expected:<[]> but was:<[highlight-measurement,
map-repository-source, map-source-selection, render-projection-dpi, route-calculation-feedback,
starred-ordering]>`), 2026-10-10T07:05:30.400Z; retained as
`evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml`.
Repro:       `bash tools/gen-feature-list.sh --check-classification` → exit 1,
`unclassified shipped spec id(s): highlight-measurement, map-repository-source, map-source-selection,
render-projection-dpi, route-calculation-feedback, starred-ordering`, 2026-10-10T07:02:13Z
(`specs read: 163 / classified: 157 / unclassified: 6 / stale: 0`).
Spec:        `spec-feature-index` / An unclassified spec id stops the run   Guideline:
`guidelines/FeatureList.md` §4 (the classification is the only human judgement, and an unclassified spec
stops the run).

## What Changes

- **`tools/feature-list/specs.json`** gains the six missing classification entries, each following §4's two
  tests (the classification is `area` + `userVisible` + `surfaces`; a `userVisible: true` spec needs a
  renderable surface):
  - `highlight-measurement` — `build-tooling`, internal, no surface (a device-free measurement harness; the
    `project-metrics-report` / `documentation-ownership` precedent).
  - `map-repository-source` — `offline-maps`, internal, phone (the repository backend that `map-download-ui`
    phrases; the `map-download-infrastructure` precedent).
  - `map-source-selection` — `offline-maps`, user-visible, phone (it gives the user a choice of source; the
    `map-download-ui` precedent).
  - `render-projection-dpi` — `map-appearance`, internal, phone and car (it names both surfaces' frames; the
    `render-performance` precedent).
  - `route-calculation-feedback` — `routing`, user-visible, phone and car (progress is an indication the user
    perceives on both; the `navigation-state-display` precedent).
  - `starred-ordering` — `favorites`, user-visible, phone (it gives the user control of the order; the
    `fav-ordering` precedent).
- **`app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt`** (new): the conformance
  case that holds the repository's own shipped set to the requirement — every spec id in `openspec/specs/`
  must be named by `tools/feature-list/specs.json`. It fails on HEAD and stays as the guard against the
  next unclassified ship.
- **No change to the tool's behaviour or its documented asymmetry**: an unclassified id fails the run, a
  stale entry is reported without failing it (`spec-feature-index` — "An unclassified spec id stops the
  run", scenario "A removed spec id is reported without failing on it"). §161's title calls the two
  directions asymmetric; the measurement in `design.md` shows that asymmetry is intended, spec'd and
  covered by `tools/gen-feature-list-selftest.sh`, so this change does not touch it.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none — the fix restores conformance to `spec-feature-index`'s existing "An unclassified spec id stops the
run" requirement by completing the classification data; no requirement text changes, so this change declares
`skip_specs: true`)

## Impact

Affected files:

- `tools/feature-list/specs.json` — six added entries (data only).
- `app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt` — new test class.
- `TODO.md` §161 — `status: open` → `status: fixed-by fix-shipped-spec-classification` (bookkeeping).

Blast radius: only the classification gate and the documents built on it read `specs.json`. The added
entries change the gate's result from exit 1 to exit 0 (`specs read: 163 / classified: 163 / unclassified: 0
/ stale: 0`) and add the six specs to the shipped-set index; no area, heading or order is added, and no
existing entry changes. Rollback: revert the two files — the data change and its guard revert together.

The process half (`TODO.md` §157 — nothing forces a change to classify the capability spec it archives) is
deliberately left open: it needs an archive-time step, which is a different change.
