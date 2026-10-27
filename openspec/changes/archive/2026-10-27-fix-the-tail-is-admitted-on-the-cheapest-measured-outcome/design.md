# Design — fix-the-tail-is-admitted-on-the-cheapest-measured-outcome

## Root cause

One constant under a derived floor. `tools/loop/state.sh:289-298` derives the next item's floor from the median of
the run's finished wall times × 1.5 and then keeps it above `LOOP_ITEM_FLOOR_MINUTES`
(`tools/loop/loop.conf:52`, 65 min) with `[([($median * 1.5 | ceil), $min] | max), …]`. A run whose items are
gated-out skips (10-15 min measured, `guidelines/Backlog.md:326-336`) therefore computes a 3-20 min floor and then
raises it to 65: with 28 minutes left it stops, although each of its last three items cost 10. The title's
"up to 50 minutes early" is that difference, on the run's own tail.

## The fix

Three sites, one change:

1. **`state.sh`** — when `n > 0`, the floor is `ceil(min(w) / 60 * 1.5)` and no longer `max(…, LOOP_ITEM_FLOOR_MINUTES)`.
   `n == 0` keeps the configured minimum unchanged. The refusal names the cheapest wall time and the count, and
   still names the configured minimum (as the no-history value).
2. **`run.sh`** — read `LOOP-DEADLINE` from the worker's (or round-2 worker's), **the round's reviewer's** and the
   reviewer retry's transcript. An item ended by the run's deadline is recorded blocked exactly as before (no
   commit, tree kept, `LOOP-TIMEOUT` in the note) but the run writes no escalation for it: `escalatedLabel` stays
   empty and the item is left for the next run. A round the clock ended is cut rather than refused, so no
   cut-short retry is started past the deadline and no rework worker is bought for a verdict nobody wrote. Once
   the deadline has ended a child, the run takes no second pass over the queue either: a pass would re-exclude
   the item whose branch the cut run had just made, and the report's decision list would name a decision the
   clock asked rather than the item.
3. **`state.sh` + `report.sh`** — `done --leftover <path>` persists the patch the ended child's work was kept in
   on the item's record; `report.sh` prints `recover #<issue> — … kept at <path>` in its action list so the
   caller reaches the work without finding the item's transcript first.

## Why each alternative is eliminated (evidence, not preference)

- **Keep the constant (the item's option 2).** The recorded decision rejects it: the measured skips median is
  107 s (`evidence/wall-by-result.log`: `skipped n=63 min=31 median=107.225 max=1413`, produced by
  `evidence/wall-by-result.sh` over the run states under `.pi/issue-loop/runs`), so the tail idles up to ~62 min
  per run — the 65-minute floor less that median is 63 min — and nothing is ever started. The defect the title
  names is exactly that idle tail.
- **Derive the owe from the item's cited paths in the pre-screen (option 3).** A citation is not the change, and
  the classification can be wrong in both directions; more decisively, it only shortens the floor for
  doc-shaped items and leaves the run-of-skips tail — the case the item was filed on — exactly as it is.
- **The median instead of the cheapest measured item.** A 3-item history `[600 s, 3600 s, 3600 s]` has a
  60-minute median and would derive a 90-minute floor, refusing the 15-minute tail the run has just paid for
  three times. The cheapest recorded outcome is the one the run has proof it can pay; the median is not.
  `state#floorIsDerivedFromTheFinishedItemsWallTimes` pins this difference.
- **Keep a lower bound only when the next item is gated.** What an item owes is decided by the paths its change
  touches, which is known only after it starts — the question the recorded decision says cannot be answered
  before the item. Option 1 accepts the exposure instead and handles its consequence (below).

## The accepted exposure, and how it is bounded

A cheap history admits a *gated* item into the tail; the run's own deadline then ends that child
(`tools/loop/run.sh:1094-1133`). Bounded at one item per run: the deadline ends that child, `stop_run` records the deadline,
and `next` refuses anything after it. The cost is the one option 1 names — that item's work is cut and left for
the next run — and the leftover patch plus the state's `leftover` field make the cut recoverable. Without part 2
of the fix the exposure would be worse than the item's own cost: the cut item would be escalated as a decision,
spending the run's decision slot and counting against the decision cap on a question the item never asked.

## Blast radius

| where | what |
|---|---|
| `tools/loop/state.sh` | the derivation (`next`), the refusal text, `done --leftover`, the item record |
| `tools/loop/run.sh` | `deadline_claim` read from the worker's, the round-2 worker's and the reviewer's transcript; `deadline_cut` stops the second pre-screen pass; the blocked path skips escalation for it; `--leftover` passed |
| `tools/loop/report.sh` | one action line and the "none" text of the action list |
| `tools/loop/loop.conf` | the `LOOP_ITEM_FLOOR_MINUTES` comment block (value unchanged, 65) |
| `tools/loop-cases.sh` | two cases rewritten/extended (`state#floorIsDerivedFromTheFinishedItemsWallTimes`, `run#treeOfACapEndedChildIsKeptAsAPatch`), one extended (`run#childIsEndedAtTheRunsDeadline`), one added (`run#reviewerEndedAtTheRunsDeadlineIsNotADecision`), comments on `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`, and `adapter#theChildIsStartedWithTheResultBound` unsets the trim record with the stream it is derived from |
| `tools/loop-mutations.sh` | `68.1` (cheapest → most expensive), `68.2` (the clock-cut guard never fires), `68.3` (the leftover field dropped), `68.4` (the reviewer's `LOOP-DEADLINE` unread), `68.5` (the queue pass runs after a deadline cut) |
| `guidelines/Backlog.md` §7 | "What an item costs, and the floor it is refused by" and "What bounds it" |
| spec delta | three MODIFIED requirements, 23 scenarios (21 of them naming a case) |

No compiled file, no Android module, no log or diagnostics line.

The adapter case's isolation is corrected with the rest (test-only, no production behaviour): the gate selects it
because this change edits `tools/loop-cases.sh`, and a worker's environment now carries
`LOOP_TOOL_RESULT_TRIM_RECORD` — exported by `tools/pi-child-events.sh` for the child it starts — so the case read a
hand-started child as naming a record file. It unsets the record with the event stream it is derived from.

## Risk

- **A run starts an item it cannot finish.** Now possible by design: the floor promises the cheapest kind of item,
  not the most expensive. The run's own deadline bounds it to one item per run, and that item's tree is kept
  uncommitted with a patch, not lost.
- **A very short item lowers the floor for a later expensive one.** Same exposure, same bound; the recorded
  decision chose it over an idle tail.
- **A deadline-cut item is not on the tracker's decision list**, so a reader who trusts only the tracker sees an
  item that is still open (correct — it is left for the next run) and nothing telling them the run cut it. The
  state and the printed report name it; the tracker's label stays as the pre-screen left it.
- **The clock-cut guard keys off `LOOP-DEADLINE`.** A transcript that lost that line would be escalated as
  before; the line is written by the run itself on the same path that writes `LOOP-TIMEOUT`, so the two cannot
  disagree without a hand-edited transcript.
- **The reviewer the deadline cut is read on the same line as the worker's.** Round 1 shipped the worker and the
  round-2 worker only, and the reviewer's own `LOOP-DEADLINE` line — written into `reviewer-report-$round.md` and
  read by nobody — left the round read as one the reviewer never answered: a retry past the deadline, then a
  rework worker, then an escalation at the round cap. `run#reviewerEndedAtTheRunsDeadlineIsNotADecision` pins
  it, and `68.4` mutates the read away.
- **The pass over the queue after a deadline cut.** Without `deadline_cut`, a run that blocked an item at its
  deadline re-ran the second pre-screen pass, which excluded the item whose branch the cut run had just made —
  and the report's decision list, read from what the run wrote, named a decision for it (`agent/needs-decision`
  was not written, because `escalate_prescreen_decisions` skips an item in `.items`). `68.5` mutates the guard.

## Verification

- Red on the base, green on the change: `evidence/red-on-base-*.xml` (the floor, the worker's deadline cut, the
  leftover patch, and round 1's review: `evidence/red-on-base-reviewer-deadline.xml`), plus
  `evidence/green-on-change-suite.xml`.
- The whole shell suite and the gate once: `evidence/gate.log`, `evidence/gate-tallies/`. **The gate is red as it
  ran** — its loop-suite step is `tests=248 failures=1 errors=0` and its mutations-check step `errors=1`, both on
  `MUTATION-PATTERN-STALE 31.1`: this round's own edit moved the retry line that record quotes. The record is
  re-pointed after the run and `tools/loop-mutations.sh --check --only 31.1` passes on the shipped tree; the gate
  was not re-run, so the item is blocked for a clean tally rather than closed on a run that did not measure the
  tree it ships (`tasks.md` 6.2).
- The six mutation records go red on the named case and green on restore: `evidence/mutations.log` (each RED line
  now carries the assertion it failed and its timestamp); `evidence/mutations-check.log` for the record check.
