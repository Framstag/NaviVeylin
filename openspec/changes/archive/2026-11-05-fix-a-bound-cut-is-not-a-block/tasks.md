# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "An item the run's own bound cut is recorded `cut`, not `blocked`", scenarios "A round the turn bound
ended leaves no decision behind" and "Two consecutive cuts do not stop the run".

- [x] 1.1 Strengthen `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`: the item is recorded `cut`, carries no
      decision label, the report prints `decisions the owner owes: none`, the branch carries
      `review-round-1-cut.md` and no `review-round-1-requested.md`, and `branch-state` reads `adoptable`. Verify:
      on HEAD the case fails at `expected 'cut', got 'blocked'` (evidence/red-on-HEAD.md).
- [x] 1.2 Add the cut step to `state#nextHonoursEveryStopCondition`: two `cut` items do not stop the run, a block
      after a cut is the first of its own pair, and two blocks still stop it. Verify: on HEAD the case fails at
      `the next candidate stands behind them: expected '23', got '21'`, because `done --result cut` is refused.

## 2. The state and the report

- [x] 2.1 `tools/loop/state.sh`: `--result cut` in the usage line and in the validation, the default verdict
      `cut`, `cut N` in the `show` and `report` tallies, and the note on `next` that the streak counts blocks only.
- [x] 2.2 `tools/loop/report.sh`: `cut N` in the three header lines, the budget left subtracts it, and a line of
      its own (`cut short by the run's own bound: …`) beside the blocks.

## 3. The engine

- [x] 3.1 `tools/loop/run.sh`: `outcome="cut"` for the reviewer the run's deadline ended and for the one its role's
      turn bound ended; `pre_gate_cut` for the round before the gate's two bound arms; the `cut)` arm of the
      outcome block records `--result cut --verdict cut` with no decision label and no `--stop-reason`.
- [x] 3.2 `tools/loop/run.sh`: `record_cut_round` writes the cut on the branch, and `bound_cut` keeps the queue's
      second pass off the item the cut child had just made.

## 4. The branch

- [x] 4.1 `tools/loop/worktree.sh`: `record-review-round … cut` writes `review-round-<n>-cut.md` and withdraws the
      announcement of the same round in the same commit; `carried_review_state` reads it; `branch_state_of` treats
      it as nothing a person owes, so the attempt stays `adoptable`; the three header comments state the value.

## 5. The docs

- [x] 5.1 `guidelines/Backlog.recipes.md` §7e: what a bound cut is and what it is not — the fifth result, the line
      it prints, the streak, the branch marker, and that the transport's own cut is still a block.
- [x] 5.2 `guidelines/Backlog.recipes.md` §7b: the deadline paragraph names the result the cut item is recorded as.

## 6. Verification

- [x] 6.1 `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`, `run#eachChildIsBoundedByItsOwnRolesTurns`,
      `run#reviewerEndedAtTheRunsDeadlineIsNotADecision`, `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`,
      `run#childOutlastingTheWallClockIsBlocked`, `run#childIsEndedAtTheRunsDeadline` and the `state`, `worktree`,
      `prescreen`, `driver`, `report` groups pass on the change.
- [x] 6.2 Mutations `80.1`–`80.3` added, `68.5` repointed at the two-condition guard and `68.4`'s description
      repointed at what it now proves; `--check` over the four touched engine scripts applies every record, and
      `80.1`–`80.3` and `68.2`, `68.4`, `68.5`, `68.6`, `73.1`, `79.4` go red and restore green.
- [x] 6.3 The evidence is recorded in `evidence/`: the red-on-HEAD runs and the gate's own log.
