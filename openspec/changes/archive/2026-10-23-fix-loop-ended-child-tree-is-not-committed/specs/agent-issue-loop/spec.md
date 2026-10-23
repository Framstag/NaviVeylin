# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A child the run ended itself leaves no commit

The run SHALL commit an item's tree only after the child that produced it has finished by its own exit. A child
the run ended — the wall-clock cap's status, or the `LOOP-TIMEOUT` line its transcript carries — SHALL leave the
branch as it stood: the item is recorded blocked with that transcript, and the tree is kept exactly as the child
left it, because nothing is discarded without `--reset-worktree`. It holds for the rework worker a refusing review
round starts as well.

#### Scenario: A worker the cap ends leaves the branch at the base

- **WHEN** the wall-clock cap ends an item's worker and that worker has left changes in the change root
- **THEN** the run commits none of them — the change root's `HEAD` is still the base commit — the item is recorded
  `blocked` with its `LOOP-TIMEOUT` transcript, the tree still carries what the child left (kept, not discarded),
  and the run's own line says the commit was skipped
- **Case**: `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: The cap ends the rework worker of a refusing review round

- **WHEN** a review round refuses the change and the wall-clock cap ends the rework worker that round starts, after
  that worker has written into the change root
- **THEN** the run commits none of it — the branch carries only what the item's own worker had already earned — the
  item is recorded `blocked` with the round's `LOOP-TIMEOUT` transcript, the tree still carries what that worker
  left, and the blocked reason names the tree it did not commit
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`
