# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A truncated spend says so

A rotated stream SHALL record what the rotation dropped — the `stream_rotation` record's `dropped_turn_ends` and `dropped_bytes` — as a figure of the item's own metrics, and the report's trend SHALL name every item whose figures are a lower bound, with the figure each dropped. A key the state does not hold means a record written before the field existed; `null` means this era records it and the item has none. It is a warning, never a stop.

#### Scenario: A rotated item records the rotation it survived

- **WHEN** an item's stream carries a `stream_rotation` record — the residue #840 leaves on a stream whose records
  alone outgrow `LOOP_CHILD_EVENTS_MAX_BYTES`: nine of fifteen streams in the run of 2026-11-09T21:42:41Z, 1 697
  dropped turns against the 1 372 the state recorded
- **THEN** the item's `metrics.streamRotation` holds that record's `droppedTurnEnds` and `droppedBytes` as numbers —
  `null` for a stream with no rotation, and the key absent only in a state written before the field existed — so what
  the short figures are short by is in the state and not only in the stream
- **Case**: `report#aTruncatedStreamSaysSo`

#### Scenario: The report names the items whose spend is a lower bound

- **WHEN** the trend block prints the run's figures and at least one item's stream rotated
- **THEN** the `truncated:` line names each of those items with the figure it dropped, beside the run's totals in the
  three-part shape the `reads:` and `calls:` lines print, and an item whose stream did not rotate is not named; the
  turns are the items that lost one, while the bytes are the records' sum over **every** rotated stream, so a rotation
  that dropped only deltas is counted in the bytes and named by no turn
- **Case**: `report#aTruncatedStreamSaysSo`

#### Scenario: Nothing rotated is no rotation, and a state that predates the field says so

- **WHEN** no item of the run has a rotated stream, or the state was written before the field existed
- **THEN** the line prints `-` with its reason and never a zero that reads as a measurement — the two dashes told
  apart as the repeat-reads line tells them apart — and no figure of the run ends because of it: it is a warning,
  never a stop
- **Case**: `report#aTruncatedStreamSaysSo`
