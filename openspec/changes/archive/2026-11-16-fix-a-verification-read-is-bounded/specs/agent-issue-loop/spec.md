## MODIFIED Requirements

### Requirement: The repeat rule's ledger is the item's, not the child's

An item's children SHALL share one ledger of the reads that item made, named beside the item's event stream, so a path
one child read is a repeat for the child started after it — replaced by one line naming the session that read it and the
turn it read it at — unless the content that path holds has changed, in which case the read SHALL be answered with the
first 2 000 characters of what it now holds (`VERIFICATION_READ_BOUND`) and one line naming the edit, the turn the item
read it at, the size the file has and the call that reads the rest, so that a verification read never re-injects the
file the session has just written; gate/evidence output SHALL be handed over whole, the class the recorded bound already
exempts.
A child given no ledger SHALL keep the ledger of its own process.
The arm a project records SHALL answer a second read of a path this child was handed ahead of this ledger's content
test: what the arm withholds SHALL NOT be handed over whole because the content changed, while a path this child
was never handed SHALL be answered by this ledger as it was (issue #925).

#### Scenario: The item's ledger outlives the child that filled it

- **WHEN** one child of an item reads a path and writes the ledger the adapter named beside the item's stream, the child
  the run starts after it reads the same unchanged path, and a later read follows an edit of that path
- **THEN** the second read is replaced by one line naming the session that read the path and the turn it read it at,
  and the read after the edit is no longer that line — it is the verification read the scenario below states
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

#### Scenario: The read that follows an edit is the file's head, not the file

- **WHEN** an item's ledger records a path and a child reads that path again after its content changed, and the same
  thing happens to a suite XML the recorded bound exempts
- **THEN** the read is handed the first 2 000 characters of the file and one `[LOOP-REPEAT-EDIT: …]` line naming the
  path, the turn the item read it at, the bound, the size the file now has and the call that reads the rest — never the
  whole file — and the record gains one `LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` line whose difference is the
  volume the child was not handed, while the suite XML is handed over whole
- **AND** a text that fits the bound is handed over whole, and the bounded read becomes what the next repeat of that
  path is measured against, its own size and turn
- **Case**: `repeat#aVerificationReadIsBoundedNotHandedOverWhole`
