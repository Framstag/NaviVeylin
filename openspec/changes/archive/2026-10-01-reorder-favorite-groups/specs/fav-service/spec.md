# Spec Delta

## ADDED Requirements

### Requirement: Repository exposes the group order as its own state

`FavoriteRepository` SHALL expose the group order as a `StateFlow<List<String>>` (`groupOrder`), updated whenever the favorites state is refreshed and in the same operation as that refresh, so the order and the group contents can never describe different stores. The order SHALL be emitted whenever the sequence of groups changes, including when the group contents are unchanged, so a consumer collected on that flow observes every reorder.

#### Scenario: The order flow carries the stored sequence

- **WHEN** the repository has loaded a store whose group order is `Cities, Work, Home`
- **THEN** `groupOrder` SHALL emit `["Cities", "Work", "Home"]`

#### Scenario: A reorder emits on the order flow

- **WHEN** `moveGroup` succeeds
- **THEN** `groupOrder` SHALL emit the new sequence
- **AND** the emission SHALL happen even when the group contents are unchanged, including for groups that hold no favorites

#### Scenario: Order and contents are refreshed together

- **WHEN** any operation refreshes the favorites state
- **THEN** the order flow and the group map SHALL both be updated in that refresh
- **AND** the order flow's sequence SHALL match the native group order

### Requirement: Repository exposes a move method for groups

`FavoriteRepository` SHALL provide a suspend function `moveGroup(groupName, newIndex)` that delegates to the JNI `moveGroup` method and returns whether the move succeeded. On success it SHALL re-emit the state flow with the group at its new position and SHALL persist the favorites file once. On failure the state flow SHALL NOT change and nothing SHALL be persisted.

#### Scenario: Move updates the exposed state

- **WHEN** `moveGroup("Work", 0)` succeeds
- **THEN** the state flow SHALL emit a map whose first key is `"Work"`

#### Scenario: Move preserves the favorites of every group

- **WHEN** `moveGroup` succeeds
- **THEN** every group's favorite list SHALL be unchanged
- **AND** only the order of the groups in the emitted map SHALL differ

#### Scenario: Move persists once

- **WHEN** `moveGroup` succeeds
- **THEN** `saveFavoriteLocations` SHALL be called exactly once

#### Scenario: Failed move leaves state untouched

- **WHEN** `moveGroup` fails (unknown group)
- **THEN** the state flow SHALL emit the previous order
- **AND** `saveFavoriteLocations` SHALL NOT be called

#### Scenario: Move before the repository is initialised

- **WHEN** `moveGroup` is called before the repository has been initialised with a file path
- **THEN** it SHALL return `false`
- **AND** no native call SHALL be made

#### Scenario: Native call runs off the main thread

- **WHEN** `moveGroup` is invoked
- **THEN** the JNI call SHALL run on the repository's background dispatcher, never on the main thread

#### Scenario: Group move takes part in write serialisation

- **WHEN** a `moveGroup` overlaps another write operation (for example a favorite added while the group move is persisting)
- **THEN** both writes SHALL take effect
- **AND** the persisted favorites file SHALL reflect both
- **AND** the group order SHALL be the one the move produced
