# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A write the run can place is recorded where it is read

The reading taken while a child runs SHALL be recorded **where it is taken**, and not only in the branch that runs
after the gate: when it places a process of the worker's own tree in the project's own tree, the run SHALL record
that reading as a step of the item under the name `outside-write`, and SHALL start no pre-gate reviewer, no gate
child and no tail for that item, because the item's work is not the run's to verify against a tree the run cannot
vouch for. The step's note SHALL name what changed and the child the process was placed in. The item's `blocked`
result and the reason for that shape are the outcome block's, written from that same reading.

#### Scenario: A placed write is recorded before any gate child starts

- **WHEN** the heartbeat's reading of the project's own tree places a process of the worker's own tree outside the
  worktree while the worker runs
- **THEN** the item's steps carry exactly one `outside-write` step holding that reading, its note names the changed
  paths and the child the process was tied to and says the item ends at that reading, and neither a pre-gate
  reviewer nor a gate child is started for the item
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`

### Requirement: A child is given one scratch directory

Every child the run starts for an item's change SHALL be handed one directory it may write that is not the change
root — a scratch directory inside that item's own directory, created before the child starts and named in the
prompt beside the change root — and the prompt SHALL say that a write with a relative path lands in the project's own
tree and not in the change root. A file a child writes there SHALL NOT be a difference in the project's own tree,
and SHALL NOT change the outcome the item's work earned.

#### Scenario: The child's prompt names a scratch directory that exists

- **WHEN** a child is started for an item
- **THEN** its prompt names a scratch directory inside the item's own directory, that directory exists before the
  child runs, a file the child writes there is there when it ends, and the item keeps the outcome its own work
  earned
- **Case**: `run#aChildIsGivenOneScratchDirectory`
