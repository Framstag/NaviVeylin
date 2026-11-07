# Tasks

Reference: `proposal.md` for scope, `design.md` for why and the per-role census,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "A child is bounded by its own role's turns", scenarios "A phase's spend does not end the phase after
it" and "A role whose key is `0` runs unbounded, and the item's total is still reported".

- [x] 1.1 Add `run#eachChildIsBoundedByItsOwnRolesTurns` to `tools/loop-cases.sh`: a worker that spends the whole of
      a recorded item pot of ten turns, then a reviewer that spends eight and blocks, with the worker's role bound
      at twenty — the case asserts no `LOOP-TIMEOUT` on the worker, the reviewer ended at its own eight with
      `LOOP_CHILD_TURNS_MAX_REVIEWER` named, and the header carrying both keys. Verify: on HEAD the case fails, and
      not on a missing key — HEAD ends the **tail** child at the item's pot of 10 (`the tail worker process exited
      124 — … the recorded turn bound of 10 turns (LOOP_ITEM_TURNS_MAX) after 10 turn(s)`), so the reviewer never
      runs.
- [x] 1.2 Add `run#aRoleWithNoRecordedTurnBoundRunsUnbounded`: the worker's key `0` beside an item pot of one turn,
      three turns appended, and assertions on `metrics.turns` = 3, `metrics.calls` = 0 and the report's
      `0 calls per turn (0 calls / 3 turns)`. Verify: on HEAD the case fails with the header's `item-turns-max=1`
      and the item blocked at one turn.
- [x] 1.3 Repoint the two existing cases at the role keys and the renamed header field:
      `run#childPastItsTurnBoundIsBlocked` records `LOOP_CHILD_TURNS_MAX_WORKER=3` and asserts
      `child-turns-max=worker:3` and the role in the transcript; `run#theRecordedZeroKeepsTheTurnBoundOff` records
      `LOOP_CHILD_TURNS_MAX_WORKER=0`; `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` records
      `LOOP_CHILD_TURNS_MAX_REVIEWER=3`.

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`: replace `item_turns_max` with `child_turn_bound <role>` reading
      `LOOP_CHILD_TURNS_MAX_<ROLE>` through `loop_conf_value`, and build the header's `child-turns-max=` field from
      the five roles.
- [x] 2.2 `child_role` gains `worker-gate` and `reviewer-pre-gate`, ordered so `-tail` and `pre-gate` are named
      before the plain shapes they also match; the agent-command key and the new turn key both derive from it.
- [x] 2.3 `run_child` reads the role's bound and takes `turns_base` from the item's stream **before** the fork, ends
      the child when the difference reaches the bound, and writes the bound, the key and the role into the
      `LOOP-TIMEOUT` line.
- [x] 2.4 Rename the header field where the run names its effective bounds; the agent-roles header line lists the two
      new roles as well.

## 3. The numbers

- [x] 3.1 Record the five keys in `tools/loop/loop.conf` with the census behind them (post-#758: 10 items, 12 worker
      sessions, 10 pre-gate rounds, 10 gate children, 11 tails, 6 review rounds; archive-wide where the sample is
      large) and the sentence that the item's total is now reported and not stopped.
- [x] 3.2 Name the keys in `guidelines/BacklogTooling.md` §11 and the three places `guidelines/Backlog.recipes.md`
      carries the old item bound.

## 4. The prompt and the wording

- [x] 4.1 `tools/loop/prompts/worker.md`: the worker's own turn key rather than the item's pot.
- [x] 4.2 The two engine comments and the two `blocked_reason` strings that said "the item's turn bound" now say the
      role's.

## 5. Verification

- [x] 5.1 The four `run` cases of tasks 1.1–1.3 pass on the change (`PASS`, `run` group).
- [x] 5.2 The red halves are recorded: both new cases fail on `f8eb72f3` (HEAD when the change was written) for the
      behaviour they assert, not for a missing key.
