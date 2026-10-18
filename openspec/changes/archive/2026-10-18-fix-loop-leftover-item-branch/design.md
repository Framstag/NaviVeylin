# fix-loop-leftover-item-branch — design

## D1 — An existing branch is judged by its commits, not by its name

The run owns each item's branch (D4), and a branch outlives the run that created it: a blocked item's branch
is its evidence, and the report names it. So the branch a next run finds is either

* **the base's own ancestry** — the previous attempt committed nothing (the common case: the run's own
  verification blocked an item that produced no diff), and `git merge-base --is-ancestor <branch> <base>`
  says so. Reusing it (`switch -C` to the base) discards nothing, because there is nothing on it that the
  base does not have. The item is workable again, which is the whole point of a blocked item.
* **work the base does not have** — the previous attempt committed a fix, an archive or both. That is the
  one record of that work (`end-item` commits it before the run's verification reads the tree), so
  overwriting the branch would throw away the evidence an escalation asks a person to judge. Refused with
  the branch and the commits named; the decision is the owner's, and the item's own entry carries the
  refusal.

| alternative | why rejected |
|---|---|
| delete the branch and create it again | destroys the previous attempt's commits, which is the one thing a blocked item's escalation points at. |
| `switch -c` under a different name (a suffix per run) | the item's branch name is what the run's report and the escalation name as evidence; two names for one item make the record ambiguous, and the branches accumulate. |
| reuse whatever the branch holds and continue from there | the item would be built on a tree that is neither the base the run recorded nor the work the reviewer saw — the stale-base refusal exists exactly to prevent building on an unreviewed tree. |

## D2 — A refusal before the item's first phase is still recorded

`run.sh`'s only answer to a failed `start-item` used to be `break`: no item, no label, no reason — the
report's "nothing processed". The refusal now goes through the same path a mid-item verdict takes
(`loop_labels escalate` + `loop_state done … --result blocked`), because the two facts a person needs are the
same: which item, and what the run already found. The run then takes the next candidate rather than ending:
one item's leftover branch is not a reason to spend no budget, and the decision cap is what stops a run whose
decisions pile up.

The `base moved` refusal keeps its own meaning (exit 5 from the run, before any item exists) — it is about
the run, not about the item.

## D3 — Verification: the loop's own shell gates

Three cases (red on HEAD, green after: `evidence/red-on-head.log`), three mutations — `12.5` makes the reuse
test never true, `12.6` makes it always true, `12.7` drops the recorded item — and the loop suite, the
mutation set and the portability suite as the gates. No compiled file changes.

## Not measured, and named

A branch that is checked out in another worktree makes `git switch` fail with git's own message; that path
still ends the run with that message and no item recorded. It is not reachable with this loop's single
worktree (`worktree.sh create` refuses a worktree that is not this repository's), so it is named here rather
than handled.
