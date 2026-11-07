# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the cut's rules,
`../guideline-layers-mechanism/` for the guards every task clears. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/UI.md`'s section cost table, its `## ` heading-number set and its three `### Why …`
      headings as `evidence/pre-cut.md`, with the exact text of the parity rule in §1. Verify: the table matches
      `tools/check-doc-routes.sh --sizes` with §1 at 26 015 B, §8 at 20 271 B and §7a at 13 867 B.
- [x] 1.2 Record every citation of this document as `evidence/pre-cut-citations.txt` —
      `grep -rn 'UI\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md`. Verify: each line names a §-number the
      baseline set carries, and the phone rows and car rows name disjoint sets.

## 2. §1 is split under its own number

- [x] 2.1 Split §1 into `§1a`…`§1f` — the parity rule, the favourites hierarchy, the row and card actions, the
      starred chip and drag checklist, the vehicle-position setting, the notification rules — keeping §1 as an index
      of them. Verify: §1 and every sub-section is under 12 000 B in
      `tools/check-doc-routes.sh --sizes`, and `UI.md §1` still resolves.
- [x] 2.2 Move the three `### Why …` blocks and each sub-section's parity-decision history into
      `guidelines/UI.why.md` under anchors of the numbers they came from, and the UI-dump recipes into
      `guidelines/UI.recipes.md`. Verify: `tools/sensors/metrics/doc-context.sh` prints no purity condition for
      `UI.md` (`layers#aFigureInANormativeDocumentIsACondition`,
      `layers#aWhyHeadingInANormativeDocumentIsACondition`).
- [x] 2.3 Verify the parity rule survived the move: the text recorded in task 1.1 is present in `§1a` unchanged but
      for its references, and it still demands one case per named surface or an explicit host substitute. Verify:
      a diff of the rule's text against `evidence/pre-cut.md` shows only reference changes, recorded in
      `evidence/parity-rule-preserved.txt`.

## 3. `UIPhone.md`

- [x] 3.1 Create `guidelines/UIPhone.md` with §5, §6a, §7, §7a, §8, §8a, §10a and §11, carrying their own numbers.
      Verify: under 48 000 B; if it lands over, move §11 or §10a to `UI.md` per `design.md` D6's last risk and
      re-measure.
- [x] 3.2 Split §8 into lettered sub-sections, one per overlay it asks about, and §7a into one per mode. Verify:
      every section of `UIPhone.md` is under 12 000 B and `UI.md §8` and `§7a` no longer resolve there.
- [x] 3.3 Move the measured pixel tables to `guidelines/UIPhone.why.md` under their section's anchor, keeping the
      bounds themselves — a minimum touch target, a driver-seat readability floor — in the normative document.
      Verify: no purity condition for `UIPhone.md`, and every moved table's rule keeps a reference to it
      (`rules#aReferenceResolvesToTheNamedRule`).
- [x] 3.4 Move the phone recipes into `guidelines/UIPhone.recipes.md`. Verify: the relocation count closes —
      blocks removed equal statements found — recorded in `evidence/relocation-uiphone.txt`.

## 4. The rules are named

- [x] 4.1 Name every rule in `guidelines/UI.md` and `guidelines/UIPhone.md`. Verify: no duplicate within either
      document and none across `guidelines/*.md`
      (`rules#aNameTwiceInOneDocumentIsReported`, `rules#aNameTwoDocumentsStateIsReported`).
- [x] 4.2 Add the two documents' rules to `tools/doc-routes.json`. Verify: `tools/check-rule-routes.sh --list`
      reports no rule of this tree as unreached (`routes#aRuleNoGlobReachesIsReported`).

## 5. The entry document

- [x] 5.1 Retarget `AGENTS.md`'s phone rows to `UIPhone.md`, keep the parity and car rows on `UI.md`, and add one
      row per new document with its sections' cost stated. Verify: `bash tools/check-doc-routes.sh` exits 0 and
      `bash tools/check-doc-routes-selftest.sh` exits 0.
- [x] 5.2 Verify each citation in `evidence/pre-cut-citations.txt` resolves to the document that now carries its
      number, and record the retargeting in the same file. Verify: no citation names a `UI*.md` section that
      document does not carry.

## 6. Verification

- [x] 6.1 Verify every condition clears: `tools/sensors/metrics/doc-context.sh` prints no `document-size` and no
      `routed-section-size` condition for `guidelines/UI.md` or `guidelines/UIPhone.md`, and neither is past the
      bound. Record `evidence/conditions-cleared.log` with both byte counts.
- [x] 6.2 Verify the heading-number check: the union of both documents' `## ` heading numbers equals the
      baseline set plus the sub-sections §1, §8 and §7a gain. Verify: the diff against `evidence/pre-cut.md` names
      only added sub-sections.
- [x] 6.3 Verify the parity requirement's own check still passes: the phone and car rows in `AGENTS.md` name
      disjoint section sets and every one resolves, so a change that names both surfaces still finds both.
- [x] 6.4 Run the suites the touched files owe and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no instrumented run, no device and **no
      `pixel-check`** — no composable, size or bound changes value in this change, so there is no pixel to measure.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
