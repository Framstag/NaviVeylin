The run ended review round 1 of this change at a bound it records, before the reviewer answered.

Recorded for issue #780 at 2026-11-05T20:59:44Z on the item branch, in the shape `worktree.sh
record-review-round <issue> <change> 1 cut` writes (issue #785): the round was cut, not refused, and no
reviewer graded this change, so it asks nobody anything and the branch stays adoptable.

The bound is recorded in `.pi/issue-loop/items/780/reviewer-report-1.md` — `LOOP-TIMEOUT: the child reached
the recorded turn bound of 250 turns (LOOP_ITEM_TURNS_MAX)` — and the escalation on the item asked the owner
what a cut round asks nobody: "retry the item, or abandon it". The owner answered retry, and a cut round is
not an unanswered one (#785), which is why this record replaces the announcement
`review-round-1-requested.md` in the same commit: a branch carrying "nobody answered round 1" beside "the run
ended round 1 at its bound" would make a person's question out of a round the loop itself ended.

What the round's *report* is not: the archived `review-round-1.md` on this branch is the pre-gate review, whose
blocking finding the round-2 and round-3 commits answered. No reviewer graded those corrections, and no
`review-round-1-passed.md` is written for that reason — the record is the cut, exactly as the run would have
written it had it not been ended first.
