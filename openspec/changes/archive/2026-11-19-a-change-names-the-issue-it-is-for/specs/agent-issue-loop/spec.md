## ADDED Requirements

### Requirement: The pre-screen refuses an item two open changes name

The pre-screen SHALL refuse an item whose number two open changes declare, before a branch is cut, and SHALL leave
that refusal on the item in the tracker.

#### Scenario: An item whose number two open changes carry is refused

- **WHEN** the item is picked and two open change directories both carry its number in `x-issue`
- **THEN** the pre-screen refuses it with both directories in the evidence, queues no work for it, and writes one
  comment on the item, so the collision is learned before a session spends its planning half on a change that is
  already unarchivable
- **Case**: `prescreen#anIssueTwoOpenChangesNameIsRefused` — the control is one open change claiming the number,
  which refuses nothing; a tree that carries no such check has no open set to read and is screened as before
