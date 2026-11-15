## MODIFIED Requirements

### Requirement: The repeat rule's ledger is the item's, not the child's

An item's children SHALL share one ledger of the reads that item made, named beside the item's event stream, so a path
one child read is a repeat for the child started after it — replaced by one line naming the session that read it and the
turn it read it at — unless the content that path holds has changed, in which case the read SHALL be handed over whole.
A child given no ledger SHALL keep the ledger of its own process.
The arm a project records SHALL answer a second read of a path this child was handed ahead of this ledger's content
test: what the arm withholds SHALL NOT be handed over whole because the content changed, while a path this child
was never handed SHALL be answered by this ledger as it was (issue #925).

#### Scenario: The item's ledger outlives the child that filled it

- **WHEN** one child of an item reads a path and writes the ledger the adapter named beside the item's stream, the child
  the run starts after it reads the same unchanged path, and a later read follows an edit of that path
- **THEN** the second read is replaced by one line naming the session that read the path and the turn it read it at,
  and the read after the edit is handed over whole
- **Case**: `repeat#anItemsLedgerSpansItsChildren`

#### Scenario: The adapter names the ledger and the session for every child of the item

- **WHEN** the adapter starts a child with the item's event stream path and the prompt the run rendered for it
- **THEN** the child is handed the item's ledger file beside that stream and is named by that prompt, so the one line a
  repeat is replaced by can name the session that read the path
- **Case**: `adapter#theChildIsStartedWithTheItemsLedger`

#### Scenario: The arm's answer stands before this ledger's content test

- **WHEN** a child reads one path, edits it, and reads it again under the `refuse` arm its project records, and a
  path the item's ledger already holds is read for the first time by a child that was never handed it
- **THEN** the second read is answered with the earlier result's range rather than handed over whole — the edit is
  not sent, because the arm answers the read before this ledger's content test — while the read of the path the
  item holds and this child does not is served as the file stands, its own notice having replaced the read the
  child could not have held
- **Case**: `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`

## ADDED Requirements

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

#### Scenario: The count is recorded and printed beside the repeats a session made

- **WHEN** two items' records hold the rule's lines and a third item's directory holds no record at all, or the
  state was written before the figure existed
- **THEN** `metrics.repeatReads` is the lines counted, `0` for a record read and holding no second read, and `null`
  for a directory holding no record; the report's `reads:` line prints `2 second reads counted by the repeat-read
  rule` beside the repeats a session made and never as that figure, says `the rule counted no second read` where
  the rule counted none, and says `the repeat-read count is unrecorded — this state predates the figure` for a state
  holding no such key rather than naming a run that counted none
- **Case**: `report#theRuleRecordsTheSecondReadsItCounted`
