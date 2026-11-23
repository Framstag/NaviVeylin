## ADDED Requirements

### Requirement: A path is refused after its repeat bound

Past the count bound `LOOP_REPEAT_READ_REFUSALS` a repeat read of one path SHALL be refused whatever arm the run
records: one line naming the path, the count and the earlier turn, and none of the text. `0`, or a project that records
no key, SHALL leave the count off. The bound SHALL count the repeats of one path in one session.

#### Scenario: A path is refused after its repeat bound

- **WHEN** the project records a positive `LOOP_REPEAT_READ_REFUSALS` and the run records `measure`, the child is
  started, and one process reads one path more times than the bound and then reads a second path twice
- **THEN** the child is handed the recorded value, the reads up to the bound are served, the read past it is answered
  with one `[LOOP-REPEAT-READ: …]` line naming the count and the earlier turn and none of the text, the read after it
  stays refused, and the second path is served its own second read
- **Case**: `adapter#aPathIsRefusedAfterItsRepeatBound`