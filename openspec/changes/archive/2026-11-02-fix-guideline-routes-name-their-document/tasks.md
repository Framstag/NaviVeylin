# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/documentation-ownership/spec.md` for the contract.
Every task's spec requirement and every delta scenario's case name is named in the task that lands it.

## 1. Baseline, before anything is edited

- [x] 1.1 Record what one guideline edit derives on base `650028bf`, and record the tree's own tallies, as
      `evidence/baseline-derived-sets.txt`:
      `bash evidence/derive-probe.sh tools/doc-routes.json guidelines/Logging.md guidelines/Logging.recipes.md
      guidelines/BuildGate.md guidelines/DesignStack.md guidelines/UI.md tools/loop/state.sh tools/issues/lib.sh
      tools/sensors/run.sh`. Verify: `guidelines/Logging.md` reads `entries: 270` naming 10 documents,
      `guidelines/Logging.recipes.md` `entries: 258` naming 9 documents and not `Logging`, and the `--list` line is
      `27 glob(s), 576 named rule(s) reached`.
- [x] 1.2 Record the two invariants green on base — `bash tools/check-rule-routes.sh` — in the same file. Verify: the
      file's last line is `every glob matches the tree and all 576 named rule(s) are reached`.

## 2. The red case

Requirement: "The review's reading set is derived from the change's paths", scenario "One guideline edit derives its
own document".

- [x] 2.1 Add `routes#aGuidelineEditDerivesItsOwnDocument` to `tools/check-rule-routes-selftest.sh`: copy this tree's
      `tools/tooling.json`, `tools/doc-routes.json` and `guidelines/` into a throwaway git tree, commit one appended
      sentence per document, and derive each with `--diff HEAD~1`; the derived entries must name the edited
      document's concern and no other. Update the file's header, which claimed no case reads the project's own map.
      Verify: `bash tools/check-rule-routes-selftest.sh --case routes#aGuidelineEditDerivesItsOwnDocument` exits 1 on
      base and prints the three failures — recorded as `evidence/red-on-HEAD.log`
      (`3 of 3 cases failed`, 2026-11-02T04:13:36Z).
- [x] 2.2 Confirm the failure is the defect and not the fixture: on base, the three edits report `270`, `258` and
      `258` entries naming ten, nine and nine documents, which is what `--diff` prints for those paths
      (`evidence/baseline-derived-sets.txt`). Verify: the numbers agree between the two files.

## 3. The map's globs name their document

Requirement: the same requirement's granularity sentence.

- [x] 3.1 Rewrite the twelve `guidelines/` globs in `tools/doc-routes.json`: `<Doc>.*` where the route owns one
      document (MapRendering, BuildGate, BuildEvidence, BuildTests, BuildNative, Build, Logging, FeatureList,
      Metrics, Regulatory), and the concern family where it owns one (`guidelines/Backlog.*`, `guidelines/Design*`);
      `guidelines/UI*.md` is already that shape and is unchanged. Verify: `jq -e '.routes|length'` is `27` and
      `bash tools/check-rule-routes.sh` exits 0 with the same 576 named rules reached.
- [x] 3.2 State the rule in the map's own `comment`, with the measurement that produced it — the file a second
      project adapts is where its rule belongs. Verify: `bash tools/check-doc-routes.sh` still exits 0 (the map's
      comment is not a document) and `jq -e . tools/doc-routes.json` parses.
- [x] 3.3 Re-derive every path of task 1.1 as `evidence/derived-sets-after.txt`. Verify: `guidelines/Logging.md`
      reads `entries: 12` naming `Logging` alone, `guidelines/Logging.recipes.md` `entries: 12`,
      `guidelines/BuildGate.recipes.md` `entries: 30` naming `BuildGate`, and the tooling paths are unchanged at 4,
      1 and 1.

## 4. The case is green, and the harness falsifies it

- [x] 4.1 Run the case and then the whole file. Verify:
      `bash tools/check-rule-routes-selftest.sh --case routes#aGuidelineEditDerivesItsOwnDocument` exits 0 with its
      three assertions passing, and `bash tools/check-rule-routes-selftest.sh` reports `all 9 cases passed` (6 before
      the change).
- [x] 4.2 Add the mutation record `route-names-its-document` to `tools/check-rule-routes-mutations.json`: the
      `guidelines/Logging.*` glob reverted to `guidelines/**`, run against the new case. Verify:
      `bash tools/mutate.sh --mutations tools/check-rule-routes-mutations.json` reports
      `4 mutation(s) run, 4 went red and restored green, 0 problem(s)` — recorded as
      `evidence/mutation-set.log` — and `--list` exits 0 (the row the gate and CI run).

## 5. The artifacts, and the evidence the run reads

- [x] 5.1 Quote the run's own measurement block, `./tools/loop/measure.sh --change
      fix-guideline-routes-name-their-document`, into `evidence/measure-block.txt`; its `LOOP-MEASURE-PATH`,
      `LOOP-MEASURE-CHECK` and `LOOP-MEASURE-SURFACE` lines are the record of the pathspec this change owes.
      Verify: the block names the three owed checks and no compiled step.
- [x] 5.2 Run the project's gate once, `./tools/loop/gate.sh`, and copy the tallies it wrote. Result
      `LOOP-GATE: tests=9 failures=0 errors=0` (the self-test's nine cases; the two other checks print no cases),
      `evidence/gate.log`, and the three JUnit-shaped tallies beside it. Verify: each tally under the gate's
      directory names one of the three checks of task 5.1 with `failures="0" errors="0"`.
- [x] 5.3 Copy every cited result into the change's `evidence/` — the probe, its two runs, the red log, the mutation
      set, the measure block, the gate log and the three gate tallies — and check the pointers mechanically:
      `bash tools/loop/evidence-check.sh openspec/changes/fix-guideline-routes-name-their-document --root .`.
      Result: `PASS` — C1 0 checked, C2 1 pair confirmed, C3 none, C4 none, C5 326 resolved. Verify: it reports no
      dangling `file:line`, `tests=`, `/tmp` pointer or section reference.
- [x] 5.4 Archive the change and confirm the delta merged into `openspec/specs/documentation-ownership/spec.md`.
      Verify: `openspec/changes/archive/<date>-fix-guideline-routes-name-their-document` exists and
      `openspec show documentation-ownership --type spec --json | jq '[.requirements[] | select(.text |
      test("SHALL NOT be wide enough to match the whole corpus"))] | length'` is 1.

## 6. What this change does not do

- [x] 6.1 `tools/**`, `openspec/**` and `AGENTS.md` are measured, not assumed: 4, 1 and 1 entries derived
      respectively (`evidence/baseline-derived-sets.txt`), so the item's `tools/**` candidate is recorded as
      eliminated in `design.md` rather than implemented. Verify: the numbers in `design.md` match the evidence file.
