# Tasks — fix-loop-blocked-attempt-is-work

## 1. The credit and the state

- [x] 1.1 `carried_change <issue> <branch> <base>` credits a change only when its earliest adding commit names
      `(#<issue>)`, keeps the "base already has it" refusal, and returns nothing otherwise.
- [x] 1.2 Factor the reviewer-refusal reader out of `cmd_adopt_item` as `carried_review_refusal <branch> <change>`.
- [x] 1.3 Add `branch_state_of <issue> <branch> <base>` (`adoptable`, `review-refused`, `archived-not-green`,
      `unfinished`, `landed`, `absent`) and the read-only `worktree.sh branch-state <issue> <branch>`, printing
      `<state> TAB <change> TAB <evidence>`.
- [x] 1.4 `cmd_adopt_item` reads the three helpers instead of its own copies; its refusals keep their wording.

## 2. The verdicts

- [x] 2.1 `start-item` sets an `unfinished` branch aside to `loop/leftover/issue-<N>-<slug>-<utc>` and starts the
      item on a fresh branch; every other carried state keeps the refusal `run.sh` matches.
- [x] 2.2 `prescreen.sh` asks `branch-state`: `unfinished` leaves the item queued, with the branch as the
      record's evidence and a reason naming the set-aside; every other state keeps the decision.
- [x] 2.3 `run.sh` prints the start's `set-aside:` line and states in the refusal path's comment that only a
      finished attempt reaches it.

## 3. The gate

- [x] 3.1 Cases: `prescreen#aFinishedAttemptBranchIsADecision`, `prescreen#aBlockedAttemptBranchIsWork`,
      `worktree#setsAsideABlockedAttemptAndStartsFresh`,
      `worktree#doesNotAdoptAChangeCreditedToAnotherIssue`; renamed
      `run#branchThatAppearsWhileTheRunIsOpenIsSetAside`.
- [x] 3.2 Fixtures aligned with the landing convention (`(#21)` in the commit subjects of the adopt cases, an
      archived change in `prescreen#escalatedItemKeepsItsBranchAsEvidence` and
      `worktree#refusesABranchCarryingAPreviousAttempt`).
- [x] 3.3 Red on HEAD `2e732f0` for the three new cases (`evidence/red/`), then green (`evidence/green/suite.txt`,
      131/131).
- [x] 3.4 Mutations 33.1, 33.2 added; 12.6, 12.7, 13.1 retargeted; `--only "12.6 12.7 13.1 33.1 33.2"` reports
      `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0` (`evidence/green/mutations.txt`).

## 4. The rules and the artifacts

- [x] 4.1 Spec delta: the requirement "Version control belongs to the run's script, not to the agent" and two of
      its scenarios, plus the added scenario "A blocked attempt's branch is set aside and the item worked".
- [x] 4.2 `.pi/skills/fix-loop/REFERENCE.md`: the branch facts name the `loop/leftover/` prefix.
- [x] 4.3 Proposal, design, tasks and evidence written; the change archived with its evidence.
- [x] 4.4 Filed the measurement that produced this change as issue #232, and answered #230 through it.
