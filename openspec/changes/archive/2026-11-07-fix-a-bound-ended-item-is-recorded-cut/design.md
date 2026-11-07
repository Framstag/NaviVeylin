# Design

## Root cause

`run.sh` decides an item's result by **which arm wrote it**, not by what the run measured. The arm that reads a
child's non-zero exit writes `outcome="blocked"` and, for the exit `run_child` uses for every bound it enforces,
adds the mechanism `stop_reason="bound"` (`tools/loop/run.sh:2334`). The arm that ends a review round overrides the
result to `cut` (`tools/loop/run.sh:2372`). One fact — a phase the run's own bound ended — therefore reaches the
state as two results, and the report has to re-translate the mechanism to print it on the right line
(`tools/loop/report.sh:172` before this change). `state.sh` already states the rule the child's arm missed: `cut` is
"the fifth result, and it is not a shade of `blocked`" (`tools/loop/state.sh:507`).

## The single fix

The result is read off the mechanism the child's arm already recorded, at the one place the result is chosen:

```sh
if [ "$pre_gate_cut" -eq 1 ] || [ "$stop_reason" = bound ]; then outcome="cut"; fi
```

`stop_reason` is `bound` exactly when `run_child` ended the child at a bound — the role's turns, the context bound, the
child clock, a vanished writer or the run's deadline — so the rule has one input, and the mechanisms stay where the
child's arm put them:

- the `cut` arm passes `--stop-reason "$stop_reason"`, and `state.sh` accepts the pairing `bound` with `cut` (and
  nothing else with `cut`), so a mechanism's spend is one field whatever result carries it;
- the `cut` arm adds the sentence the block's note used to carry ("left for the next run rather than asked about"),
  because that sentence is the reading the result now states;
- the block arm's escalation guard excused a block whose cause was the run's deadline or a bound. Both causes leave a
  `cut` above it, so the guard is removed and every block that arm records is escalated;
- `report.sh`'s bound row reads `select(.result == "cut")` again.
- The rework worker of a refusing review round is the same rule at the second commit site (`tools/loop/run.sh:2617`):
  the arm that skips that worker's commit wrote `blocked` with no mechanism, so an item a bound ended there reached
  the state as a block with no name beside it. It now records `cut` with `stop_reason="bound"` beside the round's own
  transcript, and its note keeps the reason every other cut-bound arm writes.

## Blast radius

- **What the record says.** The item's result and verdict become `cut`/`cut`; `stopReason` stays `bound`; the note
  keeps the reason word for word, gains the resume sentence, and still names the leftover patch.
- **What the run does.** No label, no comment, no slot of the decision cap, and the run still takes no second queue
  pass after a cut (`bound_cut`). The commit is still skipped and the tree is still kept.
- **Who reads it.** `state.sh report`'s table (`stop` column unchanged), `report.sh`'s counters (the item moves from
  `blocked` to `cut`), its bound row, its `recover` action (read from `leftover`, unchanged) and the two-blocked
  streak (`next` still ignores `cut` and still ignores a legacy `blocked` + `bound`).
- **A state written before this change.** An item recorded `blocked` with `stopReason: bound` is counted in the block
  tally and no longer printed on the bound row. That is the trade the change takes: the row now reads one predicate,
  and the legacy reading is the one the tree that wrote it used.
- **Files.** `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`,
  `tools/loop-mutations.sh`, the change's spec delta (six modified requirements) and two guideline sections. No
  compiled file, no Gradle path.
- **What the surface grew after the plan.** The plan counted six cases against the base it was written on, and the
  cases that assert the same rule grew after it; the change realigns them rather than leaving them red:
  `run#childPastItsContextBoundIsBlocked` renamed to `run#childPastItsContextBoundIsCut` (the context bound
  is the same arm and the same `bound` mechanism), `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` moved to
  `cut` with the arm that records it, `run#aRotatedStreamStillCountsTheTurnsItDropped` (#802 landed after the plan)
  moved to `cut` with the same `bound` mechanism, `run#theRecordedZeroKeepsTheContextBoundOff` and
  `run#aVanishedWriterEndsTheWait` asserted on `cut`. The last one already argued the new reading in its own comment —
  "a vanished writer is a bound the run enforced, not a refusal of the change" — and a `blocked` result was what made
  its note contradict its tally.

## Rollback

`git revert` of the one commit: the result line returns to `pre_gate_cut` alone, the `cut` arm drops `--stop-reason`,
`state.sh` refuses `bound` on a cut again, the report row regains its mechanism clause, the rework arm records
`blocked` again, and the mutation records return to 23.1/68.2/68.6/81.1's old shapes. Nothing is written outside the tree — no state, no tracker, no worktree
depends on the new result — so a revert needs no repair step.

## Alternatives the evidence eliminated

- **Keep `blocked` and let the report translate.** That is the tree before this change, and it is what the item
  measured: three items of one run recorded as blocks the run itself had caused, and a report row whose first clause
  is `or (.result == "blocked" and (.stopReason // "") == "bound")`. It satisfies no reader — the note, the tally and
  the row each re-derive the same fact.
- **Read the exit status (`124`) instead of the mechanism.** Two readings of the same fact, and a wall clock or a
  harness could produce `124` without the run ending anything; the mechanism is the word `run_child` writes and the
  arm already carries it.
- **Cut only the turn bound and keep the clock a block.** It would keep the two arms of one rule disagreeing, and the
  clock is what ended three of one day's six bound cuts; the deadline is the same bound one level up. The tree holds
  no reading that separates the three (they are one arm, one mechanism), so separating them would be new vocabulary
  for a distinction no reader asked for.
- **Make the deadline keep its block and cut only the child's own bounds.** The deadline is the run's budget, and the
  state already records what a deadline-cut item leaves and asks nothing about it; a `blocked` result is the only
  reading that forces the report to name a decision nobody owes.
