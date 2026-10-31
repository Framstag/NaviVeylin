# Design — guideline-uiphone-cut

## Context

See `proposal.md` — Why for the measured clusters. What shapes this cut:

- **The bound is 32 000 B** (`guideline-layers-mechanism` D4, retuned in its task 7.7) and `UIPhone.md` is 47 926 B, so it must give up roughly 16 000 B — more than a companion move can supply, because the six earlier cuts already took the rationale and the procedure out.
- **The routing table already separates what this cut separates**: a phone-map change and a phone-search change are two different rows' worth of reader, and they share a document today only because `guideline-ui-layers` split phone from car by *variant*, not by subject.
- **A companion belongs to the document beside it**: `UIPhone.why.md` anchors to `UIPhone.md`'s numbers, and the mechanism's own rule says a companion anchor must name a section *its base document* carries. So the halves' anchors must go with their halves.

## Goals / Non-Goals

**Goals.**

- Two documents under 32 000 B with every section under 12 000, and **no number moved**.
- The boundary the routing table's own line implies: map vs surface.
- The rule names and globs still resolving, with the new document's rules reached.

**Non-Goals.**

- Changing a rule's text, a bound, or a layout number.
- A per-section split (`§7a` staying with `§7b`) — the split is by cluster, not to shave bytes.
- Merging any of the halves back into `UI.md`.

## Decisions

### D1 — The split line: the phone's map against the phone's surfaces

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `UIPhoneMap.md` = §7, §7a, §7b, §8, §8a, §8b, §8c, §10a, §11 (28 632 B); `UIPhone.md` = §5, §6a, §12–§19 (19 294 B) | both under the bound with headroom, one subject each, and the two readers the routing table already distinguishes |
| B | move one section group into `UI.md` instead of a new document | `UI.md` is 32 433 B-over-bound itself and owes its own cut (`guideline-ui-cut`); pushing bytes into it moves the problem |
| C | split by section size until both fit | produces a document whose subject is "the sections that were too big", which is no subject at all |
| D | renumber into the new document | every citation becomes a lookup instead of a rename |

A. The two halves are named for what a reader is doing: `UIPhoneMap.md` for the map and its overlays, `UIPhone.md` for the details dialog, the search surface and the favourites affordances.

### D2 — The companions split beside their halves

`UIPhone.why.md` (8 018 B) and `UIPhone.recipes.md` (1 535 B) carry anchors into `UIPhone.md`'s numbers. The map half's anchors move to `UIPhoneMap.why.md` / `UIPhoneMap.recipes.md` — created only if that half actually carries rationale or procedure, because a companion that anchors to no rule is a file a reader must skip. **Verify the anchors by measurement**, not by intent: `tools/check-doc-routes.sh` reports a companion anchor that names a section its base document does not carry.

### D3 — What this cut does *not* touch

The rules keep their names, their statements and their globs; only their file changes. Where a rule's home moves, `tools/doc-routes.json` gains the glob for the new document — and the check fails on a named-but-unreached rule, so it is the referee. `AGENTS.md`'s phone row is narrowed and one row is added, with the sub-sections it names priced as the table's own rule requires.

### D4 — Verification

Per document: `tools/check-doc-routes.sh --sizes` (under 32 000, every section under 12 000), `tools/sensors/metrics/doc-context.sh` with the `document-size` row (the condition count drops 4 → 3), `tools/check-rule-routes.sh` (every glob matches, every named rule reached), `tools/check-doc-routes.sh` (every reference and companion anchor resolves), and the heading-number union compared against the cut's own baseline. **No device measurement applies**: no composable, size or bound changes value.

## Risks / Trade-offs

- **A phone-map rule left in `UIPhone.md`** (or the reverse) makes the boundary lie. → The clusters are the measured ones, and the acceptance is mechanical: both documents under the bound, every section named in a row that resolves.
- **The companions' anchors are split wrongly** → the route check reports the anchor, not a reader. Verify with the check, not by reading.
- **A ~1 000-byte margin on `UIPhoneMap.md`** → if the split lands one half over 32 000, move the smallest coherent section of that half to the other document rather than shaving; the bound is per document and the subjects are the tie-breaker.

## Migration Plan

1. Record the baseline: `UIPhone.md`'s section table, its heading-number set, its citations, its rule names.
2. Create `UIPhoneMap.md` from the map cluster; split the companions beside it; narrow `AGENTS.md`'s phone row and add the new one; add the globs.
3. Verify both documents against the bound, the section bound, the anchors, the rule names and the citations.
4. The four flat checks exit 0, `openspec validate guideline-uiphone-cut --strict` valid, hygiene OK.

**Rollback**: revert the commit.

## Apply-phase amendment (2026-10-31)

Two things the design as written did not settle, recorded here rather than worked around in silence.

### A1 — the new document's rule glob is `guidelines/UI*.md`, not a literal `guidelines/UIPhoneMap.md`

D3 says the map's rules "gain the glob for the new document". A literal `guidelines/UIPhoneMap.md`
glob **fails `tools/check-rule-routes.sh` while the new file is untracked**: that check matches a glob
against `git ls-files`, and every `guidelines/*.md` this migration adds (UIPhone.md included, and the
whole `BuildEvidence.md` family) is untracked here. The precedent is the same — `guideline-build-layers`
added its rules "under `guidelines/**` glob" for exactly this reason, and `guideline-ui-layers` put
`UIPhone.md`'s rules under the `guidelines/UI*.md` glob, not a per-file one. So the map rules are
retargeted to `UIPhoneMap.md §...` **within the existing `guidelines/UI*.md` glob group**, which already
matches the tracked `guidelines/UI.md` and matches `guidelines/UIPhoneMap.md` by pattern once it is
committed. `check-rule-routes.sh` stays green (575 named rules, unchanged); the new document is reached
by the same glob that reaches `UIPhone.md` today.

### A2 — the citation retarget needs three files outside this change's permitted set

Moving §7…§11 out of `UIPhone.md` dangles references in `guidelines/Design.md` (§8b, §11),
`guidelines/MapCar.md` (§10a) and `guidelines/UI.md` (§7a, §8b, §8c, §11). The route check resolves
references written in a base document's body, so those four files must be retargeted to `UIPhoneMap.md`
for `bash tools/check-doc-routes.sh` to exit 0 — exactly as `guideline-ui-layers` retargeted `Design.md`
and `MapRendering.md` when it moved §11 and §10a out of `UI.md`. **Those files are not in this change's
permitted set** (hard constraint 1), so the retarget is stopped and raised with the supervisor rather
than performed. Evidence: `evidence/retargeted-citations.txt` (the BLOCKED block) and the four
`::error file=guidelines/…` lines it quotes.

## Open decisions

none — the boundary is the design's D1 and the arithmetic is measured above; the only question a reader might raise (why not move bytes into `UI.md`) is answered by the fact that `UI.md` owes its own cut in the same retune.
