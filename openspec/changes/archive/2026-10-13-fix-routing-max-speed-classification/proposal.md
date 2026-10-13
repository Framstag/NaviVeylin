# Proposal — fix-routing-max-speed-classification

## Why

Root cause: `tools/feature-list/specs.json` never gained a classification line for `routing-max-speed`, the
capability `be53cdd feat: vehicle-max-speed-settings` shipped into `openspec/specs/` — `guidelines/FeatureList.md`
§9 owes that line to the same change that archives the spec ("Archiving a capability spec: add its
classification line (`specs.json`) in that same change"), and it was not paid — so the classification gate
reports the id and exits 1, and every `:app` mobile gate run in this tree is red at the verdict line.

The Phase A evidence block, verbatim:

```
Root cause: `tools/feature-list/specs.json` names no entry for `openspec/specs/routing-max-speed/` (the file
             carries 165 `specs` keys and the directory 166 `spec.md` files at HEAD `fa6151c`).
Evidence:    com.naviveylin.featurelist.ShippedSpecClassificationTest#every shipped spec id is classified
             fails on HEAD — XML tests="1" failures="1" (expected:<[]> but was:<[routing-max-speed]>),
             2026-10-13T18:56:47.467Z
Repro:       bash tools/gen-feature-list.sh --check-classification → exit 1, "unclassified shipped spec
             id(s): routing-max-speed" (specs read: 166 / classified: 165 / unclassified: 1 / stale: 0),
             2026-10-13T18:58:26Z
Spec:        spec-feature-index / An unclassified spec id stops the run
             Guideline: guidelines/FeatureList.md §4, §9
```

Retained as `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml` (the JUnit XML the case name and tally
come from) and `evidence/classification-gate-red-on-HEAD.txt` (the gate's own exit-1 run — the `file:line`
claim, the `tests=/failures=` pair and the repro all live in these two files).

The defect contradicts `spec-feature-index`'s existing requirement "An unclassified spec id stops the run",
scenario "A classified spec id passes": that scenario's WHEN holds only when every shipped spec id is named by
the classification, and on HEAD one is not. Exactly one fix follows — add the missing line — and no spec-level
behaviour changes: the requirement already states, and the shipped tool already performs, exactly the
behaviour the data now meets. The change therefore declares `skip_specs: true` in `.openspec.yaml`, the same
shape the archived `2026-10-10-fix-shipped-spec-classification` took for the same class of gap.

## What Changes

- **`tools/feature-list/specs.json`** gains one classification entry:

  ```json
  "routing-max-speed": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] },
  ```

  with the reasoning for each of the three values in `design.md` (D2). The entry restores the classification
  to one key per shipped spec (`specs read: 166 / classified: 166 / unclassified: 0`).
- **`TODO.md` §189** — `status: open` → `status: fixed-by fix-routing-max-speed-classification` plus a
  `**Loop verdict**` bullet recording the repair and the eliminated alternatives.
- **Nothing else.** No highlight is added to `tools/feature-list/highlights.json`, so no feature area becomes
  dirty, no prose is regenerated and `FEATURES.md` is untouched: publishing the capability in the catalogue is
  the separate editorial judgement §9 names ("when what it ships is worth telling a user about"), and the
  coverage gate measures highlights, not specs, so the classification line alone cannot leave a red run
  behind (D3). The tool's documented asymmetry — an unclassified id fails the run, a stale entry is reported
  without failing it (`spec-feature-index`, "A removed spec id is reported without failing on it") — is
  correct and untouched.

## Capabilities

### New Capabilities

(none — `routing-max-speed` already ships as a capability in `openspec/specs/routing-max-speed/`; this change
introduces no requirement)

### Modified Capabilities

(none — no requirement text changes. The change brings the repository's classification data into conformance
with `spec-feature-index`'s existing "An unclassified spec id stops the run" requirement, so it declares
`skip_specs: true` in `.openspec.yaml` rather than inventing a requirement delta.)

## Impact

Affected files:

- `tools/feature-list/specs.json` — one added entry (data only; not a Gradle input, not compiled).
- `TODO.md` §189 — bookkeeping (`status: fixed-by`, one `**Loop verdict**` bullet).

Blast radius: only the classification gate and the documents built on it read `specs.json`. The added entry
flips the gate from exit 1 to exit 0, adds `routing-max-speed` to the shipped-set index of the routing area,
and changes no existing entry, no area, no heading and no order.

Verification shape: nothing compiled changes, so no build and no suite is owed (`guidelines/Build.md` §2,
"Scope — what the changed files owe"); the structural checks are `jq` on the edited file, the classification
gate's exit status, and the guard case `ShippedSpecClassificationTest` re-run forced in `:app` (the case the
red XML belongs to). Rollback: revert the two files — the data line and its bookkeeping revert together, and
the gate returns to its HEAD red; there is no migration, no state and no API.

The change is **additive** (one data line) and breaks nothing: an existing caller of the classification reads
one more entry.
