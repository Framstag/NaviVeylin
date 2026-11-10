## MODIFIED Requirements

### Requirement: The bound on a child is priced in what it spends, not in how many turns it takes

The run SHALL bound a child in what a turn costs, not in how many turns it takes: it SHALL read the largest context the child's stream reported (`input + cacheRead`, or a compaction's `tokensBefore`, whichever is greater) in the wait that enforces the clock and the turn count, SHALL print one checkpoint line at `LOOP_ITEM_CONTEXT_WARN`, and SHALL end it and everything it started at the bound the child's **role** records, `LOOP_ITEM_CONTEXT_MAX_<ROLE>`, falling back to `LOOP_ITEM_CONTEXT_MAX` where a role records none. Both keys SHALL be recorded values named in the run's header; `0` or no key is no bound. An item this bound ends SHALL be recorded `cut` with `bound` as its stop reason, exactly as every other bound the run enforces ends one (issue #790): the phase is the next run's to resume, not a finding about the item.

#### Scenario: A child whose context crosses the recorded bound is ended and named

- **WHEN** a child's stream reports a growing context — 120 000, 155 000, then 210 000 tokens — while
  `LOOP_ITEM_CONTEXT_MAX` records 200 000 and `LOOP_ITEM_CONTEXT_WARN` records 143 124, and the wall clock still has
  45 min to run
- **THEN** the run prints the checkpoint once, naming the recorded 143 124, ends the child's process group, writes
  `LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 210000
  tokens — the run ended it and everything it started` into the child's transcript with the wall clock's own exit
  status, records the item `cut` with `bound` as its stop reason and that line as its note, and closes nothing
- **Case**: `run#childPastItsContextBoundIsCut`

#### Scenario: A reading phase is bounded by its own key

Measured 2026-11-10 over the day's three runs: 19 items attempted, 9 recorded `cut`, 8 of them at a largest context of
199 178–211 475 against the recorded 200 000, while the six that closed read 114 522–192 886 — the stop sat inside the
population that finishes, and every one of the eight belonged to a role whose work is reading the item.

- **WHEN** the item's key records 160 000, the reviewer's own key records 250 000, a worker's stream reports 150 000 and
  a review round's stream reports 200 000, and a second run's review round reports 260 000
- **THEN** the header names every role's figure (`item-context-bounds=…`), the round that read 200 000 answers and the
  item keeps its outcome, and the round that read 260 000 is ended with a transcript naming
  `LOOP_ITEM_CONTEXT_MAX_REVIEWER` and recorded `cut`
- **Case**: `run#eachRoleIsBoundedByItsOwnContext`

#### Scenario: A recorded zero keeps the context bound off

- **WHEN** the profile records `LOOP_ITEM_CONTEXT_MAX=0` and `LOOP_ITEM_CONTEXT_WARN=0` and a child's stream reports a
  210 000-token context before it outlives a two-second wall clock
- **THEN** the header names the bound as off (`item-context-max=0`), no checkpoint line is printed, the transcript
  carries only the wall clock's `LOOP-TIMEOUT` and names no context bound, and the item is recorded `cut`
- **Case**: `run#theRecordedZeroKeepsTheContextBoundOff`

### Requirement: The rate of items the run's own bound ended is printed beside the archive's

A run's report SHALL print how many of the items it attempted the run's own bound ended — read from the record's own **result**, `cut`, which exists only for a bound the run enforced, and from the `stopReason: bound` a record written before that result came into use carries — and the archive's rate beside it, counted the same way over every archived record. It SHALL name each such item with the turns it spent and the files its leftover patch carries, and an item's record SHALL carry that count, read from the patch, when a leftover path is recorded.

#### Scenario: The run's rate and the archive's are printed together, with what each item kept

- **WHEN** a run records 12 items, 3 of them `cut` — one with `bound` as its stop reason having kept a patch carrying
  3 files, one with `bound` and no patch, and one a review round's own reading bound ended, which carries no
  mechanism — and the archive beside it holds 25 item records, 3 of them bound and the rest carrying neither
- **THEN** the report's `bound:` line prints `3 of 12 items (25.0 %)` for the run and
  `3 of 25 items a bound ended (12.0 %)` for the archive, names all three items with their turns and, for the one
  that kept a patch, its file count, and names no item that closed
- **AND** a run the bound ended nothing in prints `0 of 3 items (0.0 %)` and an archive of no finished run prints
  `-`, because a rate over no item is not a measurement and `0 %` would read as one
- **Case**: `report#theBoundRateIsNamed`

#### Scenario: A kept patch is priced with the files it carries

- **WHEN** an item is recorded `cut` with `bound` as its stop reason and the path of a patch carrying three files, a
  second with no leftover path, and a third naming a path that does not resolve
- **THEN** the first record carries `leftoverFiles: 3` beside the path and beside the mechanism it already carried,
  and the second and third carry none — never `0`, which would read as a patch that was measured and was empty
- **Case**: `state#aCutRecordsTheFilesItsPatchCarries`
