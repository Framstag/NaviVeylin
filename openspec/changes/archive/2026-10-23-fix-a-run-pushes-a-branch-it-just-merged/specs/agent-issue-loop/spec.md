# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The publish publishes what still exists

The run's only push SHALL publish what still exists when it runs: a branch the run has already merged and released
SHALL be a named skip rather than a git error, and a push that fails for another reason SHALL say so on one line.

#### Scenario: A branch the run merged and released is a named skip

- **WHEN** a run is asked to publish and the item's branch has already been merged into the base and removed by the
  run's own release
- **THEN** the run prints that there is nothing to publish for that branch and why, and hands git no refspec at all
  — the closure it belongs to is unaffected, and no `does not match any` reaches the run log
- **AND** the remote carries no branch of a closed item
- **Case**: `worktree#pushSkipsABranchItAlreadyMerged`

#### Scenario: A push that fails says what it means

- **WHEN** the branch still exists and the push does not succeed
- **THEN** the run prints one line naming the branch and where its commits stayed — the run tree, not the remote —
  so a caller reads a publishing failure as one instead of as git's own error text
