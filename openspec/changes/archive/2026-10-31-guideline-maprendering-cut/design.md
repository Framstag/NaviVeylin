# Design — guideline-maprendering-cut

## Context

See `proposal.md` — Why for the measured clusters. What shapes this cut:

- **The bound is 32 000 B** and `MapRendering.md` is 46 751 B: it must give up ~14 800 B.
- **Two of its sections are not about producing a frame at all** — §19 (a route's numbers) and §20 (label wrapping) — so they can leave to documents whose subjects they already share, which costs no new document and no new concept.
- **The routing table already routes both as subjects**: "route data and the numbers derived from it" and the rendering rows that name `§20` beside the stylesheet contracts.
- **The referring set is measured**: `grep -rln 'MapRendering\.md.*§' guidelines/ AGENTS.md .pi/skills/` → `AGENTS.md`, `guidelines/Backlog.recipes.md` (and `guidelines/MapStyling.md` once §20 lands there).

## Goals / Non-Goals

**Goals.**

- Every document that holds a piece of this one is **under 32 000 B with every section under 12 000**.
- **No section number moved**; a citation is repaired by naming a different file.
- The five hand-written rule names in this document survive verbatim and stay reached by a glob.
- The cut's arithmetic and the retarget count are recorded, because the bound is a byte count.

**Non-Goals.**

- Touching a rendering rule, a bound, an epoch rule or a diagnostic line.
- Merging the pipeline into `MapFollow.md` — follow is the location path, not the frame path.
- Renaming a document that does not need to be re-formed (`MapFollow.md`, `MapCar.md` stay as they are).

## Decisions

### D1 — The split line, and the two sections that leave rather than split

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `MapRendering.md` keeps §1, §1a–§1e, §2, §4, §5 (29 024 B); `MapFrames.md` takes §12, §13, §14 (10 505 B); §19 → `MapRoute.md` (3 055 B); §20 → `MapStyling.md` (16 994 + 3 044) | each document is one question, both new homes are subjects that already exist, and no section is orphaned into a document of its own for the sake of a byte count |
| B | `MapFrames.md` takes §12–§14 **and** §19–§20 (16 604 B) | fits, but the document becomes "delivery, plus route numbers, plus label bounds" — three subjects in one file, which is what the bound exists to prevent |
| C | §19 and §20 become two more new documents | two 3 kB documents for material whose homes already exist; `MapRoute.md` alone is worth its row, two are not |
| D | renumber the moved sections into their new documents | every citation becomes a lookup instead of a rename |

A. §20's home is a judgement worth stating: **label wrapping is what the renderer is configured with**, and `MapStyling.md` owns the renderer's configuration (the stylesheet, the day/night pair, the icon directory, the tile cache). §19 has no such home, and the routing table already gives it a row of its own, so it becomes `MapRoute.md`.

### D2 — Companions split with their halves, and are never created empty

`MapRendering.why.md` (5 219 B) carries the pipeline's history and its regression checklist; the anchors for §12–§14's rationale move to `MapFrames.why.md` if that half holds any, and the rest stays. §19's and §20's anchors, if the companions carry them, follow their sections into `MapRoute.why.md` / `MapStyling.why.md`. A companion that would anchor to no section is not created — the mechanism's rule makes an unanchored companion a file a reader must skip, and a previous cut in this set declined to create one for exactly that reason.

### D3 — What must not change

The rule names, their statements and their globs: `tools/doc-routes.json` gains a group for each new document, and `check-rule-routes.sh` fails on a named-but-unreached rule, so it is the referee. `AGENTS.md`'s rows are retargeted and one row is added; the two rows that name a document's *subject* ("route data", "label word-wrapping") keep their subject wording and change only the document they name.

### D4 — Verification

Per document: `--sizes` (under 32 000, every section under 12 000); the `document-size` row (**3 → 2**); `check-rule-routes.sh` (575 named rules, unchanged); `check-doc-routes.sh` (every reference and companion anchor resolves, count grows by the retargets); the heading-number union against the cut's own baseline. **No device measurement applies**: no rendering path, bound, epoch or diagnostic line changes value, so there is no pixel to read and no `pixel-check` verdict owed.

## Risks / Trade-offs

- **The pipeline cluster keeps 29 024 B — 2 976 B of margin.** → If it lands over, move the smallest coherent §1 sub-section to `MapFrames.md`; do not shave a section that answers the pipeline's own question.
- **§20's home is contested** (labels are rendering, not styling). → Stated in D1 with the reason, and reversible by one row: the section keeps its number, so a later cut can move it again.
- **A citation in `guidelines/Backlog.recipes.md` or a skill file names a moved section.** → The referring set is measured before the cut, the retargets are name-only, and the count is recorded. Two precedents: this set's last cut needed exactly that for seven refs across three documents, and the retarget is what the mechanism change's D7 scopes ("no citation is rewritten" holds for *that* change only).
- **A retarget is made in a document another cut owns.** → `guidelines/UI.md` is not in this cut's referring set; if a retarget touches `MapStyling.md`, its own anchors move with §20 and the count is recorded so the next reader can see why its line changed.

## Migration Plan

1. Baseline: the section table, the heading-number set, the citations, the five rule names, the companion anchors.
2. `MapFrames.md` from §12–§14, `MapRoute.md` from §19, §20 into `MapStyling.md`; companions split; `AGENTS.md` rows retargeted and one added; globs added.
3. Retarget the measured referring set, name-only.
4. Verify: bound per document, section bound, anchors, rule count, citations, and the four flat checks.
5. `openspec validate guideline-maprendering-cut --strict` valid; hygiene OK.

**Rollback**: revert the commit.

## Open decisions

none — the split line is D1 and its alternatives are the ones it eliminated; the one contestable choice (§20's home) is decided in D1 with the reason, and it costs one row to reverse.
