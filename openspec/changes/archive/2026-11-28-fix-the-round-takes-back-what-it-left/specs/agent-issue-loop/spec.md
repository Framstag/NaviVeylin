## ADDED Requirements

### Requirement: A round that ends with a difference in the caller's tree takes it back

The run SHALL return the project's own tree to the state the round found it in when a child of the run ends and
the tree reads differently from the reading taken before that child, so that the next round's start-time check
does not refuse on a leftover of this run's own. Every path the difference names SHALL be removed — placed or
not, without consulting whether the reading could attribute the write to a child — from the round's kept patch
under the item's `outside-write` directory where one exists, and from the two readings otherwise. A tracked path
SHALL be restored and an untracked path removed, and the run's own state directory SHALL be left where it stands,
by the exclusion its start-time check already draws. The record SHALL be written before the removal, so the item
still carries what the round took back, each path taken back SHALL be named on the child's transcript under the
record, a path the caller had already changed before the round began SHALL be left where it stands and named as
not this round's, and what the tree still reads as a difference after the removal SHALL be named rather than
assumed clean.

#### Scenario: A round's difference is removed before the next round's start-time check reads the tree

- **WHEN** a child of the run ends and the project's own tree reads differently from the reading taken before it,
  the run's own state directory aside
- **THEN** every path the difference names is removed before the round ends — a tracked path restored, an
  untracked path deleted — whether or not the run could place the writer, the record `LOOP-OUTSIDE-WRITE:` is on
  the child's transcript ahead of the removal and each path taken back is named beneath it as
  `LOOP-OUTSIDE-WRITE-RESTORED:`, no path a child of the run wrote is left in the caller's tree, and what the
  reading still shows afterwards is named `LOOP-OUTSIDE-WRITE-KEPT:` rather than assumed clean
- **Case**: `run#childWriteOutsideTheWorktreeIsNoted`
