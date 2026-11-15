# agent-issue-loop delta

## ADDED Requirements

### Requirement: A blocked candidate is not work

The pre-screen SHALL exclude a candidate the tracker holds blocked by an open item, and a candidate whose change an
item this run recorded `blocked` names, giving both the verdict `blocked-by:#N`. What it read SHALL be recorded beside
the verdict — the tracker's edge, or the run's own record. An item of the same change that landed SHALL block nothing,
and a closed predecessor SHALL clear its edge without a write.

#### Scenario: A candidate the tracker holds blocked waits for its predecessor

- **WHEN** a candidate is open, authorised and labelled like any other, and the tracker holds an edge to a predecessor
  that is not closed
- **THEN** the pre-screen gives it the verdict `blocked-by:#N`, records `blockedBy:#N` as the evidence, and it reaches
  no queue
- **AND** no label is written for it, because the edge the verdict was read from is already visible on the tracker
- **AND** the same edge whose predecessor is closed, and a tracker that carries no dependency field, block nothing
- **Case**: `prescreen#aBlockedCandidateWaitsForItsPredecessor`

#### Scenario: A candidate shares the change a blocked item named

- **WHEN** the run recorded an item of change C `blocked`, and a candidate names C in its own body
- **THEN** the pre-screen gives that candidate `blocked-by:#N` naming the blocked item, and records `run-blocked:#N` so
  a reader can tell this source from an edge the tracker holds
- **AND** a candidate of another change is work exactly as before, as is one of a change whose item landed
- **Case**: `prescreen#theRunHoldsWhatItsBlockedItemShares`