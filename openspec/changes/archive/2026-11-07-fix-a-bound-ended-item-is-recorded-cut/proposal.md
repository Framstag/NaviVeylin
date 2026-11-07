# An item a bound ended is recorded `cut`, not `blocked`

## Why

Issue **#790**. `run.sh` records `cut` for a phase the run ended at its own bound on one arm and `blocked` on the
other, for the same fact: the arm that reads a non-zero exit writes `outcome="blocked"` and the mechanism
`stop_reason="bound"` (`tools/loop/run.sh:2334`), while the arm that ends a **review round** overrides the result to
`cut` (`tools/loop/run.sh:2372`, issue #785). The two arms of one rule therefore disagree, and the state's own
comment already argues the second reading: `cut` is "the fifth result, and it is not a shade of `blocked`"
(`tools/loop/state.sh:507`).

**Root cause, in one sentence:** the bound a run enforces on its own child (`run_child`) is a phase the next run
resumes, but the result the item is written with is chosen by *which arm produced it* — the review round's arm says
`cut` and the child's arm says `blocked` — so every later reader needs the mechanism's vocabulary to tell work from a
refusal.

The tree shows one mechanism and two readings of it, not two mechanisms:
`git grep -n 'stop_reason="bound"' tools/loop/run.sh` → `:2343` (one site), and the result is decided at `:2372` from
`pre_gate_cut` alone.

**The evidence the item carries, verified in the archive** (`.pi/issue-loop/run.json`, 2026-11-05T21:37:13Z):
#788, #778 and #759 each held `result: blocked` with a note naming the turn bound the run had enforced, plus a
4–6 k-line `leftover-from-killed-run.patch`. Verified in the tree today: the state, the report and the streak all
still special-case the mechanism — `report.sh`'s bound row reads
`select(.result == "cut" or (.result == "blocked" and (.stopReason // "") == "bound"))` (before this change,
`tools/loop/report.sh:172`), so the report cannot count a bound-ended item as a cut from its result.

**The red case, on the base `6e9319f1`** (one `bash tools/loop-selftest.sh --case 'run#child*IsCut'` run over the
base tree with this change's case file; the three cases this change rewrites):

```
2026-11-07T00:37:14Z
run#childPastItsTurnBoundIsCut|   an item whose child crossed the bound is recorded cut, not blocked (issue #790): expected 'cut', got 'blocked'
FAIL run#childPastItsTurnBoundIsCut
run#childPastItsContextBoundIsCut|   an item whose child crossed the bound is recorded cut, not blocked (issue #790): expected 'cut', got 'blocked'
FAIL run#childPastItsContextBoundIsCut
run#childOutlastingTheWallClockIsCut|   an item whose child was ended is recorded cut, not blocked: expected 'cut', got 'blocked'
FAIL run#childOutlastingTheWallClockIsCut
loop-selftest: 3 of 3 cases failed
2026-11-07T00:37:14Z
```

`evidence/red-on-HEAD.log` is that run; `evidence/red-on-HEAD.xml` its tally
(`tests=3 failures=3 errors=0 time=22`), kept beside it because the shell runner writes no JUnit XML of its own.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "An item the run's own bound cut is recorded
`cut`, not `blocked`" (`:3151`) — modified, because it named a review round only — "A block records the mechanism
that stopped it" (`:2314`), modified, because `bound` is the one mechanism a `cut` carries — "A child the run ended
itself leaves no commit" (`:2103`), whose scenarios said `blocked` for the same cuts — and "One command starts a
budgeted run, with no dialogue" (`:11`), modified only where its wall-clock scenario (`:54`) still read "the item is
recorded `blocked`": that result and its `Case:` are repointed, and every other scenario of the requirement is
carried unchanged.

**Owning guideline section:** `guidelines/Backlog.recipes.md`, section 7e "Leftovers, the report and the queue"
(`:725`, "What a bound cut is, and what it is not", `:758`, "A **block** the run's own bound caused", `:776`) and
section 7b (`:297`, the paragraph that ended "the item is recorded `blocked` with that count and `bound` as its mechanism").

surface: 6 requirements modified (39 scenarios, every scenario name the shipped spec has for them carried — the
adoption-refusal scenario #794 landed after this change's plan is carried too; the two scenarios of "One command
starts a budgeted run" that carry no `Case:` line carry none in the shipped spec either), cases realigned — 3
renamed and rewritten (`run#childPastItsTurnBoundIsCut`, `run#childOutlastingTheWallClockIsCut`,
`run#childPastItsContextBoundIsCut`), 6 asserted on the new result (`run#childIsEndedAtTheRunsDeadline`,
`run#treeOfAChildTheCapEndedIsNotCommitted`, `run#theRecordedZeroKeepsTheTurnBoundOff`,
`run#theRecordedZeroKeepsTheContextBoundOff`, `run#aVanishedWriterEndsTheWait`,
`run#aRotatedStreamStillCountsTheTurnsItDropped`) and `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`,
whose arm this change converts — 6 engine sites (`run.sh`'s result choice, its `cut` arm's record and note, its block
arm's escalation guard, its rework-worker arm; `state.sh`'s stop-reason pairing; `report.sh`'s bound row), 2 guideline
sections, and the records of `tools/loop-mutations.sh` this change touches (14.3, 14.5, 14.6 and 19.3 repointed; 23.1
and 81.1 renamed with their case; 68.2 and 68.6 retired with the guard they quoted; 92.1–92.3 added for the new
result, the mechanism the item's own child keeps beside it and the same rule at the rework arm) — counted with
`tools/loop/measure.sh --change
fix-a-bound-ended-item-is-recorded-cut` (`LOOP-MEASURE-SUITE cases=345`, `LOOP-MEASURE-CHANGE … scenarios=39
cases=37 unnamed=2`), `grep -c '^case_run_' tools/loop-cases.sh` (128) and `bash tools/loop-mutations.sh --check`
(262 records).

## What Changes

1. **The result follows the mechanism, not the arm.** `run.sh` records `cut` when the arm that reads the child's
   non-zero exit named `bound` — the child's role's turn bound, the context bound, the child clock, a vanished writer
   or the run's deadline — and keeps the reason it recorded word for word as the item's note, so the count and the
   `LOOP-TIMEOUT` line survive.
2. **The mechanism stays beside the result.** The `cut` arm passes `--stop-reason bound` and `state.sh` accepts
   exactly that pairing (`bound` with `cut`; `refused`/`unmergeable` still belong to a block only), so a mechanism's
   spend is totalled from the same field whichever result carries it. The item's note says the attempt is the next
   run's to resume, as it did as a block.
3. **The report reads the result alone.** `report.sh`'s bound row is `select(.result == "cut")` again: an item the
   run's bound ended is on that line and out of the block tally because of its result, not because a later reader
   translated the mechanism.
4. **A block is always a question.** The block arm's escalation guard excused a block whose cause was the run's own
   deadline or a bound; both causes now leave a `cut`, so the guard is removed and every block this arm records is
   escalated.

## What this does not do

- It does not change what the branch, the tree or the tracker carry: the commit stays skipped (`end-item` is not run
  for a child the run ended, #271), the tree stays kept and the patch stays named, and no decision label is written
  for a cut.
- It does not make a cut child's work resumable in place; the next run's pre-screen offers the item with its branch,
  as it did for a bound block (#754 owns the resume path).
- It does not re-count, re-classify or raise the decision cap (`tools/loop/loop.conf:20`, `LOOP_MAX_DECISIONS_DEFAULT`).
- It does not touch the transport-cut block (an answer cut twice), the refusal vocabulary, or the two-blocked streak,
  which keep reading `blocked` and `refused` as they did.
