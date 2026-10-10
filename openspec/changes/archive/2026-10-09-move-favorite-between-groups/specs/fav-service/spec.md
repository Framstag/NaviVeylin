# Spec Delta

## ADDED Requirements

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
