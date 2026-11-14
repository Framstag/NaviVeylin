# Design — a signalled case ends on its own clock

## D1 Three levers, and the one the tree rejects with a measurement

The item left the fix's shape open and offered four (its `ITEM_DECISION` records `(1) + (1')`):

1. **stage and bound the case** in `tools/loop/tests/loop-cases.sh` — no engine change;
2. **read the case's stream with the case's own pid as the authority** in `tools/loop/tests/loop-selftest.sh`, the
   rule #315 applied to a gate step;
3. both halves;
4. **a shorter, width-independent `LOOP_CASE_MAX_SECONDS`** (`tools/loop/loop.conf:377` is `900`, multiplied by the
   pool's width at `tools/loop/tests/loop-selftest.sh:196-198`), so a hung case becomes a red at a gate's scale.

**(2) cannot red this case.** The relay that reads a case's stream already stops within a second of the case's own
end: the pool gives it twenty looks 0.05 s apart before it writes the case's status
(`tools/loop/tests/loop-selftest.sh:582-585`), which is the 1 s the item measured at base. The hang is that the
**case has not ended** — it is inside `wait "$first"`, and its stub child holds the write end of the fifo the case
opened. A reader keyed on the case's pid therefore has nothing to key on: the pid is alive and always will be while
the run it waits on is alive. (2) is a sound rule for a case that dies leaving an orphan (that is #851's shape, and
`run#noProcessOfASignalledRunOutlivesIt` is its case); it is not a lever on a case that is still waiting.

**(4) bounds every case to cure one.** It is the cheapest falsifiable lever, and its cost is the whole suite: `900`
is the backstop for a case that runs for a quarter of an hour, and the suite's slowest case is 9–15 s
(`guidelines/Backlog.recipes.md:743`). A bound at a gate's scale would have to be spent on every `run#` case, whose
own children run for minutes by design (`--minutes 90`), so the number would bound the harness rather than the
defect.

**Chosen: (1).** The case is the thing that waits and the case is what the change bounds. `(1')` is defined nowhere
in the package; the second half this change reads it as — the deterministic re-staging the item's "The red case"
bullet asks for — is `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (D5). No third lever is added, and no
engine file is touched.

## D2 Staging: what makes the HUP the only thing that can end the run

`kill -HUP "$owner"` races the child's own success. When the stub child answers before the signal arrives, the run
ends its item, records the item, and exits `0` on its own — which is the 16-case pool's
`expected '129', got '0'`. The case then measures the gap between its own read of `run.ownerPid` and its own `kill`,
not the HUP path.

So the case asserts the stage it needs: `.items[0].result` is empty when the signal is sent
(`tools/loop/tests/loop-cases.sh:14324`). Both facts it reads are stages and not samples of a race —

- `run.ownerPid` is written by `loop_state init --owner-pid "$$"`, which runs **after** the `HUP`/`INT`/`TERM` traps
  are installed (`tools/loop/run.sh:718-721`, `:730`), so a state that names an owner names a run that would take
  the signal;
- the child writes its pid inside its own action, which the stub `pi` runs before it opens the case's fifo
  (`tools/loop/tests/fixtures/pi:106`, `:114-118`), so a pid in `child.pid` names a child that has not yet blocked.

With the item unanswered, the run can leave only through its trap, so the assertion the case already made (`129`,
`SIGHUP` as the stop, the unpick) is about the path it claims to be about.

## D3 The clock: the case's own, in seconds, below the suite's backstop

`CASE_WAIT_TICKS=300` at 0.1 s per tick is 30 s, and the tick is the one `wait_for_stub_child` above it already
uses. Two bounds set the number from either side:

- **Above the work the wait legitimately does**: the `1 s` `TERM`→`KILL` grace a run's trap gives the child it ends
  (`guidelines/Backlog.recipes.md:724`) is the longest thing the case is waiting for once the signal is taken, so
  30 s is thirty times it — a slow host, a loaded pool, and an extra trap retry all fit inside it. The two cases that
  share the staging ran in 8.02 s together (`evidence/green-on-change.log`).
- **Below the suite's backstop**: `LOOP_CASE_MAX_SECONDS` is `900` (`tools/loop/loop.conf:377`) times the pool's
  width (`tools/loop/tests/loop-selftest.sh:196-198`) — 4 h in the 16-case pool this race was measured in. A case
  that answers at 30 s is a case the suite reports while the pool is still working, which is the whole point of a
  clock of one's own.

The helper keeps the ended process's status in `WAIT_PID_STATUS` rather than returning it, because `wait` has to run
in the case's own shell: a subshell cannot reap its parent's child, and a `$( … )` around the wait would answer the
helper's status instead of the process's. `124` is the outlived-ticks answer — `timeout(1)`'s convention, and
distinguishable from the `129` a HUP-ended run reports, so a reader never has to guess which of the two a number is.

## D4 What the bound answers with, and what it releases

A bound that only fails is a bound that leaves the next case in the pool holding a stream. So the arm that answers
(`:14330-14337`) does four things in order, and the first of them is the verdict's content:

1. names **the process it waited on, the stop the run recorded and the item it recorded** — the three facts a reader
   needs to tell "the run never took the signal" from "the run took it and did not end";
2. releases the child it staged (`printf 'go\n' >&8`), so the run's own trap can end it;
3. tells the run to end (`loop_end_pid "$owner"`) and reaps it at a second, shorter bound (50 ticks, then `kill -9`);
4. closes the fifo it held (`exec 8>&-`), so the case's stream reaches its own end before the case answers.

That order is why the case can never be the reason a pool does not finish again: everything the case held is
released *inside the branch that failed*, on the path that is taken exactly when the run refused to end.

## D5 The deterministic case, and what each assertion buys

`run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (`:14362`) stages the measured race instead of hoping for it: it
starts a pid, reaps it, and sends the HUP to that dead pid, so the signal reaches nothing, the run goes on, and the
only thing that can answer the case is its own clock. Then, in order (`:14399-14415`):

- `124` — the case's own wait answered at its own bound rather than at the suite's. On a tree without the helper the
  value is `''` (the command does not exist), which is the red `evidence/red-on-HEAD.log` carries
  (`tests=1 failures=1`, `2026-11-13T22:52:39Z`).
- `""` for `run.stop.reason` — nothing ended the run while the case waited, which is what makes it the run that never
  took the signal rather than one that took it and ended. It is read *before* the case's own cleanup writes anything.
- the run ends after its child is released and it is told to (`:14412`) — the case leaves nothing holding its stream.
- `blocked` for the item (`:14415`) — the run ended **through its own trap**, so the process the case waited on is not
  the only one that ended and the cleanup path is the run's and not the case's.

The child's own end after the run's is asserted by `run#noProcessOfASignalledRunOutlivesIt` (green here), not by this
case, and deliberately: re-staging that assertion would move the artifacts this change's evidence quotes.

## D6 Why the early exits of the rewritten case changed too, and why no engine file did

The rewritten case's three early exits (`:14308`, `:14315`, `:14318`) used a bare `wait "$first"` to end a run that
had failed to reach its child. Those paths now end it at a bounded wait as well (`wait_for_pid_end "$first" 100 ||
kill -9`), so that **no `wait` on a process this file started is unbounded** — the defect was one instance of a shape,
and a helper only three call sites wide is a helper the next case will not use.

They did not fit the same site: a case that already answers with a `fail` can only lose by sitting in an unbounded
wait, and the three paths are the ones a broken run takes most often in a pool.

No engine file moves, so **no mutation record is added**. The falsification this change owes is the new case failing
where the helper is absent, which `evidence/red-on-HEAD.log` and `evidence/red-on-HEAD.xml` carry; the mutation a
record would need — deleting the bound from `wait_for_pid_end` — re-creates the hang the change removes, and a
falsification that hangs the suite is not a falsification.

## D7 What is not done

- **No driver-stream change.** D1 rejects it with the measurement it would have to answer: the relay's grace is
  already 1 s and the case is alive throughout.
- **No shorter `LOOP_CASE_MAX_SECONDS`.** D1 rejects it: it bounds the harness rather than the defect.
- **No definition invented for `(1')`.** The recorded answer names it and the package does not define it; D1 states
  what this change reads it as, so the next reader can disagree with a named reading instead of an empty slot.
- **No citation added to the guideline paragraph.** `guidelines/Backlog.recipes.md:746-753` carries the 2026-11-11
  numbers without the path of the log that kept them
  (`openspec/changes/archive/2026-11-11-fix-a-cut-child-is-continued-by-its-run/evidence/pool-head.log`); this change
  names it here and in `proposal.md` rather than widening a routed guideline paragraph in a fix that is about a case.