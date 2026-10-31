# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the cut's rules,
`../guideline-layers-mechanism/` for the guards every task clears. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/Build.md`'s section cost table and its `## ` heading-number set as
      `evidence/pre-cut.md`. Verify: the table matches `tools/check-doc-routes.sh --sizes`, with §7 at 12 052 B and
      the document at 166 383 B.
- [x] 1.2 Record every citation of this document outside `guidelines/` as `evidence/pre-cut-citations.txt` —
      `grep -rn 'Build\.md §' AGENTS.md openspec/changes/{change-readiness-gate,finding-shape-at-filing}/*.md` and
      every `.pi/skills/*/SKILL.md`. Verify: each line names a §-number the baseline set carries.

## 2. The gate document

- [x] 2.1 Create `guidelines/BuildGate.md` with §2 "Common behavior" and §2a–§2e, carrying their own numbers.
      Verify: `wc -c guidelines/BuildGate.md` is under 48 000 and every section under 12 000.
- [x] 2.2 Move the gate's rationale into `guidelines/BuildGate.why.md` — §2c's timing history, §2d's JVM criteria
      derivation, §2b's liveness-guard anecdotes — under anchors of the numbers they came from, and move the
      command transcripts to `guidelines/Build.recipes.md`. Verify: `tools/sensors/metrics/doc-context.sh` prints no
      purity condition for `BuildGate.md`
      (`layers#aFigureInANormativeDocumentIsACondition`, `layers#aWhyHeadingInANormativeDocumentIsACondition`).
- [x] 2.3 Name every rule in `BuildGate.md` and verify uniqueness:
      `grep -o '\*\*Rule: [^*]*' guidelines/BuildGate.md | sort | uniq -d` prints nothing
      (`rules#aNameTwiceInOneDocumentIsReported`).
- [x] 2.4 Verify the statement `guideline-layers-mechanism` removed from `AGENTS.md`'s `Agent iteration loop (measure
      first)` block is stated in `BuildGate.md` §2a, which is the section that block always summarised. Verify: the
      removed block's distinguishing tokens are found in §2a, the finding is recorded in
      `evidence/relocation-from-entry-document.txt`, and `bash tools/check-doc-routes.sh` exits 0.

## 3. The evidence document

- [x] 3.1 Create `guidelines/BuildEvidence.md` with §4, §4a–§4f, §7 and §11, carrying their own numbers; §7's
      12 052 bytes fall under the bound by its transcripts moving out. Verify: the document is under 48 000 and
      §7 is under 12 000.
- [x] 3.2 Move §4e's falsification case histories and the gate-timing records into `guidelines/BuildEvidence.why.md`
      under their anchors, and the tallies and XML samples into `guidelines/Build.recipes.md`. Verify: no purity
      condition for `BuildEvidence.md`, and the relocation count closes.
- [x] 3.3 Name every rule in `BuildEvidence.md`. Verify: no duplicate name in the document, and
      `bash tools/check-doc-routes.sh` exits 0 so every rule reference resolves
      (`rules#aReferenceResolvesToTheNamedRule`).

## 4. The test document

- [x] 4.1 Create `guidelines/BuildTests.md` with §6 and §6a–§6h, carrying their own numbers. Verify: under 48 000,
      every section under 12 000.
- [x] 4.2 Move each constraint's trap and case history into `guidelines/BuildTests.why.md` under its anchor, and the
      JUnit stub detail into `guidelines/Build.recipes.md`. Verify: no purity condition for `BuildTests.md`, and
      `layers#aCommandThatIsTheRuleIsNotACondition` — a command that *is* a constraint is not reported.
- [x] 4.3 Name every rule in `BuildTests.md`. Verify: no duplicate name, and the names do not collide with
      `Build.md`'s, `Backlog.md`'s or `UI.md`'s (`rules#aNameTwoDocumentsStateIsReported`).

## 5. The native document and the device recipes

- [x] 5.1 Create `guidelines/BuildNative.md` with §1, §10, §10f, §10j, §10k and §12, carrying their own numbers, and
      promote the six `### ` sub-topics inside §12 to lettered sub-sections under it — measured, the document
      carries nine `###` in all and six of them are inside §12 (`awk 'NR>=2028 && /^### /'` over the pre-cut file).
      Verify: under 48 000, every
      section under 12 000, and every promoted sub-section number resolves in the routing table.
- [x] 5.2 Move §10a–§10e, §10g–§10i's device recipes and §12's vcpkg command transcripts into
      `guidelines/BuildNative.recipes.md` under anchors of their numbers. Verify: the relocation count closes and no
      recipe is left in `BuildNative.md`.
- [x] 5.3 Keep §10f, §10j and §10k's rules in the normative document and name them. Verify: no purity condition for
      `BuildNative.md`, and the review can read the rule/recipe boundary of the §10 cluster in one diff.

## 6. The remainder of `Build.md`

- [x] 6.1 Reduce `guidelines/Build.md` to §1, §3, §5, §8 and §9, carry §9's transcripts into `Build.recipes.md` and
      its policy detail kept per the cut's predicate, and name every rule left. Verify: under 48 000, §9 under
      12 000, no duplicate rule name.
- [x] 6.2 Add the one-line index entry per moved cluster to `Build.md` — the number, and the document that took it.
      Verify: every number §1–§12 appears in exactly one of the five documents or in an index entry, checked with
      the baseline set from 1.1.

## 7. The entry document

- [x] 7.1 Retarget the `AGENTS.md` routing rows that name `Build.md` for a section that moved, and add one row per
      new document with its sections' cost stated as the table requires. Verify:
      `bash tools/check-doc-routes.sh` exits 0 and `bash tools/check-doc-routes-selftest.sh` exits 0.
- [x] 7.2 Verify every citation in `evidence/pre-cut-citations.txt` resolves by its new document name, and record
      the retargeting in the same file. Verify: no citation names a `Build*.md` section that document does not carry
      (`routes#aCompanionIsReadAndNotRouted` covers the companions' side of this).

## 8. Verification

- [x] 8.1 Verify every condition clears: `tools/sensors/metrics/doc-context.sh` prints no `document-size` and no
      `routed-section-size` condition for any `guidelines/Build*.md`. Record `evidence/conditions-cleared.log` with
      each document's byte count.
- [x] 8.2 Verify the heading-number check: the union of the five documents' `## ` heading numbers equals the
      baseline set plus the sub-sections §12 and §7 gain. Verify: the diff against `evidence/pre-cut.md` names only
      added sub-sections.
- [x] 8.3 Add the new documents' rules to `tools/doc-routes.json`. Verify: `tools/check-rule-routes.sh --list`
      reports no rule of this tree as unreached (`routes#aRuleNoGlobReachesIsReported`), and
      `tools/check-rule-routes.sh` exits 0.
- [x] 8.4 Run the suites the touched files owe and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no instrumented run, no device and no
      pixel check — no compiled file is touched, so no Kotlin, native or Auto path is affected.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
