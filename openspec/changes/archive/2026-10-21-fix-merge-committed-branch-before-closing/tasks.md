# Tasks — fix-merge-committed-branch-before-closing

## 1. The red case

- [x] 1.1 Write `run#mergesItsCommittedBranchBeforeClosing` in `tools/loop-cases.sh` (registered in
  `LOOP_SHELL_CASES` and the dispatcher) and record its red-on-HEAD run in `evidence/red-on-HEAD.txt`:
  `bash tools/loop-selftest.sh --case 'run#mergesItsCommittedBranchBeforeClosing'` → exit 1, the item closed
  with its fix on the branch (2026-10-21T06:51:30Z). Parent: `agent-issue-loop`, scenario "The branch the run
  committed is merged before the item is closed".

## 2. The merge action

- [x] 2.1 Add `merge-item <issue> <branch>` to `tools/loop/worktree.sh` (dispatch entry + the command): check
  the recorded base, the branch's commits, the ancestor relation and the project tree's cleanliness, then
  `git merge --ff-only`, printing `merged: branch <name> -> <sha> · commits <a,b>`.
  Verification: `bash tools/loop-selftest.sh --case 'worktree#refusesToMergeOverADirtyProjectTree'` — a dirty
  project tree refuses (exit 5, base unmoved), and the same call on a clean tree lands the fast-forward.

## 3. The run's success path

- [x] 3.1 Add `merge_committed_branch` to `tools/loop/run.sh` (call `worktree.sh merge-item`, advance the
  recorded base with `state.sh set-base`, parse the branch and commits) and call it on the `closed` outcome
  immediately before `labels.sh close`: on success record `merged.branch`/`merged.commits` and name the merge
  in the evidence comment; on exit 5 set the outcome to `blocked` with the refusal as the reason.
  Verification: task 1.1's case is green (`PASS run#mergesItsCommittedBranchBeforeClosing`).
- [x] 3.2 Write `worktree#refusesToMergeOverADirtyProjectTree` in `tools/loop-cases.sh`. Verification: the case
  passes. Parent: `agent-issue-loop`, scenario "A merge the base cannot take is refused rather than forced".
- [x] 3.3 Adjust the two cases the merge moved: `run#commitsWithoutAnAmbientIdentity` counts the two commits
  from the base the run started on (the base branch now carries them), and
  `worktree#keepsByDefaultAndRemovesOnRequest` matches the listed worktree path exactly instead of the
  `worktrees/loop` substring (which is also the loop's own root). Verification: both cases pass.

## 4. Suite and revert-check

- [x] 4.1 `bash tools/loop-selftest.sh` in the change root — `all 130 cases passed` (2026-10-21T07:33:35Z),
  transcript in `evidence/loop-suite.txt`. No Gradle gate: the delta touches no compiled file
  (`guidelines/Build.md` §2, scope block).
- [x] 4.2 Revert-check: replace the `merge_committed_branch` call on the `closed` outcome with a no-op, re-run
  `run#mergesItsCommittedBranchBeforeClosing` → it fails again ("the item was closed with its fix on
  fix/issue-21-…"), restore, re-run → PASS. Transcript in `evidence/revert-check.txt`.

## 5. Documentation

- [x] 5.1 `guidelines/Backlog.md` §7 — state what the run does with the branch it committed: it merges it
  before closing, and a merge it cannot take blocks the item with its branch. Verification: one paragraph
  added beside the commit-identity paragraph; no other section changes.
