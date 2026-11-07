# Design — a bound-ended rework leaves a phase, not a question

## Root cause

The run records *which of its arms* ended a child instead of the fact that its own bound did. The result word of
that fact already moved (issue #790): a bound that ends the item's own child or its rework worker is recorded `cut`
with `stop_reason="bound"` (`tools/loop/run.sh:2404`, `:2685`), so the item is no longer escalated. What did not
move is the **branch**: the arm reading the rework worker of a refusing round writes nothing onto it, so the highest
round the branch names is the reviewer's refusal — `carried_review_state` (`tools/loop/worktree.sh:418`) reads it
`failed`, `branch_state_of` (`:454`) reports `review-refused`, the next pre-screen names the item
`agent/needs-decision` (`tools/loop/prescreen.sh:73`) and the item spends a slot of the decision cap on a question
the run asked itself. `start-item` refuses the branch on the next start as well (`tools/loop/worktree.sh:314`).

## The single fix

1. `tools/loop/run.sh` — the rework-cut arm (`:2678`) records the phase on the branch through `record_rework_cut`
   (`:1794`, called at `:2688`), a sibling of the existing `record_cut_round`, which calls
   `worktree.sh record-review-round <issue> <change> <round> rework-cut` when the item has a change. The queue's
   second pass is already skipped (`bound_cut=1`, the generic `cut)` arm).
2. `tools/loop/worktree.sh` — `record-review-round` accepts `rework-cut` and writes
   `evidence/review-round-<n>-rework-cut.md` **beside** the announcement and the refusal (both stay: the round was
   asked *and* answered); `carried_review_state` reads that file ahead of the refusal of the same round;
   `branch_state_of` reports the state `rework-cut`; `start-item` treats it as the blocked attempt it is, renaming
   the branch aside and working the item fresh, with its own line naming which attempt was moved; `adopt-item`
   refuses it, because a phase is not a verdict to merge.
3. `tools/loop/prescreen.sh` — a `rework-cut` arm queues the item as work with the branch as evidence, and a stated
   question of the item's own still outranks the branch, exactly as it does for `unfinished`.
4. `guidelines/Backlog.recipes.md`, section 7e ("Leftovers, the report and the queue") states the two branch
   records — the reviewer's own cut and the rework's — and the two readings they give.
5. `tools/loop-cases.sh` — `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` is extended with the branch
   record and the reading, and two cases are added: `prescreen#aReworkCutBranchIsWork` (the reading and the queue)
   and `worktree#setsAsideAReworkCutBranchAndStartsFresh` (the start).

No state schema changes: `stop_reason` already carries `bound`, and `state.sh` already reads it for the report's
`cut short by the run's own bound:` line and for `next`'s two-blocked streak (`tools/loop/report.sh:179`,
`tools/loop/state.sh:304`).

## Blast radius

- The result word does not move: a bound that ends the rework worker is already recorded `cut` with `bound` beside it
  (`tools/loop/run.sh:2684-2685`), and no decision label is written for it. Nothing adopts, closes or merges a change
  on the strength of this change.
- The new branch state is read by three callers of `branch-state` (`run.sh`'s adoption exclusions, `prescreen.sh`,
  `start-item`); the first two treat an unknown state exactly as before (the adoption arm only acts on `adoptable`),
  and `start-item` gains one arm. `adopt-item` gains a refusal for it, so a phase can never be merged as a verdict.
- An item that already carries `agent/needs-decision` stays its owner's: the `rework-cut` arm of the pre-screen
  honours `awaiting_decision` before it offers the branch as work.
- A branch whose last round holds neither an answer nor this record still reads `review-unanswered` and is still the
  owner's (#262). The new value is written only by the arm that ended the rework at a bound.
- The worktree the cut child left dirty is the run's own pre-flight's question, not this arm's: `run.sh` keeps it and
  names it, and `--reset-worktree` discards it (#664). That reading is unchanged here — the branch record is what
  makes the *item* work for the next pre-screen, and the kept tree is repaired by the run that starts it.

## Known gaps

Three claims this change carries are asserted by no case. They are named here rather than promised a case this
correction round does not write:

- `tools/loop/prescreen.sh:373-380` — a stated question of the item's own outranks `rework-cut` exactly as it outranks
  `unfinished`, because the two states share the arm; the case that exercises the ordering
  (`prescreen#itemAwaitingADecisionIsNotQueued`) carries an `unfinished` branch only.
- `tools/loop/worktree.sh:518-519` — `cmd_adopt_item`'s `rework-cut` refusal is reached only by a hand-run
  `adopt-item`, since `run.sh:415` acts on `adoptable` alone; no case calls it.
- The record's own text (`tools/loop/worktree.sh:616-628`, "the round was asked and answered") is asserted by no case:
  the two branch cases write their own fixture text, and the run case asserts the file's presence.

## Alternatives the evidence eliminated

- **Count only decisions whose reason is a fork** (the item's option B): starts the run but leaves every cut item
  parked on the tracker, and the branch nothing reads stays unread — it re-counts the question instead of removing
  it.
- **Raise `LOOP_MAX_DECISIONS_DEFAULT`** (option C): it was raised 3 → 10 on 2026-10-20 for this same reason and
  would be raised again each time it fires; a cap that must move to let the loop start is not a cap.
- **Age the decision** (option D): self-excusing — a run that files a question per item never has to see the pile.
- **Record the whole round as `cut`** (`record-review-round … cut`): `branch_state_of` would read the branch
  `adoptable` (a round the run itself ended asks nobody anything, #785), and the next run would merge and close an
  item on a change the reviewer refused. `cut` means the run ended the round, not its rework.
- **Record the round as failed**: `review-refused` is the owner's question (#185), which is the spend this change
  exists to stop; and the refusal was never answered, so it would also close the item's only open work.

## Open decisions

none

## Rollback

Revert the four files (`run.sh`, `worktree.sh`, `prescreen.sh`, `tools/loop-cases.sh`), `tools/loop-mutations.sh`
(the two re-anchored records and `94.1`, whose pattern quotes `record_rework_cut "$round"` and would otherwise name a
line the reverted tree no longer has) and the guideline paragraph.
A state written by a run that carried this change reads as before under the older scripts: `stop_reason=bound` and
the block's note are already understood (#786), and a branch carrying `review-round-<n>-rework-cut.md` reads as
`review-unanswered` again — the pre-change behaviour, no migration and no lost commit.

## Verification shape

The gate is the loop's whole self-test and is not this child's (the run deferred it to a fresh child after the diff
review). This child ran the three cases red on a copy of the run's base `9b3f9d5b` carrying only the change's case file
(`evidence/red-on-HEAD.log`, `tests=3 failures=3`), green on the change (`evidence/green-on-change.log`,
`tests=3 failures=0`), then the rule's own surface: the `prescreen` group (31 cases), the `worktree` group (25), the
`state` group (26), `run#*Cut*` (6) and the named run/driver cases that read a bound, a block, the cap, the review
record and the case-declaration driver — 93 cases, all `PASS` (`evidence/focused-cases.log`).
`bash tools/loop-mutations.sh --check` applies 272 of 272 records, `bash tools/loop-mutations.sh --only 94.1`
reports the record's own `RED` and its `restore … green again` (`evidence/mutation-94.1.log`), and
`tools/loop-mutations-selftest.sh` — 34 of 34 — is the gate's, owed for a `tools/loop-mutations.sh` delta
(`LOOP_GATE_SUITES`), so it is not quoted from this child.
