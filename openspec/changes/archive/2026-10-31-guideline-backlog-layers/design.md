# Design — guideline-backlog-layers

## Context

See `proposal.md` — Why for the section-by-section measurement. Three facts shape this cut and none is restated
there:

- **The four open loop changes read this document.** `shaping-inbox` 6 citations, `change-readiness-gate` 5,
  `loop-consumes-a-prepared-change` 8, `finding-shape-at-filing` 9 — against §1, §4, §5, §6, §7, §9, §10.
- **Two sections are past the 12 000-byte section bound** once their rationale leaves: §7 (56 602 B) and §9
  (15 785 B) — §9 only becomes a problem *inside* the layered document, because the same number must now be a
  normative section.
- **The mechanism this cut is judged by** is `guideline-layers-mechanism`: a companion is read but not routed, a
  companion's `## §N` anchor names a section its base document carries, a named rule is unique across
  `guidelines/*.md`, and the `document-size` row files this document's condition until it is under 48 000 bytes.

## Goals / Non-Goals

**Goals.**

- `Backlog.md` under 48 000 bytes with every section under 12 000, and no section number moved.
- Every rule in it named, so the four open changes can be reviewed rule by rule.
- The loop's procedure — 56 602 bytes of it — reachable by a route rather than by a search.

**Non-Goals.**

- Rewriting a rule. This cut moves text; a sentence that changes meaning is a defect in the cut.
- Regenerating the sub-section index or the rule list from the companion. Worth doing once three documents carry
  one; not here.
- Touching the four open changes, `tools/loop/**`, or any check.

## Decisions

### D1 — §7's 56 602 bytes become five sub-sections whose bodies live in the companion

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `Backlog.md §7` is an index of `§7a`…`§7e`; their bodies are `Backlog.recipes.md §7a`…`§7e` | the sub-section numbers exist in the base document, so the companion's anchors resolve (the rule the mechanism landed); the bodies are bounded at ~12 kB each; a reader lands on the phase they need |
| B | §7 moves to the companion as one 56 602-byte section | legal — a companion is unbounded — but a reader searching the procedure still pulls 56 602 bytes, which is the defect |
| C | §7 becomes a new top-level document `Loop.md` | adds a routing row and a document for a procedure the routing table should not route at all, and splits the tracker's ownership across two documents |
| D | leave §7 where it is | the largest unrouted block in the corpus stays unrouted, and the document stays over its bound |

A. The project's lettered-split idiom already means "the section's own body becomes an index of them"; the only
change here is where the bodies are, which is what a companion is for. **Risk:** an index whose pointers drift from
the companion — **Mitigation:** the anchor rule is a case (`layers#aCompanionAnchorThatNoSectionCarriesIsReported`),
so a `§7d` deleted from the base document while the companion still anchors to it fails the route check.

### D2 — §9's rules stay, its transcripts leave

§9 "Checks" is 15 785 bytes and is **routed**, so a reader is sent to it by the routing table today. It is a
normative section — which checks exist, what a red one means, who may write what — wrapped around command
transcripts and `expected output` blocks. Those move to `Backlog.recipes.md §9`, the rules stay, and the section
falls under the bound without a lettered split. **Alternative:** split §9 into `§9a`/`§9b` (normative/recipes) and
route the sub-section. Rejected because the transcripts are not a routed read at all: a reader who is sent to §9
wants the rule, and the transcript is what they run afterwards. The routing row keeps naming `§9`.

### D3 — The rationale goes to the companion by section number, not by theme

`§12` "The decisions, and why" and `§13` "Deferred, and why" move whole. Every other section's rationale goes to
`Backlog.why.md` **under an anchor of the number it came from** (`## §4 Invariants — why`), rather than into a
thematic essay under §12. **Alternative:** one long rationale section in the companion. Rejected because a reader
who has just read `§5` must be able to jump from the number they are holding, and because a theme-named anchor
does not resolve against the base document — the anchor rule would have nothing to check.

### D4 — What counts as rationale, in this document specifically

The predicate, applied to each section, so the cut is not a judgement call made 14 times:

```
RECIPES    a fenced block whose content is a command's output, a transcript, or a table of numbers
           a numbered "step 1 / step 2" sequence
           a prompt's text, a phase's protocol
WHY        a paragraph naming an issue, a change, a date, or a measurement
           a paragraph whose subject is a decision already made ("it used to", "was rejected", "the reason")
           "and why" and "deferred" material by name
RULES      everything left, plus every obligation a reader must obey
```

**Risk:** mis-classifying a rule as rationale loses it from the routed read — the failure this whole effort exists
to prevent. **Mitigation:** the cut is a *move*, so the text is still in the tree and still citable; the relocation
count closes (removed blocks = statements found in the receiving companion); and the review reads the diff for a
rule that left the normative layer.

### D5 — Rule naming here, and the collision risk this document carries

Every rule in the normative layer becomes `**Rule: <name>**`. Names are unique across `guidelines/*.md`, and this
document shares a vocabulary with `Build.md` and `UI.md` — "an item may only enter the loop when exactly one fix
follows from a proven root cause" is a `Backlog.md` rule a `Build.md` section is tempted to restate. **Risk:** a
name collision, which the mechanism reports as a CI failure rather than as a review note. **Mitigation:** the cut
task states the rule explicitly — where a rule is already owned elsewhere, the cut leaves a `see rule` reference
instead of a second statement, and the check is the referee.

### D6 — The bound is the acceptance criterion, not a target

The change is done when `tools/sensors/metrics/doc-context.sh` no longer prints this document's condition, and not
before: `Backlog.md` under 48 000 bytes, every section under 12 000, and `tools/check-doc-routes.sh` green with the
new §7 row resolving. A cut that leaves the document at 50 000 bytes has moved text without clearing the condition,
and the condition is what the next reader feels.

## Risks / Trade-offs

- **The cut is large and mechanical, and a dropped line is silent.** → The relocation count closes per section, the
  route check fails on a dangling reference, and the diff is read by an independent reviewer who did not write it.
- **Four open changes cite this document.** → No `§N` moves; §7 gains sub-sections rather than losing its number;
  the heading-number check compares the heading-number set before and after.
- **A rule moves to the companion by misclassification.** → Named rules make it visible: a companion carrying a
  `**Rule: …**` line is a rule in the wrong layer, and the review's rule-level reading set names the rules the diff
  governs.
- **The index of §7a…§7e can drift from the companion.** → The anchor rule is a case; a pointer to a sub-section the
  base document does not carry fails the route check.

## Migration Plan

1. Land `guideline-layers-mechanism` first: the guards, the two metric rows, the map, the reviewer prompt.
2. Cut this document in the order the tasks state — rationale out, then procedure out, then rules named, then the
   §7 index and the §9 transcript split, then `AGENTS.md`.
3. Verify: the document condition clears, the route check is green, the heading-number set is unchanged, and the
   four open changes' citations still resolve.
4. Then the four loop changes, whose design work assumed this document.

**Rollback**: revert the commit — two new files removed, the base document restored, `AGENTS.md`'s block restored.

## Open decisions

none — the one decision that could have been deferred (whether §7 needs its own document) is settled in D1 with its
alternatives, and the bound in D6 is the mechanism's own, retunable in one registry edit rather than here.
