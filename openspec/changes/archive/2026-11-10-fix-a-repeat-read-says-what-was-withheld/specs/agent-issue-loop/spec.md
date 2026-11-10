# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: A run counts the reads its sessions repeated

An item's record SHALL carry the file reads its children made whose path the same session had already read, the
characters of those repeats' own results, the characters the repeat rule withheld from those repeats — the repeat's size
less the one line that replaced it — and the reads they are a share of, read from the event stream the item's children
appended to and from the record the rule writes beside it — and the report SHALL print them, per item and for the run,
naming the item that repeated most, and SHALL NOT call the raw repeat volume re-injected. The figure SHALL be a warning:
no key bounds it and no item's outcome reads it.

#### Scenario: The reads a session repeated are recorded, printed and named

- **WHEN** an item's event stream records 3 reads of one path and 1 of another in one session, and a second session
  that reads the first session's path again, and another item's stream records 2 reads of one path, and a third item's
  stream opens no file at all
- **THEN** the record carries the read calls (`metrics.reads`), the ones whose path the same session had already read
  (`metrics.dupReads`) and the characters of those repeats' own results (`metrics.dupChars`) — `ctx_read` and `read`
  alike, a repeat counted per session so the second session's read is a read and not a repeat
- **AND** the table's column appended after the calls-per-turn one prints the item's figure as `repeat/total`
- **AND** the trend's `reads:` line prints the run's repeats out of the run's file reads with the percentage, the
  characters in the repeat results, the characters the repeat rule withheld of them and the item that repeated most, and
  an item whose stream measured no read prints `-` in the column, the trend printing the dash for a run that opened no
  file and the other dash, with its reason, for a state written before the field existed
- **AND** the record carries `metrics.withheld` — the repeat lines of the record the rule writes beside the stream
  counted and summed — and it is `null` for an item whose directory holds no repeat record, so a state written before
  the field prints the dash rather than a zero the run never measured
- **Case**: `report#anItemRecordsItsRepeatReads`

#### Scenario: A repeat whose text was withheld is recorded as the rule's own figure

- **WHEN** a session reads one path, reads the same unchanged content a second time, and the repeat rule replaces that
  second result with its one line
- **THEN** the rule appends `LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` to the record the adapter names beside
  the child's stream — one line per repeat, `<n>` the result's size and `<k>` the notice's — and the line carries no
  path, so `chars - kept` is the volume the child was not handed
- **Case**: `repeat#aRepeatIsRecordedAsWhatItWithheld`
