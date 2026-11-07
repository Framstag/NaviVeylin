# Design — guideline-maprendering-layers

## Context

See `proposal.md` — Why for the cluster measurement. Three constraints:

- **The route check reports a bare ambiguous number by design** — `MapRendering.md` carries two `## 14.` headings,
  so a reference must quote heading text today. That is a defect the addressing scheme already detects and cannot
  repair from inside one file.
- **Five rules are already hand-written as `**Rule: <name>**`** in this document, which is the idiom the mechanism
  made machine-readable; they are the names that must survive this cut unchanged.
- **Two unrouted blocks are 5 081 bytes** of history and numbers — the regression checklist and the parameter
  table — and a reader reaches them only by search.

## Goals / Non-Goals

**Goals.**

- Four normative documents, each under 48 000 bytes, every section under 12 000, **no number changed** where the
  number is unique, and the duplicated `14` resolved.
- The regression checklist and the parameter table reachable by a route rather than a search.
- The five existing rule names carried forward unchanged, so a citation of one still resolves.

**Non-Goals.**

- Touching rendering code, a bound, an epoch rule or a diagnostic line.
- Re-deciding any pitfall the checklist records: the checklist is moved, not reappraised.
- Merging the four documents into fewer — the clusters are what decide the count.

## Decisions

### D1 — Four documents, split by the subject each cluster already is

| | form | why / why not |
|---|---|---|
| **A (chosen)** | `MapRendering.md` (pipeline, buffers, route data, labels) + `MapFollow.md` (location, follow, camera) + `MapCar.md` (car renderer, phone canvas) + `MapStyling.md` (stylesheets, icons, cache) | the pipeline cluster is 38 415 B and the other three 52 689; each is one subject; numbers carried over |
| B | one document with rationale and procedure companions | the normative body stays ~85 000 B, 1.8× the bound — the change would ship a red condition |
| C | split the pipeline, leave the rest | leaves 52 689 in the remainder, still past the bound |
| D | renumber into the new documents | every citation becomes a lookup instead of a rename, and the five existing rule names lose the section they are cited by |

A. The clusters were not invented here: `AGENTS.md` already routes the pipeline, the follow path, the car surface
and the stylesheet material as four different kinds of change.

### D2 — The duplicated `§14` is resolved by the split, not by a renumbering

The two `14` sections are two subjects — "Rotation Gesture Display-Layer Handoff" and "Android Auto renderer" — and
they land in two documents under D1. `MapRendering.md §14` then means one thing, `MapCar.md §14` means the other, and
the routing row that today quotes heading text to disambiguate can drop it. **Alternative:** renumber the AA
renderer to `§21`, which repairs the ambiguity in one edit but changes a number that `AGENTS.md` and two archived
changes cite. Rejected: the split resolves it for free, and this change's whole premise is that numbers do not move.

**Risk:** a citation that wrote the *bare* `MapRendering.md §14` now resolves to the handoff where it meant the car
renderer — **Mitigation:** the baseline records every bare `§14` citation, and each is checked by hand in the task
rather than assumed; a bare reference that meant the AA renderer is retargeted to `MapCar.md §14`.

### D3 — §1 is split, and `### 1.1` is promoted

§1 is 22 592 bytes. It already contains `### 1.1 Follow framing — the anchor applies to the RENDER TARGET (read
before touching follow mode)`, which is a sub-section by another name. The split promotes it to `§1a` and distributes
the rest into `§1b`…`§1e` by question, so `§1` stays the routed index. **Alternative:** leave §1 whole and let the
document bound absorb it. Rejected: the shipped requirement is on the section, not the document, and §1 is 1.9× it.

### D4 — What classifies a block

```
RECIPES    the parameter table, a measurement recipe, a logcat or screenshot procedure, a GPX replay
WHY        the regression checklist and each pitfall's history
           a heading or sentence about what a mistake cost ("caused jumps multiple times")
           a paragraph naming a change, an issue, a date or a measurement
RULES      the pipeline's order and its invariants, the epoch and buffer rules, the angles,
           the bounds, the stylesheet and packaging contracts
```

**Risk:** a rendering rule lands in the checklist and leaves the routed read, where the previous effort
(`fix-*` changes against this document) showed the pipeline is where a wrong edit costs the most — **Mitigation:**
each pitfall moves with a reference from the rule it belongs to, and the review reads that mapping specifically.

### D5 — The five existing rule names are the ones that must survive

They are `**Rule: …**` bullets in §1.1, §2 and §2's pool rules. The cut keeps each name verbatim, so a name cited by
a change keeps resolving, and it checks the names against `guidelines/*.md` for a collision — `Build.md` and
`UI.md` both discuss frame handling. **Risk:** a collision with a name in another document, which the check reports
as a CI failure. **Mitigation:** where the rule is already owned elsewhere, leave `see rule <File>.md §N "<name>"`;
where it is owned here, no other document may state it, and the cut reports a second statement rather than
duplicating one.

### D6 — Verification

- Per document: `tools/check-doc-routes.sh --sizes` — every section under 12 000, the routing row's stated cost
  equal to the measured one; `tools/sensors/metrics/doc-context.sh` — no `document-size` condition.
- the heading-number check: the union of the four documents' heading numbers equals `MapRendering.md`'s
  baseline set plus the sub-sections §1 gains, **and the duplicated-number count goes from 1 to 0**.
- The five rule names: present verbatim, each resolving in its section.
- **No device measurement applies**: no rendering path, bound, epoch or diagnostic line is edited, so there is no
  pixel to read and no `pixel-check` verdict owed — and this change says so instead of implying a run.

## Risks / Trade-offs

- **This is the document with the most hand-written rule history.** → The checklist and each pitfall's history are
  moved with a reference from the rule they explain, and the relocation count closes per document.
- **`MapRendering.md` at ~44 000 bytes is close to the bound** after §19 and §20 stay in it. → Measured in the task
  that creates it; if it lands over, §20 moves to `MapFollow.md` (labels are a follow-path concern) rather than
  leaving a red condition.
- **A bare `§14` citation changes meaning.** → Named and handled in D2 with a hand check, not with an assumption.
- **Four documents and three companions is seven new files.** → Each is created by the task that empties its
  document, so a partial landing leaves no half-layered document and the condition closes per document.

## Migration Plan

1. Land `guideline-layers-mechanism`, then `guideline-backlog-layers`.
2. Split §1 in `MapRendering.md` first (it is the routed pipeline read), then create `MapFollow.md`, `MapCar.md` and
   `MapStyling.md` with the sections they take, then the companions, then the rule names.
3. Verify per document, then retarget `AGENTS.md`'s rendering rows and add one row per new document.
4. Verify the duplicate-number count and every bare `§14` citation.

**Rollback**: revert the commit.

## Open decisions

none — the count of documents follows the clusters (D1) and the bound (D6); the ambiguity resolution (D2) is decided
by the split rather than deferred, because leaving a duplicated number in place would keep a defect the addressing
scheme is built to report.

## Apply-phase note (2026-10-31)

**The kept-set arithmetic in the brief was wrong; the design's own risk note was right.** The brief that
handed this change over summed D1's `MapRendering.md` kept set to 54 479 B, which is over the 48 000 B
bound and would have forced the §20 fallback. Re-summed from the measured section costs
(`tools/check-doc-routes.sh --sizes`, `guidelines/MapRendering.md` 96 487 B):

```
§1 22 592 + §2 2 781 + §4 1 463 + §5 1 134 + §12 1 727 + §13 6 258 + §14 (handoff) 2 460
  + §19 3 056 + §20 3 008 = 44 479 B      (not 54 479 — the brief added 10 000)
```

So D1's assignment already fits: the cut leaves `MapRendering.md` at **46 751 B**, under the bound without
moving §20 to `MapFollow.md` and without a fourth normative document. D1 and D3 are unchanged; the §20
fallback in "Risks / Trade-offs" is not triggered.

**Scope addition, recorded rather than silent.** The move dangles five base-document citations and one
companion citation; each is retargeted to the document that now carries the number (listed in
`evidence/retargeted-citations.txt`): `Design.md` §563 and §820, `UI.md` §440, `UIPhone.md` §409,
`BuildNative.md` §206, `BuildNative.recipes.md` §343. This is the same retarget the landed `guideline-ui-layers`
change made to the documents the route check resolves. No other text in those documents changes.

**What was not moved.** §1's history is split by question into `§1a`…`§1e` and its dated/issue tokens and the
two unrouted blocks move to the companions; the remaining prose history in `§1a`–`§1e` stays in place because
the document fits under the bound without extracting it. Recorded as a remaining item rather than claimed as
done.
