## ADDED Requirements

### Requirement: A report reports the shape a run spent its budget in

The report printed what an item cost and nothing about the shape it spent it in, so a session that made one tool call per turn read exactly like one that batched its independent reads (issue #757). It SHALL derive each item's calls per turn from the counts its event stream records, print the run's rate, and name the items below a recorded floor — a floor that SHALL only ever warn.

#### Scenario: An item below the recorded floor is named, and one above it is not

- **WHEN** an item's event stream records 6 calls in 2 turns and another's records 2 calls in 4 turns, and `LOOP_ITEM_CALLS_PER_TURN_MIN` records a floor between them
- **THEN** the trend prints the run's own rate with the two counts it came from (`1.33 calls per turn (8 calls / 6 turns)`) and names the item below the floor with its own ratio (`#22 0.5`) while the item above it is not named, the table's last column reads `3` and `0.5`, a floor of `0` prints as off and names no item, an item that recorded no turn prints `-` with its reason, a state written before the two counts existed says so rather than naming a run of items that answered without a tool, and both items keep the outcome their work earned
- **Case**: `report#anItemBelowTheCallsPerTurnFloorIsNamed`
