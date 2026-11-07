## ADDED Requirements

### Requirement: An item's record keeps what the run measured to decide it

An item's record SHALL carry the gate tally the run read for it and the verdict of the review round the run read its
diff in, whichever arm the item ended on — not the arm that closed it alone — and the report SHALL NOT name a gate to
re-run for an item whose record carries a tally, and SHALL NOT count an item whose diff was read as never reviewed.

#### Scenario: A blocked item records the gate it ran, and no gate is re-run for it

- **WHEN** a change child stops at the diff, the gate child reports a tally for the gate it ran, and the tail refuses
  the item on that gate
- **THEN** the item's record carries the gate child's tally in `metrics.gate`, in place of the empty field it was
  written with, and the report's action list holds no `re-run the gate` line for that item
- **Case**: `run#aRedGateIsBlockedByTheTail`

#### Scenario: The round that read the diff is recorded on an item that then blocked

- **WHEN** the round before the gate answers `PASS` and the item then ends blocked on its gate
- **THEN** the record carries `metrics.reviewVerdict: PASS` and `metrics.reviewRounds: 1`, and the report's review line
  counts it as a first round that passed rather than as an item never reviewed
- **Case**: `run#aBlockedItemRecordsTheRoundThatReadIt`
