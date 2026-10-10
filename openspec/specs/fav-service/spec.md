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

### Requirement: Overlapping write operations are serialised

When two repository write operations overlap, both SHALL take effect. Each successful write SHALL be reflected in the exposed state and in the persisted favorites file, and no write SHALL be discarded by another write that is running concurrently. Each write's native mutation, state refresh and file persist SHALL be observable as one atomic unit, so that no other write's mutation can be observed between them.

#### Scenario: Two overlapping writes both survive

- **WHEN** two write operations are issued concurrently (for example two favorites added, or a star change while a delete is still persisting)
- **THEN** the exposed state SHALL contain the effect of both operations
- **AND** the persisted favorites file SHALL contain the effect of both operations

#### Scenario: No write is persisted from an out-of-date store

- **WHEN** a write operation reads the store and another write mutates the store before the first write persists
- **THEN** the persisted file SHALL reflect the store as it stands after both mutations
- **AND** neither mutation SHALL be absent from the persisted file

#### Scenario: A concurrent read never exposes a partially applied store

- **WHEN** the store is being rebuilt by a write (or by a load of the favorites file) and another operation reads the favorites at the same time
- **THEN** the read SHALL return either the store as it stood before that rebuild or the fully rebuilt store
- **AND** SHALL NOT return an empty or partially rebuilt store

#### Scenario: Adding to a group that does not exist yet cannot self-deadlock

- **WHEN** a favorite is added to a group that does not exist yet, so the group is created first (group creation persists, then the favorite persists again)
- **THEN** the group and the favorite SHALL both be present in the exposed state and in the persisted file
- **AND** the operation SHALL return success without blocking

#### Scenario: Phone and car surfaces share the same guarantee

- **WHEN** the phone favorites sheet and the Android Auto favorites screen each issue a write against the same repository
- **THEN** both writes SHALL be serialised by the same contract
- **AND** neither surface SHALL observe a lost write

### Requirement: Serialisation does not change the single-writer contract

Serialising overlapping writes SHALL NOT change the behavior of writes that do not overlap: each successful write SHALL still be persisted exactly once, a failed write SHALL still persist nothing, and writes issued one after another SHALL be applied in issue order.

#### Scenario: Sequential writes each persist once

- **WHEN** two write operations are issued one after the other without overlapping
- **THEN** each successful write SHALL persist exactly once
- **AND** the final state SHALL contain the effect of both writes

#### Scenario: A failed write still persists nothing

- **WHEN** a write fails (unknown group or unknown favorite)
- **THEN** nothing SHALL be persisted
- **AND** the exposed state SHALL be unchanged

#### Scenario: Existing persistence expectations are unchanged

- **WHEN** a favorite is deleted, renamed, moved or starred, or a group is added, deleted, renamed or colored
- **THEN** the persisted file SHALL still be written exactly once per successful operation
- **AND** no additional native save call SHALL be made for that operation compared with the behavior before write serialisation
- **AND** adding a favorite to a group that does not exist yet SHALL keep its existing two-step persist (group creation, then the favorite)

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

### Requirement: Repository exposes a cross-group move method

`FavoriteRepository` SHALL provide a suspend function `moveFavoriteToGroup(sourceGroup, favName, targetGroup, newIndex)` that delegates to the JNI `moveFavoriteToGroup` method and returns whether the move succeeded. On success it SHALL re-emit the state flow with the favorite present in the target group and absent from the source group, and SHALL persist the favorites file exactly once. On failure the state flow SHALL NOT change and nothing SHALL be persisted. The call SHALL run on the repository's background dispatcher, never on the main thread, and SHALL take part in the same write serialisation as every other write.

A destination group that does not exist yet SHALL be created as part of the move, so a move into a brand-new group needs no separate call and a move never fails merely because the destination is new. That creation persists on its own before the move persists, the same two-step sequence as adding a favorite to a group that does not exist yet. Once it exists, the destination group is left in place even if the move itself is then refused.

#### Scenario: Move updates the exposed state

- **WHEN** `moveFavoriteToGroup("Work", "Office", "Cities", 0)` succeeds
- **THEN** the state flow SHALL emit a map where group "Cities" contains "Office" at index 0
- **AND** group "Work" SHALL no longer contain "Office"

#### Scenario: Move persists once

- **WHEN** a cross-group move succeeds
- **THEN** `saveFavoriteLocations` SHALL be called exactly once for that operation
- **AND** the persisted file SHALL record the favorite in the destination group

#### Scenario: Failed move leaves state untouched

- **WHEN** a cross-group move fails because the source group is unknown or the source group does not hold the favorite
- **THEN** the state flow SHALL emit the previous grouping
- **AND** `saveFavoriteLocations` SHALL NOT be called

#### Scenario: Destination group created as part of the move

- **WHEN** the user moves a favorite into a group that does not exist yet
- **THEN** the group SHALL be created and the favorite SHALL be moved into it without a separate creation call
- **AND** both SHALL be visible in the exposed state and present in the persisted file
- **AND** the operation SHALL report success without blocking

#### Scenario: Refused move leaves both groups untouched

- **WHEN** a cross-group move is refused because the destination group already holds a favorite of that name
- **THEN** the repository SHALL return `false`
- **AND** the state flow SHALL still show the favorite in its source group
- **AND** nothing SHALL be persisted

#### Scenario: Move before the repository is initialised

- **WHEN** a cross-group move is requested before the repository has been initialised with a file path
- **THEN** it SHALL return `false`
- **AND** no native call SHALL be made

#### Scenario: Cross-group move overlapping another write

- **WHEN** a cross-group move overlaps another write operation (for example a reorder inside a group)
- **THEN** both operations' effects SHALL be present in the exposed state and in the persisted file
- **AND** neither SHALL be discarded

#### Scenario: Native call runs off the main thread

- **WHEN** a cross-group move is invoked
- **THEN** the JNI call SHALL run on the repository's background dispatcher, never on the main thread
