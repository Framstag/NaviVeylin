# `MapRendering.md` is cut by the question it answers: the pipeline, a frame's delivery, and a route's numbers

## Why

The document bound is **32 000 bytes** (retuned 2026-10-31; `guideline-layers-mechanism` D4 and its task 7.7). `guidelines/MapRendering.md` measures **46 751 B** and owes a cut.

Measured with `tools/check-doc-routes.sh --sizes`:

| cluster | sections | bytes |
|---|---|---|
| the pipeline and its data rules — including the §1 index and `§1a`–`§1e` | §1, §1a, §1b, §1c, §1d, §1e, §2, §4, §5 | **29 024** |
| a frame's **delivery** — front-buffer emission, the overrun window, the display-layer handoff | §12, §13, §14 | **10 505** |
| a **route's** numbers | §19 | **3 055** |
| label wrapping — the bounds the renderer configures | §20 | **3 044** |

Three questions, three homes: the pipeline stays, delivery becomes its own document, and the two sections that are not about producing a frame at all move to the documents that already own their subject — a route's numbers to a document of their own (`MapRoute.md`), label wrapping to `MapStyling.md`, where the renderer's configured bounds already live beside the icons and the stylesheet.

**Acceptance scenario.** After the cut, `MapRendering.md` and every document it hands a section to is under 32 000 B with every section under 12 000, no section number moves, the five hand-written rule names still resolve, and the `document-size` conditions drop from 3 to 2.

**Owning guideline.** `guidelines/MapRendering.md` §2 "Bitmap Lifecycle" and its `Known Pitfalls` checklist are the record of what the pipeline's rules cost; the bound is in `openspec/specs/documentation-ownership/spec.md`, "The routing cost is measured".

**Surface.** `wc -c guidelines/MapRendering.md` → `46751`; the four clusters above; `document-size` conditions before the cut: **3** (MapRendering, UI, Backlog).

surface: 0 cases added, 1 document becomes 2 normative documents plus 2 sections re-homed (1 new document: `MapFrames.md`; §19 → `MapRoute.md`, §20 → `MapStyling.md`), 0 rules renamed, 2 routing rows, 0 fixtures — `wc -c guidelines/MapRendering.md && tools/check-doc-routes.sh --sizes | grep '^Map'`

## What Changes

- **`MapRendering.md`** keeps §1, §1a–§1e, §2, §4 and §5 — 29 024 B.
- **`MapFrames.md`** (new) takes §12, §13 and §14, carrying their own numbers.
- **`MapRoute.md`** (new) takes §19 — a route's numbers, which the routing table already routes as a subject of its own.
- **`MapStyling.md`** gains §20 (label wrapping), whose bounds the renderer configures beside the stylesheet and icon contracts it already owns.
- **Companions**: `MapRendering.why.md` and `MapRendering.recipes.md` split with their halves under the anchors they carry; `MapFrames.why.md` only if that half holds rationale.
- **`AGENTS.md`**: rows retargeted for the sections that moved, one row added for `MapFrames.md`.
- **`tools/doc-routes.json`**: the new documents' rule globs.
- **Citations** naming a moved section are retargeted by document name — the referring set is measured, not guessed: `AGENTS.md`, `guidelines/Backlog.recipes.md`, plus `guidelines/MapStyling.md` for §20.

No requirement changes and no rule changes: `skip_specs: true`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — text moves; no behavior changes.

## Impact

| path | what changes |
|---|---|
| `guidelines/MapRendering.md` | keeps the pipeline and its data rules |
| `guidelines/MapFrames.md` | new — §12, §13, §14 |
| `guidelines/MapRoute.md` | new — §19 |
| `guidelines/MapStyling.md` | gains §20 and its anchors |
| `guidelines/MapRendering.why.md`, `MapRendering.recipes.md` | split; `MapFrames.why.md` only if warranted |
| `AGENTS.md`, `tools/doc-routes.json` | rows and globs |

No C++, native signature, CMake, stylesheet or asset is touched. Scope: **documentation**.

**Rollback path.** Revert the commit; the two new documents are new files and every line returns to `MapRendering.md`.

## Not in this change

- **The other two cuts** the retune forces: `guideline-ui-cut`, `guideline-backlog-cut` (`guideline-uiphone-cut` landed).
- **Renumbering.** A section that changes document keeps its number; a citation is repaired by naming the new file.
