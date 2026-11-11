# Design — a cut child is continued by the run that cut it

## D1 — The applier is reused, not rewritten

`tools/loop/worktree.sh resume_from_leftover` is what applies a kept patch today (issue #754), and it is the whole of
what a continuation needs: the same patch, the same outcome vocabulary (`applied` / `no-apply`), the same record in
`state.sh`'s `run.resumes`, and the same 3-way fallback for a base that moved. A second applier would be a second set
of rules about what a patch that does not apply means.

What a *later* run does not have, and this run does, is the tree the patch was taken from: the run is still standing in
what the killed child left, which **is** the patch's content, so applying the diff to it cannot succeed. `continue-item`
therefore puts the tree back on the branch's `HEAD` first, removes the untracked work the reset does not reach
(`git clean -qfd`) and — the one part that needs the patch — removes the **ignored** paths under
`LOOP_UNTRACKED_RECORD_DIR` that the patch *carries*, because `keep_leftovers` copies those into the record and copies
nothing of the ones above its size bound. Then `resume_from_leftover` runs unchanged.

Two properties follow from that order, and both are asserted: the continuation starts on exactly what the record holds
(not on a half-written file a `SIGKILL`ed process left), and nothing is discarded that the record does not hold — a
named-but-not-copied ignored path stays in the tree.

## D2 — One continuation per item, and it is a step of the same item

The continuation is **not** an item attempt of its own: it is a phase of the item, at the same step the worker failed,
recorded as `worker-continuation` (`state.sh step`), after the worker's own `worker` step. Three reasons:

- the item's budget, its event stream and its record are the item's — a second attempt would need a second record and
  the state refuses two (`state.sh done` joins a second account to the first);
- the ceiling is then structural: the block that starts a continuation is in the item body and runs at most once, so no
  flag has to be reset and no state can carry a second one;
- the report reads the item's record, so a reader who sees `worker-continuation` beside `worker` sees a phase that was
  resumed in the run instead of a phase handed forward.

**A second context cut is the item's `cut`.** Nothing resets the ceiling: the continuation child is bounded by the same
role key as the worker (`worker`), and if it reaches that bound too, the run records the item `cut` and moves on. That
is what keeps the run from spending an unbounded number of sessions on one item.

## D3 — Unconditional, while the run's own deadline stands — the item's open question

The item left open whether the continuation is unconditional or only while the run has "an item's worth" of deadline
left. It is **unconditional while the deadline has not passed**, for three reasons:

1. **A continuation after the deadline is a lie about the cause.** `run_child` ends a child on its first reading once
   the run's deadline has passed, so a continuation started then would be cut by the *clock*, and the item's record
   would name the clock instead of the context bound that actually ended the work — the distinction #829 ("a round cut
   at a spend bound names neither the clock nor the bound") and #785 paid for.
2. **"An item's worth" has no reading the engine already holds.** The floor `state.sh next` uses
   (`LOOP_ITEM_FLOOR_MINUTES`, derived from the measured items) is a rule about *starting an item*, and a continuation
   is not an item: it starts with the work already in the tree. Reusing the floor would refuse continuations that the
   run could clearly afford.
3. **The failure mode is bounded by the ceiling.** The worst case is one continuation that is cut immediately, which
   costs one child start and one record; the alternative's worst case is not paying for a continuation the run had
   time for.

`run_child` keeps enforcing the deadline on the continuation as it does for every child, so the guard here is only
about *starting* it: the run reads `run.deadlineEpoch` and names the reason on its own output when it does not.

## D4 — The context bound is the child's own reading

Measured in this change's own first red run: the continuation was ended at the bound on its first reading. Cause: the
bound was read from `LOOP_CHILD_EVENTS` from line 0 in every `run_child` call, so the reading was the **item's** largest
context and not the child's, and a child started on a fresh window inherited the number that ended its predecessor.

The turn bound was already a difference (`turns_base`, read before the child starts); the context bound now is too
(`ctx_lines`, a line count read before the fork). What it fixes beyond the continuation: the same inherited reading was
ending a role's child on another role's spend, which is the shape #842 and #760's per-role keys exist to separate.

## D5 — The prompt is the work package plus the patch's summary, and the role is the worker's

`tools/loop/prompts/worker-continuation.md` carries the item, the class, the change root, the work package, the evidence
and scratch directories, the base, the gate and measure commands, the skills, the bound line the run wrote, and the
patch — its file and hunk counts and up to twenty paths — and it points the child at `worker.md` for everything that is
the worker's own: the phases, the project routing, what its shell refuses and the report block. One rule, one file.

`child_role` reads the prompt's name as `worker-*` → `worker`, which is what the continuation is: the same role, a fresh
session. Its turn bound, its context bound, its agent command and its header figures are therefore the worker's,
without a new key and without a new figure in the header.

The skeleton does **not** repeat the worker's phases, its routing or its refusal block: it names them, and the heading
of the refusal block by name, and points at `worker.md`. Two copies of a rule are two rules — `worker.md` is read by
every role that continues or verifies an item already (`worker-gate.md` and `worker-tail.md` do the same), and a
continuation that disagreed with the worker about what a shell refuses would be a defect no case could see. The
prompt cases hold each skeleton's own text where that text *is* the rule (`prompt#thePhasePromptsCarryTheirOwnRoute`
names the three skeletons whose whole body is a child's prompt); this one's body is the state it was handed.

## D6 — What the report and the record say

The item's note opens with the resume sentence `state.sh` folds in from `run.resumes` (`--detail` is passed by
`continue-item`): *the run continued the item in the run that cut it — its child passed the context bound, so the work
it left in `<patch>` was applied onto the branch's `HEAD` and one continuation child took it from there*. The same
mechanism writes the non-apply sentence, so an item the run could not continue says so in its own note and not only in
a log beside it.

No new field was added to the item record: `resumes` already carries the patch, the outcome and the sentence, and the
step ledger already carries the phase. A field nobody reads is what #850's change removed from the report.

## Open decisions

none

The one fork the item left — whether the continuation is unconditional or gated on "an item's worth" of remaining
deadline — is settled in D3, with the three reasons the item asked for. Nothing else here is a choice: the applier,
the patch vocabulary, the role, the ceiling and the `cut` record are rules that already exist (#754, #790, #785,
#829), read at the one place this change adds.

## D7 — What is deliberately not changed

- `LOOP_ITEM_CONTEXT_MAX` (200 000) and `LOOP_ITEM_CONTEXT_WARN` (143 124): #836 keeps them and this change does not
  re-open the bound.
- The `cut` result for an item the run cannot continue, with `bound` as its mechanism: the next run still resumes a
  phase this run ended, and `state.sh` still refuses every other mechanism beside a cut.
- The turn bound, the wall clock and the run's deadline: none of them starts a continuation. The turn bound and the
  clock measure what the child spent failing to converge, and a fresh session does not give that back; the context
  bound is the one where the fresh session *is* the cheaper half.
- `git apply`'s own answer for a patch that does not apply, including a patch with no hunk in it at all: it is
  `resume_from_leftover`'s (`no-apply`), and the continuation inherits it rather than inventing a second rule for
  "there was nothing to carry over".
