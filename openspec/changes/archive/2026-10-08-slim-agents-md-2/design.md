# Design

See `proposal.md` — **Why** for the per-block measurement, and
`specs/documentation-ownership/spec.md` for the three requirements this change adds to the capability
`scope-guideline-reads` created and `slim-agents-md` extended.

## Context

`AGENTS.md` is 28 374 B (~7.9k tokens) and is injected into every session. `slim-agents-md` relocated eight
blocks whose owners already existed; what remains includes three kinds of surplus:

```
1  material with NO owner ........ logging (3 466 B), the libtui pinning note (~1 100 B)
2  rules whose owner EXISTS ....... JNI stub (1 596), Native Integration (2 326),
                                    Stylesheets (1 661), Constraints (617)
3  facts about one area ........... AA manifest/distribution (~2 500), harness notes (~900)
```

The routing table (4 655 B) and the tooling it needs already work: `tools/check-doc-routes.sh` resolves 65
references and its self-test passes, the config's reading instruction is section-scoped, and
`guidelines/Build.md` §2 documents the loop that governs how a session works. Two mechanisms are therefore
in place that this change relies on: a route to any section, and a check that a route still resolves.

One mechanism is fragile: the check hardcodes `files="Build Design UI MapRendering Regulatory"`
(`tools/check-doc-routes.sh:28`), so a sixth guideline would be reachable but unchecked.

## Goals / Non-Goals

**Goals**

- A home for every rule that is in the always-read file, so no block stays there for want of a destination.
- The entry document bounded by purpose — what a session needs *before* it reads anything else — rather
  than by kind (facts yes, rules no), which is what the previous change's rule could not express for
  material that is genuinely useful.
- The check's coverage derived from the repository, so adding a document cannot silently escape it.

**Non-Goals**

- Not shortening any guideline body, and not deleting a rule: every block moves or stays, verified by
  search (the previous change's discipline).
- Not a logging *spec*: the logging rules are enforced by a CI step, a build gate and a disclosure
  statement, and `guidelines/Logging.md` is where they are stated. A spec would be a third copy.
- Not trimming the routing table's heading text below the point where the check can still disambiguate a
  duplicated number (only unique, unsuffixed numbers lose their text).
- Not touching `TODO.md` (~66k tokens, on demand only) or the guideline bodies' own size.

## Decisions

### D1 — logging gets its own guideline; the other blocks go to existing owners

**Chosen.** `guidelines/Logging.md` owns observability. The JNI-stub facts, the Native Integration rules,
the stylesheet rules, the manifest detail, the harness notes and the Constraints duplicates move to owners
that already exist.

| Alternative | Why not |
|---|---|
| Distribute logging across `Design.md` §5 (the bridge), `Build.md` §10 (the tag recipe), `Regulatory.md` §9 (the disclosure) | No new file, but a logging change becomes a three-hop read, the routing row must name three sections, and the concern keeps growing: it already carries a CI gate on the libosmscout side, a build gate on the Kotlin side, a retention policy and a legal disclosure. The document set is organized by concern; logging is one. |
| Keep logging in `AGENTS.md` because it is "always relevant" | Nothing about it is needed before the first read: a session that asks about a route calculation never needs the level mapping. The test of the new requirement is *when* a reader needs it, not whether it matters. |
| Move the AA manifest detail to a new `guidelines/Manifests.md` | The two specs (`auto`, `android-automotive-os`) already own that contract; a third document would be a copy site, which is what the first two changes removed. |

### D2 — the boundary for the entry document is "needed before the first read", and it is written down

**Chosen.** Requirement 2 states the rule; the do-not-move set is what it produces:

```
stays in AGENTS.md                            leaves
  Project Overview, Tech Stack, Module          logging rules and bridge facts
  Structure, Documentation Map                  JNI-stub facts, Native Integration rules
  the app-architecture facts                    stylesheet rules, packaging facts
  OpenSpec Workflow, Backlog maintenance        AA manifest/distribution detail
  the four iteration one-liners                 the harness/tooling notes
  the two distribution facts                    the Constraints duplicates
  the version-state and AAB facts
```

Rationale: the previous change's rule ("facts stay, rules reference") was enough for rules but silent about
facts that are merely *interesting*. Several blocks that survived it are facts a reader needs only while
working in one area — the manifest permission list, the four sync tasks, the `java.library.path` mechanics.
Without a second axis the file keeps regrowing the same way it did after every previous edit.

**Alternative considered.** A size budget (e.g. "the entry document stays under 20 KB"). Rejected: a number
invites trimming the wrong block to hit it, and the file's cost depends on what the reader must do with it,
not on its size. Requirement 2 is the same rule with a reason.

### D3 — the check derives its documents, and reports an unrouted one

**Chosen.** `tools/check-doc-routes.sh` builds its list from `guidelines/*.md` (sorted, basenames without
`.md`) and fails when a document is not named by the routing table.

Rationale: the hardcoded list is the one thing that would let this change's own new document escape
verification; and an unrouted document is exactly the failure this change fixes by hand (logging had no
route because it had no document, and the harness notes have neither). Document-level completeness is
cheap, unambiguous and needs no judgement; *section*-level completeness stays out of scope, as the previous
change's D2 decided.

**Alternatives considered.** (a) Keep the list, add `Logging` to it — rejected: the next document repeats
the bug. (b) Extend the check to sections — rejected: the previous change measured that completeness there
would force an 80-row table and duplicate each document's own table of contents.

### D4 — the routing table keeps heading text only where a number cannot disambiguate

**Chosen.** A row keeps `"<heading text>"` when its number is used twice in the document
(`MapRendering.md` §14) or is absent (the two `Design.md` appendices, `UI.md`'s "Keeping this document
honest", `MapRendering.md`'s "Known Pitfalls" and "Parameter Overview"). A row whose number is unique and
unsuffixed drops it.

Rationale: the check resolves a unique number without text, so the text buys readability, not correctness —
and the table is 4 655 B of an always-injected file. Estimated saving ~1.8 KB (~0.5k tokens per session).
The trade-off is real and one-directional: a reader skimming the table sees numbers instead of names for
those rows, so the change keeps text wherever a reader could otherwise be misled, and keeps it in the rows
that name several documents at once.

**Alternative considered.** Split the table into its own file. Rejected: the route must live where the
reader already is; a separate file is one more hop before the first useful byte.

### D5 — relocation, not rewriting; the count must close again

**Chosen.** Every moved block is verified by searching for its statement in the receiving document, one
`grep` per block, and the counts are reported together (blocks removed = statements found). A block whose
statement exists nowhere is not deleted; it is filed.

This repeats the previous change's discipline deliberately: it is what caught the difference between
"moved" and "lost" there, and this change moves seven blocks rather than eight.

### D6 — what this change does not do

- It does not touch `TODO.md`; the backlog is the largest artifact in the repository (~66k tokens) but it
  never enters a session that is not reading it on purpose.
- It does not shorten any guideline; the guideline corpus grows again by the relocation targets, and that is
  the intended direction, because the always-read file is the one that costs every session.
- It does not add a CI step: the existing "Check documentation routes" step runs the derived check.
- Threading, lifecycle and the native boundary are not applicable: documents and one shell script.

## Risks / Trade-offs

- **A reader loses an invariant that used to be in front of them.** The AA section is the sharp end: its
  manifest detail moves to two specs, and a session that edits a manifest now reads a spec first. →
  Mitigation: the invariants stay as one-liners with routes (§D2), the routing row for the car path already
  names Design §8, UI §3/§3a-§3c and four specs, and the AA facts that a reader needs before the first read
  (two flavors, one applicationId, the AAOS overlay) stay in the entry document.
- **A new document becomes a place where rules accumulate unseen.** `Logging.md` is not routed by kind of
  change only — a logging change also touches `Design.md` §5 and `Build.md` §10 for its recipe. →
  Mitigation: the routing row names all three, and the log-specific rules live in one of them, so the
  three-hop read is the recipe read, not a rule read.
- **The derived document list changes the check's failure modes**: a repository with a stray `.md` under
  `guidelines/` would now fail as "unrouted". → Accepted, and it is the intended behaviour; the message
  names the file and how to route it.
- **Trimming the routing table's heading text degrades the table for humans** (D4). → Mitigation: text is
  kept wherever a number is ambiguous or absent, and the check still catches a bad reference.
- **The relocation count may not close** if a block's statement turns out to live only in `AGENTS.md`. →
  D5: file it rather than delete it, and report the count as it is.
- **This is the third change to edit `AGENTS.md` today, and a peer session is active in the tree.** →
  Sequencing in the proposal; apply it alone, and check the wrapper/peer probes before the verification
  pass.

## Migration Plan

By section, not by line, since the predecessors moved every line below the routing table:

```
1  inventory: one line per block — the phrase to grep and the owner that must contain it
2  create guidelines/Logging.md (the three logging subsections, moved)
3  Build.md §6    <- JNI-stub facts        Build.md §1  <- the two harness paragraphs
   Build.md §12   <- stylesheet packaging facts
4  Design.md §5   <- the Native Integration rules;  Design.md §2 stays the Constraints owner
5  MapRendering.md §16/§16a <- the stylesheet rules
   specs auto + android-automotive-os <- the manifest/distribution detail
6  AGENTS.md: replace each moved block with the route (and the two logging facts), trim the table rows,
   add the logging row
7  tools/check-doc-routes.sh: derive the document list; -selftest.sh: the extra-guideline cases
8  config.yaml: the context inventory names the sixth guideline
9  verify: the relocation count closes; wc -c before/after for every document; the check exits 0 with 65+
   references and its self-test passes; the revert-check of D6/§4; validate --strict and doctor
```

**Rollback:** `git checkout` the two edited documents, delete `guidelines/Logging.md`, restore the check's
list. Nothing at runtime reads these files, so a rollback needs no rebuild and no re-measurement.

## Open Questions

1. **Whether the harness notes (the `view_image` package, the libtui pinning) belong in a guideline at
   all.** They are about the agent harness, not about this repository; `Build.md` §1 is the closest owner
   and is where the screenshot prerequisite already lives. If a harness section ever grows, a
   `guidelines/Tooling.md` would be its honest home — a judgement that does not change this change's specs,
   approach or tasks.
2. **How far the entry document can go before it stops being useful.** The floor is roughly 10-12 KB; this
   change targets ~17 KB. A later pass could measure what a session actually reads in its first ten turns
   and trim from that evidence instead of from block sizes.
