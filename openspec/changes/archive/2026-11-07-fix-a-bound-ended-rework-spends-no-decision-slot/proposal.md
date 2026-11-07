# A rework worker the run's own bound ended asks its owner no question

## Why

Issue **#788**. The decision cap counts one population — every eligible item carrying `agent/needs-decision`
(`tools/loop/run.sh:861`) — and the loop writes most of that population itself. On 2026-11-05 the chain ran end to
end: 8 of the 14 eligible decisions read exactly "retry the item, or abandon it — the child reached the recorded turn
bound", the cap is 10, and the 12:45:35Z run exited before it picked an item.

**Root cause, in one sentence:** the run records *which of its own arms* ended a child rather than the fact that its
own bound did, so the branch a **rework worker** of a refusing round leaves behind carries the round's announcement
and the reviewer's refusal with nothing beside them saying the run ended that phase — `carried_review_state`
(`tools/loop/worktree.sh:418`) reads the round as `failed`, `branch_state_of` (`:454`) reports `review-refused`, the
next pre-screen names the item `agent/needs-decision` (`tools/loop/prescreen.sh:73`) and the item spends a slot of
the cap on a question the loop asked itself.

The tree shows one fact read two ways, not two mechanisms:

- the result word already moved: a bound that ends the item's own child **or** its rework worker is recorded `cut`
  with `stop_reason="bound"` and no decision label (issue #790, `tools/loop/run.sh:2404`, `:2685`), so the item
  itself is no longer escalated;
- the *branch* half of the same fact did not: the rework-cut arm writes nothing for the branch, so the highest round
  the branch names is the reviewer's refusal. `review-round-<n>-cut.md` is the record for a round the **reviewer's**
  child never answered (issue #785), and it cannot be reused here — it withdraws the announcement and makes the
  branch `adoptable`, and nothing may adopt a change its reviewer refused;
- `start-item` (`tools/loop/worktree.sh:314`) therefore refuses the item on its next start as well, and the branch
  nothing reads stays unread.

**The red test.** Three cases, all on the run's base `9b3f9d5b`, run against a copy of that base with only
`tools/loop-cases.sh` taken from the change (`items/817/scratch/red817`, `git archive 9b3f9d5b`) — `evidence/red-on-HEAD.log`,
tally in `evidence/red-on-HEAD.xml` (`tests=3 failures=3 errors=0 time=12`, `2026-11-07T13:10:04Z`–`13:10:16Z`):

```
run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted|   the branch records the rework the run's own bound ended: expected '1', got '0'
prescreen#aReworkCutBranchIsWork|   a branch whose rework a bound ended reads as its own value, not as the refusal: expected 'rework-cut', got 'review-refused'
worktree#setsAsideAReworkCutBranchAndStartsFresh|   a branch whose rework a bound ended does not park the item: loop: branch fix/issue-21-demo already exists and carries commits the base does not …: expected '0', got '5'
```

The same three after the change — `evidence/green-on-change.log`, `evidence/green-on-change.xml`
(`tests=3 failures=0 errors=0 time=14`, `2026-11-07T13:09:45Z`–`13:09:59Z`), so the tree's revert is the red run
itself.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md:2396` "A block records the mechanism that stopped
it", `:1826` "A review round is a record on the branch, from its announcement to its answer" (the fifth value the
branch can carry), and `:1249` "A blocked attempt's branch is kept under a name of the run's own, and its item is
worked" (a phase the bound cut is work, not a question).

**Owning guideline section:** `guidelines/Backlog.recipes.md`, section 7e "Leftovers, the report and the queue" (`:877`,
"What a bound cut is, and what it is not" at `:936`) — it named the reviewer's own cut and now names the rework's.

surface: 2 cases added, 1 extended, 3 engine scripts (`run.sh` one arm plus a helper, `worktree.sh` four readings
and one writer, `prescreen.sh` one arm), 2 mutation records re-anchored (one per shifted file) and 1 added for the new
branch write, 1 guideline paragraph corrected — counted with `bash tools/loop-selftest.sh --list | wc -l` (355 cases)
and `bash tools/look.sh 'rework-cut' --path tools/loop/worktree.sh` (16 matches).

## What Changes

1. **`run.sh` records the rework's cut on the branch.** The arm that reads a rework worker the run ended
   (`tools/loop/run.sh:2678`) calls the new `record_rework_cut` (`:1794`, called at `:2688`), which writes
   `worktree.sh record-review-round <issue> <change> <round> rework-cut` beside `record_cut_round`. The announcement
   and the refusal stay.
2. **`worktree.sh` reads that record as its own state.** `record-review-round` accepts `rework-cut`;
   `carried_review_state` reads `review-round-<n>-rework-cut.md` ahead of the refusal of the same round;
   `branch_state_of` reports `rework-cut`; `start-item` sets that branch aside under the run's leftover prefix and
   works the item fresh; `adopt-item` refuses it (the phase is work, not a verdict).
3. **`prescreen.sh` queues it as work.** A `rework-cut` branch is offered as an eligible item with the branch as its
   evidence, and a stated question of the item's own still outranks it — the same ordering `unfinished` already has.

## What this does not do

- It does not re-count, re-classify or raise the decision cap (`tools/loop/run.sh:861`, `tools/loop/loop.conf:20`):
  the cap is left where it is, and its population shrinks because no question is written.
- It does not re-record the result: `cut` and `stop_reason="bound"` are issue #790's, already in the base.
- It does not make a cut child's work resumable in place: the pre-screen offers the item and `start-item` sets the
  attempt aside; the resume path belongs to #754 and nothing here replaces it.
