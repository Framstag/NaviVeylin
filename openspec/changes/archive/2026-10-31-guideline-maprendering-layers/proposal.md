# `MapRendering.md` becomes four rendering documents, and its duplicated §14 is resolved

## Why

`MapRendering.md` costs 96 482 bytes over 22 sections, and it carries a defect nothing can fix while the sections
share a file: **the number `14` is used twice** — §14 "Rotation Gesture Display-Layer Handoff" (2 460 B) and §14
"Android Auto renderer — smooth follow" (11 744 B). The routing table resolves it with heading text because a bare
`§14` is ambiguous, which the route check reports by design. The document is 2× the document bound, §1 "Render
Pipeline Architecture" is 22 592 bytes against a 12 000-byte section bound, and `Known Pitfalls (Regression
Checklist)` (4 163 B) is unrouted.

By cluster, what the 96 482 bytes are:

| cluster | sections | bytes | what it is |
|---|---|---|---|
| the pipeline and its buffers | §1, §2, §4, §5, §12, §13, §14 (handoff) | 38 415 | normative — the pipeline, bitmap lifecycle, epochs, overrun |
| location, follow and camera | §3, §6, §7, §8, §9, §10, §11 | 14 799 | normative — deduplication, angles, bearing, markers, rotation, zoom |
| the car renderer and the phone canvas | §14 (AA renderer), §18 | 14 996 | normative — the car's own render path and what happens to the phone surface |
| stylesheets, icons and the tile cache | §15, §15a, §16, §16a, §16b, §17 | 16 568 | normative + the packaging contract |
| route data and labels | §19, §20 | 6 064 | normative |
| unrouted | Known Pitfalls, Parameter Overview | 5 081 | history (a regression checklist) and a number table |

The pipeline cluster alone is 38 415 bytes and the other three total 52 689; one document cannot hold them under the
bound, and they are not one subject: the follow path is not the car renderer, and the stylesheet contract is not the
pipeline. **The clusters' numbers are what the tree cites, so the split keeps them** — a citation that said
`MapRendering.md §13` says `MapRendering.md §13` still, and one that said `MapRendering.md §14 "Android Auto
renderer"` says `MapCar.md §14` with no ambiguity left to resolve.

**Acceptance scenario.** After the change, four rendering documents are each under 48 000 bytes with every section
under 12 000, `MapRendering.md §14` names exactly one section, the regression checklist is read from
`MapRendering.why.md` rather than searched for, and the rules in all four are named.

**Owning guideline.** `guidelines/MapRendering.md` §2 "Bitmap Lifecycle" and the "Known Pitfalls" checklist are the
sections that recorded what the pipeline's rules cost when they were broken; the layer rule is
`openspec/specs/documentation-ownership/spec.md`, "A guideline document carries one kind of text".

**Surface.** Measured with `tools/check-doc-routes.sh --sizes`:

```
MapRendering.md today           96 482 B, 22 numbered sections, §14 twice
sections past the 12 000 bound              1 (§1, 22 592 B)
unnumbered, unrouted blocks                 2 (Known Pitfalls 4 163, Parameter Overview 918)
document-size conditions it carries         1
rule names it carries today                 5 — already hand-written as **Rule: …**
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`), 1 document becomes 4 normative documents
(3 new) plus companions, 5 rule names carried forward, 1 addressing defect resolved (the duplicate §14), 0 fixtures
realigned
— `tools/check-doc-routes.sh --sizes | grep '^MapRendering'`

## What Changes

- **`MapRendering.md`** keeps the pipeline and its data rules: §1, §2, §4, §5, §12, §13, §14 (handoff), §19 and §20.
- **`MapFollow.md`** takes §3, §6, §7, §8, §9, §10 and §11.
- **`MapCar.md`** takes the second §14 (the Android Auto renderer) and §18.
- **`MapStyling.md`** takes §15, §15a, §16, §16a, §16b and §17.
- **§1 is split into lettered sub-sections** (`§1a`…`§1e`): 22 592 bytes answer more than one question, including
  `### 1.1 Follow framing`, which is already a section by another name.
- **Companions**: `MapRendering.why.md` (the regression checklist, the "caused jumps multiple times" history, the
  reversal record), `MapRendering.recipes.md` (the parameter table, the measurement recipes),
  `MapFollow.why.md`, `MapStyling.why.md` (the failed-load rules' history and the packaging failures behind them).
- **The duplicate `§14` is gone** without renumbering anything: one §14 stays in `MapRendering.md`, the other is
  `MapCar.md`'s §14.
- **Every rule is named** in all four documents, `**Rule: <name>**`, unique across `guidelines/*.md`.
- **`AGENTS.md`** retargets its rendering rows and adds a row per new document.

No requirement changes. `skip_specs: true` — the layer rule and the guards landed in `guideline-layers-mechanism`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no rendering behavior changes. The pipeline, its epochs, its buffers and its bounds keep their rules; the
change moves them and names them.

## Impact

| path | what changes |
|---|---|
| `guidelines/MapRendering.md` | reduced to the pipeline and its data rules; §1 split; `§14` unambiguous; rules named |
| `guidelines/MapFollow.md` | new — §3, §6–§11 |
| `guidelines/MapCar.md` | new — §14 (AA renderer), §18 |
| `guidelines/MapStyling.md` | new — §15, §15a, §16, §16a, §16b, §17 |
| `guidelines/MapRendering.why.md`, `MapRendering.recipes.md`, `MapFollow.why.md`, `MapStyling.why.md` | new companions |
| `AGENTS.md` | rendering rows retargeted, rows added |

No C++ source, native signature, CMake file, stylesheet or asset is touched. Scope: **documentation** — the
rendering rules, not the renderer.

**Rollback path.** Revert the commit; the new documents are new files and every line returns to `MapRendering.md`.

## Not in this change

- **The other five documents** and the four open loop changes.
- **Renumbering anything at all.** The duplicate §14 is resolved by the split, not by a new number, so no citation
  in the tree changes its number.
- **A generated companion index**, as in the other layering changes.
