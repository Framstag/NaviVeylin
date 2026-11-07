# Tasks — fix-loop-reset-worktree-submodule

## 1. The red tests, before the fix

- [x] 1.1 `submodule_fixture` / `submodule_in_worktree` in `tools/loop-cases.sh`: the configured submodule path made
  a real module of two commits, and the module's checkout placed inside a kept worktree.
  Verified: the two new cases reach the dirt (`fail "the fixture did not leave the submodule on another commit"` is
  not tripped) — `evidence/red-on-head.log`.
- [x] 1.2 `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard`, registered in `LOOP_SHELL_CASES` and the
  dispatch table. Red on the base: `expected '', got ' M app/src/main/cpp/libosmscout'`; tally
  `evidence/red-on-head.xml` (`tests="1" failures="1"`). Green after the fix: `evidence/green-after-fix.log`.
- [x] 1.3 `run#resetWorktreeNamesTheDirtItCannotDiscard`. Red on the base `run.sh` (checked out of `5e8d739b` for the
  run): `expected '5', got '0'`; tally `evidence/red-on-head-names.xml` (`tests="1" failures="1"`), log
  `evidence/red-on-head-names.log`. Green with the fix: `evidence/focused-reset-cases.log`.

## 2. The fix

- [x] 2.1 `tools/loop/run.sh` — the reset branch checks the configured module out after its two verbs and re-reads
  `git status --porcelain`; a non-empty read prints the remaining paths and refuses with exit 5 instead of
  announcing a clean tree. Verified by both cases green: `bash tools/loop-selftest.sh --case 'run#resetWorktree*'`
  → `all 3 cases passed` (`evidence/focused-reset-cases.log`), which includes the untouched
  `run#resetWorktreeDiscardsWhatItNames`.
- [x] 2.2 `tools/loop/loop.conf:269` and `PORTING.md:56` — the submodule key's description names both places that
  place the module. Verified by `tools/check-portability.sh` in the gate (the engine still names no project path).

## 3. Falsification

- [x] 3.1 Mutation `13.8` (the checkout never runs) reddens the first case, restore green —
  `evidence/mutation-13.8.log`.
- [x] 3.2 Mutation `13.9` (the repair skips its own verification) reddens the second case, restore green —
  `evidence/mutation-13.9.log`.
- [x] 3.3 `bash tools/loop-mutations.sh --check` — every record applies to the file it names (run inside the gate).

## 4. The spec and the record

- [x] 4.1 The delta `openspec/changes/fix-loop-reset-worktree-submodule/specs/agent-issue-loop/spec.md`: one added
  requirement, two scenarios, each naming exactly one case. Verified by `openspec validate
  fix-loop-reset-worktree-submodule --strict`.
- [x] 4.2 `evidence/` — the two red tallies, their logs, the green logs, both mutation logs and the surface counts.
  Verified by `bash tools/loop/evidence-check.sh openspec/changes/fix-loop-reset-worktree-submodule --root .`.

## 5. The gate, once

- [x] 5.1 `./tools/loop/gate.sh` run from the change root; its `LOOP-GATE` line is quoted in the item's report
  (`tests=601 failures=0 errors=0`, 23 suites, 971 s — `evidence/gate-round2.log`), and every suite's tally is in
  `evidence/gate-*.xml`. No compiled file is touched by this change, so the Gradle suite is owed only by the paths an
  earlier item already committed; the shell suites the profile maps for `tools/loop/**` are owed and ran.
- [x] 5.2 The first gate run was red on two suites — `mutation#everyDeclaredRecordApplies` and five cases of
  `tools/loop-mutations-selftest.sh` — because both count the declared set as the lines beginning `  '` and the two new
  records were quoted with `"` (`loop-mutations: all 175 cases passed`: `'all 173 cases passed' not found`). The
  records were requoted in the set's own style, the two suites went green focused (`evidence/focused-declared-record-
  applies.log`, `evidence/focused-mutations-selftest.log`), and the gate was then run once more — its red first run is
  kept as `evidence/gate-round1-red.log`, not overwritten.
