## Purpose

Adds the reorder operation to the Kotlin repository that wraps the favorites JNI CRUD, keeping the reactive state in sync and persisting exactly once per successful move.

## ADDED Requirements

### Requirement: Repository exposes a move method for favorites

`FavoriteRepository` SHALL provide a suspend function `moveFavorite(groupName, favName, newIndex)` that delegates to the JNI `moveFavorite` method and returns whether the move succeeded. On success it SHALL re-emit the state flow with the new order and SHALL persist the favorites file once. On failure the state flow SHALL NOT change and nothing SHALL be persisted.

#### Scenario: Move updates the exposed state

- **WHEN** `moveFavorite("Work", "Office", 0)` succeeds
- **THEN** the state flow SHALL emit the group "Work" with "Office" at index 0

#### Scenario: Move persists once

- **WHEN** `moveFavorite` succeeds
- **THEN** `saveFavoriteLocations` SHALL be called exactly once

#### Scenario: Failed move leaves state untouched

- **WHEN** `moveFavorite` fails (unknown group or favorite)
- **THEN** the state flow SHALL emit the previous order
- **AND** `saveFavoriteLocations` SHALL NOT be called

#### Scenario: Move before the repository is initialised

- **WHEN** `moveFavorite` is called before the repository has been initialised with a file path
- **THEN** it SHALL return `false`
- **AND** no native call SHALL be made

#### Scenario: Native call runs off the main thread

- **WHEN** `moveFavorite` is invoked
- **THEN** the JNI call SHALL run on the repository's background dispatcher, never on the main thread
