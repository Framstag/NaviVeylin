# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run. The
change touches no compiled file, so the structural checks that stand in for a build are the loop's own gate
(`tools/loop/gate.sh`) and `openspec validate --strict`.

- [x] 1. The three cases that reproduce the gap, and their red run on HEAD: `run#aMergedBranchIsRemoved` (the
   branch the run merged is still there afterwards), `clean#sweepsOnlyWhatIsInTheBase` (the sweep of what the
   loop left removes no branch at all) and `report#landedBranchesAreNamedAsRemovable` (the report does not
   tell the leftovers from the work). Each is registered in `LOOP_SHELL_CASES`, dispatched in
   `loop_run_shell_case` and implemented in `tools/loop-cases.sh`.
   Verification: the three cases against the engine at `522d69a` — `FAIL` for each, `EXIT=1`
   (`evidence/red-HEAD.log`), with the tally in `evidence/red-HEAD.xml` (`tests="3" failures="3"`). Each
   failed for the reason its own line names: `the run left the branch of the item it closed behind:
   fix/issue-21-…`, `'would remove fix/issue-1-landed' not found` and `'landed, removable:' not found`.

- [x] 2. `tools/loop/worktree.sh` — a `release-branch <branch>` subcommand: the worktree is detached from the
   branch it holds and the branch is then removed with git's own safe delete, which refuses a branch whose
   work is not in the base. The worktree's branch marker stays: it names the item whose work the tree
   carries, and a tree that is dirty when the next run starts is attributed by it.
   Verification: `run#aMergedBranchIsRemoved` — the branch is gone, the worktree is on no branch of its own,
   the item's change is in the base, and the worktree still holds the commit it carried.

- [x] 3. `tools/loop/run.sh` — a branch the run merged is released and removed: the run created it (or adopted
   it), the run merged it, and the run leaves no branch behind for an item it closed.
   Verification: `run#aMergedBranchIsRemoved` and `run#mergesItsCommittedBranchBeforeClosing` green — the
   branch of the closed item is gone after the run, the item is closed with its commits in the base, and the
   closure's own record still names the branch and the commits it carried. The three cases that used the
   branch as a handle for the item's commits (`run#commitsWithoutAnAmbientIdentity`,
   `run#resetWorktreeDiscardsWhatItNames`, `worktree#keepsSourcesNotBuildOutput`,
   `run#mergesAFinishedAttemptAndClosesTheItem`) now read them from the base, which is where they are.

- [x] 4. `tools/loop/loop-clean.sh` — the branch sweep: every branch the loop names whose patches are all in
   the base and that no worktree holds is removed with `--apply`; a branch that carries unlanded work and a
   branch a worktree holds are kept and named with the reason.
   Verification: `clean#sweepsOnlyWhatIsInTheBase` green — the landed branch is gone, the branch that carries
   work and the one a worktree holds are still there, the report names each of them before the sweep removes
   anything, and `clean#reportsWithoutApplyAndRemovesWithIt` still passes.

- [x] 5. `tools/loop/report.sh` — a second branch line names the branches that carry no patch the base does
   not have, so the flat list of names says which are leftovers. The question is asked once and used twice:
   the line and the action list.
   Verification: `report#landedBranchesAreNamedAsRemovable` green — the report names the landed branch under
   that line and not among the actions, and a branch that carries work is named as an action;
   `report#landedBranchIsNoAction`, `report#actionsNameDecisionAndBranch` and `report#headerAndTableShape`
   still pass.

- [x] 6. `tools/loop-mutations.sh` — the mutations for the new invariants: the worktree's release (`42.1`),
   the deletion of the merged branch (`42.2`), the sweep's own question (`42.3`) and the report's second line
   (`42.4`).
   Verification: `bash tools/loop-mutations.sh --only '42.1 42.2 42.3 42.4'` — every record `RED`, every
   restore green again, `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`; and the **full** set —
   `red-when-mutated: 91 · not-noticed: 0 · restore-failures: 0`, `EXIT=0` (`evidence/mutation-full.log`).

- [x] 7. `guidelines/Backlog.md` §7 "The loop" — the paragraph that states who owns a branch and when it goes.
   Verification: `tools/check-doc-routes.sh` — `all 152 section references resolve, all 4 skill references
   resolve`, `EXIT=0` (the gate's suite for `guidelines/**`).

- [x] 8. The project's gate, once: `./tools/loop/gate.sh` — the suites its pathspec owes
   (`tools/loop-selftest.sh`, `tools/loop-mutations-selftest.sh`, `tools/check-doc-routes.sh`; no compiled
   path changed, so the build's own gate is not owed).
   Verification: `LOOP-GATE: tests=203 failures=0` — `EXIT=0` (`evidence/gate.log`), the per-suite tallies
   copied out of the build directory into `evidence/gate-tallies/` (`tools_loop-selftest.sh.xml`
   `tests="169" failures="0"`, `tools_loop-mutations-selftest.sh.xml` `tests="34" failures="0"`).

- [x] 9. Archive: the change sits under `openspec/changes/archive/<date>-add-loop-branch-hygiene/`, the delta
   merged into `openspec/specs/agent-issue-loop/spec.md`, every cited log and XML in its `evidence/`.
   Verification: archived with `openspec archive add-loop-branch-hygiene --yes`; the requirement "A branch the
   loop made is removed when its work is in the base" and the scenario "A branch the run merged is removed"
   are in `openspec/specs/agent-issue-loop/spec.md`; `openspec validate agent-issue-loop --type spec` — only
   the pre-existing warnings; `openspec doctor` — root ok; `tools/check-doc-routes.sh` after the archive —
   `EXIT=0`.
