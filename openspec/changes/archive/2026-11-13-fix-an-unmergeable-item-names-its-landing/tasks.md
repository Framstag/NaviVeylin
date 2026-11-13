# Tasks

The line numbers are the base's (`647b289a`) where they are a claim about the defect, and the tree's as this change
leaves it where they are a claim about the fix; the red run's tally is `evidence/red-on-HEAD.xml`.

The delta compiles nothing, so no build task is owed: the structural checks of the files this change edits are what
stands in for one, and they are `evidence/structural-checks.log`.

## 1. The red case

- [x] 1.1 `report#anUnmergeableItemNamesItsLanding` (`tools/loop/tests/loop-cases.sh`, added after
      `report#landedBranchIsNoAction`) — three items a merge refused: one whose branch the tree still holds, one
      commit ahead of the state's base and refused over a dirty tree the refusal names path by path; one whose
      branch the tree no longer holds; and one whose branch is in the tree carrying nothing the base does not have,
      refused over a clean tree, so the counting arm's zero and the path clause's absence are read too. Its
      `# files:` first line is the sibling report cases'. Verify, against the base's report
      (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`): `FAIL report#anUnmergeableItemNamesItsLanding` — `and
      the one command that lands it: 'git -C …/repo merge --ff-only fix/issue-21-two-mechanisms' not found in:   4.
      land fix/issue-21-two-mechanisms — 1 commit(s) the base does not have — item #21` (`tests=1 failures=1
      errors=0`, 2026-11-13T13:33:40Z).
- [x] 1.2 The declared case name and its dispatch entry: `report#anUnmergeableItemNamesItsLanding` in
      `LOOP_SHELL_CASES` and in the `case` table, or the suite answers `no such case`. Verify:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS` (the
      gate plan's first line, run before anything else this phase).

## 2. The report — `tools/loop/report.sh`

- [x] 2.1 The block beside the branch arm (`report.sh:674`–`:716`): one action per item whose `stopReason` is
      `unmergeable`, naming the branch the refusal wrote, what it carries by patch identity, the
      `git -C <root> merge --ff-only <branch>`, and — where the refusal listed paths — those paths and
      `git -C <root> status --porcelain`. Verify: the case passes
      (`evidence/green-red-case.log`), and the same record the item cites reads as `evidence/action-list-after.log`
      where the base read as `evidence/action-list-before.log`.
- [x] 2.2 The branch arm skips a branch this block named, so the landing is one line: the branch is not the branch
      arm's action as well (#850), while an item the run escalated keeps its `decide` line. Verify: the case's
      `assert_eq "1" "$(… grep -c 'land fix/issue-21-two-mechanisms')"` and its `decide #21` assertion pass.
- [x] 2.3 The `none` sentence names the eighth source. Verify: `report#emptyRunSaysNone` → `PASS` in the report
      group's run (`37 cases … all 37 cases passed`).

## 3. The rule's own document

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7e: the new arm in the action list's paragraph, and `seven sources` →
      `eight`. Verify: `tools/check-doc-values.sh`, `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` →
      `evidence/structural-checks.log`.

## 4. The mutation record

- [x] 4.1 `loop-mutations.sh` `863.1` reads the arm out (`select((.stopReason // "") == "unmergeable")` →
      `select(false)`); the case must go red and its restore green. Verify:
      `bash tools/loop/tests/loop-mutations.sh --only '863.1'` → `RED 863.1 report#anUnmergeableItemNamesItsLanding`,
      `restore 863.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-863.1.log`); `--check --only '863.1'` → `every selected record applies to the file it
      names`.

## 5. The change's own artifacts

- [x] 5.1 The readiness checker's three answers: `design.md`'s `## Open decisions` declares `none`, the delta's one
      scenario carries its `**Case**:` line, and `proposal.md` carries a line beginning `surface:` with the command
      its count is re-taken from. Verify: `tools/check-change-readiness.sh
      openspec/changes/fix-an-unmergeable-item-names-its-landing` → `LOOP-CHANGE-READY: ok`
      (`evidence/structural-checks.log`).
