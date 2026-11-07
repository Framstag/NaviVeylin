# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the cut's rules,
`../guideline-layers-mechanism/` for the guards every task clears. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/MapRendering.md`'s section cost table and its `## ` heading-number set as
      `evidence/pre-cut.md`, marking the duplicated `§14`. Verify: the table matches
      `tools/check-doc-routes.sh --sizes` with §1 at 22 592 B and two sections numbered 14.
- [x] 1.2 Record every citation of this document as `evidence/pre-cut-citations.txt` —
      `grep -rn 'MapRendering\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md` — with the bare `§14`
      citations marked. Verify: each line names a §-number the baseline set carries, and the bare `§14` lines are
      each attributed to the handoff or to the car renderer by hand, with the attribution recorded.
- [x] 1.3 Record the five existing rule names verbatim as `evidence/pre-cut-rule-names.txt`. Verify: five names, all
      distinct, each with its section.

## 2. §1 is split under its own number

- [x] 2.1 Split §1 into `§1a`…`§1e`, promoting `### 1.1 Follow framing` to `§1a` and distributing the rest by
      question, keeping §1 as an index of them. Verify: §1 and every sub-section is under 12 000 B in
      `tools/check-doc-routes.sh --sizes`, and `MapRendering.md §1` still resolves.
- [x] 2.2 Move the history out: the paragraphs about what a mistake cost ("caused jumps multiple times"), each
      pitfall's derivation and each recorded reversal go to `guidelines/MapRendering.why.md` under anchors of their
      numbers, and the parameter table and measurement recipes to `guidelines/MapRendering.recipes.md`. Verify:
      `tools/sensors/metrics/doc-context.sh` prints no purity condition for `MapRendering.md`.
- [x] 2.3 Verify the five rule names survive verbatim: diff `evidence/pre-cut-rule-names.txt` against the names the
      document carries. Verify: the diff is empty, and `bash tools/check-doc-routes.sh` exits 0
      (`rules#aReferenceResolvesToTheNamedRule`).

## 3. `MapFollow.md`

- [x] 3.1 Create `guidelines/MapFollow.md` with §3, §6, §7, §8, §9, §10 and §11, carrying their own numbers.
      Verify: under 48 000 B, every section under 12 000 B.
- [x] 3.2 Move its rationale to `guidelines/MapFollow.why.md` — why a bearing is smoothed, why a marker is deduped,
      the measurement behind each angle bound — under the same anchors. Verify: no purity condition for
      `MapFollow.md`, and the relocation count closes in `evidence/relocation-mapfollow.txt`.
- [x] 3.3 Name every rule in `MapFollow.md`. Verify: no duplicate name in it, and
      `rules#aNameTwoDocumentsStateIsReported` finds no collision with the other three rendering documents.

## 4. `MapCar.md`

- [x] 4.1 Create `guidelines/MapCar.md` with the second §14 (the Android Auto renderer, 11 744 B) and §18, carrying
      their own numbers. Verify: under 48 000 B and every section under 12 000 B.
- [x] 4.2 Verify the duplicate number is resolved: `grep -c '^## 14\.' guidelines/MapRendering.md
      guidelines/MapCar.md` reports one each, and `bash tools/check-doc-routes.sh` exits 0 with no ambiguous
      reference to a bare `§14` anywhere in the tree.
- [x] 4.3 Verify the bare `§14` citations from task 1.2 point at the document their attribution recorded, and
      retarget the ones that meant the car renderer to `MapCar.md §14`, recording both in
      `evidence/pre-cut-citations.txt`. Verify: each citation resolves to the section it means.

## 5. `MapStyling.md`

- [x] 5.1 Create `guidelines/MapStyling.md` with §15, §15a, §16, §16a, §16b and §17, carrying their own numbers.
      Verify: under 48 000 B, every section under 12 000 B.
- [x] 5.2 Move the failed-stylesheet-load history and the packaging failures behind §15a and §16a into
      `guidelines/MapStyling.why.md` under their anchors, and the icon-resolution procedure into a recipes companion
      if it is a procedure under the cut's predicate. Verify: no purity condition for `MapStyling.md`, and no
      load-failure *rule* moved out (`layers#aCommandThatIsTheRuleIsNotACondition`).

## 6. `MapRendering.md` holds its own subject

- [x] 6.1 Reduce `guidelines/MapRendering.md` to §1, §2, §4, §5, §12, §13, §14 (handoff), §19 and §20 plus its index
      entries for the numbers the other three documents take. Verify: under 48 000 B; if it lands over, move §20 to
      `MapFollow.md` per `design.md`'s second risk and re-measure.
- [x] 6.2 Verify the regression checklist is routed or referenced rather than searched for: its new home in
      `MapRendering.why.md` is named by the routing table's rendering row or by a rule that points at it. Verify:
      `grep -rn 'Known Pitfalls' guidelines/*.why.md AGENTS.md` finds the reference and
      `bash tools/check-doc-routes.sh` exits 0.

## 7. The entry document

- [x] 7.1 Retarget the `AGENTS.md` rendering rows, add one row per new document with its sections' cost stated, and
      drop the heading text the duplicate `§14` row needed. Verify: `bash tools/check-doc-routes.sh` and
      `bash tools/check-doc-routes-selftest.sh` exit 0.
- [x] 7.2 Add the four documents' rules to `tools/doc-routes.json` for the rendering paths. Verify:
      `tools/check-rule-routes.sh --list` reports no rendering rule as unreached and
      `tools/check-rule-routes.sh` exits 0 (`routes#aRuleNoGlobReachesIsReported`).

## 8. Verification

- [x] 8.1 Verify every condition clears: `tools/sensors/metrics/doc-context.sh` prints no `document-size` and no
      `routed-section-size` condition for any `guidelines/Map*.md`. Record `evidence/conditions-cleared.log` with
      each document's byte count.
- [x] 8.2 Verify the heading-number check: the union of the four documents' `## ` heading numbers equals the
      baseline set plus the sub-sections §1 gains, and the duplicated-number count is 0. Verify: the diff against
      `evidence/pre-cut.md` names only added sub-sections.
- [x] 8.3 Verify every citation in `evidence/pre-cut-citations.txt` resolves to the document that now carries its
      number, including the attributions made in task 1.2. Verify: no citation names a `Map*.md` section that
      document does not carry.
- [x] 8.4 Run the suites the touched files owe and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no native or instrumented run, no
      device and **no `pixel-check`** — no rendering path, bound, epoch or diagnostic line is edited, so no pixel
      verdict is owed.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
