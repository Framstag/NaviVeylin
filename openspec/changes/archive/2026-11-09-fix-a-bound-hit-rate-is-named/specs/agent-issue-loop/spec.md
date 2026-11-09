# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The rate of items the run's own bound ended is printed beside the archive's

A run's report SHALL print how many of the items it attempted the run's own bound ended — read from the record's own
mechanism, `stopReason: bound` — and the archive's rate beside it, counted only over archived records that carry that
field. It SHALL name each such item with the turns it spent and the files its leftover patch carries, and an item's
record SHALL carry that count, read from the patch, when a leftover path is recorded.

#### Scenario: The run's rate and the archive's are printed together, with what each item kept

- **WHEN** a run records 11 items, 2 of them cut with `bound` as their stop reason — one having kept a patch carrying
  3 files — and the archive beside it holds 17 item records that name a mechanism, 3 of them bound, and 5 older
  records whose item predates the field
- **THEN** the report's `bound:` line prints `2 of 11 items (18.2 %)` for the run and
  `3 of 17 items whose record names a mechanism (17.6 %)` for the archive, names the two items with their turns and,
  for the one that kept a patch, its file count, and names no item that closed
- **AND** a run the bound ended nothing in prints `0 of 3 items (0.0 %)` and an archive of no finished run prints
  `-`, because a rate over no item is not a measurement and `0 %` would read as one
- **Case**: `report#theBoundRateIsNamed`

#### Scenario: A kept patch is priced with the files it carries

- **WHEN** an item is recorded `cut` with `bound` as its stop reason and the path of a patch carrying three files, a
  second with no leftover path, and a third naming a path that does not resolve
- **THEN** the first record carries `leftoverFiles: 3` beside the path and beside the mechanism it already carried,
  and the second and third carry none — never `0`, which would read as a patch that was measured and was empty
- **Case**: `state#aCutRecordsTheFilesItsPatchCarries`
