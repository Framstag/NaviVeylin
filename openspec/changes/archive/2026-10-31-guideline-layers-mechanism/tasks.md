# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/documentation-ownership/spec.md` for the contract.
Every task's spec requirement and every delta scenario's case name is named in the task that lands it.

## 1. Baseline, before anything is edited

- [x] 1.1 Record the pre-change measurement as evidence and verify it against the figures the proposal quotes:
      `bash tools/check-doc-routes.sh --sizes` (8 routed sections past 12 000 B), `wc -c guidelines/*.md AGENTS.md`
      (5 documents past 48 000 B, `AGENTS.md` 26 646 B), and `tools/sensors/metrics/doc-context.sh` for the two
      existing rows. Write `evidence/pre-change-sizes.txt`. Verify: the counts in the file are 8 and 5.
- [x] 1.2 Record the pre-change `## ` heading-number set of every `guidelines/*.md` and of `AGENTS.md` as
      `evidence/pre-change-section-numbers.txt` — the comparison basis for `layers#noSectionNumberMoves`. Verify:
      the file lists 10 documents and `git status --short` shows no other change.
- [x] 1.3 Record the rule-name inventory as `evidence/pre-change-rule-names.txt`: every `**Rule: <name>**` the tree
      carries, with its file and section (expected: 5, all in `MapRendering.md` — `grep -c '\*\*Rule:' guidelines/*.md`
      is the authority; the `- **Never …**` bullets are *not* rule names but the unnamed population this change only
      makes nameable: Design 38, Build 3, Logging 3, UI 1, MapRendering 1). Verify: the file holds exactly 5 names,
      all distinct, and the unnamed population is recorded beside them as `evidence/pre-change-unnamed-rules.txt`.
- [x] 1.4 Record the working tree's pre-existing modification as `evidence/pre-change-working-tree.txt`
      (`git status --short`), so this change's own diff can be told apart from what was in the tree before it. Verify:
      the file names the modified tracked files, including `guidelines/UI.md`, and nothing this change creates.

## 2. The route check reads companions and rule names

Requirement: "A guideline document carries one kind of text", "A rule is named",
"Every guideline document is covered by the route check" (modified).

- [x] 2.1 Make `tools/check-doc-routes.sh` read `guidelines/*.why.md` and `guidelines/*.recipes.md` as companions
      of their base document: not a document a route must name, still scanned for anchors and skill paths.
      Verify: `routes#aCompanionIsReadAndNotRouted`.
- [x] 2.2 Resolve every `## §N <heading>` anchor a companion writes against its base document and report the
      companion, the anchor and the base document when the number is absent. Verify:
      `layers#aCompanionAnchorThatNoSectionCarriesIsReported` and
      `layers#aDroppedSectionIsReportedInItsCompanion`.
- [x] 2.3 Extract `**Rule: <name>**` per section and report a name stated twice in one document, or in two
      documents under `guidelines/`. Verify: `rules#aNameTwiceInOneDocumentIsReported` and
      `rules#aNameTwoDocumentsStateIsReported`.
- [x] 2.4 Resolve `see rule <File>.md §N "<name>"` and report a reference whose name the section does not carry,
      naming the names it does carry. Verify: `rules#aReferenceResolvesToTheNamedRule` and
      `rules#aStaleRuleReferenceIsReported`.
- [x] 2.5 Add the fixture cases to `tools/check-doc-routes-selftest.sh` — one fixture concern whose rule sits in
      the normative document and whose measurement sits in its `.why.md`, plus one case per refusal above, all
      offline in a scratch tree. Verify: `bash tools/check-doc-routes-selftest.sh` exits 0 and its case list
      carries every name this group names.
- [x] 2.6 Add one record per new refusal to `tools/check-doc-routes-mutations.json` and verify each fires:
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`.
- [x] 2.7 **Revert-check** — write a companion whose anchor names a section its base document lacks; the route
      check must fail naming the anchor (`layers#aCompanionAnchorThatNoSectionCarriesIsReported`). Restore, then
      `bash tools/check-doc-routes.sh` green. Record the two runs in `evidence/`.
- [x] 2.8 **Revert-check** — state one rule name in two documents; the route check must fail naming both
      (`rules#aNameTwoDocumentsStateIsReported`); and rename a rule a reference quotes, which must fail naming the
      stale reference (`rules#aStaleRuleReferenceIsReported`). Restore, then re-run green. Record both runs.
- [x] 2.9 Verify the changed-file map already covers the edited check, so no wiring task is owed here:
      `grep -c 'check-doc-routes' tools/loop/loop.conf` and `.github/workflows/build.yml`.
- [x] 2.10 Run the check over the pre-migration tree and verify it exits 0 — the landing proof of `design.md` D8:
      the 5 hand-written rule names are distinct, no companion exists, and no anchor is dangling. Record
      `evidence/route-check-on-pre-migration-tree.log` with its exit code.

## 3. The document bound and the purity condition

Requirement: "The routing cost is measured" (modified, `sensors#documentSizeRow`), "A normative document carries no
rationale and no procedure".

- [x] 3.1 Add the `document-size` row to `tools/sensors/sensors.json` — `greater-than`, threshold 48 000,
      `consecutive: 2`, `lifecycle: file+close`, `class: improvement`, `category: specs-and-process`,
      `fingerprint.fields: ["document"]`, served by `tools/sensors/metrics/doc-context.sh` with a
      `normative-document`-scope branch beside the existing `routed-section` and `entry-document` ones. Verify:
      the row's threshold is asserted in `tools/sensor-cases.sh`, and `tools/build-inputs.sh --check` still passes
      with the new row.
- [x] 3.2 Add the purity condition to the same metric: inside a normative `guidelines/<Concern>.md`, report a
      fenced block carrying a `tests=`/`failures=` tally or a timestamp, a `(measured YYYY-…)` figure, an
      `issue #N` reference, or a `### Why …` heading, each with file, line and token. Verify:
      `layers#aFigureInANormativeDocumentIsACondition` and `layers#aWhyHeadingInANormativeDocumentIsACondition`,
      and that a command which *is* the rule's statement is not reported — `layers#aCommandThatIsTheRuleIsNotACondition`.
- [x] 3.3 Add the cases for both rows to `tools/sensor-cases.sh` and their conditions to
      `tools/sensor-mutations.sh`. Verify: `bash tools/sensor-selftest.sh` and
      `bash tools/sensor-mutations.sh --list` exit 0.
- [x] 3.4 Run the metric over the pre-migration tree and verify it prints exactly **5** document conditions
      (`Build.md`, `Backlog.md`, `UI.md`, `MapRendering.md`, `Design.md`) and the purity conditions it finds, with
      a file and a line each. Record `evidence/sensor-conditions-on-pre-migration-tree.log` with the count.
- [x] 3.5 **Revert-check** — pad a fixture document past the bound; the metric must print its path and byte count
      (`sensors#documentSizeRow`). Restore, then re-run the row green. Record both runs.
- [x] 3.6 **Revert-check** — put a `(measured 2026-10-31)` figure inside a normative document; the metric must
      print the condition with its line (`layers#aFigureInANormativeDocumentIsACondition`). Restore, then re-run
      the row green. Record both runs.

## 4. The derived reading set

Requirement: "The review's reading set is derived from the change's paths".

- [x] 4.1 Write `tools/doc-routes.json`: a path glob to the sections and the rule names that own that path's
      conventions, reaching all 5 rule names the tree carries today. Verify: `tools/check-rule-routes.sh --list`
      prints 5 names and one glob at least per name, and the check exits 0.
- [x] 4.2 Write `tools/check-rule-routes.sh` — read-only, text-only, no build, no device, no network; exit 0 when
      every glob matches a tracked path and every named rule is reached, 1 when one is not, 2 on a usage error;
      `--list` prints the map, `--diff <range>` prints the set for a range. Read the map's path from
      `tools/tooling.json` rather than naming it as a literal. Verify: `bash tools/check-portability.sh` exits 0.
- [x] 4.3 Write `tools/check-rule-routes-selftest.sh` with one case per refusal and the pass cases, offline in a
      scratch tree. Verify: `routes#aGlobMatchingNoTrackedPathIsReported`,
      `routes#aRuleNoGlobReachesIsReported` and `routes#theReadingSetFollowsTheDiff` — a fixture change naming
      fewer sections than the map derives is reported by `--diff`.
- [x] 4.4 Write `tools/check-rule-routes-mutations.json`, one record per refusal. Verify:
      `bash tools/mutate.sh --mutations tools/check-rule-routes-mutations.json --list` exits 0.
- [x] 4.5 Wire the new check: add the `tools/doc-routes.json` path to `tools/tooling.json`, the suite edges for
      `tools/check-rule-routes*` and `tools/doc-routes.json` to `LOOP_GATE_SUITES` in `tools/loop/loop.conf`, and
      the step beside `tools/check-doc-routes.sh` in `.github/workflows/build.yml`. Verify:
      `bash tools/check-action-runtimes.sh` exits 0, and the suite edges resolve with a **bounded** invocation:
      `timeout 300 bash tools/loop-selftest.sh --list` plus `timeout 300 bash tools/loop-selftest.sh --case 'gate#*'`.
      Do **not** run the whole `tools/loop-selftest.sh`: measured, one full run outlived a 900 s tool cap and had to
      be killed, and its evidence bought nothing the two bounded invocations do not.
- [x] 4.6 Run `tools/check-rule-routes.sh` over the pre-migration tree and verify it exits 0 — the other half of
      the D8 landing proof. Record `evidence/rule-routes-on-pre-migration-tree.log` with its exit code.
- [x] 4.7 **Revert-check** — name a glob under which the tree holds no file; the check must fail naming the glob
      (`routes#aGlobMatchingNoTrackedPathIsReported`). Restore, then re-run green. Record both runs.
- [x] 4.8 **Revert-check** — remove the glob that reaches one rule name; the check must fail naming the rule and
      its section (`routes#aRuleNoGlobReachesIsReported`). Restore, then re-run green. Record both runs.

## 5. The review reads the derived set

Requirement: "The review's reading set is derived from the change's paths".

- [x] 5.1 Rewire `tools/loop/prompts/reviewer.md` item 7 to read the set `tools/check-rule-routes.sh --diff`
      derives from the diff, to grade per rule name, and to record a section the change adds to that set as an
      addition rather than as a substitution. Verify: `grep -n 'check-rule-routes' tools/loop/prompts/reviewer.md`
      names the command, and the prompt still states its read-only contract and its eight graded items.
- [x] 5.2 Verify the loop's own suite is green with the prompt edited:
      `bash tools/loop-selftest.sh --from-git HEAD~1` exits 0, and `bash tools/loop/evidence-check.sh --selftest`
      exits 0.

## 6. The entry document

Requirement: "The entry document is bounded by what a session needs before it reads anything else" (shipped),
"A concern has one owning document" (shipped, for the companion convention).

- [x] 6.1 Reduce `Backlog maintenance` (1 777 B), `Agent iteration loop (measure first)` (3 351 B) and the per-area
      half of `Key Conventions` (5 405 B) in `AGENTS.md` to one sentence and the route each — **removal only, and
      nothing written under `guidelines/`**, because the six changes that own those documents are what cut them. A
      block whose statement is found in no guideline stays in place and is recorded as a rule without an owner.
      Verify: the removed-block count equals the statements found in the owning documents
      (`guidelines/Backlog.md`, `guidelines/Build.md` §2a and §9, `guidelines/Logging.md`, `guidelines/MapRendering.md`
      §15, `guidelines/Design.md`), the finding is recorded block by block in `evidence/relocation-entry-document.txt`,
      `git status --short -- guidelines/` names only the pre-existing modification recorded in task 1.4, and
      `wc -c AGENTS.md` is below 32 000.
- [x] 6.2 Add the companion convention to the `## Documentation Map` region: one row naming the companion suffix
      convention and what a reader finds there, so a reader reaches the rationale without a route per companion
      file — a companion is not a routed document, so no per-file row is owed. Verify:
      `bash tools/check-doc-routes.sh` exits 0 and no companion is reported as unrouted.
- [x] 6.3 Re-measure what the diet moved and verify the routing table's own figures still agree with the ruler:
      `bash tools/check-doc-routes.sh --sizes` — every cost a row states equals the section's byte count. Verify:
      the check exits 0 and `AGENTS.md`'s own section costs are re-measured under the entry bound.

## 7. Integration verification

- [x] 7.1 Run the shell suites this change owes and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-rule-routes-selftest.sh`, `bash tools/sensor-selftest.sh`,
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
      `bash tools/mutate.sh --mutations tools/check-rule-routes-mutations.json --list`,
      `bash tools/check-portability.sh`, `bash tools/check-todo-references.sh`. Excluded, and why: no `./gradlew`
      target, no instrumented run, no device and no pixel check — this delta touches no compiled file
      (`guidelines/Build.md` §2a's scope block), so no Kotlin, native or Auto path is affected.
- [x] 7.2 Verify `layers#noSectionNumberMoves`: recompute the `## ` heading-number set of every document and diff
      it against `evidence/pre-change-section-numbers.txt`. Verify: the diff is empty for every document.
- [x] 7.3 Verify the landing proof of `design.md` D8 in one run: `tools/check-doc-routes.sh`,
      `tools/check-rule-routes.sh` and `tools/check-portability.sh` exit 0 on the pre-migration tree, while the
      metric prints the 5 document conditions and the purity conditions. Record
      `evidence/landing-proof.log` with all five exit codes and the condition count.
- [x] 7.4 Verify `openspec validate guideline-layers-mechanism --strict` exits 0 and
      `openspec validate --changes --strict` reports no new failure among the open changes.
- [x] 7.6 **Added during apply, 2026-10-31.** `tools/check-doc-routes-selftest.sh`'s ruler helper `section_spans`
      iterated `guidelines/*.md` including companions, so the case `routed-read#bound` counted a companion's `## §N`
      anchors as sections — 165 measured against the report's 151, the day the first companion existed
      (`guideline-backlog-layers`). The check itself (`--sizes`) excluded companions correctly; the self-test's own
      copy of the ruler had not. Verify: `bash tools/check-doc-routes-selftest.sh` passes every case with a companion
      present, the exclusion has one home rather than two, and the incident with its before/after numbers is recorded
      in `evidence/selftest-companion-exclusion.txt`. The same question was asked of the metric's `normative-purity`
      scope, whose total (101 before the first companion) must not rise because a companion was added.
      **Lesson for the design:** the mechanism's D8 landing proof was green because no companion existed yet — a
      guard can be untested in exactly the dimension the next change introduces, and the first cut is where that
      surfaces.
- [x] 7.5 Verify none of the six document changes the proposal names is a prerequisite for this change: the
      checks, the two metric rows, the map and the reviewer prompt pass with no document migrated. Verify: the
      suites of 7.1 pass on the pre-migration tree with `guidelines/` untouched except `AGENTS.md`, and
      `git diff --name-only -- guidelines/` names only `AGENTS.md`.

## Workflow follow-up

- Independent read-only review before archive, per the project's review requirements; archive the change.
- Then land `guideline-backlog-layers`, which retires the `Backlog.md` document condition and unblocks
  `shaping-inbox`, `change-readiness-gate`, `loop-consumes-a-prepared-change` and `finding-shape-at-filing`.
- Then the remaining five document changes, largest first.

- [x] 7.7 **Retune — apply phase, 2026-10-31, at the owner's instruction.** The document bound is **32 000** bytes:
      the entry document's own bound, so no document a route names costs more than the document a session is handed
      before it reads anything else. Edits: `tools/sensors/sensors.json` (`document-size` threshold),
      `tools/sensor-cases.sh` (its assert and its message), this change's delta sentence and `design.md` D4, which
      records the retune and reverses its own option D on the re-measured arithmetic (the routing table has 7 104 B
      of headroom, so the rows the extra documents need fit). Verify: `tools/sensor-selftest.sh` — **all 75 cases
      passed**; the metric reports exactly **4** conditions and they are `UIPhone 47926`, `MapRendering 46751`,
      `UI 45324`, `Backlog 35890`; `openspec validate guideline-layers-mechanism --strict` valid. Those four each owe
      a cut of their own — `guideline-uiphone-cut`, `guideline-maprendering-cut`, `guideline-ui-cut`,
      `guideline-backlog-cut` — and task 3.1 above stands as history: it records the bound as it was first landed.

## Task state — written by the parent session, 2026-10-31

The applying child ran every verification but never filled the boxes, so the boxes here were written in one pass by
the parent **from that child's per-task report**, which quotes the command and the observed line for each task, plus
the parent's own acceptance re-run (`check-doc-routes.sh` → 169 refs, 0 exit; `check-rule-routes.sh` → 5 named rules,
0 exit; `document-size` 5 conditions; selftest 32 cases). Four corrections were sent back and applied: the rule-name
count corrected to 5, the duplicated `fail_check` in `doc-context.sh` deleted, the `normative-purity` scope's
companion exclusion confirmed by measurement, and task 7.6 added for the self-test ruler fix. Task 7.5 was verified
**before** any document was migrated and cannot be re-verified now that the six cuts have landed — its evidence is
the log it wrote at the time.
