# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the cut's rules,
`../guideline-layers-mechanism/` for the guards every task clears. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/Design.md`'s section cost table and its `## ` heading-number set as
      `evidence/pre-cut.md`. Verify: the table matches `tools/check-doc-routes.sh --sizes` with §5 at 12 781 B and
      §8 at 12 058 B.
- [x] 1.2 Record every citation of this document as `evidence/pre-cut-citations.txt` —
      `grep -rn 'Design\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md openspec/specs -r`. Verify: each line
      names a §-number the baseline set carries.
- [x] 1.3 Record the pre-cut rule inventory: `grep -c '\*\*Rule:' guidelines/Design.md` → **0**, so this document
      carries no named rule and this change is where its rules gain names. Record the unnamed population instead —
      `grep -cE '^[-*] \*\*(Never|Do not|Always|MUST)'` → 38 and `grep -cE '(SHALL|MUST)'` → 36 — as
      `evidence/pre-cut-unnamed-rules.txt`. Verify: the three numbers are quoted from those commands, and no rule
      name is recorded as pre-existing.

## 2. The rationale leaves first

- [x] 2.1 Create `guidelines/Design.why.md` with §13 "Superseded decisions" and Appendix B "Provenance" moved whole
      under anchors of the numbers they belong to, and each remaining section's rationale added under its own
      anchor. Verify: `tools/sensors/metrics/doc-context.sh` prints no purity condition for `Design.md`
      (`layers#aFigureInANormativeDocumentIsACondition`, `layers#aWhyHeadingInANormativeDocumentIsACondition`).
- [ ] 2.2 **Left open, with its finding recorded — see `evidence/blocked-2.2-finding.md`.** The premise is real:
      `Design.why.md §13` records five reversals and each has a rule born from it. The fix does not belong to this
      change, because four of those five rules live in documents owned by changes that have already landed
      (`MapRendering.md §7`/`§8`, `UI.md`, `Regulatory.md`) — an inline pointer would write into a finished change's
      files from this one. `bash tools/check-doc-routes.sh` exits 0 regardless
      (`rules#aReferenceResolvesToTheNamedRule`). The review is asked to grade it as a recorded residual: accept (§13
      is one hop from the row that routes the document), or add the five one-line pointers in a follow-up change that
      owns all four documents.
- [x] 2.3 Create `guidelines/Design.recipes.md` with Appendix A's apply checklist and §2's "Adding to the stack"
      recipes, under the anchors of the sections they came from. Verify: the relocation count closes in
      `evidence/relocation-design-recipes.txt`.

## 3. The native boundary

- [x] 3.1 Create `guidelines/DesignNative.md` with §5 split into `§5a` and `§5b` — the boundary's shape, and the two
      database-open entries with their asymmetry whole in one sub-section. Verify: §5 and each sub-section are under
      12 000 B, and the document is under 48 000 B.
- [x] 3.2 Move the asymmetry's rationale — why the two entries differ and what each cost — into
      `guidelines/DesignNative.why.md` under its anchor, keeping the asymmetry rule itself in the normative
      document. Verify: no purity condition for `DesignNative.md`, and the rule still states the asymmetry
      (`layers#aCommandThatIsTheRuleIsNotACondition` covers the boundary's own commands).
- [x] 3.3 Name every rule in `DesignNative.md`. Verify: no duplicate name in it
      (`rules#aNameTwiceInOneDocumentIsReported`), and none colliding with `BuildNative.md`'s
      (`rules#aNameTwoDocumentsStateIsReported`).

## 4. The cross-variant seams

- [x] 4.1 Create `guidelines/DesignCrossVariant.md` with §6, §7, §8 split into `§8a` and `§8b`, §10 and §11, carrying
      their own numbers. Verify: under 48 000 B; if it lands over, move §11 to `Design.md` per `design.md`'s last risk
      and re-measure.
- [x] 4.2 Keep every seam that is a pointer a pointer: §6, §7 and §9's references to `MapRendering.md` and `UI.md`
      stay references rather than becoming restatements. Verify:
      `grep -c 'MUST\|SHALL' guidelines/DesignCrossVariant.md` does not grow against the baseline, and
      `bash tools/check-doc-routes.sh` exits 0 so every reference resolves.

## 5. The stack and the data rules

- [x] 5.1 Create `guidelines/DesignStack.md` with §2's rules and §9 "Data, persistence & search", carrying their own
      numbers — the recipes having left in task 2.3. Verify: under 48 000 B, every section under 12 000 B.
- [x] 5.2 Name every rule in `DesignStack.md`. Verify: no duplicate within it, and
      `rules#aReferenceResolvesToTheNamedRule` — a data rule that `Backlog.md` or `MapRendering.md` already owns is
      left as a reference.

## 6. `Design.md` holds its own subject

- [x] 6.1 Reduce `guidelines/Design.md` to §1, §3, §4 and §12 plus its index entries for the numbers the other three
      documents take. Verify: under 48 000 B and every section under 12 000 B.
- [x] 6.2 Verify no rule name of the four documents collides with one another document owns — `check-doc-routes.sh`
      fails on a duplicate, so let it be the referee — and that the pre-cut unnamed population is accounted for:
      each of the 38 bold-imperative bullets and 36 obligation lines in `evidence/pre-cut-unnamed-rules.txt` is either
      a named rule now or recorded as deliberately left unnamed, with the reason. Verify: the check exits 0 and no
      item of that file is unaccounted for. **Recorded 2026-10-31:** `bash tools/check-doc-routes.sh` exits 0
      (`check-doc-routes: all 307 section references resolve`), and the 38/36 population in
      `evidence/pre-cut-unnamed-rules.txt` is accounted for item by item there and in `evidence/post-cut.md`.

## 7. The entry document

- [x] 7.1 Retarget the `AGENTS.md` rows that name `Design.md` for a section that moved, **including the row that
      names §2's "Adding to the stack" recipes, which now names `Design.recipes.md`**, and add one row per new
      document with its sections' cost stated. Verify: `bash tools/check-doc-routes.sh` and
      `bash tools/check-doc-routes-selftest.sh` exit 0, and the recipes row's target exists.
- [x] 7.2 Verify each citation in `evidence/pre-cut-citations.txt` resolves to the document that now carries its
      number. Verify: no citation names a `Design*.md` section that document does not carry.
- [x] 7.3 Add the four documents' rules to `tools/doc-routes.json` for the architecture paths. Verify:
      `tools/check-rule-routes.sh --list` reports no design rule as unreached
      (`routes#aRuleNoGlobReachesIsReported`).

## 8. Verification

- [x] 8.1 Verify every condition clears: `tools/sensors/metrics/doc-context.sh` prints no `document-size` and no
      `routed-section-size` condition for any `guidelines/Design*.md`. Record `evidence/conditions-cleared.log` with
      each document's byte count.
- [x] 8.2 Verify the heading-number check: the union of the four documents' `## ` heading numbers equals the
      baseline set plus the sub-sections §5 and §8 gain. Verify: the diff against `evidence/pre-cut.md` names only
      added sub-sections.
- [x] 8.3 Verify no decision's text changed meaning: the superseded decisions in `Design.why.md` §13 and the
      provenance in its Appendix B are byte-identical to `evidence/pre-cut.md`'s copies but for their anchors.
      Verify: a diff shows only heading lines added.
- [x] 8.4 Run the suites the touched files owe and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no native build, no instrumented run
      and no device — no module, dispatcher, native signature or surface is edited.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
