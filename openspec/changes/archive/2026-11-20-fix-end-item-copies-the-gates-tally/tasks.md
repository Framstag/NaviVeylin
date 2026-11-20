# Tasks — the tally travels in the archive's own commit

The gate is not this child's to run: the run hands this item's one gate to the child after the diff is read, so the
task that runs the gate's full selection, writes the change's pointers and archives the change is that child's. This
change compiles nothing: its own verification is the case it adds, the cases the delta reaches, and the structural
checks of the text it edits.

Base: `298d0052b8377dd5dc61c9f83dddba271c61f586` (the run's). Measure block, taken with that base in the tree:
`LOOP-MEASURE-CHANGE fix-end-item-copies-the-gates-tally scenarios=1 cases=1 unnamed=0`,
`LOOP-MEASURE-SUITE cases=498 cmd=tools/loop/tests/loop-selftest.sh --list`,
`LOOP-MEASURE base=9367d1c039aa4e5d9342f8aca989a86bd8b704bc paths=309 insertions=23162 deletions=329`
(`scratch/measure.log` in the item directory) — the pathspec's 309 paths are the tree's, because the block's base is
the one `git merge-base HEAD origin/main` resolves and not the run's; the checks it names are the gate child's.

## 1. The red leg first

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: added `worktree#endItemCopiesTheGatesTallyIntoTheEvidence` — a worktree,
      an item started for a change the state records as `fix-demo`, an archived change holding only a red XML, and a
      green testsuite under `app/build/test-results/loop-gate/` — asserting that `end-item` leaves the tally's XML in
      the change's `evidence/`, that the copy is committed (a clean tree), and that `branch-state` prints `adoptable`.
      Registered in the case list and in the dispatch. Red before the copy exists — with `end-item`'s
      `copy_gate_tally_into_evidence` call removed, the base's own behaviour —
      `the gate's tally was not copied into the archived change's evidence: TEST-demo-red-on-HEAD.xml`, the suite
      ending `loop-selftest: 1 of 1 cases failed` — `evidence/red-on-base.log`.

## 2. The copy

- [x] 2.1 `tools/loop/worktree.sh`: `passing_xml_in` reads `carried_verification`'s three clauses once over a
      directory; `item_change_dir` reads the change from `state.sh get current.change` and finds it under
      `openspec/changes/archive/` (whole, or with the date prefix the archive adds); `copy_gate_tally_into_evidence`
      copies every `*.xml` under `LOOP_GATE_TALLY_DIR` into that change's `evidence/` when it carries no passing run,
      never replacing a file already there; `cmd_end_item` calls it before the first commit is staged. Verify: 1.1's
      leg passes — `evidence/green-case.log`.
- [x] 2.2 The red-only leg is unchanged and still refuses:
      `run#doesNotMergeAnAttemptWithoutAGreenVerification` — `PASS` — `evidence/green-red-only.log`.
- [x] 2.3 The group the delta reaches: `bash tools/loop/tests/loop-selftest.sh --group worktree` →
      `all 30 cases passed — 22.84 s` — `evidence/worktree-group.log`.

## 3. What the delta owes

- [x] 3.1 `guidelines/Backlog.recipes.md:315` section 7b: the sentence about the gate's own run overwriting the previous run's
      XML evidence now names `end-item`'s `copy_gate_tally_into_evidence` as the step that copies the tally into the
      archived change's `evidence/`, instead of stating the convention with no owner. Verify:
      `tools/check-doc-values.sh` exit 0, `tools/check-doc-routes.sh` exit 0 (356 section and 8 skill references
      resolve), `tools/check-todo-references.sh` exit 0 — `evidence/structural-checks.log`.
- [x] 3.2 `openspec validate fix-end-item-copies-the-gates-tally --strict` → `is valid`, exit 0 —
      `evidence/structural-checks.log`.
- [x] 3.3 The rest of the pathspec's selection is the gate child's, cited from the measure block rather than run
      here: `tools/loop/tests/loop-selftest.sh --from-git 9367d1c0…`, `./gradlew test -PforceTests -PjvmTestsOnly
      --no-build-cache`, `bash tools/loop/tests/loop-mutations.sh --check` and the structural checks listed in
      `scratch/measure.log`. This child ran 1.1, 2.2, 2.3 and 3.1.
- [x] 3.4 The declaration the delta adds is the one the item's own cheapest check reads:
      `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` — `PASS` (the first leg
      of the new case, run before anything else).

## 4. Evidence

- [x] 4.1 Copied into this change's `evidence/` as they were produced: `red-on-base.log`, `green-case.log`,
      `green-red-only.log`, `worktree-group.log`, `structural-checks.log` — and beside the item, in
      `.pi/issue-loop/items/979/`.
