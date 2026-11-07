# Tasks

Reference: `proposal.md` for the measurement, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The bound values (the loop's own configuration)

Requirement: none — these are recorded project values (`loop.conf`), and the derivation beside each key is what a
later reader re-checks.

- [x] 1.1 Re-derive all five `LOOP_CHILD_TURNS_MAX_<ROLE>` values from the archive's own census
      (`evidence/role-census.py`, 245 items: worker p95 161.8 / max 219, gate p95 69 / max 71, tail p95 56.2 / max
      60, reviewer p95 57.7 / p99 68, pre-gate n=12 all censored at 55) and record the rule the numbers follow —
      a stop bound sits at or above the population's p95, never at its p75. Values: worker 110 → 170, gate 60 → 80,
      tail 45 → 65, reviewer 55 → 75, pre-gate 55 → 75. Verify: `grep -n 'LOOP_CHILD_TURNS_MAX' tools/loop/loop.conf`
      shows the five new values, and the comment above them carries the census table the numbers came from.

## 2. The mechanism a bound block records

Requirement: "A block records the mechanism that stopped it", scenario "A block the run's own bound caused is named
as one".

- [x] 2.1 `tools/loop/state.sh`: add `bound` to the closed vocabulary of `done --stop-reason` and name all three in
      the refusal. Verify: `state#aBlockedItemNamesTheMechanismThatStoppedIt` (on HEAD it fails on the vocabulary —
      `--stop-reason bound` is a usage error).
- [x] 2.2 `tools/loop/run.sh`: record `stop_reason="bound"` in the arm that blocks an item whose child the run ended
      (`last_exit` = `LOOP_CHILD_TIMEOUT`, or `LOOP-TIMEOUT` in the transcript), and say in the item's note that the
      attempt waits for the next run. Verify: `run#childPastItsTurnBoundIsBlocked` asserts `stopReason = bound` and
      the note (red on HEAD, which records no mechanism).
- [x] 2.3 `tools/loop/run.sh`: a block with `stop_reason = bound` writes no decision label, as a deadline cut already
      does. Verify: the same case asserts the label stub's record carries no `add-label agent/needs-decision` (red
      on HEAD, which escalates it).
- [x] 2.4 `tools/loop/report.sh`: name a `bound` block on the report's "cut short by the run's own bound" line,
      beside the cuts it already names, so a reader sees what the next run resumes.

## 3. The streak counts refusals

Requirement: "An item the run's own bound cut is recorded `cut`, not `blocked`", scenario "A block at a bound holds
no pair".

- [x] 3.1 `tools/loop/state.sh`: `cmd_next`'s streak holds refusals only — a `blocked` item whose `stopReason` is
      `bound` is no member of the pair — and the reason it prints says `two consecutive refusals ended blocked`.
      Verify: `state#aBlockAtTheRunsBoundIsNoRefusal` (red on HEAD: two bound blocks stop the run).
- [x] 3.2 `tools/loop-cases.sh`: extend `state#nextHonoursEveryStopCondition`'s reason assertion and the vocabulary
      case, repoint `run#childOutlastingTheWallClockIsBlocked` at the new contract (a cap-ended item records `bound`
      and is no longer escalated — the escalation was #674's, and the bound it read is the one #786 narrows), and
      declare the files each of the touched cases can be reached by.
- [x] 3.3 `tools/loop-mutations.sh`: two records for the two sites this change adds (81.1 the mechanism, 81.2 the
      streak), and record 68.6 repointed at the widened escalation guard, which it already owned. Verify:
      `tools/loop-mutations.sh --only "68.2 68.6 81.1 81.2"` — each goes red for its own reason, each restores green
      (`red-when-mutated: 4 · not-noticed: 0`, `evidence/mutations.log`).

## 4. The documents that carry the same numbers

- [x] 4.1 `guidelines/Backlog.recipes.md` §7b: the five values, and the sentence that says a bound-ended item is
      recorded `blocked` — it now records `bound` and asks nobody a question.
- [x] 4.2 `guidelines/BacklogTooling.md` §10's configuration table: the five values.
- [x] 4.3 `tools/loop/state.sh`, `tools/loop/run.sh` and `tools/loop/report.sh` header comments: `two consecutive
      blocked items` → `two consecutive refusals`, since that is what the stop now reads.

## 5. The tracker

- [x] 5.1 Take `agent/needs-decision` + `status/on-hold` off `#788`, `#786`, `#778` and `#759`: each was blocked by
      the run's own bound (or, for `#786`, by a gate red on its own change), which is work and not a question, and
      leaving the hold on them excludes them from the next run for the reason this change repairs. Verified with
      `tools/issues/list.sh --status open`.
- [x] 5.2 Close nothing here: a bound-ended item recorded `cut` (filed as `#790`), the kept patch a later run never
      reads (`#754`) and a bound in tokens (`#760`) are open and independent.
