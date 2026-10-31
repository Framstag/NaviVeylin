# Tasks

Reference: `proposal.md` for the cut, `design.md` for why, `../guideline-layers-mechanism/` for the mechanism and
the guards every task here clears. No requirement of this change's own: `skip_specs: true`, because the layer rules
landed with the mechanism.

## 1. Baseline

- [x] 1.1 Record `guidelines/Backlog.md`'s section cost table and its `## ` heading-number set as
      `evidence/pre-cut.md` — the comparison basis for the heading-number check. Verify: the table lists 14
      sections with §7 at 56 602 B and §9 at 15 785 B, matching `tools/check-doc-routes.sh --sizes`.
- [x] 1.2 Record every citation the four open loop changes write against this document —
      `grep -o 'Backlog.md §[0-9]*[a-z]*' openspec/changes/{shaping-inbox,change-readiness-gate,loop-consumes-a-prepared-change,finding-shape-at-filing}/*.md`
      — as `evidence/pre-cut-citations.txt`, and verify every one names a §-number the baseline set carries.

## 2. The rationale leaves

- [x] 2.1 Create `guidelines/Backlog.why.md`: move `§12` "The decisions, and why" (5 770 B) and `§13` "Deferred,
      and why" (1 524 B) whole, under headings carrying the same numbers. Verify: the route check reports no
      companion anchor without a base section (`layers#aCompanionAnchorThatNoSectionCarriesIsReported`).
- [x] 2.2 Move every section's rationale into the companion under an anchor of the number it came from, applying
      the `WHY` predicate of `design.md` D4. Verify: the relocation count closes — the number of blocks removed
      from `Backlog.md` equals the number found in `Backlog.why.md`, counted with
      `grep -c '^- \*\*' ` over both plus a diff of the moved text, recorded in `evidence/relocation-why.txt`.
- [x] 2.3 Verify no rule left with the rationale: read `evidence/relocation-why.txt` and confirm every moved block
      states no obligation — a `MUST`, `SHALL`, `never` or `only` obligation that moved is put back. Verify: the
      check `grep -c 'SHALL\|MUST' guidelines/Backlog.why.md` is 0.

## 3. The procedure leaves

- [x] 3.1 Create `guidelines/Backlog.recipes.md`: move §2 "The participants and the flow" (2 538 B), §8 "The human
      lane" (1 502 B) and every `RECIPES` block of `design.md` D4 from the other sections into it, each under an
      anchor of its source number. Verify: the relocation count closes, recorded in `evidence/relocation-recipes.txt`.
- [x] 3.2 Verify the normative document is free of procedure: `tools/sensors/metrics/doc-context.sh` reports no
      purity condition inside `guidelines/Backlog.md` (`layers#aFigureInANormativeDocumentIsACondition` and
      `layers#aWhyHeadingInANormativeDocumentIsACondition` both clean), and no command transcript is left in it.

## 4. §7 becomes an index and §9 keeps its rules

- [x] 4.1 Split §7's 56 602 bytes into `§7a`…`§7e` — one sub-section per phase of the loop — with the bodies in
      `Backlog.recipes.md` and `§7` in the base document reduced to an index listing the five headings. Verify: §7
      in the base document is under 12 000 B, each companion sub-section carries an anchor the base document
      carries, and the route check exits 0.
- [x] 4.2 Cut §9 "Checks" (15 785 B) so its rules stay and its transcripts move to `Backlog.recipes.md §9`. Verify:
      `tools/check-doc-routes.sh --sizes` reports `Backlog.md §9` under 12 000 B without a lettered split.
- [x] 4.3 Add the routing row for §7's sub-sections to `AGENTS.md`'s `## Documentation Map`, stating the cost of the
      sub-sections as the table's own rule requires. Verify: `bash tools/check-doc-routes.sh` exits 0 and the row's
      stated cost equals the measured one (`routed-read#figure`).

## 5. The rules are named

- [x] 5.1 Name every rule in `guidelines/Backlog.md` as `**Rule: <name>** <statement>.` and verify each name is
      unique in the document: `grep -o '^\s*[-*] \*\*Rule: [^*]*' guidelines/Backlog.md | sort | uniq -d` prints
      nothing (`rules#aNameTwiceInOneDocumentIsReported`, `rules#aNameTwoDocumentsStateIsReported`).
- [x] 5.2 For every rule the cut found already owned in another document, leave a `see rule <File>.md §N "<name>"`
      reference instead of a second statement. Verify: `bash tools/check-doc-routes.sh` exits 0, so no reference
      names a rule its section does not carry (`rules#aReferenceResolvesToTheNamedRule`).
- [x] 5.3 Add the globs for this document's rules to `tools/doc-routes.json`, with the tooling of the mechanism
      change. Verify: `tools/check-rule-routes.sh --list` reports no rule of this document as unreached
      (`routes#aRuleNoGlobReachesIsReported`).

## 6. The entry document

- [x] 6.1 Verify the statement `guideline-layers-mechanism` removed from `AGENTS.md`'s `Backlog maintenance` block is
      stated here — that change reduced the block to one sentence plus its route and recorded where each statement was
      found, because a removal whose statement lives nowhere is forbidden. Verify: the removed block's distinguishing
      sentence is found in `guidelines/Backlog.md`, the finding is recorded in
      `evidence/relocation-from-entry-document.txt`, and `bash tools/check-doc-routes.sh` exits 0.

## 7. Verification

- [x] 7.1 Verify the document condition clears: `tools/sensors/metrics/doc-context.sh` prints no `document-size`
      and no `routed-section-size` condition for `guidelines/Backlog.md`, and `wc -c guidelines/Backlog.md` is under
      48 000. Record `evidence/conditions-cleared.log` with the byte count and the metric's output.
- [x] 7.2 Verify the heading-number check: the `## ` heading-number set of `guidelines/Backlog.md`, minus the
      five sub-sections §7 gains, equals `evidence/pre-cut.md`'s set. Verify: the diff is empty.
- [x] 7.3 Verify every citation in `evidence/pre-cut-citations.txt` still resolves — the route check resolves
      `Backlog.md §6`, `§7`, `§9`, `§10` and each sub-section it was pointed at. Verify:
      `bash tools/check-doc-routes.sh` exits 0 and each of the four changes' citations appears in its resolved set.
- [x] 7.4 Verify the layer is honest: no rule remains in `Backlog.why.md` or `Backlog.recipes.md`, and no rationale
      or transcript remains in `Backlog.md`. Verify: the metric prints no purity condition for the document, and
      `grep -c 'Rule:' guidelines/Backlog.why.md guidelines/Backlog.recipes.md` is 0.
- [x] 7.5 Run the suites the touched files owe and name the exclusions:
      `bash tools/check-doc-routes.sh`, `bash tools/check-doc-routes-selftest.sh`,
      `bash tools/check-rule-routes.sh`, `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no
      instrumented run, no device and no pixel check — this delta touches no compiled file and no application code.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
- Then the four loop changes, whose section numbers this change kept in place.

## Task state — written by the parent session, 2026-10-31

The applying child ran every verification but never filled the boxes, so the boxes here were written in one pass by
the parent **from that child's per-task report**, which quotes the command and the observed line for each task, plus
the parent's own acceptance re-run (`guidelines/Backlog.md` 112 888 → 35 890 B, no section over 12 000,
`document-size` 5 → 4, purity for this document 0, heading set = pre-cut plus `§7a`…`§7e`, both checks exit 0). One
incident was resolved mid-change and is recorded in `../guideline-layers-mechanism/evidence/selftest-companion-exclusion.txt`.
