# A round the run's own bound cut is a cut, not a block

## Why

`run.sh` records `blocked` for every phase the run itself ended, and `labels.sh escalate` then writes
`agent/needs-decision` and `status/on-hold` on the item — whether the phase it ended was the work, the gate or the
last reader. Nothing in the item's record separates a round that was **cut** from a round that **refused**: the
review round's own verdict already says `cut-short` or `deadline`, and the item's result says `blocked` anyway.

**Root cause, in one sentence:** the item's *result* is written from the child's *exit*, while the question the
result answers is whether the item's owner owes an answer — and a bound the run itself enforced is the run's
budget, not a finding about the item.

Measured 2026-11-05 (#780): the tail report carried `LOOP-VERDICT: closed`, the gate `tests=460 failures=0`,
`evidence-check.sh` PASS, the change was archived, and branch `fix/issue-780-…` held three commits — then review
round 1 was ended at the role's turn bound after 53 turns, and the item was recorded `blocked` on "the run ended
the reviewer of round 1 … the round was cut, not refused" and escalated. #783, #767 and #763 reached the same state
that day: archived change, evidence in `evidence/`, a review-round report on the branch, `blocked` for a cut round.
Each spent a decision slot and a person's evening on a round nobody refused, and each counted towards the run's
two-blocked stop — half of the decision cap that stopped that day's fourth run before it picked an item.

surface: 1 result added to the state (`cut`, beside `done`/`skipped`/`blocked`/`falsified`), 2 tally lines
(`state.sh show`, `state.sh report`) and 3 lines of `report.sh` extended, 1 line of `state.sh next` documented as
blocked-only, 2 outcome sites in `run.sh` moved from `blocked` to `cut` (plus the pre-gate round's two bound arms),
1 branch marker (`review-round-<n>-cut.md`) added to `worktree.sh` with the announcement withdrawn beside it,
1 helper (`record_cut_round`) and 1 run flag (`bound_cut`), 1 case strengthened, 4 case assertions repointed,
3 mutations added and 2 repointed, 2 guideline sections. Counted with
`git diff --stat` and `bash tools/loop-mutations.sh --list | wc -l`.

## What Changes

1. **The state has a fifth result.** `state.sh done --result cut` records an item whose phase the run's own bound
   ended: no decision label, no `--stop-reason`, no entry in the two-blocked streak `next` stops on, and its own
   line in the report (`cut short by the run's own bound: #21 cut-short`) beside the blocks it did not cause.
2. **A cut review round is a cut item.** The reviewer's `LOOP-TIMEOUT` (its role's turn bound) and `LOOP-DEADLINE`
   (the run's own deadline) end the item as `cut`, as does the round before the gate when a bound ended it. The
   wall-clock cap that ends an item's own worker is unchanged: there the item's work did not finish, and that stays
   a block its owner is asked about.
3. **The branch carries the cut, not a silence.** The round is written on the branch as
   `review-round-<n>-cut.md` and the announcement of the same round is withdrawn in that commit, so
   `branch-state` reads `adoptable` rather than `review-unanswered` — the next pre-screen offers the attempt to the
   loop's own adoption path instead of to a person.
4. **The queue's second pass is skipped after a cut**, the rule the deadline already keeps (#674): the pass would
   re-exclude the item whose branch the cut child had just made, and the report would name a decision the run had
   already answered.

## What this does not do

- It does not resume the cut phase itself. The item is left for the next run and its branch is what a later run
  reads; *the phase it resumes at* is #754's mechanism (the tree of a bound-ended child is kept as a patch nothing
  reads), and this change only stops the record from asking a person about it.
- It does not re-derive any bound. `LOOP_CHILD_TURNS_MAX_<ROLE>`, the wall clock and the deadline are #784's and
  #686's numbers and stay as they are.
- It does not change the transport's own cut: a report cut twice on its way to the file is still `blocked` with its
  reason, because no bound ended it and nothing about it says the item is converging.
