# Proposal — fix-the-tail-is-admitted-on-the-cheapest-measured-outcome

## Why

**Root cause.** The floor `next` refuses an item by is derived from the median of the run's finished wall times and
then floored at one constant: `tools/loop/state.sh:296-297` takes `[([($median * 1.5 | ceil), $min] | max), $n,
($median | round)]`, where `$min` is `LOOP_ITEM_FLOOR_MINUTES` (`tools/loop/loop.conf:52`, 65 min). The constant is
therefore a lower bound for *every* history, so a run whose items are 10-15 min skips keeps the 65-minute floor and
stops up to ~50 min before its deadline (`guidelines/Backlog.md:384-389`, issue #674). It is the "one constant for
every item" defect one level down, named as the accepted cost of #663.

**The recorded decision settles the scope.** The item's recorded `Option 1` answers it as *floor +
no-decision-for-clock-cut + persisted leftover record*, because the floor alone is not shippable: a floor derived
from a cheap history admits a **gated** item into the tail, and the run's own deadline then cuts that item. A cut
item that is escalated as a decision spends the run's decision slot on a question the item never asked — and the
work it left must survive the run that cut it.

**The red cases on the base (`2de16da0`), 2026-10-27T06:43Z** (JUnit-shaped tallies in `evidence/`):

| case | XML | what it reads |
|---|---|---|
| `state#floorIsDerivedFromTheFinishedItemsWallTimes` | `tests=1 failures=1` | `only 27 min left, below the 90 min floor … (median 60 min …)` — a cheap tail is refused |
| `run#childIsEndedAtTheRunsDeadline` | `tests=1 failures=1` | `expected '', got 'agent/needs-decision'` — the deadline's item is escalated |
| `run#treeOfACapEndedChildIsKeptAsAPatch` | `tests=1 failures=1` | the state's item record carries no `leftover` — the patch exists only in the transcript |

**Owning requirement.** `agent-issue-loop` — "The floor the run refuses by is what its own items cost", "Both clocks
a run refuses by hold what one item's gate owes", and "A child the run ended itself leaves no commit".

**Owning guideline.** `guidelines/Backlog.md` §7 "The loop" — "What an item costs, and the floor it is refused by"
and "What bounds it".

## What changes

1. **`tools/loop/state.sh` — the floor follows the cheapest measured outcome.** When at least one finished item
   records a wall time, the floor is that item's minutes × the 1.5 spread, rounded up to the whole minute, with no
   lower bound against `LOOP_ITEM_FLOOR_MINUTES`. The configured minimum is the floor only for a run with no
   measurement. The refusal names the cheapest wall time, the count and the configured minimum.
2. **`tools/loop/run.sh` — a clock-cut item is not a question.** An item whose worker (or rework worker) was ended
   by the run's own deadline carries `LOOP-DEADLINE` in its transcript. It is still recorded `blocked`, with its
   tree uncommitted and the `LOOP-TIMEOUT` line as its reason, but the run writes no decision label for it: it is
   left for the next run, not escalated. A cap-ended child is unchanged — that item did ask a question.
3. **`tools/loop/state.sh` + `tools/loop/report.sh` — the leftover is a persisted record.** `done --leftover <path>`
   writes the patch an ended child's work is kept in into the item's state record, and the report's action list
   prints `recover #<issue> — the run's clock ended it; the work it left is kept at <path>`.
4. **`tools/loop/loop.conf` and `guidelines/Backlog.md` §7** carry the new derivation and the accepted exposure
   instead of the constant's accepted cost.
5. **`tools/loop-cases.sh`** rewrites the derivation case (cheapest, cheap tail admitted, the #663 reversal), adds
   the clock-cut assertions, and the leftover assertions. `tools/loop-mutations.sh` gains three records and
   re-points the four blocked-path records at the re-indented escalation block. The adapter case's "started by
   hand" invocation unsets `LOOP_TOOL_RESULT_TRIM_RECORD` with the event stream it is derived from: a worker's
   environment carries it now, and the gate selects that case because this change edits `tools/loop-cases.sh`
   (test isolation only — no production behaviour moves).

## Impact

- Affected files: `tools/loop/state.sh`, `tools/loop/run.sh`, `tools/loop/report.sh`, `tools/loop/loop.conf`,
  `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `guidelines/Backlog.md`, `openspec/specs/agent-issue-loop/spec.md`.
- Additive to the state schema (a new `leftover` field per item); no state migration: an absent field reads as none.
- No compiled file, no Android module, no log line.
- Rollback: revert this change; the state's extra field is ignored by the old reader, and the floor returns to
  `max(median × 1.5, LOOP_ITEM_FLOOR_MINUTES)`.
