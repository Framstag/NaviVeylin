# Tasks

Reference: `proposal.md` for what each document carries, `design.md` for the cut's rules,
`../guideline-layers-mechanism/` for the guards every task clears. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record each of the four documents' section cost table, `## ` heading-number set and every dated figure,
      issue reference and fenced block as `evidence/pre-cut.md`. Verify: the tables match
      `tools/check-doc-routes.sh --sizes` and the four byte counts are those the proposal states.
- [x] 1.2 Record every citation of the four documents as `evidence/pre-cut-citations.txt` —
      `grep -rnE '(Regulatory|FeatureList|Metrics|Logging)\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md openspec/specs -r`
      — and the routing table's rows for these four documents verbatim as `evidence/pre-cut-routing-rows.txt`.
      Verify: each citation line names a §-number the baseline set carries, and the row file holds one row per
      document.
- [x] 1.3 Record the three existing rule names verbatim, with their sections, as
      `evidence/pre-cut-rule-names.txt`. Verify: three names, all distinct.
- [x] 1.4 Record the purity conditions the metric prints for the four documents before the cut, with file and line,
      as `evidence/pre-cut-purity.txt`. Verify: the file lists at least one condition per document, or names the
      document as clean.

## 2. `Logging.md`

- [x] 2.1 Create `guidelines/Logging.recipes.md` with the logcat recipes and the NDK bridge's configuration detail,
      under anchors of the sections they came from, leaving §3's coordinate rule where it is. Verify:
      `tools/sensors/metrics/doc-context.sh` prints no purity condition for `Logging.md`, and
      `grep -n 'coordinate' guidelines/Logging.md` still finds the rule in §3.
- [x] 2.2 Name every rule in `Logging.md`, keeping the two existing names verbatim. Verify: the names in
      `evidence/pre-cut-rule-names.txt` for this document are unchanged, and no name is duplicated in it or across
      `guidelines/*.md` (`rules#aNameTwoDocumentsStateIsReported`).

## 3. `Metrics.md`

- [x] 3.1 Create `guidelines/Metrics.recipes.md` with the invocations and their sample output, leaving every metric
      definition in place. Verify: no purity condition for `Metrics.md`, and the relocation count closes in
      `evidence/relocation-metrics.txt`.
- [x] 3.2 Name every rule in `Metrics.md`, and add its rules to `tools/doc-routes.json`. Verify:
      `tools/check-rule-routes.sh --list` reports no rule of this document as unreached
      (`routes#aRuleNoGlobReachesIsReported`).

## 4. `FeatureList.md`

- [x] 4.1 Create `guidelines/FeatureList.recipes.md` with the generator's invocations and its gate commands, and
      `guidelines/FeatureList.why.md` for the classification vocabulary where it is rationale rather than rule.
      Verify: no purity condition for `FeatureList.md`, and each moved block's receiving document is recorded.
- [x] 4.2 Name every rule in `FeatureList.md`, keeping the classification vocabulary terms exactly as they are —
      they are a closed vocabulary a gate reads. Verify: `bash tools/gen-feature-list.sh --check-classification`
      exits 0 unchanged, and no rule name duplicates one in another document.

## 5. `Regulatory.md`

- [x] 5.1 Create `guidelines/Regulatory.why.md` (the inventory history, the review record with its dates, why a duty
      was added) and `guidelines/Regulatory.recipes.md` (the source list, the checklists, the review commands), under
      anchors of their sections. Verify: no purity condition for `Regulatory.md`.
- [x] 5.2 Cut §9 "Repo inventory and open items" so its rules stay in the document and its inventory and review
      tables move — §9 is routed, so a reader sent to it must find a rule, not a date table. Verify: §9 remains
      routed, is under 12 000 B, and every rule it held is read one by one against `evidence/pre-cut.md`.
- [x] 5.3 Name every rule in `Regulatory.md`, keeping the existing name verbatim. Verify: the name survives, no
      duplicate in the document, and every duty a build gate or a platform contract enforces is a named rule rather
      than a sentence.

## 6. Verification

- [x] 6.1 Verify every condition clears: `tools/sensors/metrics/doc-context.sh` prints no purity condition and no
      size condition for any of the four documents, and each is under 48 000 B with every section under 12 000.
      Record `evidence/conditions-cleared.log` with the byte counts and the metric's output. Verify against
      `evidence/pre-cut-purity.txt`: every condition it listed is gone.
- [x] 6.2 Verify the heading-number check: each document's `## ` heading-number set is unchanged. Verify: the
      diff against `evidence/pre-cut.md` is empty for all four.
- [x] 6.3 Verify the layer is honest: no rule remains in a companion and no transcript or dated figure remains in a
      normative document. Verify: `grep -c 'Rule:' guidelines/{Regulatory,FeatureList,Metrics,Logging}.*.md` is 0,
      and the metric's purity output is empty.
- [x] 6.4 Verify the citations: every line of `evidence/pre-cut-citations.txt` still resolves, and the routing
      table's rows for these four documents are byte-identical to the copies in
      `evidence/pre-cut-routing-rows.txt` — this change adds no row for them and retargets none. Verify:
      `bash tools/check-doc-routes.sh` exits 0, the rows match the recorded copies, and this change edits no file
      outside `guidelines/` **except `tools/doc-routes.json`**, which task 3.2 requires it to extend with the globs
      for the rules it names and which `AGENTS.md` does not carry. (The literal earlier form of this task said "no
      file outside `guidelines/`", which contradicted task 3.2 — the applying worker flagged the contradiction;
      the exception is the intended scope.)
- [x] 6.5 Run the suites the touched files owe and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-todo-references.sh`, `bash tools/gen-feature-list.sh --check-classification`. Excluded: no
      `./gradlew` target, no instrumented run and no device — no compiled file, no gate source and no licence input
      is edited, so no tally is owed and no pixel is read.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
