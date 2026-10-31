# Design — guideline-build-layers

## Context

See `proposal.md` — Why for the cluster measurement. Two constraints shape this cut:

- **A citation must survive the split.** The reference form is `<File>.md §N`, so a section that changes document
  changes its *address* while its number stays. Three citations of this document exist outside `guidelines/` — two
  in `change-readiness-gate`, one in `finding-shape-at-filing` — and `AGENTS.md`'s routing table names §1, §2, §2a,
  §4, §6, §6a–§6h, §7, §9, §10, §10a–§10j, §11 and §12.
- **The bound is not negotiable and the subject is not one subject.** The gate, the test suite, the evidence rules
  and the native build are four concerns that happen to have grown in one file.

## Goals / Non-Goals

**Goals.**

- Five normative documents under 48 000 bytes each, with every section under 12 000, and **no number changed**.
- The device recipes (37 656 B) reachable as procedure, with a route rather than a search.
- Every rule named, so the two open changes that cite this document can be reviewed rule by rule.

**Non-Goals.**

- Reducing the number of documents below what the bound requires.
- Restructuring the content: this is a cut, and a sentence that changes meaning is a defect in it.
- Touching the checks, the registry, the sensor rows, or the Gradle build.

## Decisions

### D1 — Four new normative documents, each carrying the numbers it takes

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `Build.md` (skills, commands, release, packaging, licence) + `BuildGate.md` (§2, §2a–§2e) + `BuildEvidence.md` (§4, §4a–§4f, §7, §11) + `BuildTests.md` (§6, §6a–§6h) + `BuildNative.md` (§10, §10f, §10j, §12) | each is one subject, each is under the bound by measurement, and **no section number moves** — a citation is repaired by naming a different document |
| B | one normative build document with rationale and procedure companions only | measured: the normative body is ~120 000 bytes, 2.5× the document bound. It cannot be met, so the bound would be left failing and the change would ship a red condition |
| C | split into two documents (gate vs the rest) | leaves ~70 000 bytes in the larger one, still past the bound |
| D | renumber the moved sections into the new documents (`BuildTests.md §1`…) | every citation becomes a lookup instead of a rename, and the four open changes plus the routing table must be re-derived rather than repaired |

A. The choice of what a document keeps is *subject*, not size: the gate is not the test suite. The numbers are kept
deliberately — `BuildTests.md` carries `§6` and `§6a`…`§6h` as its own numbers — which is what makes the split a
file rename for every citation in the tree and in the four open changes.

**Risk:** a reader who lands in `Build.md` and looks for §6a finds a hole where the number used to be — **Mitigation:**
each document whose cluster moved keeps a one-line index entry naming the document that took the number, and
`AGENTS.md`'s row for that number is retargeted in the same change.

### D2 — The rationale companion is per document, not one shared file

Five documents get five `*.why.md` companions rather than one. **Alternative:** one `Build.why.md` for the build
tree. Rejected because a companion anchors to *its base document's* numbers (the mechanism's rule), so a section
`§4c` in `BuildEvidence.md` and a section `§4c` in `Build.md` would collide in one shared companion, and the anchor
contract would have no base to resolve against.

### D3 — What classifies a block, so the cut is not a judgement call

```
RECIPES    a fenced block holding a command's output, a transcript, a tally, a JUnit XML
           a numbered boot/step sequence (AVD, head unit, emulator)
           a table of measured numbers with no rule attached
WHY        a paragraph naming an issue, a change, a date or a measurement
           a paragraph about a decision already made ("it used to", "was rejected", "the reason this exists")
           a gate-timing record as history rather than as a threshold
RULES      everything that states what a reader must do, plus its refusal path
```

Applied to the four clusters: the gate-timing baseline in §2c is *history* (why) and its threshold is a rule; §4e's
falsification cases are *history*, §4a's verdict rule is normative; §6b's "the case that proved it" is history,
§6b's constraint is normative; §10a–§10k are recipes almost entirely, except §10f, §10j and §10k's rule about which
number a route length is.

**Risk:** a rule misclassified as procedure leaves the routed read — the failure the whole effort exists to prevent.
**Mitigation:** the cut is a move, the relocation count closes per document, and the review reads the diff for a rule
that left a normative layer.

### D4 — Rule naming, and the vocabulary this document shares

`Build.md` shares vocabulary with `Backlog.md` (the loop's refusals), `UI.md` (readability bounds), `Metrics.md`
(what a run measures) and `Logging.md` (what a log line may carry). **Risk:** a name collision, which is a CI
failure. **Mitigation:** where a rule is already owned elsewhere the cut leaves `see rule <File>.md §N "<name>"`
instead of a second statement — which is also the cheapest way to shrink the document — and the check is the
referee rather than a reviewer's memory.

### D5 — The bound is the acceptance criterion

Each of the five documents is verified against `document-size` (48 000) and `routed-section-size` (12 000) in the
task that creates it, not at the end. A document that lands at 50 000 bytes has moved text without clearing the
condition, and the condition is what the next reader feels. If a document cannot fall under 48 000 without a further
cut, the cut is named in that task and lands there rather than in a follow-up change.

### D6 — Verification

- `tools/check-doc-routes.sh --sizes` per document: every section under 12 000, the routing row's stated cost equal
  to the measured one (`routed-read#figure`).
- `tools/sensors/metrics/doc-context.sh`: no `document-size` condition for any of the five.
- the heading-number check: the union of the five documents' heading numbers equals `Build.md`'s set before the
  cut, plus the sub-sections §7 gains.
- The relocation count closes per document: blocks removed equal statements found in the receiving document.
- No device measurement applies: no compiled file, no UI and no rendering path is touched, and this change says so
  rather than implying a run.

## Risks / Trade-offs

- **Five documents for one file is a lot of routing rows.** → `AGENTS.md` gains four rows and replaces the rows
  that named moved sections; the diet the mechanism change lands in the same entry document is what pays for them,
  and each new row is shorter than the tick list of `§10a`…`§10k` it replaces.
- **A citation outside `guidelines/` names `Build.md §6a`.** → Three such citations exist in two open changes; the
  rename is recorded in `evidence/retargeted-citations.txt` and each is checked to resolve against the new document.
- **The `§10` cluster's rule/recipe boundary is the thinnest in the document.** → §10f, §10j and §10k's rule about a
  route length stay normative in `BuildNative.md`; everything else in §10a–§10k is a recipe, and the review reads
  that boundary specifically.
- **A 166 383-byte cut in one change is large by construction.** → Ordered by cluster so each task lands one
  document and its companions complete; the document condition is per document and closes as each lands, so a
  partial landing is measurable rather than all-or-nothing.

## Migration Plan

1. Land `guideline-layers-mechanism`, then `guideline-backlog-layers`.
2. Cut cluster by cluster in the order the tasks state: gate, evidence, tests, native, then the remainder of
   `Build.md`; the device recipes go to a companion of `BuildNative.md`.
3. Verify per document: the size, the section bound, the named rules, the companions' anchors.
4. `AGENTS.md` last, so the routing table is written once against the final set of documents.
5. Verify the three external citations resolve by their new document name.

**Rollback**: revert the commit; every line is in a new file or in `Build.md`, and no state is migrated.

## Open decisions

none — the count of documents is decided by D1 and D5's measurement, and the one question a reader might ask (may
the five be fewer?) is answered in the proposal's "Not in this change": the bound decides, not a preference.
