## ADDED Requirements

### Requirement: Both clocks a run refuses by hold what one item's gate owes

The profile SHALL record what one item's gate owes, and neither the child's wall-clock bound nor the item floor the
run refuses a start by SHALL be recorded below it: a clock smaller than an item's own gate ends or refuses work the
run has already decided it can pay for.

#### Scenario: The child's bound follows what one item's gate owes

- **WHEN** the profile records `LOOP_ITEM_GATE_SECONDS` and a child wall-clock floor below it
- **THEN** the run gives each child the larger of the two, so an item owing a red round and a green one is not ended
  inside its own cost
- **Case**: `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`

#### Scenario: The item floor is not below what one item's gate owes

- **WHEN** the profile records `LOOP_ITEM_GATE_SECONDS` and an `LOOP_ITEM_FLOOR_MINUTES` whose minutes are fewer than
  it, or records a floor that is not a number
- **THEN** the suite fails naming both numbers, because `next` would start an item that pays for its gate and loses
  the phases after a green round
- **Case**: `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`
