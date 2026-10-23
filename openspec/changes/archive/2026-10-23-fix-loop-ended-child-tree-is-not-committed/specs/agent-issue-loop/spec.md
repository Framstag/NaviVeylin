# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A child the run ended itself leaves no commit

The run SHALL commit an item's tree only after the child that produced it has finished by its own exit. A child
the run ended — the wall-clock cap's status, or the `LOOP-TIMEOUT` line its transcript carries — SHALL leave the
branch at the base commit: the item is recorded blocked with that transcript, and the tree is kept exactly as the
child left it, because nothing is discarded without `--reset-worktree`.

#### Scenario: A worker the cap ends leaves the branch at the base

- **WHEN** the wall-clock cap ends an item's worker and that worker has left changes in the change root
- **THEN** the run commits none of them — the change root's `HEAD` is still the base commit — the item is recorded
  `blocked` with its `LOOP-TIMEOUT` transcript, the tree still carries what the child left (kept, not discarded),
  and the run's own line says the commit was skipped
- **Case**: `run#treeOfAChildTheCapEndedIsNotCommitted`
