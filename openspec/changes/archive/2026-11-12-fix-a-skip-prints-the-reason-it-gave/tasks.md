# Tasks

The change's line numbers are the base's (`0e1ad308`) where they are a claim about the defect, and the tree's as this
change leaves it where they are a claim about the fix; the red run's tally is `evidence/red-on-head.xml`.

## 1. The red case

- [x] 1.1 Add `run#aSkipPrintsTheReasonTheChildGave` (`tools/loop/tests/loop-cases.sh`): a `too-wide` skip whose child
      gave a `LOOP-ROOT-CAUSE` sentence asserts the item's note and the report's `skipped:` line carry it, and a second
      run whose child gave none asserts the constant is still what both carry. Verify: against the base's writer,
      `bash tools/loop/tests/loop-selftest.sh --case 'run#aSkipPrintsTheReasonTheChildGave'` →
      `FAIL … the note is the child's own sentence, not the constant the skip arm wrote: expected 'one item of 35
      tasks, …', got 'skipped by the worker'` (2026-11-12T13:40Z; `evidence/red-on-head.log`,
      `evidence/red-on-head.xml`, `tests=1 failures=1 errors=0`).
- [x] 1.2 Its first-line `# files:` declaration names the scripts and fixtures the case reaches, and the declaration
      case accepts it: `bash tools/loop/tests/loop-selftest.sh --case
      'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.

## 2. The engine — `tools/loop/run.sh`

- [x] 2.1 The skip arm's note is the child's reason (`run.sh:3410`): `--note "${root_cause:-skipped by the worker}"`,
      with the comment above the call naming why the note is what the report prints and where the constant still
      belongs. Verify: the case passes (`evidence/green-case.log`, 2026-11-12T13:40:33Z):
      `PASS run#aSkipPrintsTheReasonTheChildGave`.

## 3. The mutation record

- [x] 3.1 `tools/loop/tests/loop-mutations.sh` `854.1` writes the constant back; the case must go red. Verify:
      `bash tools/loop/tests/loop-mutations.sh --only '854.1'` → `RED 854.1` with the case's own failure line, then
      `restore 854.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-854.1.log`, 2026-11-12T13:40:47Z).
- [x] 3.2 Every record still applies to the file it names: `bash tools/loop/tests/loop-mutations.sh --check` →
      `loop-mutations: all 341 cases passed — every selected record applies to the file it names`
      (`evidence/structural-checks.log`, 2026-11-12T13:41Z).

## 4. The records

- [x] 4.1 `guidelines/Backlog.recipes.md` section 7e, after the paragraph that states the report's three answers
      (`:1394`): the paragraph that says a skip's line names the reason the item records, what the constant was, and
      that the decision list's reading is unchanged.
- [x] 4.2 The delta `specs/agent-issue-loop/spec.md` ADDS the requirement "A skip's reason reaches the report" beside
      "A state the run writes is readable from the item", and its one scenario names the case. Verify:
      `openspec validate fix-a-skip-prints-the-reason-it-gave --strict` and `tools/check-openspec-hygiene.sh` →
      `evidence/doc-checks.log`, beside `tools/check-doc-values.sh` and `tools/check-doc-routes.sh` (the two structural
      checks of the guideline paragraph this change adds), all four exit 0 (2026-11-12T13:42:49Z).
