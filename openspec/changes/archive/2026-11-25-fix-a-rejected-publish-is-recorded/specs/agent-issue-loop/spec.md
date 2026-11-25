# agent-issue-loop — deltas

## MODIFIED Requirements

### Requirement: The publish publishes what still exists

The run's only push SHALL publish what still exists when it runs: a branch the run has already merged and released
SHALL be a named skip rather than a git error, and a push that fails for another reason SHALL say so on one line.
The base branch a landing was merged into SHALL be published through that same push, so the remote's base carries
the run's last landing and a reader's CI measures the tree the run produced.

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

- **Case**: `worktree#publishesTheBaseItLandedOn`

#### Scenario: The base a run landed on is published

- **WHEN** a run is asked to publish and it lands an item on its base branch, while the remote's base still stands
  at the commit the run started from
- **THEN** the run publishes that base branch from the item's tail, through the one function that publishes, so the
  remote's base carries the run's last landing
- **AND** a base publish that does not succeed is one line naming the base and where its commits stayed, with no
  retry
- **Case**: `worktree#publishesTheBaseItLandedOn`

#### Scenario: A refused publish is recorded on the item

- **WHEN** a run is asked to publish and the remote refuses the push for a branch or for the base it landed on
- **THEN** the branch it refused is named in the run's report as a leftover whose commits stay in the run tree
  and never reached the remote
- **AND** the item's record carries the refused branch and the local head it names, beside the landing's sha
  and the base it landed on, so a reader comparing that sha with the remote has the sentence that explains the
  difference
- **Case**: `worktree#aRejectedPushIsRecordedOnTheItem`
