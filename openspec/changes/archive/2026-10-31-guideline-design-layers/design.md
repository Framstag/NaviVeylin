# Design — guideline-design-layers

## Context

See `proposal.md` — Why for the cluster measurement. What shapes this cut:

- **The routing table already addresses this document by four kinds of change** — layering/DI/threading, the stack
  and its recipes, the native boundary, and the cross-variant seams — which is the split's justification rather than
  its invention.
- **Two sections are past the 12 000-byte bound** (§5 12 781, §8 12 058) and both are the section a reader is sent
  to from a row that names them.
- **Six rules are already hand-written as `**Rule: …**`**, and §13 "Superseded decisions" plus Appendix B
  "Provenance" are rationale in a document a change reads for rules.

## Goals / Non-Goals

**Goals.**

- Four normative documents, each under 48 000 bytes, every section under 12 000, **no number changed**.
- The rationale reachable (the superseded decisions, the provenance, why the two database-open entries differ)
  without being in the routed read.
- The apply checklist and the stack recipes reachable as procedure, since that is what a reader does with them.

**Non-Goals.**

- Touching a Kotlin file, a Hilt module, a JNI signature or the submodule.
- Re-deciding any superseded decision: §13's entries are moved, and their statements stay in the section that owns
  the rule they superseded.
- Restating a rule from `MapRendering.md`, `UI.md` or `Build.md`; where a seam is a pointer, the pointer stays.

## Decisions

### D1 — Three new normative documents, split by the kind of change that reads them

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `Design.md` (§1, §3, §4, §12) + `DesignStack.md` (§2, §9) + `DesignNative.md` (§5) + `DesignCrossVariant.md` (§6, §7, §8, §10, §11) | each is one subject, each under the bound, numbers carried over, and the four documents are the four readers the routing table already names |
| B | one `Design.md` with companions | the normative body stays ~65 000 B, 1.35× the bound — a red condition would ship |
| C | two documents (core vs the rest) | leaves ~40 000 in one and ~25 000 in the other, but puts the native boundary and the cross-variant seams in one document for no reason a reader shares |
| D | renumber into the new documents | every citation becomes a lookup; `AGENTS.md` names §1, §2, §3, §4, §5, §8, §9, §10, §11 and §12 of this document |

A. The four documents answer four questions a change asks in the order a change asks them: what may I touch, what is
already there, what crosses the native boundary, what crosses the variants.

### D2 — §5 and §8 are split under their unchanged numbers

§5 is 12 781 bytes — 781 over the bound — and §8 is 12 058. Both answer more than one question (§5: both sides of the
bridge, the two database-open entries, the submodule discipline; §8: the car host path, the surface ownership, the
diagnosis tags). Each becomes lettered sub-sections under its unchanged number, so `Design.md §5` becomes
`DesignNative.md §5` as an index and every citation of a sub-section resolves. **Alternative:** leave each whole and
accept a bound violation. Rejected: the bound is a shipped requirement, and 781 bytes over is a split, not a
judgement call.

### D3 — The recipes and the checklist are procedure, and the routing row follows them

`AGENTS.md` today sends a reader to "`Design.md` §2 — its 'Adding to the stack' subsection holds the recipes for a
dependency, a screen, a full-screen sheet, a details sheet and a native function". Those recipes are procedure by
construction, so they move to `Design.recipes.md §2` alongside Appendix A's apply checklist, and **the `AGENTS.md`
row is retargeted in the same change**. **Alternative:** leave the recipes in §2 because a row names them. Rejected —
the row is a sentence, and the sentence is easier to change than the layer boundary is to defend.

### D4 — §13 and Appendix B move whole, and their references stay valid

§13 "Superseded decisions" (4 967 B) and Appendix B "Provenance" (829 B) are rationale without exception, so they
move to `Design.why.md` under anchors naming the sections they belong to. A superseded decision that a *rule* still
depends on stays a reference in the rule ("superseded by …, `Design.why.md §13`"), never an operator the reader must
resolve. **Risk:** the reversal record becomes unreachable, which is what it is for — a later change re-proposing the
rejected option. **Mitigation:** the routing table's design row names the companion, so the record has a route.

### D5 — What classifies a block

```
RECIPES    the recipes for adding a dependency, a screen, a sheet, a native function
           the apply-phase checklist, a verification procedure
WHY        the superseded decisions, the provenance appendix
           a paragraph naming an issue, a change, a date or a measurement
           the rationale behind a seam (§8's "why the phone is not discarded", §5's asymmetry)
RULES      the layering, DI, state and threading rules, the native boundary's discipline,
           the cross-variant seams, the engineering principles
```

### D6 — Verification

- Per document: `tools/check-doc-routes.sh --sizes` (every section under 12 000, the row's cost equal to the
  measured one); `tools/sensors/metrics/doc-context.sh` (no `document-size` condition).
- the heading-number check: the union of the four documents' heading numbers equals `Design.md`'s baseline set
  plus the sub-sections §5 and §8 gain.
- **No rule name is carried forward**: `grep -c '\*\*Rule:' guidelines/Design.md` → **0**, so this document owns no
  named rule today and this change is where its rules gain names. The "six" an earlier draft of this design claimed
  came from a grep that counted bold *wording* (`- **Never …**` bullets, 38 of them, plus 36 `SHALL`/`MUST` lines) —
  that population is what has to be found by reading, not by a regex, and `evidence/pre-cut-unnamed-rules.txt`
  records it.
- The `AGENTS.md` row that named §2's recipes now names `Design.recipes.md`, and the route check resolves it.
- **No device measurement applies**: no module, dispatcher, native signature or surface is touched — this change
  moves architecture rules, so there is nothing to observe on a device, and it says so.

## Risks / Trade-offs

- **A layering or threading rule leaves the document a change reads first.** → §1, §3, §4 and §12 stay in
  `Design.md`, which is the document the routing row for layering/DI/threading keeps naming; nothing a dispatcher
  decision needs moves out of it.
- **Four documents, three companions, seven new files.** → Created per cluster, so a partial landing leaves no
  half-layered document and the condition closes per document.
- **§5's split could split the asymmetry rule in two.** → The two database-open entries' asymmetry is one rule and
  stays whole in `§5a`; the split's boundary is "the boundary's shape" versus "the two entries", not the entries
  against each other.
- **`DesignCrossVariant.md` at ~30 000 bytes** is the largest of the four and includes §8's split. → Measured in the
  task; if it lands over, §11 moves to `Design.md` rather than leaving a red condition.

## Migration Plan

1. Land `guideline-layers-mechanism`, then `guideline-backlog-layers`.
2. Cut in the order the tasks state: the companions first (so nothing is duplicated while sections move), then §5's
   split and `DesignNative.md`, then §8's split and `DesignCrossVariant.md`, then `DesignStack.md`, then the rules.
3. Retarget `AGENTS.md`'s rows last, including the §2-recipes row, so the table is written once against the final
   set of documents.
4. Verify the heading-number set, the rule names and every citation.

**Rollback**: revert the commit.

## Amendment during apply — D2's `§5` split boundary

D1 and D2 hold (four documents, three companions, no number moved), with one recorded amendment to D2's *risk*
clause. D2 said "the two database-open entries' asymmetry is one rule and stays whole in §5a". The split landed at
the `###` heading the section already carried: **§5a** = the boundary's shape and its JNI contract (pre-cut lines
282–370), **§5b** = the bridge's two sides, the two database-open entries and the submodule discipline (pre-cut
lines 372–425). The asymmetry is therefore whole in §5b, not §5a — it is one bullet and was not split — and the
submodule discipline stays beside it, which is the grouping a reader repairing a bridge citation already uses.
Moving the asymmetry text into §5a alone would have re-ordered content for no reader, so the boundary stayed where
the document had already drawn it. `§8` split at the "started-period observations" bullet: **§8a** = the car host
path and the surface, **§8b** = the screen-observation seam, parity and distribution.

## Computed sums (measured before the edit; `evidence/step0-sums.md`)

§1+§3+§4+§12 = 23 034 B (Design.md) · §2+§9 = 7 653 B (DesignStack.md) · §5 = 12 781 B (DesignNative.md, over the
section bound by 781) · §6+§7+§8+§10+§11 = 22 775 B (DesignCrossVariant.md, §8 over by 28). Total 66 243 B against
4 × 48 000 = 192 000 B, so the **document** bound was comfortable and the **section** bound was what bit — as D2
says. After the cut all four documents are under 48 000 B and every section under 12 000 B (`evidence/post-cut.md`).

## Open decisions

none — the split follows the routing table's own four readers (D1) and the bound (D2); the one decision a reader
could contest, whether the recipes may live outside §2, is decided in D3 with its alternative and the row it changes.
