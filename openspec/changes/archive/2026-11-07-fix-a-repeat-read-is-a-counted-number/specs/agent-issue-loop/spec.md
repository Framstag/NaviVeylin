## ADDED Requirements

### Requirement: A run counts the reads its sessions repeated

An item's record SHALL carry the file reads its children made whose path the same session had already read, the
characters those repeats re-injected, and the reads they are a share of, read from the event stream the item's
children appended to — and the report SHALL print them, per item and for the run, naming the item that repeated most.
The figure SHALL be a warning: no key bounds it and no item's outcome reads it.

#### Scenario: The reads a session repeated are recorded, printed and named

- **WHEN** an item's event stream records 3 reads of one path and 1 of another in one session, and a second session
  that reads the first session's path again, and another item's stream records 2 reads of one path, and a third item's
  stream opens no file at all
- **THEN** the record carries the read calls (`metrics.reads`), the ones whose path the same session had already read
  (`metrics.dupReads`) and the characters of those repeats' own results (`metrics.dupChars`) — `ctx_read` and `read`
  alike, a repeat counted per session so the second session's read is a read and not a repeat
- **AND** the table's column appended after the calls-per-turn one prints the item's figure as `repeat/total`
- **AND** the trend's `reads:` line prints the run's repeats out of the run's file reads with the percentage, the
  characters re-injected and the item that repeated most, and an item whose stream measured no read prints `-` in the
  column, the trend printing the dash for a run that opened no file and the other dash, with its reason, for a state
  written before the field existed
- **Case**: `report#anItemRecordsItsRepeatReads`
