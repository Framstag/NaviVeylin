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
  Verification: task 1.1's case is green (`PASS run#mergesItsCommittedBranchBeforeClosing`), including the
  three assertions round 2 added for the record and the closure's evidence (task 6.1).
- [x] 3.2 Write `worktree#refusesToMergeOverADirtyProjectTree` in `tools/loop-cases.sh`. Verification: the case
  passes. Parent: `agent-issue-loop`, scenario "A merge over a project tree that is not clean is refused, and
  nothing is closed" — the command-level half; the run-level half is task 6.1's second case.
- [x] 3.3 Adjust the two cases the merge moved: `run#commitsWithoutAnAmbientIdentity` counts the two commits
  from the base the run started on (the base branch now carries them), and
  `worktree#keepsByDefaultAndRemovesOnRequest` matches the listed worktree path exactly instead of the
  `worktrees/loop` substring (which is also the loop's own root). Verification: both cases pass.

## 4. Suite and revert-check

- [x] 4.1 `bash tools/loop-selftest.sh` in the change root — `all 130 cases passed` (transcript in
  `evidence/loop-suite.txt`, written 2026-10-21T07:33:45Z). No Gradle gate: the delta touches no compiled file
  (`guidelines/Build.md` §2, scope block). Superseded by task 6.5's 132-case run.
- [x] 4.2 Revert-check: replace the `merge_committed_branch` call on the `closed` outcome with a no-op, re-run
  `run#mergesItsCommittedBranchBeforeClosing` → it fails again ("the item was closed with its fix on
  fix/issue-21-…"), restore, re-run → PASS. Transcript in `evidence/revert-check.txt`.

## 5. Documentation

- [x] 5.1 `guidelines/Backlog.md` §7 — state what the run does with the branch it committed: it merges it
  before closing, and a merge it cannot take blocks the item with its branch. Verification: one paragraph
  added beside the commit-identity paragraph; no other section changes.

## 6. Review round 2 — the assertions the review found missing

- [x] 6.1 `run#mergesItsCommittedBranchBeforeClosing` now asserts the record the scenario promises, not only the
  base: `.items[0].merged.branch` is the item's branch, `.items[0].merged.commits` ends at the branch's tip and
  every commit it names is on the base, the base the run recorded is the merge, and the closure's evidence
  carries `merged <branch> (commits <shas>) onto the base`. Second case
  `run#doesNotCloseOnAMergeTheBaseCannotTake`: a person's uncommitted file in the project tree at closure →
  `blocked`, the reason names the tree and the branch, the base did not move, the fix is not on the base, no
  merge is recorded, nothing is closed, and the escalation names the branch. Verification: both cases pass
  (`evidence/loop-suite-round2.txt`), and both fail when the production line they assert is removed (6.4).
- [x] 6.2 `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom` exercises the refusal's other arm at the
  command level: a base that moved under the branch is refused with `does not descend`, the base stays, and
  neither the branch's commit nor the recorded base moves. Parent: `agent-issue-loop`, scenario "A branch the
  base does not descend from is not replayed". Verification: the case passes.
- [x] 6.3 Spec delta and the merged capability re-cut so each scenario names the case that asserts its whole
  `THEN`: scenario 2 is the dirty tree at closure (run level, `run#doesNotCloseOnAMergeTheBaseCannotTake`), and
  the non-descendant arm is scenario 3 with its own case. No production line changed.
- [x] 6.4 Revert-check for each of the three assertions, transcript `evidence/revert-check-round2.txt`: a
  refused merge that no longer blocks (`run.sh:957`, the outcome line removed while the merge itself still
  refuses) → `run#doesNotCloseOnAMergeTheBaseCannotTake` FAIL (`expected 'blocked', got 'closed'`); a closure
  that records no merge (`run.sh:970`, `run.sh:988`) → `run#mergesItsCommittedBranchBeforeClosing` FAIL (`the
  run recorded no merged commits`); a branch replayed instead of refused (`worktree.sh:295-296`) →
  `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom` FAIL (`expected '5', got '2'`). Restored tree: all
  three PASS, and `run.sh`/`worktree.sh` byte-identical to the committed state.
- [x] 6.5 `bash tools/loop-selftest.sh` after the corrections — `all 132 cases passed (JDK 27, offline, no
  token, no sandbox)`, 0 `FAIL` lines, transcript `evidence/loop-suite-round2.txt` (2026-10-21T07:49:20Z). The
  two structural checks the review asked to see are transcribed beside it: `check-doc-routes: all 89 section
  references resolve` (`evidence/check-doc-routes.txt`) and `check-portability: OK — 44 engines and 2
  templates…` (`evidence/check-portability.txt`).
