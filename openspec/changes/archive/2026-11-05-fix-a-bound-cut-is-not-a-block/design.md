# Design — the cut is a result, and the branch says so

## 1. Where the wrong word is written

`run.sh` writes the item's record once, in the `case "$outcome"` block at the end of the item loop, and every arm
of that block that is not `closed`, `falsified` or `skipped` falls into `*)`, which escalates and records
`--result blocked`. The **round** already knew better: the review loop sets `review_verdict="cut-short"` for a
reviewer its role's turn bound ended and `review_verdict="deadline"` for one the run's deadline ended, and the
deadline arm already skipped the escalation through `deadline_claim` (#674). So the item's result is the only
place where a cut and a refusal were spelled the same.

**Decision:** a fifth arm, `cut)`, writes `--result cut`, takes the in-progress mark off, names the leftover patch
if there is one, and writes **no** decision label — and the two review-round bound arms set `outcome="cut"`
instead of `blocked`, as do the pre-gate round's two bound arms (through one flag, `pre_gate_cut`, because that
round's refusal string is shared with the transport's own cut and the two must keep different results).

## 2. Why the wall-clock cap and the run's deadline on the item's own worker stay `blocked`

A bound that ends an item's **worker** leaves the item's work unfinished: #271 forbids committing that tree, so
the branch is at the base and nothing about the item is resumable except by working it again. Escalating that is
the rule #203 and #674 already measured, and `run#childOutlastingTheWallClockIsBlocked` holds it. A bound that ends
a **review round** leaves the opposite shape: the change is archived, the gate answered, the commits are on the
branch, and only the reading is missing — which is exactly what makes the record's word matter.

## 3. Why the branch marker, and why two files in one commit

`worktree.sh branch-state` classifies a branch by what it carries, and `review-unanswered` (#262) is the branch of
a round that was announced and never answered — a question only its owner can answer, because nothing in the
branch says whether the reviewer was killed by the machine or never reached. A round the run *itself* ended is the
one case where the answer is known, and the run is the party that wrote the announcement. Leaving both files on
the branch would state "nobody answered round n" beside "the run ended round n", so the cut is one commit that
writes `review-round-<n>-cut.md` and removes `review-round-<n>-requested.md`. `carried_review_state` reads the
highest round and its value `cut`, `branch_state_of` treats it as nothing a person owes, and `adopt-item`'s two
refusals (`refused`, `requested`) stay exactly as they are.

Deleting the announcement alone — which `adopt-item`'s own refusal message already offers as a deliberate act —
was rejected: it would leave no record that a round was asked, and the reader of a later archived change could
not tell a change that was reviewed once from one whose round the loop cut.

## 4. Why the queue's second pass is skipped

The pass exists to admit candidates the run has not seen yet, and it runs when the queue is empty. After a cut the
item's own branch carries commits the base does not have, so the pass classifies that branch as the owner's — the
measured shape of #780's report, which asked its owner a question the loop had just answered for itself. #674
solved the same collision for the deadline with `deadline_cut=0`; this change adds `bound_cut=1`, set in the `cut`
arm, and the guard reads both. The cost is bounded and named: a run that empties its queue right after a cut ends
one pass earlier than it otherwise would.

## 5. What the run still writes about a cut

The item's note keeps the reason word for word (`the run ended the reviewer of round 1 at its role's turn bound —
the round was cut, not refused, so the change is left for the next run`), its `metrics.reviewVerdict` keeps the
round's own value (`cut-short` or `deadline`) so the review rate counts it as undecided, and the leftover patch is
still named in the state and in the report's action list. The item's own table prints `cut` in the outcome column,
and the run's header line prints `cut N` beside the other four tallies.

## 6. Alternatives rejected

- **Recording `blocked` and suppressing only the label.** The streak and the report would still read the item as
  a question, and `blocked` would keep its second meaning.
- **Writing `cut` and leaving the announcement on the branch.** The next pre-screen would read
  `review-unanswered` and hand the item to a person, which is the outcome the issue names as wrong.
- **Adopting and closing the item on a cut in the same run.** A round nobody graded is not a passed round; the
  change is left for the next run and the next run's own adoption path decides, which is the rule `adopt-item`
  already states.
