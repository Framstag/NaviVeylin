# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A label write the tracker refuses is recorded, not swallowed

A label transition the run could not write SHALL be recorded with the item, and the report SHALL name that
item and the transition, because the item's state on the tracker is then stale in a way a reader of the
tracker cannot see. The run SHALL NOT record a decision label it did not write.

#### Scenario: An escalation the tracker refused is named

- **WHEN** the run blocks an item and the escalation's label write fails, so neither the decision label nor the
  hold reaches the tracker
- **THEN** the item is still recorded blocked with no decision label claimed, the refused transition is
  recorded with it, and the report's action list names the item and the write a person has to repeat by hand
- **Case**: `run#aFailedEscalationWriteIsNamed`

#### Scenario: An in-progress mark the tracker refused to clear is named

- **WHEN** the run ends an item and the write that removes the in-progress mark fails
- **THEN** the escalation that did land stands, the refused transition is recorded with the item, and the
  report names the mark nobody is holding any more
- **Case**: `run#aFailedUnpickIsNamed`
