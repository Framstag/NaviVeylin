## ADDED Requirements

### Requirement: A close over a gate that did not pass records its waiver

An item SHALL close over a gate that did not pass only when the record carries the clause it was waived with, and an
item's record SHALL carry that clause and the suite it names beside the tally it waived — so that a close over a red
gate and a close over a green one are told apart by the record, and the report counts the waivers instead of printing
both as the same verdict.

#### Scenario: A close over a red tally that records no waiver is refused

- **WHEN** `tools/loop/state.sh done` is asked to record an item `done` with a tally that carries a nonzero `failures=`
  or `errors=` and no `--gate-waived` clause
- **THEN** the write is refused as a usage error and no item is recorded, so the item stays queued for the next
  attempt; a waiver beside a result that is not `done`, beside no `--gate` at all, or beside a tally that passed is
  refused the same way, and a tally that carries no count (a project with no gate, a gate that never ran) closes
  without one
- **Case**: `state#aCloseOverARedGateWithoutAWaiverIsRefused`

#### Scenario: A waived close keeps its clause and the report counts it

- **WHEN** an item is recorded `done` over a red tally with `--gate-waived` and `--gate-waived-suite`, and a second
  item of the same run closes over a tally that passed
- **THEN** the record carries the clause in `metrics.gateWaived` and the suite in `metrics.gateWaivedSuite` beside the
  tally in `metrics.gate`, the green close carries neither, and the report's `waived:` line prints
  `1 of 2 closed items (<suite> ×1)` with what the count is a count of
- **Case**: `state#aCloseOverARedGateRecordsWhyItWasWaived`

#### Scenario: The run carries the clause its child recorded, and blocks the close that recorded none

- **WHEN** the child whose verdict the run decides from reports a red `LOOP-GATE` with `LOOP-GATE-WAIVED` and
  `LOOP-GATE-WAIVED-SUITE`, and a second run's child reports the same red tally with neither
- **THEN** the first item is recorded `closed` with both fields carried into the record, its report counts one waived
  close, and the second item is recorded `blocked` with the tally kept and the reason naming the line that would have
  recorded the waiver — and nothing is closed on the tracker for it, because the refusal is read before the merge
- **Case**: `run#aCloseOverARedGateIsWaivedOrBlocked`
