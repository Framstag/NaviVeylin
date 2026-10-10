# Design — fix-shipped-spec-classification

## Context

See `proposal.md` — Why. `tools/gen-feature-list.sh` derives the shipped spec ids from `openspec/specs/`
(one id per `spec.md`, nested ids included) and compares them with the keys of `tools/feature-list/specs.json`
(`:164-171`). The comparison has two directions: an id the classification does not name (`unclassified`)
sets `gate_errors` and fails the run; an id the classification names but the shipped set no longer contains
(`stale`) is reported and does **not** set `gate_errors` (`:191-218`). `guidelines/FeatureList.md` §4 states
both directions as intended, and `tools/gen-feature-list-selftest.sh` covers them
(`gate: an unclassified shipped spec id fails the run`, `gate: a stale classification entry does not fail the
run`).

Today the shipped set holds 163 ids and the classification names 157, in six missing entries.

## Goals / Non-Goals

**Goals**

- `bash tools/gen-feature-list.sh --check-classification` exits 0 with `unclassified: 0 / stale: 0`.
- A host case fails when any future shipped spec id is left unclassified, so the same residue cannot
  reappear unnoticed.

**Non-Goals**

- The archive-time step that would force a change to classify the spec it archives — that is `TODO.md` §157,
  a different change.
- Any change to the gate's two directions, its output, or its exit codes (see Decisions — eliminated).

## Decisions

**D1 — Complete the classification data; do not change the tool.** The defect is data, not behaviour: the
requirement `spec-feature-index` — "An unclassified spec id stops the run" already states the contract, and
the tool already enforces it. The fix is six entries in `tools/feature-list/specs.json`. No production code
changes.

**D2 — Classify all six ids the gate reports, not the two §161 recorded.** §161 was filed 2026-10-09 with
two unclassified ids; the 2026-10-10 land of the parallel workstream shipped four more without their
classification lines, so the same root cause now leaves six. Fixing two would leave the gate red and the
defect open. Each entry follows §4's tests and the nearest sibling already in the file:

| spec id | area | userVisible | surfaces | sibling precedent / deciding text |
|---|---|---|---|---|
| `highlight-measurement` | `build-tooling` | false | — | `project-metrics-report`, `documentation-ownership` — a device-free measurement harness |
| `map-repository-source` | `offline-maps` | false | phone | `map-download-infrastructure` — the repository backend `map-download-ui` phrases |
| `map-source-selection` | `offline-maps` | true | phone | `map-download-ui` — §4 shape B: it gives the user a choice of source |
| `render-projection-dpi` | `map-appearance` | false | phone, car | `render-performance` — internal, but it names both surfaces' frames |
| `route-calculation-feedback` | `routing` | true | phone, car | `navigation-state-display` — an indication the user perceives on both |
| `starred-ordering` | `favorites` | true | phone | `fav-ordering` — it gives the user control of the order |

**D3 — A JUnit conformance case in `:app`, not a case in the tool's self-test.** The requirement's
"classified spec id passes" scenario is a WHEN/THEN over the repository's own set. The tool's self-test
builds a synthetic fixture tree, so it cannot fail when the real `specs.json` is incomplete; extending it to
read the project would break its stated contract ("It reads no project document") and still need the real
paths threaded in. A conformance case that reads the two real artefacts — precedent
`FavAutoZoomClampRangeTest` and `MapMenuBackOrderComposeTest`, which read `openspec/` and source files —
runs in the forced both-flavor gate and its JUnit XML is the retained evidence. The case parses the
classification with `kotlinx.serialization.json` (already on the `:app` test classpath) rather than shelling
out to the script, so it needs no `bash` and no `jq` and names exactly the ids the gate would name.

**D4 — `skip_specs: true`.** No requirement text changes: the fix restores conformance to an existing
requirement. The proposal/spec instructions forbid inventing a requirement to satisfy validation; a fix that
changes data but not behaviour is exactly the "no spec-level behaviour changes" case that marker is for.

**Eliminated alternatives**

- **Make the two directions symmetric.** §161's title calls them asymmetric. Measured: the tool already
  treats them as `guidelines/FeatureList.md` §4 and `spec-feature-index` require — `stale` does not set
  `gate_errors` (`tools/gen-feature-list.sh:215-218`) and its non-failing behaviour is a passing self-test
  case. §161's "exit 1 on a stale entry" observation was confounded by the two genuinely unclassified ids
  present in the same run. There is nothing to change, and changing it would contradict the spec. Eliminated.
- **Weaken the gate so it reports instead of failing.** Contradicts the requirement. Eliminated.
- **Delete the six specs from `openspec/specs/`.** They are shipped capabilities with UI and tests behind
  them. Eliminated.
- **Mark a spec `userVisible` to avoid a future coverage burden.** §4 makes `userVisible` a property of the
  spec, not a lever; each entry above follows the deciding text or the nearest sibling, not the coverage
  gate's convenience.

## Risks / Trade-offs

- [A wrong `userVisible`/`surface` classification passes this gate but can fail a later catalogue run's
  coverage gate or silently drop a capability] → each value cites a sibling already classified in the same
  file or the spec's own deciding text (D2); a reviewer can falsify any one by naming a better sibling.
- [The conformance case reads the module-relative `..` path — the `:app` unit test working directory]
  → the same convention `FavAutoZoomClampRangeTest` and `MapMenuBackOrderComposeTest` already rely on.
- [A later change adds a spec and forgets the classification line] → this is §157's gap; the new case makes
  the omission fail the build instead of a later generation run, but does not implement the archive-time
  step.

## Migration Plan

None: data and a test. Rollback is `git revert` of the change commit — the entries and the guard revert
together. The gate returns to exit 1, which is the state on HEAD.

## Open Questions

None.
