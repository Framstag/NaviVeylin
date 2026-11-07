# Design — guideline-small-docs-layers

## Context

See `proposal.md` — Why. What makes this change different from its five siblings:

- **The bound is not the driver.** All four documents are under 48 000 bytes and none has a section over 12 000, so
  no split is owed and none is made. The driver is the **purity condition** — dated figures, issue references and
  command transcripts inside rules — and the **rule naming**, which is what gives the derived reading set something
  to reach.
- **Two of these documents state rules a build gate enforces.** `Logging.md` §3's coordinate rule is enforced by
  `checkNoCoordinatesInLogs`, and `Regulatory.md` §2's licence duties by the licence policy gate. Naming a rule is
  what would let a gate name it; this change deliberately stops before that.
- **Three rule names already exist** (one in `Regulatory.md`, two in `Logging.md`) and must survive verbatim.

## Goals / Non-Goals

**Goals.**

- Every rule in the four documents named, unique across `guidelines/*.md`.
- No dated figure, issue reference, transcript or `### Why …` heading inside a rule, so the purity condition reports
  nothing in any of the four.
- The evidence reachable — a companion per document, anchored by the same numbers — rather than deleted.

**Non-Goals.**

- Splitting any of the four.
- Wiring a build gate to a rule name.
- Re-reading a regulatory duty or a metrics definition; the text moves, and a rule keeps its meaning.

## Decisions

### D1 — Companions only, and only where the evidence is

Each document gets what it actually carries, not a fixed pair of companions:

| document | why | recipes |
|---|---|---|
| `Regulatory.md` | yes — inventory history, review record, why a duty was added | yes — source list, checklists, review commands |
| `FeatureList.md` | only if the classification vocabulary is rationale | yes — the generator's invocations and gates |
| `Metrics.md` | no — its rationale is one paragraph per rule and stays inline as the rule's own reason | yes — the invocations and sample output |
| `Logging.md` | no — the disclosure statement's history is small and belongs with the rule | yes — logcat recipes, NDK bridge configuration |

**Alternative:** a fixed `why`+`recipes` pair per document, which is simpler to describe and creates two empty or
near-empty files. Rejected: a companion that anchors to no rule is a file a router must name and a reader must
skip, and the layer rule says a document carries *one* kind of text, not that every document carries all three.

### D2 — The purity predicate, applied to these four

```
RECIPES    the logcat recipes, the generator's invocations, the metrics invocations and their output,
           the regulatory source list, the review commands
WHY        the inventory history, the review record with its dates, why a duty was added,
           a paragraph naming an issue or a change, the provenance of a classification rule
RULES      what may be logged, the publisher's duties, the platform gates, what the two documents
           are, what one run measures, the classification vocabulary
```

The boundary that matters most is `Regulatory.md`: §2's duties are rules, §9's inventory is history, and §9 is
*routed* — so a reader sent to §9 for a rule must find the rule and not a date table. §9 is cut rather than moved:
its rules stay, its inventory and review dates go to the companion.

### D3 — `Logging.md`'s coordinate rule keeps its place

It is the rule a build gate enforces, and it is the shortest document in the set. The cut moves the logcat recipes
and the NDK bridge's configuration detail, and leaves §3's rule where it is, named. **Risk:** the rule ends up
duplicated with the gate's comment in the build. **Mitigation:** the gate is not edited here; the duplication is
pre-existing, and the change that wires a gate to a rule name is where it becomes a reference.

### D4 — No `AGENTS.md` row is added

The four documents are already routed. A companion is not a routed document (the mechanism's rule), so the rows for
this document set do not change — except that the one row for `Logging.md` already names §3, which is exactly the
rule the change keeps in place. **Alternative:** a row per companion for discoverability. Rejected: the mechanism
landed one row stating the companion convention, and 14 companion rows across the seven changes would spend the
entry document's whole headroom.

### D5 — Verification

- Per document: `tools/sensors/metrics/doc-context.sh` reports **no purity condition** and no size condition, and
  `tools/check-doc-routes.sh --sizes` shows every section under 12 000.
- Every rule name unique in its document and across `guidelines/*.md`; the three existing names verbatim.
- the heading-number check: each document's `## ` heading-number set is unchanged.
- The relocation count closes per document: blocks removed equal statements found in the companion.
- **No device measurement applies** and no build gate is exercised: no compiled file, no gate and no policy input is
  edited, so there is no tally to quote and no pixel to read — and this change says so.

## Risks / Trade-offs

- **A rule is misclassified as history in `Regulatory.md` §9.** → §9 is the routed section most at risk, and the task
  reads its rules one by one against the baseline rather than applying the predicate mechanically.
- **The three existing rule names must survive** or a citation breaks. → Recorded in the baseline and diffed.
- **Five companions for four documents is five new files** for a change that clears no size condition. → The
  condition it clears is the purity one, per file and line, and the guards file it automatically if the cut is
  incomplete — so a partial landing is measurable rather than silent.
- **`Metrics.md`'s rules are short and its invocations are half the document.** → Only the invocations and their
  output move; the definitions stay, because a metric definition with no number is the rule.

## Migration Plan

1. Land `guideline-layers-mechanism`.
2. Cut document by document in the order the tasks state, smallest boundary first: `Logging.md`, `Metrics.md`,
   `FeatureList.md`, then `Regulatory.md`'s §9 which needs the most care.
3. Verify per document: no purity condition, names unique, heading numbers unchanged.
4. No `AGENTS.md` change is owed; verify the route check is green anyway.

**Rollback**: revert the commit.

## Open decisions

none — whether a companion is created for a document is decided by what the document carries (D1), and the
gate-to-rule-naming question is explicitly out of scope in the proposal rather than deferred inside this design.
