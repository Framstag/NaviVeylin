# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run. The
change touches no compiled file, so the structural checks that stand in for a build are the loop's own gate
(`tools/loop/gate.sh`) and `openspec validate --strict`.

- [x] 1. The two cases that reproduce the defect, and their red run on HEAD:
   `worktree#refusesABranchWhoseReviewRoundNeverAnswered` (a branch with a passed round 1 and an announced, unanswered
   round 2 is reported `adoptable` and merged) and `run#announcesAndRecordsTheReviewRound` (a successful run's
   branch carries no record of its review round). Both registered in `LOOP_SHELL_CASES`, dispatched in
   `loop_run_shell_case` and implemented in `tools/loop-cases.sh`.
   Verification: the suite on the engine at `522d69a7ac21` — `loop-selftest: 2 of 168 cases failed`, each case
   failing for the reason its own line names (`evidence/red-HEAD.log`, 2026-10-22T21:05Z; the tally is
   `evidence/red-HEAD.xml`, `tests="168" failures="2"`).

- [x] 2. `tools/loop/worktree.sh` — `carried_review_refusal` becomes `carried_review_state`: the highest review
   round the branch carries and what that round says (`passed`, `failed`, `requested`), a refusal outranking a
   pass and either outranking a request. `branch_state_of` gains `review-unanswered`, and `adopt-item` refuses it
   with the round named beside the refusal it already had.
   Verification: `worktree#refusesABranchWhoseReviewRoundNeverAnswered` and
   `worktree#refusesABranchWhoseReviewFailed` green; mutation `18.3` makes the first red and `18.1` the second.

- [x] 3. `tools/loop/worktree.sh` — `record-review-round <issue> <change> <round> requested|passed` writes the
   round into the change's own `evidence/` directory as a commit on the item's branch; `record-review-failure`
   keeps its name, its `review-failed-round-<n>.md` and its commit subject.
   Verification: `run#recordsAFailedReviewOnTheBranch` and `run#announcesAndRecordsTheReviewRound` green, and
   mutation `18.2`/`18.4` redden them.

- [x] 4. `tools/loop/run.sh` — the review loop announces the round before it starts the reviewer and records the
   answer on `PASS` or on a zero-blocking report, both guarded by `[ -n "$change" ]` so a falsification writes
   nothing; a record that cannot be written warns and names what a later run reads instead.
   Verification: the same two run cases, plus `run#aReportCutTwiceBlocksAsTheTransportsFault` and
   `run#falsifiedSkipClosesWithItsMeasurement`, both unaffected — the run keeps no marker for a round that
   decided nothing and none for a falsification.

- [x] 5. `tools/loop-cases.sh` — `run#commitsWithoutAnAmbientIdentity` realigned: the run now commits the round's
   announcement and answer on the item's branch, so the case counts the two `fix:` commits by subject and asserts
   the run's identity over every commit since the base.
   Verification: `run#commitsWithoutAnAmbientIdentity` green.

- [x] 6. `tools/loop/prescreen.sh` (comment) and `guidelines/Backlog.md` §7 — the adoption paragraph states the
   review round in both directions and which branch stays adoptable. The pre-screen's routing needs no change:
   every state but `unfinished` was already the owner's decision.
   Verification: `tools/check-doc-routes.sh` green in the gate's log.

- [x] 7. `tools/loop-mutations.sh` — records `18.1` rewritten (the adoption's read of the verdict, whose old
   pattern `refused_round=` no longer exists), `18.3` and `18.4` added.
   Verification: `tools/loop-mutations.sh --only '18.1 18.2 18.3 18.4'` — 4 red when mutated, 0 not-noticed, 0
   restore failures (`evidence/mutations-18.log`).

- [x] 8. The change's own gate, run once from the change root: `./tools/loop/gate.sh`.
   Verification: the gate's own line — 202 tests, 0 failures, over `tools/loop-selftest.sh`,
   `tools/loop-mutations-selftest.sh` and `tools/check-doc-routes.sh` — in 13 min 15 s (`evidence/gate.log`),
   with the three per-suite tallies under `evidence/gate-tallies/`: `tests="168" failures="0"`,
   `tests="34" failures="0"` and `tests="0" failures="0"`, whose sum is the gate's 202. Run by hand on the
   committed HEAD after the run that produced this change was ended by its own child cap mid-archive; the tree
   was the commit's own, unmodified.

- [x] 9. The mutation set of the engine files this change touches, before the change is archived.
   Verification: `tools/loop-mutations.sh --from-git 522d69a` — the set derived from this change's own diff —
   `selected 63 of 89 records`, `red-when-mutated: 63 · not-noticed: 0 · restore-failures: 0` in 17 min 31 s
   (`evidence/mutation-files.log`): every selected record red when mutated and green again after its restore.

- [x] 10. `openspec validate fix-loop-unanswered-review-round-is-not-adopted --strict`, then archive: the delta
   merged into `openspec/specs/agent-issue-loop/spec.md`, the requirement's `## Purpose` untouched, and
   `evidence-check.sh` clean over this change directory.
   Verification: `Change 'fix-loop-unanswered-review-round-is-not-adopted' is valid`, the change archived as
   `openspec/changes/archive/2026-10-23-fix-loop-unanswered-review-round-is-not-adopted/`, and
   `evidence-check.sh` reporting no unresolved pointer over it.
