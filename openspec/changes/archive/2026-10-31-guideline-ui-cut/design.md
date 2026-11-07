# Design — guideline-ui-cut

## Context

See `proposal.md` — Why for the measured clusters. What shapes this cut:

- **The bound is 32 000 B** and `UI.md` is 45 327 B: it must give up ~13 400 B.
- **The routing table already separates the two halves**: its car row names §2, §3, §3b, §3c, §4, §6b, its parity row names §1 and §6, and its shared rows name §9 and §10. This cut is the file catching up with the table.
- **The referring set is measured**: `AGENTS.md`, `guidelines/Backlog.recipes.md`, `guidelines/Design.md`, `guidelines/UIPhone.md`. Two of those are documents other changes own, so the retarget is name-only and its count is recorded.
- **The companions carry anchors into `UI.md`'s numbers.** `UI.why.md` is 15 551 B — the largest companion in the tree — so its split is the one place this cut can get the half-split wrong.

## Goals / Non-Goals

**Goals.**

- Two documents under 32 000 B with every section under 12 000, and **no number moved**.
- The parity rule (§1) readable in the document that owns parity, without the car's platform constraints in front of it.
- Every companion anchor naming a section **its own base document** carries.
- The named-rule count unchanged and every rule still reached.

**Non-Goals.**

- Deciding anything about parity: §1 and §6 move nowhere.
- Moving §9/§10 into a third document — they are shared *because* they are the parity document's subject.
- Touching a Compose file, a string, a resource or a manifest: this is a documentation cut, and no size or bound changes value.

## Decisions

### D1 — The split line: the car's surface against what both variants share

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `UICar.md` = §2, §3, §3a, §3b, §3c, §4, §6b (20 651 B); `UI.md` = §1, §6, §8d, §9, §10, §20 + maintenance (23 638 B of sections; 24 881 B in the finished file) | each is one subject, the routing table already addresses them separately, both fit with room |
| B | move §9 and §10 out as well | they are the *shared* rules; a document for "cross-variant material" would own parity and nothing else, and the parity rule is the reason the document exists |
| C | split by section size until both fit | leaves a document whose subject is "the sections that were too big" |
| D | renumber into the new document | every citation becomes a lookup instead of a rename |

A. The new document is named for its reader: a car-screen change reads `UICar.md`, a parity question reads `UI.md`, and a change that must touch both reads both — which the parity requirement already demands.

### D2 — The companions split by anchor, and `UI.why.md` is the case to test

`UI.why.md` (15 551 B) holds the three hand-written `### Why …` blocks plus each section's rationale; its anchors name `UI.md` sections. Every anchor for a car section (§2, §3, §3a, §3b, §3c, §4, §6b) moves to `UICar.why.md`, and the ones for §1, §6, §9, §10, §20 stay. `UICar.recipes.md` is created **only if** the car half holds procedure. **The test is not intent but measurement**: `tools/check-doc-routes.sh` reports a companion anchor that names no section of its base document, so run it after the split rather than reasoning about it.

### D3 — What must not change

The rule names, their statements and their globs; `tools/doc-routes.json` gains the car document's group and `check-rule-routes.sh` is the referee (a named-but-unreached rule is a failure). `AGENTS.md`'s car row is retargeted, and its parity row must keep naming `UI.md` — if a retarget moves a row that does not name a car section, that is a defect in this cut, not a detail.

### D4 — Verification

Per document: `--sizes` (under 32 000, every section under 12 000); the `document-size` row (**2 → 1**); `check-rule-routes.sh` (the named-rule count **unchanged** — 575 before this cut); `check-doc-routes.sh` (every reference and companion anchor resolves); the heading-number union against the cut's own baseline. **No device measurement applies**: no composable, string, size or bound changes value, so no pixel is claimed and no `pixel-check` verdict is owed — and the change says so rather than implying a run.

## Amendment during apply — §8d was missing from this design's arithmetic

Measured 2026-10-31, after the baseline: `UI.md` carries **§8d \"Cross-variant map presentation\"**, 5 539 B and
nine already-named rules, and **neither of D1's two clusters claimed it**. The design's row A therefore
read `UI.md` as §1/§6/§9/§10/§20 when the document's remainder is 23 638 B of sections (24 881 B in the
finished file, whose preamble is lengthened by pointing at the sibling document).

§8d **stays in `UI.md`**, and the reason is its own content rather than its title: of its nine rules, seven
are dual-surface (per-surface anchors with shared labels and hierarchy, one reroute policy for the session,
the row rule for street-name labels, one route and one marker palette per presentation, the opaque casing,
lowercase stylesheet hexes, the per-surface voice switch with the shared GPS switch), one is car-only (the AA
surface indicators' fixed palettes) and one is phone-only (the location-options-only settings). A section that
is seven-ninths dual is parity material, and the cut does not split mid-section — that would be a renumbering.

Two consequences, both recorded rather than hidden:

- The arithmetic still closes: 20 651 + 23 638 = 44 289 B of sections, both halves under 32 000, largest
  section §10 at 8 816 B under 12 000. **The omission did not make the cut infeasible — it made the design's
  figures wrong.** In the finished tree `UICar.md` is 21 562 B and `UI.md` 24 881 B; the two preambles replace
  one, which is the 1 048 B that the halves total above the baseline.
- `§8d` is named by **no row** of the routing table (the car row names §2/§3/§3b/§3c/§4/§6b, the cross-variant
  row §3a, the parity row §1/§6, the dark-mode row §9/§10, the honesty row §20). So the table never had to
  disambiguate it, which is exactly why the omission was invisible to a design that read the table's clusters.
  Its `UI.why.md` anchor (`## §8d The car search distance reference`) is car-flavoured even though the section
  is dual — the anchor stays with `UI.md` because the section stays.

## Risks / Trade-offs

- **`UI.why.md` is 15 551 B** and its anchors are the cut's most error-prone part. → Anchor-by-anchor split, then the check, then a count recorded in `evidence/relocation.txt`. A companion anchor is a case, not a hope.
- **Two referring documents belong to other changes** (`Design.md`, `UIPhone.md`). → Name-only retargets, recorded per file, with the exact lines listed so the next writer sees why they changed.
- **A row retargeted that does not name a car section** (e.g. the parity row) would break the requirement that a parity question finds both documents. → Verify the parity rows still name `UI.md` and that the car rows' sections are the ones that moved.
- **`UI.md` is 24 881 B and `UICar.md` 21 562 B — 7 119 B and 10 438 B of room.** → No shaving, and no incentive to move a section that does not belong.
- **The design's own figures were short by §8d.** → The amendment above records it; task 2.3's union check is what would have caught it, because the baseline set carries §8d and neither cluster did.

## Migration Plan

1. Baseline: section table, heading-number set, citations, rule names, companion anchors with their byte spans.
2. `UICar.md` from the car cluster; the companions split by anchor; `AGENTS.md`'s car rows retargeted and one row added; the globs added.
3. Retarget the measured referring set, name-only; record the count per file.
4. Verify: bound per document, section bound, anchors, rule count, citations, parity rows, the four flat checks.
5. `openspec validate guideline-ui-cut --strict` valid; hygiene OK.

**Rollback**: revert the commit.

## Open decisions

none — the boundary is D1 and its alternatives are the ones it eliminated; the one question a reader might raise (why §9 and §10 do not leave as well) is answered there.
