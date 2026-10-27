# Tasks — fix-a-reused-worktree-keeps-the-previous-submodule-pin

## 1. The red test, before the fix

- [x] 1.1 `worktree#checksOutTheNewPinWhenItReusesTheWorktree` in `tools/loop-cases.sh`, registered in
  `LOOP_SHELL_CASES` and the dispatch table, built on #310's `submodule_fixture` / `submodule_in_worktree`. Red on
  the base: `the reused worktree is the tree the new base records: expected '', got ' M
  app/src/main/cpp/libosmscout'`; tally `evidence/red-on-head.xml` (`tests="1" failures="1"`), log
  `evidence/red-on-head.log`. Green after the fix: `evidence/green-after-fix.log`.
- [x] 1.2 The case's own fixture guards: the kept worktree is clean on the previous pin before it is reused, and
  the two bases pin different commits — both assertions pass on the base, so the red line above is the defect and
  not a broken fixture.

## 2. The fix

- [x] 2.1 `tools/loop/worktree.sh` — `sync_submodule` (the configured `LOOP_SUBMODULE_PATH`, gated on the tree's
  `.gitmodules`, best-effort with the fresh path's note) is called by the fresh path where its inline block stood
  and by the reused path immediately after its switch. Verified by the case green, and by the whole `worktree`
  group: `tools/loop-selftest.sh --group worktree` → `all 19 cases passed`
  (`evidence/focused-worktree-group.log`), so the fresh path's own case
  (`worktree#checksOutTheSubmoduleTheLoopConfigNames`) is not regressed.
- [x] 2.2 `tools/loop/loop.conf:341` and `PORTING.md:58` — the submodule key's description names the three places
  that place the module. Verified by `tools/check-portability.sh` and its self-test in the gate.

## 3. Falsification

- [x] 3.1 Mutation `13.12` (the reused path skips the checkout) reddens the case, restore green:
  `evidence/mutation-13.12.log` (`red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`).
- [x] 3.2 `bash tools/loop-mutations.sh --check --files tools/loop/worktree.sh` — every record naming that file
  applies to it (13 of 196 selected).

## 4. The spec and the record

- [x] 4.1 The delta `openspec/changes/fix-a-reused-worktree-keeps-the-previous-submodule-pin/specs/agent-issue-loop/spec.md`:
  one added requirement, one scenario naming exactly one case. Verified by
  `openspec validate fix-a-reused-worktree-keeps-the-previous-submodule-pin --strict`.
- [x] 4.2 `evidence/` — the red tally and log, the green logs, the mutation log, the surface counts, the gate log.
  Verified by `bash tools/loop/evidence-check.sh openspec/changes/fix-a-reused-worktree-keeps-the-previous-submodule-pin --root .`.

## 5. The gate, once

- [x] 5.1 `./tools/loop/gate.sh` run from the change root, exit 0: `LOOP-GATE: tests=643 failures=0 errors=0` over the
  21 steps the profile maps for this delta — no Gradle suite is owed by a path this change touches, only by the paths
  an earlier item already committed (`evidence/gate.log`, the tallies in `evidence/gate-tallies/`). It was run once.
