# A child the context bound ended is continued by the run that cut it

## Why

`LOOP_ITEM_CONTEXT_MAX` ends a child and the run keeps the work that child left as a patch — and then hands the item
to the **next** run, in a tree that run may no longer have. The bound is not the defect: the answer on #836 keeps
`LOOP_ITEM_CONTEXT_MAX` at 200 000 and the checkpoint at 143 124, and #754 refuses to commit a cut child's tree.
What the run of `2026-11-11T11:37:08Z` (`.pi/issue-loop/run.json`) lost was **when** the resume happens:

- it cut two of its eight items at the bound — #847 at 200 174 tokens after 142 turns and 846 s, #839 at 200 869 after
  125 turns and 1 389 s — keeping 1 010 460 B (160 hunks over 5 files) and 1 323 610 B (188 hunks) in
  `items/847/leftover-from-killed-run.patch` and `items/839/leftover-from-killed-run.patch`;
- the two are 33.08 of the run's 75.45 Mtok (44 %) and closed nothing;
- the run then stopped `queue empty` at 15:14:07Z against a 23:05:00Z deadline — 435 min of its own budget unspent —
  with both patches in hand and 18 items excluded;
- and the applier's no-apply arm fired on **both** patches the next attempt inherited (`run.resumes` records
  `outcome: no-apply`), because the base had moved between the runs.

**Root cause, in one sentence:** nothing resumes a cut item inside the run that cut it, although that run still holds
the patch, the work package and whatever is left of its deadline — so the one moment the tree the patch was taken from
is still the tree it is applied to is thrown away.

## What Changes

After a worker the context bound ended, the run applies that worker's kept patch itself, onto the branch's own
`HEAD`, and starts **one** continuation child from it — the same role, a fresh session, a prompt that carries the
work package and the patch's own summary. The item is decided by that child's answer as any item is. The bound, the
checkpoint, the `cut` record for an item the run cannot continue, and #754's no-commit rule are unchanged; what
changes is who applies the patch.

Two smaller things follow from it and are part of this change:

1. **The context bound is read from the child's own stream.** Every child of an item appends to one event file, and the
   bound was read from the whole file — the **item's** largest context — so a fresh session inherited the number that
   ended the child before it and a continuation would be cut on its first reading. The reading is now a baseline plus
   the lines the child adds, exactly as the turn count already was.
2. **The ceiling is one continuation per item.** A second cut is the item's `cut`: that is what keeps this from being a
   loop that never ends an item. A patch that does not apply is the applier's own answer (#754) — the run records the
   non-apply and leaves the item `cut` rather than continuing it from nothing.

**Surfaces.** `tools/loop/run.sh` (the continuation, the child's own context baseline), `tools/loop/worktree.sh`
(`continue-item`: the tree put back on the branch's `HEAD` and the patch applied onto it, reusing `resume_from_leftover`),
`tools/loop/prompts/worker-continuation.md` (new), `tools/loop/tests/fixtures/pi` (a continuation's own report),
`tools/loop/tests/loop-cases.sh` (2 cases added, 1 rewritten), `tools/loop/tests/loop-mutations.sh` (2 records), the
capability `agent-issue-loop`.

**The open question the item left is answered:** the continuation is taken **unconditionally** while the run's own
deadline has not passed — not gated on "an item's worth of deadline left". A continuation started after the deadline
would be ended on its first second, and its cut would name the clock instead of the bound that actually fired, which
is the distinction #829 and #785 were filed about. The decision and its argument are in `design.md`.

surface: 2 cases added, 1 case rewritten, 2 mutation records added, 1 prompt skeleton added, 1 guideline section
realigned, 1 spec requirement modified (2 scenarios added, 3 kept) — `grep -c 'run#aContextCutWorkerIsContinuedByItsOwnRun\|run#aCutWhosePatchDoesNotApplyIsLeftCut' tools/loop/tests/loop-cases.sh`
