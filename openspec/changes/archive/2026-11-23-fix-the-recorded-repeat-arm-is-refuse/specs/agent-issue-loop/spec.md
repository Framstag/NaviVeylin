## MODIFIED Requirements

### Requirement: A second read of a path a child was handed is counted, and the arm decides its answer

A second read of a path a child was handed SHALL be counted, and answered by the arm `LOOP_REPEAT_READ` the project
records: `measure` SHALL serve it, `refuse` SHALL answer it with one line naming the path and the earlier result's
range instead of the text. An absent or undeclared value SHALL be `measure` and a path the child was never handed
SHALL be served. The count SHALL be recorded as `metrics.repeatReads`, printed beside the repeats a session made,
whichever arm answers.

#### Scenario: A second read is counted under either arm, and the refusal arm withholds the text

- **WHEN** one process reads a path, reads it again, and the project records `measure` for the first pair and
  `refuse` for a second, and a third pair runs under a value the rule does not declare
- **THEN** the second read under `measure` is served whole and the record gains one
  `LOOP-TOOL-RESULT-REPEAT-READ: <tool> turn=<n> arm=measure` line, the second read under `refuse` is answered with
  one `[LOOP-REPEAT-READ: …]` line — the path, the earlier result's line range, its size and the call that reads a
  range of it — with none of the text, not even the edit the child itself made, and its record line names
  `arm=refuse`, and the undeclared value serves its read as `measure` does
- **Case**: `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`

#### Scenario: The child is started with the arm the project records

- **WHEN** the adapter starts a child and the project's `loop.conf` records `refuse`, then a word the rule does not
  declare, then no key at all
- **THEN** the child is handed `refuse`, then `measure`, then `measure` — the value the file records, never one the
  adapter chose, and the arm that cannot break a child for a key nobody wrote
- **Case**: `adapter#theChildIsStartedWithTheRepeatReadArm`

#### Scenario: The arm the project records is the refusal

- **WHEN** this project's `loop.conf` is read for the arm it records
- **THEN** it records `refuse`, so a second read of a path a child was handed is answered with the earlier result's
  range and its turn by default, and `measure` remains only the fallback a value nobody declares and a project that
  records no key are handed
- **Case**: `adapter#theRecordedRepeatArmIsRefuse`

#### Scenario: The count is recorded and printed beside the repeats a session made

- **WHEN** two items' records hold the rule's lines and a third item's directory holds no record at all, or the
  state was written before the figure existed
- **THEN** `metrics.repeatReads` is the lines counted, `0` for a record read and holding no second read, and `null`
  for a directory holding no record; the report's `reads:` line prints `2 second reads counted by the repeat-read
  rule` beside the repeats a session made and never as that figure, says `the rule counted no second read` where
  the rule counted none, and says `the repeat-read count is unrecorded — this state predates the figure` for a state
  holding no such key rather than naming a run that counted none
- **Case**: `report#theRuleRecordsTheSecondReadsItCounted`