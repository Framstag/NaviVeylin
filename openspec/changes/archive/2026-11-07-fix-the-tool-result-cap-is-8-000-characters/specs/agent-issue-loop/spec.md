## ADDED Requirements

### Requirement: The numbers a run records name the bounds they were measured against

A run's state SHALL record, once in `.run`, the bounds its children's figures are read against: the character bound a
tool result is cut at (`LOOP_TOOL_RESULT_MAX_CHARS`) and the two bounds on the context an item's turns cost
(`LOOP_ITEM_CONTEXT_WARN`, `LOOP_ITEM_CONTEXT_MAX`). Each SHALL be the number the configuration records, and `0`
where it records none or `0`.

#### Scenario: A run's record and its report name the bounds its figures were read against

- **WHEN** a run is initialised against a configuration that records a result bound and a context warning and stop
- **THEN** its state carries those three numbers in `.run`, each equal to what the configuration records, and the
  live view `state.sh show` prints them beside the budget and flags it already prints
- **AND** the report's header names the same result bound, beside the per-item table whose `context` column is the
  largest context a turn of that item reported, so the column is read against the stop that bounds it
- **AND** a configuration that records no such key, or records `0`, is recorded as `0` — no bound — rather than as a
  number the run invented, and a state written before the fields existed prints them as unrecorded, never as `0`
- **Case**: `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst`
