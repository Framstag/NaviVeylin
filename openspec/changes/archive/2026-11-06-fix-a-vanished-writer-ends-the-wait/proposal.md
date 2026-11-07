# A wait ends when the writer of the record it waits for is gone

## Why

The run ends its wait for a child on the child's own completion record: `run_child` clears `$2.status`, starts the
subshell that writes it (`tools/loop/run.sh:1337`), and polls `while [ ! -e "$status_file" ]`
(`tools/loop/run.sh:1386`) with three clocks and two spend bounds beside it — the run's deadline (`:1402`), the child's
wall clock (`:1410`), the role's turn bound (`:1423`) and the item's context bound (`:1438`). Nothing in that loop asks
whether the subshell that would write the record still exists, so a writer killed before it wrote one is waited for
until the child's wall clock: `LOOP_CHILD_MAX_SECONDS` is 2 700 s here, and `child_cap` raises it to what one item's
gate owes.

**Root cause, in one sentence:** every condition that ends the wait measures what the child *spends*, and none measures
whether anything is still there to write the record the wait ends on — so a vanished writer is waited out until a
clock, and the run holds the one lock that keeps the next run out while it does.

**The red case, on the base `c0dbcf5c`** (`bash tools/loop-selftest.sh --case 'run#aVanishedWriterEndsTheWait'`, the
engine change the only thing reverted — `evidence/red-on-HEAD.log`):

```
2026-11-06T07:02:23Z
run#aVanishedWriterEndsTheWait|   the run was still waiting 60 s after the writer of its child's record was killed
--- run#aVanishedWriterEndsTheWait[0] (exit 1)
FAIL run#aVanishedWriterEndsTheWait
loop-selftest: 1 of 1 cases failed
exit 1
2026-11-06T07:03:27Z
```

`evidence/red-on-HEAD.xml` is the tally of that run (`tests=1 failures=1 errors=0 time=64`), kept beside the log
because the shell runner writes no JUnit XML; the same cases on the change are `evidence/green-on-change.log`
(`tests=3 failures=0`, 2026-11-06T07:03:30Z–07:03:44Z).

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "A child never outlives the run's deadline"
(`:2205`) is the requirement the wait's clocks live under, and the one this delta sits beside: the delta **adds**
"A wait ends when the writer of the record it waits for is gone", because no existing requirement stated the
condition that was missing.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:195 "What an item costs, and the bounds on it",
its paragraph `:262` "What bounds it" — the paragraph naming the clocks a child is bounded by, which is where this
reading belongs.

surface: 2 cases added, 1 wait condition and 1 transcript arm in `run_child`, 1 mutation record, 1 guideline paragraph,
1 requirement added — counted with `bash tools/loop-selftest.sh --list | wc -l` (324 cases), `grep -c '^case_run_'
tools/loop-cases.sh` (123) and `grep -c '^#### Scenario' openspec/specs/agent-issue-loop/spec.md` (319).

## What Changes

1. **`run_child`'s wait reads its own writer's liveness.** One more condition inside the loop: while the record is
   absent, a `kill -0` on the subshell that answers nothing ends the wait with `ended_by=writer` (`:1468`). It is read
   once a second with the other bounds and used only to stop waiting — the loop never `wait`s on the pid, so #227's
   shape, a poll that believed a child gone and then blocked for ever, is not reachable from it.
2. **The reason is a bound, and the transcript names it.** The timeout arm writes `LOOP-TIMEOUT: the child's writer
   (pid <n>) vanished before it wrote its completion record — the run ended the wait` (`:1515`), which is the line the
   run reads as its own timeout: the item is recorded `blocked` with `bound`, no decision label and no decision slot,
   and the sweep of the change root and the leftover patch an ended child already gets run unchanged.
3. **Two cases prove both halves.** `run#aVanishedWriterEndsTheWait` kills the child's process group — the subshell
   that writes the record and the child in it — and asserts the run ends within seconds, names the vanished writer and
   records the item without a question; `run#aSlowChildIsNotMistakenForAVanishedWriter` keeps a live stub that has
   written nothing for five seconds and asserts the run is still waiting, writes no such line, and finishes on the
   record the child writes at last.

## What this does not do

- It does not end the run. A run whose *session* died is still a run: this change ends the wait it was stuck in and
  records the item, and the orphan's own continuation is the second guard the item names beside `.pi/issue-loop/lock` —
  the lease is read when it is taken (`tools/loop/state.sh:815`) and never while it is held. That is a second
  mechanism with its own red case, so it is a separate item rather than this delta (`tools/loop/prompts/worker.md`,
  the eligibility gate's conditions 2 and 5).
- It does not change any clock, any bound or the status the run returns: a vanished writer ends the wait at
  `LOOP_CHILD_TIMEOUT`, exactly as the cap and the deadline already do.
- It does not touch the `cut` result a bound-ended *round* records, and it does not make a cut child's work resumable
  in place.
