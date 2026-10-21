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
      *Verification:* sourced by `worktree.sh`, `prescreen.sh` and `report.sh` (all three source `lib.sh`), and its
      unlanded answer is asserted by 3.1–3.3 below through the three readers. Its second answer — git cannot read
      the branch — is not asserted by any case; the readers keep the safe side of it (3.2, 3.3) and say so.

## 3. The three readers

- [x] 3.1 `tools/loop/worktree.sh` (`cmd_start_item`): reuse when nothing is unlanded, refuse naming the unlanded
      commits otherwise, and refuse when git cannot read the branch.
      *Verification:* `bash tools/loop-selftest.sh --case worktree#reusesABranchWhoseWorkAlreadyLanded` passes;
      `--case worktree#reusesABranchThatCarriesNoWorkOfItsOwn` and
      `--case worktree#refusesABranchCarryingAPreviousAttempt` still pass.
- [x] 3.2 `tools/loop/prescreen.sh` (`previous_attempt_branch_of`): the same question on the branch-name prefix.
      The code keeps a branch git cannot compare read as carrying work; no case asserts that path, and no scenario
      states it.
      *Verification:* `--case prescreen#landedBranchIsNotADecision` and `--case prescreen#previousAttemptBranchIsADecision`
      pass.
- [x] 3.3 `tools/loop/report.sh`: the action list counts unlanded patches and skips a branch with none, keeping
      `ahead=1` on the "git cannot answer" path. That path is code only: no case asserts it and no scenario states
      it.
      *Verification:* `--case report#landedBranchIsNoAction` and `--case report#actionsNameDecisionAndBranch` pass,
      and `--case report#emptyRunSaysNone` still prints its `none` line.

## 4. Falsification

- [x] 4.1 `tools/loop-mutations.sh`: 12.5, 12.6 and 13.1 updated to the lines this change leaves behind; 33.1
      (the helper asks object identity), 33.2 (the pre-screen asks ancestry), 33.3 (the report counts ancestry)
      added, one per site.
      *Verification:* `bash tools/loop-mutations.sh --only "12.5 12.6 13.1"` → three `RED` + three green
      restores; `--only "33.1 33.2 33.3"` → three `RED` + three green restores
      (`evidence/green/mutations.txt`).

## 5. The loop's own prose

- [x] 5.1 `guidelines/Backlog.md` §7 "The loop": what a branch carries is asked by patch identity, with the
      measurement and the issue.
- [x] 5.2 `.pi/skills/fix-loop/SKILL.md` (the report paragraph) and `REFERENCE.md` (§"the worktree and the branch"):
      the same rule where the loop's facts live.
      *Verification* (no compiled file, so no build): `grep -rn "patch identity" guidelines/Backlog.md
      .pi/skills/fix-loop/SKILL.md .pi/skills/fix-loop/REFERENCE.md` names all three, and `openspec validate
      fix-loop-branch-read-by-patch --strict` passes — the check of the change directory, so it is run before the
      archive; afterwards the same delta is validated as part of 7.2.

## 6. The gate

- [x] 6.1 `bash tools/loop-selftest.sh` — the gate for `tools/loop/**`, offline, once.
      *Verification:* it prints `loop-selftest: all <n> cases passed`; the run is `evidence/green/suite.txt`. No
      Gradle gate applies: the change touches no compiled file (proposal, Impact).
      *Configuration note:* in this checkout the gate can only pass through `evidence/probes/run-gate.sh`, which
      moves `LOOP_TMP_DIR` one level out for the run's duration and restores the file — the pre-existing path
      collision 6.2 describes. `suite.txt` is that run; it is the same suite, started from the same tree.
- [x] 6.2 `bash tools/loop-mutations.sh` — the full set, because this change owns `tools/loop/**`.
      *Verification:* `red-when-mutated: 66 · not-noticed: 2 · restore-failures: 0`, `exit=1` — the two the counters
      call `not-noticed` are 12.7 and 18.2, recorded `MUTATION-DID-NOT-APPLY` because their sed patterns in `run.sh`
      no longer match; they do not apply on HEAD either (`evidence/red/stale-mutations-on-head.txt`), so `exit=1`
      comes from them and from nothing this change touched. The run is `evidence/green/mutations.txt`.
      *Configuration note:* the run moved `LOOP_TMP_DIR` one level out for its duration and restored the file,
      because this checkout's own path contains `worktrees/loop` and
      `worktree#keepsByDefaultAndRemovesOnRequest` greps exactly that in `git worktree list` — a pre-existing
      collision, reproduced against the run's base `2e732f0` in `evidence/red/case-path-collision.txt` (the
      artefact prints the engine commit and the run's time) and left for its own item.

## 7. Archive

- [x] 7.1 Copy every cited run into `openspec/changes/fix-loop-branch-read-by-patch/evidence/` (`red/`, `green/`)
      and into `.pi/issue-loop/items/228/`.
- [x] 7.2 `openspec archive fix-loop-branch-read-by-patch --yes`: the directory exists under
      `openspec/changes/archive/<date>-fix-loop-branch-read-by-patch/` and both requirements merged into
      `openspec/specs/agent-issue-loop/spec.md`.
      *Verification:* `grep -c "patch identity" openspec/specs/agent-issue-loop/spec.md` is at least 1 and
      `openspec validate --specs agent-issue-loop` (or `openspec doctor`) is clean.

## 8. The first review's corrections

- [x] 8.1 The report requirement is **MODIFIED**, not added beside one. The delta's `ADDED` requirement is gone:
      the action-list statement lives in "One command starts a budgeted run, with no dialogue", whose scenarios
      "The report names the actions the caller owes" and "An action list is empty only when the state says so"
      now read a branch by what it carries, and "A branch whose patches are in the base is not an action" is a
      scenario beside them. `proposal.md` says so in "Modified Capabilities" and "Previous specifications
      changed".
      *Verification:* `grep -c '^## ADDED Requirements' <delta>` is 0, both requirements are under `MODIFIED`, and
      `grep -n 'carries commits the run' openspec/specs/agent-issue-loop/spec.md` finds no action-list requirement
      left to contradict the patch-identity one.
- [x] 8.2 No scenario states what no case asserts. The pre-screen scenario's "a branch git cannot compare is still
      read as carrying work" clause, the action-list requirement's "a range git cannot answer" sentence, and task
      2.1's "the helper's two answers are asserted by 3.1–3.3" are all gone; 2.1, 3.2 and 3.3 say which path is code
      only.
      *Verification:* `grep -rn 'cannot compare\|cannot answer' <delta> <live spec>` names the code's behaviour in
      `design.md` and the tasks, never a scenario.
- [x] 8.3 The mutation run is quoted as it printed. Task 6.2 and the worker report carry
      `not-noticed: 2 · restore-failures: 0`, `exit=1`, with the two mutations named, instead of `not-noticed: 0`.
      *Verification:* `tail -2 evidence/green/mutations.txt` matches the quote in both places.
- [x] 8.4 No probe timestamp is invented, and no claim is made about who deleted the landed branches. The
      `2026-10-21T05:52:12Z` quoted for `probe-landed-branch.sh` is gone from `proposal.md` and `design.md` (the
      probe prints none), and the population paragraph states what the probe shows — the branches that exist
      today — and that the landed ones are not observable from here at all.
      *Verification:* `grep -rn '05:52:12' <change> | grep -v tasks.md` and `grep -rn 'deleted by hand'
      <change>` return nothing.
- [x] 8.5 The probe scripts find the repository from where they are, not from where they were written: each
      walks up to the directory holding `.git`, so an archived probe is still runnable; the landed-branch probe
      uses `.pi/tmp` for its throwaway repository as its own header says; and the path-collision probe takes any
      revision, not only `HEAD`, and stamps its output with the time, the engine it ran and the tree's HEAD.
      Nothing about what a probe measured changes.
      *Verification:* `bash evidence/probes/probe-landed-branch.sh` from the archived directory prints the same
      three lines (`1`, `not an ancestor`, `0 unlanded`); `bash evidence/probes/probe-case-path-collision.sh
      2e732f0 base` prints `run … · engine 2e732f0…` and `FAIL worktree#keepsByDefaultAndRemovesOnRequest`, which
      is what `evidence/red/case-path-collision.txt` now holds.
- [x] 8.6 The gate, once more, after the corrections: `evidence/probes/run-gate.sh` — the same suite, same
      configuration (6.1's note). It is re-run because 8.5 touched the wrapper, and its result is quoted rather
      than compared as an improvement over 6.1.
      *Verification:* `evidence/green/suite-round2.txt` opens `2026-10-21T06:35:15Z`, ends
      `2026-10-21T06:43:57Z`, holds 131 `PASS` lines and no `FAIL`, and ends `exit=0`.
