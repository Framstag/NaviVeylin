# Proposal — fix-routing-failure-reporting-classification

## Why

Root cause: `tools/feature-list/specs.json` never gained a classification line for
`routing-failure-reporting`, the capability the archived change `distinguish-routing-failures` shipped into
`openspec/specs/` (its spec directory is committed as `97fb38a`). `guidelines/FeatureList.md` §9 owes that line
to the same change that archives the spec — "Archiving a capability spec: add its classification line
(`specs.json`) in that same change" — and it was not paid, so the classification gate names the id and exits 1,
and every `:app` mobile gate run in this tree is red at the verdict line.

This is the **third recorded instance** of `TODO.md` §157 ("Nothing forces a change to classify the capability
specs it archives — the first unclassified spec fails a later run instead"): §157's own text predicted it, the
archived `fix-routing-max-speed-classification` was the second, this is the third, and §157 named the enforcement
point that would have caught all three — "a model-free CI check (the tool already has
`--check-classification`, which exits 1 and names the id) run on every pull request". This change closes this
instance **and lands that CI half**, so a fourth occurrence fails at the pull request that causes it instead of
in a later session's gate.

The Phase A evidence block, verbatim:

```
Root cause: `tools/feature-list/specs.json` names no entry for `openspec/specs/routing-failure-reporting/`,
             the capability the archived change `distinguish-routing-failures` shipped (the file carries 166
             `specs` keys and `openspec/specs/` holds 167 `spec.md` files at HEAD `97fb38a`).
Evidence:    com.naviveylin.featurelist.ShippedSpecClassificationTest#every shipped spec id is classified
             fails on HEAD — XML tests="1" failures="1" (expected:<[]> but was:<[routing-failure-reporting]>),
             2026-10-15T17:42:05.964Z
Repro:       bash tools/gen-feature-list.sh --check-classification → exit 1, "unclassified shipped spec
             id(s): routing-failure-reporting" (specs read: 167 / classified: 166 / unclassified: 1 /
             stale: 0), 2026-10-15T17:41Z
Spec:        spec-feature-index / An unclassified spec id stops the run
             Guideline: guidelines/FeatureList.md §4, §9; TODO.md §157
```

Retained as `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml` (the JUnit XML the case name and the tally
come from) and `evidence/classification-gate-red-on-HEAD.txt` (the gate's own exit-1 run — the `file:line`
claim, the `tests=/failures=` pair and the repro all live in these two files).

The defect contradicts `spec-feature-index`'s existing requirement "An unclassified spec id stops the run",
scenario "A classified spec id passes": that scenario's WHEN holds only when every shipped spec id is named by
the classification, and on HEAD one is not. Exactly one data fix follows — add the missing line — and no
spec-level behaviour changes: the requirement already states, and the shipped tool already performs, exactly
the behaviour the data now meets. The change therefore declares `skip_specs: true` in `.openspec.yaml`, the same
shape the archived `2026-10-10-fix-shipped-spec-classification` and `2026-10-13-fix-routing-max-speed-classification`
took for the same class of gap.

## What Changes

- **`tools/feature-list/specs.json`** gains one classification entry:

  ```json
  "routing-failure-reporting": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] },
  ```

  with the reasoning for each of the three values in `design.md` (D1). The entry restores the classification to
  one key per shipped spec (`specs read: 167 / classified: 167 / unclassified: 0 / stale: 0`).
- **`.github/workflows/build.yml`** gains a model-free step that runs
  `bash tools/gen-feature-list.sh --check-classification` — the enforcement point `TODO.md` §157 names as its
  first fix candidate, so an unclassified capability fails on the pull request that causes it. The check is
  cheap (no Gradle, no model, two files read) and is green on this tree after the line above (D3).
- **`TODO.md` §157** gains an **Instance, 2026-10-15** bullet naming this third occurrence and recording that its
  first fix candidate (the CI check) is now landed; §157 stays `open` because its second candidate — making the
  archive step itself run the check before it moves `changeRoot` — is a change to the archiving workflow, which
  this change does not modify.
- **Nothing else.** No highlight is added to `tools/feature-list/highlights.json`, so no feature area becomes
  dirty, no prose is regenerated and `FEATURES.md` is untouched: publishing the capability in the catalogue is
  the separate editorial judgement §9 names ("when what it ships is worth telling a user about"), and the
  coverage gate measures highlights, not specs, so the classification line alone cannot leave a red run behind
  (D2). The tool's documented asymmetry — an unclassified id fails the run, a stale entry is reported without
  failing it (`spec-feature-index`, "A removed spec id is reported without failing on it") — is correct and
  untouched.

## Capabilities

### New Capabilities

(none — `routing-failure-reporting` already ships as a capability in
`openspec/specs/routing-failure-reporting/`; this change introduces no requirement)

### Modified Capabilities

(none — no requirement text changes. The change brings the repository's classification data into conformance
with `spec-feature-index`'s existing "An unclassified spec id stops the run" requirement, so it declares
`skip_specs: true` in `.openspec.yaml` rather than inventing a requirement delta.)

## Impact

Affected files:

- `tools/feature-list/specs.json` — one added entry (data only; not a Gradle input, not compiled).
- `.github/workflows/build.yml` — one added step (CI configuration; not compiled).
- `TODO.md` §157 — bookkeeping (one `**Instance**` bullet; the entry stays `open`).

Blast radius: only the classification gate and the documents built on it read `specs.json`. The added entry
flips the gate from exit 1 to exit 0, adds `routing-failure-reporting` to the shipped-set index of the routing
area, and changes no existing entry, no area, no heading and no order. The CI step is additive and green on
this tree's data.

Verification shape: nothing compiled changes, so no build and no suite is owed (`guidelines/Build.md` §2,
"Scope — what the changed files owe"); the structural checks are `jq` on the edited file, the classification
gate's exit status before and after, the CI YAML's parse, and the guard case `ShippedSpecClassificationTest`
re-run forced in `:app` (the case the red XML belongs to). Rollback: revert the three files — the data line and
its bookkeeping revert together, and the gate returns to its HEAD red; there is no migration, no state and no
API.

The change is **additive** (one data line, one CI step) and breaks nothing: an existing caller of the
classification reads one more entry.
