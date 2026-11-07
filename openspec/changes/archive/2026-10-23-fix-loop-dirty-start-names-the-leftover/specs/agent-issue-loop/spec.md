## ADDED Requirements

### Requirement: A start the worktree refuses mid-run names the item whose work it holds

A start refused because the worktree is not clean SHALL be attributed before the run ends on it: the item the
worktree's branch marker names SHALL be recorded `blocked` with the leftovers and escalated, the stop reason SHALL
name that item and the files it found, and the candidate that could not start SHALL NOT be recorded.

#### Scenario: A refused start is attributed to the item whose work the tree holds

- **WHEN** an item's start is refused mid-run because the worktree is not clean
- **THEN** the item the worktree's branch marker names is recorded blocked with the leftovers it left and escalated,
  the stop reason names that item and the files — not the refusal's first line — and the item that could not start
  keeps no record of its own
- **Case**: `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`
