# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A run counts the reads its sessions repeated

An item's record SHALL carry the file reads its children made whose path the same session had already read, the
characters those repeats returned, the characters the repeat rule withheld, the characters that entered in their
place, and the reads they are a share of — read from the event stream the item's children appended to and from the
item's own tool-result trim record, because the stream carries the result the tool returned and cannot say which of
the two the child ever saw. The report SHALL print them, per item and for the run, naming the item that repeated
most, and SHALL NOT label the raw repeat volume a re-injection. The figure SHALL be a warning: no key bounds it and
no item's outcome reads it.

#### Scenario: The reads a session repeated are recorded, printed and named

- **WHEN** an item's event stream records 3 reads of one path and 1 of another in one session, and a second session
  that reads the first session's path again, and another item's stream records 2 reads of one path, and a third item's
  stream opens no file at all
- **THEN** the record carries the read calls (`metrics.reads`), the ones whose path the same session had already read
  (`metrics.dupReads`) and the characters of those repeats' own results (`metrics.dupChars`) — `ctx_read` and `read`
  alike, a repeat counted per session so the second session's read is a read and not a repeat
- **AND** the table's column appended after the calls-per-turn one prints the item's figure as `repeat/total`
- **AND** the trend's `reads:` line prints the run's repeats out of the run's file reads with the percentage, the
  volume those repeats returned and the item that repeated most, and an item whose stream measured no read prints `-`
  in the column, the trend printing the dash for a run that opened no file and the other dash, with its reason, for a
  state written before the field existed
- **Case**: `report#anItemRecordsItsRepeatReads`

#### Scenario: What the repeat rule withheld is named, and the raw volume is not called a re-injection

- **WHEN** one item's trim record holds the line the repeat rule wrote when it replaced a read of a path the session
  held unchanged, and another item of the same run kept no trim record
- **THEN** the record carries what the rule withheld (`metrics.repWithheld`) and the characters that entered as its
  one line (`metrics.repKept`), read from the trim record beside the item's stream, beside the raw volume the repeats
  returned (`metrics.dupChars`)
- **AND** the trend's `reads:` line prints the withheld volume and what entered in its place, never the raw repeat
  volume as characters re-injected, and an item with no trim record prints the raw volume with the dash and reason
  that say what the rule withheld is unrecorded
- **Case**: `report#theRepeatRuleNamesWhatItWithheld`
