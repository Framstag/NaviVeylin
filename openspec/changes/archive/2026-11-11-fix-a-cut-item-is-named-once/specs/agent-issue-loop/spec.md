# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: A cut item is named to the caller as work to resume

The report SHALL name every item the run's own bound ended as an action, saying that no decision is owed for it and
carrying the mechanism the run recorded, because a bound is the run's clock and not a finding about the item. An item
SHALL be named on one such line and no more, and the kept patch of the work its child left SHALL be named on that
line.

#### Scenario: The action list names a cut item for resume and asks for no gate

- **WHEN** a run ends one of an item's children at a bound, records the item `cut` with the mechanism, and the item
  never reached a gate
- **THEN** the report's "actions the caller owes" list carries a `resume #<item>` line naming the mechanism and the
  item's transcript, and it carries no `re-run the gate for #<item>` line
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: A cut item is named once, and its kept work travels on that line

- **WHEN** an item the run's own bound ended carries a kept patch, and the run's state also holds the record of an
  item whose kept work names no bound cut — the shape a tree wrote before `cut` existed
- **THEN** the action list names the cut item once, on its `resume` line, carrying the patch its child left and the
  note that names which bound ended it, and names it on no recovery line; and it names the record with no bound cut
  on its recovery line, carrying that patch, and names it on no resume line
- **Case**: `report#aCutItemIsNamedOnceAndItsKeptWorkWithIt`
