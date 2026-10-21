# Tasks — fix-loop-branch-read-by-patch

## 1. The red case, on HEAD, before the fix

- [x] 1.1 `tools/loop-cases.sh`: `landed_branch` fixture (a branch committed, its patch cherry-picked onto `main`
      and amended to the loop's own landing message, so the two commits differ while the patch is identical), and
      the three cases `worktree#reusesABranchWhoseWorkAlreadyLanded`,
      `prescreen#landedBranchIsNotADecision`, `report#landedBranchIsNoAction`, registered in
      `LOOP_SHELL_CASES` and in `loop_run_shell_case`'s dispatch.
      *Verification:* `bash tools/loop-selftest.sh --case <name>` exits 1 for each on HEAD `2e732f0` with the
      message quoted in `proposal.md`; the run is `evidence/red/red-cases.txt`
      (2026-10-21T05:50:29Z…05:50:35Z).

## 2. The one question, in one place

- [x] 2.1 `tools/loop/lib.sh`: `loop_unlanded_commits <repo> <base> <branch>` — `git cherry`, printing
      `<sha> <subject>` per unlanded patch, returning 1 when git cannot answer.
      *Verification:* sourced by `worktree.sh`, `prescreen.sh` and `report.sh` (all three source `lib.sh`), and the
      helper's two answers are asserted by 3.1–3.3 below.

## 3. The three readers

- [x] 3.1 `tools/loop/worktree.sh` (`cmd_start_item`): reuse when nothing is unlanded, refuse naming the unlanded
      commits otherwise, and refuse when git cannot read the branch.
      *Verification:* `bash tools/loop-selftest.sh --case worktree#reusesABranchWhoseWorkAlreadyLanded` passes;
      `--case worktree#reusesABranchThatCarriesNoWorkOfItsOwn` and
      `--case worktree#refusesABranchCarryingAPreviousAttempt` still pass.
- [x] 3.2 `tools/loop/prescreen.sh` (`previous_attempt_branch_of`): the same question on the branch-name prefix, a
      branch git cannot compare still read as carrying work.
      *Verification:* `--case prescreen#landedBranchIsNotADecision` and `--case prescreen#previousAttemptBranchIsADecision`
      pass.
- [x] 3.3 `tools/loop/report.sh`: the action list counts unlanded patches and skips a branch with none, keeping
      `ahead=1` on the "git cannot answer" path.
      *Verification:* `--case report#landedBranchIsNoAction` and `--case report#actionsNameDecisionAndBranch` pass,
      and `--case report#emptyRunSaysNone` still prints its `none` line.

## 4. Falsification

- [x] 4.1 `tools/loop-mutations.sh`: 12.5, 12.6 and 13.1 updated to the lines this change leaves behind; 33.1
      (the helper asks object identity), 33.2 (the pre-screen asks ancestry), 33.3 (the report counts ancestry)
      added, one per site.
      *Verification:* `bash tools/loop-mutations.sh --only "12.5 12.6 13.1"` → three `RED` + three green
      restores; `--only "33.1 33.2 33.3"` → three `RED` + three green restores
      (`evidence/green/mutations-new.txt`).

## 5. The loop's own prose

- [x] 5.1 `guidelines/Backlog.md` §7 "The loop": what a branch carries is asked by patch identity, with the
      measurement and the issue.
- [x] 5.2 `.pi/skills/fix-loop/SKILL.md` (the report paragraph) and `REFERENCE.md` (§"the worktree and the branch"):
      the same rule where the loop's facts live.
      *Verification* (no compiled file, so no build): `grep -rn "patch identity" guidelines/Backlog.md
      .pi/skills/fix-loop/SKILL.md .pi/skills/fix-loop/REFERENCE.md` names all three, and `openspec validate
      fix-loop-branch-read-by-patch --strict` passes.

## 6. The gate

- [x] 6.1 `bash tools/loop-selftest.sh` — the gate for `tools/loop/**`, offline, once.
      *Verification:* it prints `loop-selftest: all <n> cases passed`; the run is `evidence/green/suite.txt`. No
      Gradle gate applies: the change touches no compiled file (proposal, Impact).
- [x] 6.2 `bash tools/loop-mutations.sh` — the full set, because this change owns `tools/loop/**`.
      *Verification:* `not-noticed: 0`, `restore-failures: 0`, 66 mutations red and restored; 12.7 and 18.2 do not
      apply **on HEAD either** (their sed patterns in `run.sh` are stale — `evidence/red/stale-mutations-on-head.txt`),
      and the run is `evidence/green/mutations.txt`.
      *Configuration note:* the run moved `LOOP_TMP_DIR` one level out for its duration and restored the file,
      because this checkout's own path contains `worktrees/loop` and
      `worktree#keepsByDefaultAndRemovesOnRequest` greps exactly that in `git worktree list` — a pre-existing
      collision, reproduced on HEAD in `evidence/red/case-path-collision.txt` and left for its own item.

## 7. Archive

- [x] 7.1 Copy every cited run into `openspec/changes/fix-loop-branch-read-by-patch/evidence/` (`red/`, `green/`)
      and into `.pi/issue-loop/items/228/`.
- [x] 7.2 `openspec archive fix-loop-branch-read-by-patch --yes`: the directory exists under
      `openspec/changes/archive/<date>-fix-loop-branch-read-by-patch/` and both requirements merged into
      `openspec/specs/agent-issue-loop/spec.md`.
      *Verification:* `grep -c "patch identity" openspec/specs/agent-issue-loop/spec.md` is at least 1 and
      `openspec validate --specs agent-issue-loop` (or `openspec doctor`) is clean.
