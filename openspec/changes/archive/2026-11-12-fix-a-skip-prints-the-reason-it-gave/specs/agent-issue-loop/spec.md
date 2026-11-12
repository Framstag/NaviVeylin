# agent-issue-loop delta

## ADDED Requirements

### Requirement: A skip's reason reaches the report

A skip the run records SHALL carry, in the note the report prints on its `skipped:` line, the reason the child gave
for the skip, rather than a constant recorded beside that reason in the same item — so the one account a caller reads
names why the item is a skip and the question its `decide` line asks. A child that gives no reason SHALL leave the
note as the base wrote it.

#### Scenario: A skip taken on the plain worker path prints the child's reason

- **WHEN** a worker answers `LOOP-VERDICT: skipped` with a `LOOP-ROOT-CAUSE` sentence and the run records the item
  `skipped`
- **THEN** the item's note is that sentence and the report's `skipped:` line prints it instead of a constant, and a
  skip whose child gave no reason leaves the note the constant the base wrote
- **Case**: `run#aSkipPrintsTheReasonTheChildGave`
