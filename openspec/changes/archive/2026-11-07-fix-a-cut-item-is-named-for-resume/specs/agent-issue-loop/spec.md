# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A cut item is named to the caller as work to resume

The report SHALL name every item the run's own bound ended as an action, saying that no decision is owed for it and
carrying the mechanism the run recorded, because a bound is the run's clock and not a finding about the item.

#### Scenario: The action list names a cut item for resume and asks for no gate

- **WHEN** a run ends one of an item's children at a bound, records the item `cut` with the mechanism, and the item
  never reached a gate
- **THEN** the report's "actions the caller owes" list carries a `resume #<item>` line naming the mechanism and the
  item's transcript, and it carries no `re-run the gate for #<item>` line
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`
