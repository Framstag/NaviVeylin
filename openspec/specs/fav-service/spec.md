# fav-service Specification

## Purpose

Provides a Kotlin repository layer that wraps the JNI CRUD methods for favorite location groups and favorites, exposing reactive state via Kotlin Flow for use by Compose ViewModels.

## Requirements

### Requirement: FavoriteRepository loads favorites on init
The system SHALL load favorite locations from the JNI layer when `FavoriteRepository` is first constructed. The loaded groups and favorites SHALL be exposed as a `StateFlow<Map<String, List<FavoriteLocation>>>` keyed by group name.

#### Scenario: Favorites loaded at construction
- **WHEN** `FavoriteRepository` is created
- **THEN** `client.loadFavoriteLocations()` SHALL be called and the result SHALL be exposed via the state flow

#### Scenario: Empty state on first use
- **WHEN** no favorites file exists yet
- **THEN** the state flow SHALL emit an empty map

### Requirement: Repository exposes CRUD methods for groups
The system SHALL provide suspend functions: `addGroup(name)`, `deleteGroup(name)`, `getGroups()`. Each SHALL delegate to the corresponding JNI method and update the state flow on success.

#### Scenario: Add group succeeds
- **WHEN** `addGroup("Work")` is called
- **THEN** the state flow SHALL emit a map containing group "Work" with an empty fav list

#### Scenario: Add duplicate group returns false
- **WHEN** `addGroup` is called with an existing group name
- **THEN** it SHALL return `false` and the state flow SHALL NOT change

#### Scenario: Delete group succeeds
- **WHEN** `deleteGroup("Work")` is called
- **THEN** the state flow SHALL emit a map without group "Work"

### Requirement: Repository exposes CRUD methods for favorites
The system SHALL provide suspend functions: `addFavorite(groupName, favName, lat, lon)`, `deleteFavorite(groupName, favName)`, `renameFavorite(groupName, oldName, newName)`. Each SHALL delegate to the corresponding JNI method and update the state flow on success.

#### Scenario: Add favorite to group succeeds
- **WHEN** `addFavorite("Work", "Office", 48.85, 2.35)` is called
- **THEN** the state flow SHALL emit a map where group "Work" contains the new favorite

#### Scenario: Add duplicate favorite returns false
- **WHEN** `addFavorite` is called with a fav name that already exists in the group
- **THEN** it SHALL return `false` and the state flow SHALL NOT change

#### Scenario: Delete favorite succeeds
- **WHEN** `deleteFavorite("Work", "Office")` is called
- **THEN** the state flow SHALL emit a map where group "Work" no longer contains "Office"

#### Scenario: Rename favorite succeeds
- **WHEN** `renameFavorite("Work", "Office", "HQ")` is called
- **THEN** the state flow SHALL emit a map where the fav is renamed to "HQ"

### Requirement: Repository persists on every write
The system SHALL call `client.saveFavoriteLocations()` after every successful write operation (add/delete/rename group or favorite).

#### Scenario: Save called after add
- **WHEN** a favorite is added successfully
- **THEN** `saveFavoriteLocations()` SHALL be called

#### Scenario: Save called after delete
- **WHEN** a favorite is deleted successfully
- **THEN** `saveFavoriteLocations()` SHALL be called

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
