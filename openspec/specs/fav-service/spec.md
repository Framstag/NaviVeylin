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
