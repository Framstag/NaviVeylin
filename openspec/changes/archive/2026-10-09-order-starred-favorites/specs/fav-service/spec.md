# Spec Delta

## ADDED Requirements

### Requirement: Repository exposes the stored starred order

`FavoriteRepository` SHALL expose the store's starred order as reactive state: one entry per starred favorite, each naming the group that holds it and the favorite itself, in the sequence the store reports. The sequence SHALL be the store's starred order and SHALL NOT be derived from the iteration order of the group map. The state SHALL be refreshed after the initial load and after every successful write, and SHALL be empty when no favorite is starred or when no store is loaded.

#### Scenario: Exposed order is the stored one

- **GIVEN** the store holds a starred order that differs from the sequence a group-map iteration would produce
- **WHEN** the exposed starred order is read
- **THEN** it SHALL be the store's sequence
- **AND** every entry SHALL name its group and its favorite

#### Scenario: Order updates on a write

- **WHEN** a favorite is starred, unstarred or moved in the starred order
- **THEN** the exposed starred order SHALL reflect the store's order afterwards

#### Scenario: No starred favorites

- **WHEN** no favorite is starred
- **THEN** the exposed starred order SHALL be empty

#### Scenario: Read before the repository is initialised

- **WHEN** the starred order is read before the repository has been initialised with a file path
- **THEN** it SHALL be empty

### Requirement: Repository exposes a move method for the starred order

`FavoriteRepository` SHALL provide a suspend function `moveStarredFavorite(groupName, favName, newIndex)` that delegates to the JNI `moveStarredFavorite` method and returns whether the move succeeded. On success it SHALL re-emit the exposed starred order and SHALL persist the favorites file once. On failure the exposed state SHALL NOT change and nothing SHALL be persisted.

#### Scenario: Move updates the exposed order

- **WHEN** `moveStarredFavorite("Work", "Office", 0)` succeeds
- **THEN** the exposed starred order SHALL begin with "Office"

#### Scenario: Move persists once

- **WHEN** `moveStarredFavorite` succeeds
- **THEN** `saveFavoriteLocations` SHALL be called exactly once

#### Scenario: Failed move leaves state untouched

- **WHEN** `moveStarredFavorite` fails (unknown group, unknown favorite, or a favorite that is not starred)
- **THEN** the exposed starred order SHALL be the previous one
- **AND** `saveFavoriteLocations` SHALL NOT be called

#### Scenario: Move before the repository is initialised

- **WHEN** `moveStarredFavorite` is called before the repository has been initialised with a file path
- **THEN** it SHALL return `false`
- **AND** no native call SHALL be made

#### Scenario: Native call runs off the main thread

- **WHEN** `moveStarredFavorite` is invoked
- **THEN** the JNI call SHALL run on the repository's background dispatcher, never on the main thread

#### Scenario: Serialised against another write

- **WHEN** a starred move and another write operation are issued concurrently
- **THEN** the persisted favorites file SHALL contain the effect of both operations
- **AND** neither operation SHALL be lost
