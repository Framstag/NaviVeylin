# Design — guideline-ui-layers

## Context

See `proposal.md` — Why for the cluster measurement. What shapes the cut:

- **The routing table has already drawn the split.** Its phone rows and its car rows name disjoint section sets, so
  the two documents this change creates are the two documents a reader is already being sent to — the table just
  cannot say so while the sections share a file.
- **Three sections are past the 12 000-byte bound** and two of them are the numbers the phone rows name (§8, §7a).
  A split of those sections is owed by the shipped requirement, not by this change.
- **Three `### Why …` headings are hand-written rationale inside §1**, which is the routed parity read a change
  starts from. They are the clearest evidence that the layering is not a new convention but an existing one made
  enforceable.

## Goals / Non-Goals

**Goals.**

- Two normative documents, each under 48 000 bytes, every section under 12 000, and **no number changed**.
- The parity rules read without the parity history, and the phone surfaces read without the measurements that
  produced them.
- Every rule named, so a phone-UI change can be reviewed rule by rule rather than section by section.

**Non-Goals.**

- Touching a Compose file, a string, a resource or a manifest: the split is a documentation change.
- Deciding any parity question. The three `### Why …` blocks are moved, not revisited.
- Merging the two documents back together — that is the routing table's line, drawn before this change.

## Decisions

### D1 — Two normative documents, the routing table's own line

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `UI.md` (parity, car, shared) + `UIPhone.md` (phone surfaces), numbers carried over | the phone cluster is 51 208 bytes and the car-plus-parity cluster 46 197; one document cannot hold both under the bound, and the routing table already addresses them separately |
| B | one `UI.md` with why and recipes companions | the normative body stays ~90 000 bytes, 1.9× the document bound: the change would ship a red condition |
| C | three documents (parity, car, phone) | the car cluster is 19 131 bytes and is the parity rule's own application; splitting it adds a row and a companion set for a document under the bound by itself |
| D | renumber the phone sections into `UIPhone.md` | every phone citation becomes a lookup; there are 8 such rows in the routing table and the phone rows are what a phone change starts from |

A. The name is chosen so the routing rows read as they already do: a phone change reads `UIPhone.md`, a car change
reads `UI.md`, a parity question reads both — which is what the parity requirement already demands.

### D2 — §1 gets lettered sub-sections, because three things share one number

§1 costs 26 015 bytes and holds the parity rule, the record of parity decisions already made, and three `### Why …`
blocks. The cut leaves `§1` in `UI.md` as an index of `§1a`…`§1f` — the parity rule, the favourites hierarchy, the
row and card actions, the starred chip and drag checklist, the vehicle-position setting, the notification rules —
with their bodies in place and their rationale in the companion. This is the shipped lettered-split rule applied to
a section that answers more than one question, and it keeps `UI.md §1` resolving for the parity row.

### D3 — §8 and §7a, the two phone sections over the bound

Both are split rather than moved: §8's overlay sizing is 20 271 bytes of one question asked about six overlays, and
§7a is 13 867 bytes about one control in three modes. Each gets lettered sub-sections under its unchanged number,
with the measured pixel tables that justify each bound going to `UIPhone.why.md` under the same anchor. The routing
row keeps naming §8 and §7a, and gains the sub-section it should route if one sub-section answers the question.

### D4 — What classifies a block

```
RECIPES    a UI-dump geometry recipe, a pixel-check step, an adb command and its output
           a table of measured sizes with no rule attached
WHY        a paragraph about a decision already made ("is not locked during a car session",
           "are phone-only", "the license list is phone-only")
           a paragraph naming an issue, a change, a date or a measurement
RULES      what a surface must do, what it must not do, and the bound a control must meet
```

Applied to the three hand-written `### Why …` blocks: they are `WHY` by construction, and their move is the proof
the predicate is not an invention of this change. The measured pixel tables in §8 and §7a are the thinnest boundary
in the document — a table that *is* the bound (the minimum touch target, the driver-seat readability floor) stays
normative; a table that only records how a number was arrived at goes to the companion.

**Risk:** a bound moves to the companion and the rule that cites it loses its number — **Mitigation:** the rule
keeps a `(see UI.why.md §8…)` reference, and the companion anchor is a case
(`layers#aCompanionAnchorThatNoSectionCarriesIsReported`).

### D5 — Parity is verified, not assumed

The parity requirement says a feature that names both surfaces provides one case per surface, or an explicit host
substitute. This change moves the parity section, so it must show the rule survived: the task verifies that
`UI.md`'s parity rule names the same demands after the move, and that the phone sections it leaves behind are the
ones the car rows do not claim. **Alternative:** verify only that the route check is green. Rejected — the route
check proves an address resolves, not that a parity rule still reads as a rule.

### D6 — Verification

- Per document: `tools/check-doc-routes.sh --sizes` (every section under 12 000, the routing row's cost equal to the
  measured one), `tools/sensors/metrics/doc-context.sh` (no `document-size` condition).
- the heading-number check: the union of both documents' heading numbers equals `UI.md`'s set before the cut,
  plus the sub-sections §1, §8 and §7a gain.
- Every rule name unique across `guidelines/*.md`.
- **No device measurement applies and this change says so**: no pixel is claimed, no UI-dump geometry is asserted and
  no `pixel-check` run is owed, because no composable, size or bound changes — the numbers are moved, and the tasks
  verify that no number changed value.

## Risks / Trade-offs

- **Splitting a UI document while an open change cites it.** → No number moves; the retargeted rows are listed in
  `evidence/pre-cut-citations.txt` and each is checked.
- **The measured tables' rule/recipe boundary.** → Named in D4 as the thinnest boundary, and it is what the review
  reads first in this diff.
- **Two companions per document is four new files.** → Each is created by the task that empties its document of
  rationale, so a partial landing leaves no half-layered document.
- **`UIPhone.md` at ~40 000 bytes is close to the bound.** → Measured in the task that creates it; if it lands over,
  §11 or §10a moves to `UI.md` rather than leaving a red condition, and the design's D1 line (phone vs shared) is
  what decides it.

## Migration Plan

1. Land `guideline-layers-mechanism`, then `guideline-backlog-layers`.
2. Split §1 in `UI.md` first (it is the routed parity read), then create `UIPhone.md` with the phone sections, then
   the two companions of each, then the rule names.
3. Verify per document, then retarget `AGENTS.md`'s phone rows and add the two new ones.
4. Verify the external citations and the parity rule's text.

**Rollback**: revert the commit.

## Apply-phase amendment (2026-10-31) — the design's arithmetic was wrong; the bound was not given

**D1's kept set and D4's predicate changed during apply, and this is why.** Measured on the tree the change
landed on (per-section cost from `tools/check-doc-routes.sh --sizes`):

- D1's kept set `UI.md = §1, §2, §3, §3a, §3b, §3c, §4, §6, §6b, §9, §10` + maintenance + prelude =
  **61 368 B**, and the phone set `§5, §6a, §7, §7a, §8, §8a, §10a, §11` = **52 135 B**. Two documents
  under 48 000 hold at most 96 000 B, so **≥ 17 500 B must leave the normative documents** — and the splits
  (§1, §8, §7a) and the rule names only *add* bytes to them.
- D4's marker list (`measured`, `Measured`, `issue #`, `TODO.md §`, `owner finding`, `reported on device`)
  finds only **850 B** in the kept set and **1 741 B** in the phone set. The predicate as written does not
  find 17 500 B to move, so D1-as-written could not meet the bound the same document set as its Goal.
- Even the design's own narrowest figure (`car-plus-parity` 46 197 + maintenance 1 728) is **48 440 B with the
  prelude** — over the bound before §1's index heading and any rule prefix.

**The amendment (D1).** `UI.md` keeps the parity rule, the car surfaces and the shared settings surfaces;
it drops the favourites/notification sub-sections of §1 to `UIPhone.md` and §8d ("Cross-variant map
presentation") stays in `UI.md` because it is cross-variant. §1's phone sub-sections carry their own numbers
in `UIPhone.md` (§12–§19). The resulting documents, **as measured when this amendment was written**: `UI.md`
43 162 B, `UIPhone.md` 46 889 B — the final sizes are `UI.md` 45 324 B and `UIPhone.md` 47 926 B (`wc -c`),
every section under 12 000.

**The amendment (D4).** A table or paragraph that records a decision already made, a measurement, a rejected
alternative or a history is WHY even when a reader of the rule consults it — the same standard this set
already applied to `Design.md` §13 and `Build.md` §2c. So §1's **10 303-byte `### Current parity decisions`
table** is WHY and moved to `UI.why.md` §1; the parity *rule* stayed in `UI.md` §1, and where a decision row
constrains new work the constraint is stated in the rule and the row is its evidence.

**The amendment (D3's §8 split).** §8 was split into §8/§8b/§8c/§8d (not "one per overlay"): §8b holds the
navigation status card, §8c the overlay controls and their density, §8d the cross-variant presentation.

Evidence for every figure: `evidence/pre-cut.md`, `evidence/conditions-cleared.log`,
`evidence/post-cut-sizes.txt`.

**Files outside the change's stated ownership that were touched, and why.** Moving §11, §8, §10a out of
`UI.md` dangled references in `guidelines/Design.md` (§11 ×3, §8), `guidelines/MapRendering.md` (§10a), the
companion `guidelines/Backlog.recipes.md` (§8, §7a) and `.pi/skills/compose-geometry/SKILL.md` (§8 ×2). The
route check resolves references written in a base document's body, so `Design.md` and `MapRendering.md` had
to be retargeted for `check-doc-routes.sh` to stay green; the companion and skill retargets are recorded for
honesty though no check resolves them. Each is a rename of the document, never a change to a statement, and
they are listed in `evidence/retargeted-citations.txt`.

## Open decisions

none — the document count follows the routing table's own line (D1) and the bound (D6); the one question worth
recording, whether the car material should be its own document, is answered in D1's alternative C.
