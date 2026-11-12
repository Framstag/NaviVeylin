# Design — the checkpoint splits the item's worker

## D1 — The checkpoint ends the item's first worker, not every child

Only the phase the continuation machinery already continues is split: the item's first worker
(`worker-prompt.md`). Three reasons:

1. **The continuation is the split.** A phase ended at the checkpoint has to be handed to a fresh child with the
   same state, and the tree already carries exactly that for the worker (#851): `worktree.sh continue-item` puts the
   tree back on the branch's `HEAD`, applies the kept patch, and one continuation child reads the work package. A
   split for a phase with no continuation would be a cut the run did not have to take.
2. **The other phases read rather than grow.** The gate, the tail and the reviewer already have their own context
   keys (`LOOP_ITEM_CONTEXT_MAX_WORKER_GATE`, `…_WORKER_TAIL`, `…_REVIEWER`) and their work is a file, not a tree; a
   rework round answers a findings list against a tree that already holds the change. Ending one of them at the
   checkpoint would buy a session start and lose the reading it was doing.
3. **One split per item is the ceiling.** The continuation is started from the item body, which runs once, so the
   phase that is split cannot be started twice. The continuation itself is exempt (it is not the phase
   `worker-prompt`), so a continuation that crosses the checkpoint runs on to the stop: if it reaches
   `LOOP_ITEM_CONTEXT_MAX` the item is `cut`, exactly as #851's ceiling says.

## D2 — Taken only from a child that has something to continue

A continuation is the kept patch applied onto the branch's `HEAD` (#851). A worker that reaches 143 124 having
written nothing leaves a patch with no hunk, and the applier refuses it — so splitting that child would record the
item `cut` at 143 124, below the stop of 200 000, which is exactly the bound #836 refused to lower. The split is
therefore taken only when `git status --porcelain` in the worktree is non-empty at the crossing. That reading is one
fork in the whole second the stream is read — not one per look, of which there are twenty — so a worker that writes
its change after the crossing is split on its next reading. A session that has read for 143 124 tokens and written
nothing is a reading session, and the stop's own continuation (#851) still bounds it.

## D3 — The stop wins when one reading crosses both

The checks keep their order: the reading is compared with the role's stop first, then with the checkpoint. A child
that reports 210 000 in one record is ended and named by `LOOP_ITEM_CONTEXT_MAX`, as today
(`run#childPastItsContextBoundIsCut` pins it), and the checkpoint fires only on a reading between the two values.
That is the shape the measurement is about: the two cut items crossed 143 124 near turn 100 and 200 000 fifty turns
later, so a poll that takes one reading a second sees the checkpoint first on every ordinary growth curve. Naming
the stop when the reading is already past it is the truthful line, not a fallback.

## D3b — The split is the continuation, with its own step name

`child_ended_by_context_bound` already reads which bound ended the child from the one `LOOP-TIMEOUT` line
`run_child` wrote; it now recognises the checkpoint's line too. The continuation block that #851 landed is used
unchanged, and only the step name is chosen from that line:

- `worker-continuation` — the child the context **bound** ended (issue #851);
- `worker-checkpoint` — the child the context **checkpoint** ended (this change).

The item's note carries the same distinction through `worktree.sh continue-item <issue> <reason>`, whose sentence
otherwise says "its child passed the context bound" — a sentence that would be false for a split taken below the
stop. The two names are the whole of what a reader needs: the step ledger says which session the run chose to end,
and the record's `resumes` entry says the work was applied in the run that ended it.

## D4 — Why the prompt skeleton changes by one sentence

`tools/loop/prompts/worker-continuation.md` opened with "the run **ended it** when its context reached
`LOOP_ITEM_CONTEXT_MAX`". A checkpoint continuation would meet a false statement about its own start, so the
sentence names the split the profile records and the `{{BOUND_LINE}}` it already renders says which one fired. No
second skeleton is added: the state handed to the child, the phases, the routing, the refusals and the report block
are the same, and two skeletons would be two rules (design D5 of #851).

## D5 — Unconditional while the run's deadline stands — the item's open question

The item leaves open whether the split is unconditional at the checkpoint or only while the run has an item's worth
of deadline left. #851 asked the same question of its own step and answered it in its `design.md` D3: unconditional
while `run.deadlineEpoch` has not passed, because a continuation started after the deadline is ended on its first
second and its cut names the clock instead of the reading that ended the work. This change does not invent a second
answer: the checkpoint split reuses that guard and that sentence, so a run that is past its deadline does not start
one and says why on its own output.

## D6 — What does not change

- `LOOP_ITEM_CONTEXT_MAX` (200 000) and `LOOP_ITEM_CONTEXT_WARN` (143 124): #836 and #851 keep both; this change
  takes the reading, not the numbers.
- The `LOOP-CONTEXT:` line: it is still printed once when the reading crosses the checkpoint, immediately before
  the child is ended, and it still names the recorded 143 124.
- The stop's line, the `cut` record, `bound` as the stop reason and #754's no-commit rule.
- The continuation's prompt file, its role (`worker`), its bounds and the `worker-continuation` step name.

## Open decisions

none

## Alternatives eliminated

- **Split every phase at the checkpoint.** A gate, tail or reviewer child ended there has no continuation, so the
  item would be cut at 143 124 instead of 200 000 — the bound lowered by the back door, which #836 refused.
- **Split on the reading alone, with nothing in the tree.** The applier refuses a patch with no hunk, so the item
  would be cut at the checkpoint instead of running on to the stop — the same lowering by another route; D2 reads the
tree once at the crossing instead.
- **Split the continuation too.** A continuation that crosses the checkpoint would start a second continuation and
  the ceiling would be gone; the item could never end.
- **A second prompt skeleton for the checkpoint.** The state, the route and the rules are identical; a second copy
  is a second set of rules (the reason #851's design D5 gives for one skeleton).
- **Gate the split on remaining deadline ("an item's worth").** #851's D3 eliminates it: the floor is a rule about
  starting an item, and a continuation starts with the work already in the tree.
