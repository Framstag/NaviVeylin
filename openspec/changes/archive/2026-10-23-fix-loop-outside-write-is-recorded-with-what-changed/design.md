# Design — the difference is evidence; the decision is opt-in

## D1 — The default is to record, not to decide

The comparison is between two readings of a tree the run does not own. Measured 2026-10-23: 5 of 139 items
across 4 runs blocked on it, and in the three cases of that day the child provably did not write there (every
write target in `items/{274,276,285}/events.jsonl` is under `.pi/worktrees/loop/` or the item's evidence
directory, both gitignored). A false block costs the item's work, one of the run's decision slots, and — with a
second one behind it — the run itself (`two consecutive blocked items`).

So the default keeps the evidence and drops the verdict: the child's transcript gains one
`LOOP-OUTSIDE-WRITE:` line, the item keeps the outcome its own work earned, and the review round that ran during
the difference stands. What the run promises stays true — nothing of the difference is committed into the item's
change, because the run commits from the worktree only.

## D2 — The line names what changed, because a decision must be answerable

Carried from #285 (`project_state_diff`, `outside_write_clause`, `outside_write_pids`, `outside_write_line`): the
symmetric difference of the two readings, bounded and quoted, plus the processes whose working directory is
inside the project and outside the worktree. Without it the note is the sentence that made #274's round-2 worker
unanswerable — "the project tree changed while this process ran" — and the owner has nothing to restore, nothing
to look at and no process to stop.

## D3 — Strict is a flag with a recorded default, not a heuristic

`--strict-outside-write yes|no`, default `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT="no"` in `loop.conf`. A run whose
project tree is exclusive — no peer session, no second run, no landing during a child — can buy today's verdict
back; the run header names the effective value, so a run can be repeated and audited.

## D4 — The reviewer round stands by default, and is discarded under strict

The discard ("a reviewer that wrote cannot have been the reviewer") rests on the same inference as the block, so
it lives under the same flag. Under the default the round's verdict is read as it always was, and the difference
is recorded beside it.

## D5 — #285's change is folded, not landed twice

`fix/issue-285-loop-outside-write-names-no-path-so-the` was blocked by this defect before any review round, and
`git merge-tree` reports a conflict with the base on `guidelines/Backlog.md` (both changes edit the same
paragraph). Its measured line, its case and its mutation record are what the note is worth, so they are carried
here with their evidence cited; #285 closes as superseded instead of landing a naming fix that this change would
have to re-edit.

## D6 — The landing guard is not the attribution, and is left alone

`worktree.sh merge-item` refuses a base that is not clean. That is a rule about what may be merged onto, and it
gets its own answer right: the refusal names the status line (`?? demo-owned.txt`), so a person can clean the
tree. It is left exactly as it is — the change removes the run's *inference about the child*, not the merge's
safety rule — and the two are distinguishable in the record: the attribution said "the worker wrote outside the
worktree", the landing refusal says what the dirty base carries.

## Risks

| risk | why it is acceptable | how it is caught |
|---|---|---|
| a child really does write into the project tree and the run now lands its work anyway | the run commits from the worktree, so the write is never in the change; the note names the path, and a person can restore it | `--strict-outside-write yes`, and the note's paths |
| the note is ignored because it changes no outcome | it is a `state.sh` step and a progress line, both in the run's own transcript, and the child's report keeps the line | `run#childWriteOutsideTheWorktreeIsNoted` asserts the step and the paths |
| strict is turned on for a tree that is not exclusive, and blocks return | the header names the value, so a blocked item's transcript says which mode it ran in | `run#strictOutsideWriteBlocksTheItem` |
