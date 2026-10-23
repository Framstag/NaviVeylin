# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A write outside the change root is recorded by what changed

A child that changes the project's own tree SHALL be recorded, in its transcript and in the reason the item's
block carries, with the `git status --porcelain` lines that appeared and with the processes whose working
directory is inside that tree and outside the change root, so a later reader can attribute the write — which path
to restore and which process to look at — and answer the block's escalation.

#### Scenario: A write outside the change root is named by the path and the process

- **WHEN** a child that ended leaves the project's own tree different from before it ran
- **THEN** its transcript carries one `LOOP-OUTSIDE-WRITE` line naming the status lines that appeared and the
  processes still working in that tree, the item is recorded `blocked` with that line as its reason, and the
  escalation's comment on the tracker carries the same paths
- **Case**: `run#childWriteOutsideTheWorktreeIsBlocked`
