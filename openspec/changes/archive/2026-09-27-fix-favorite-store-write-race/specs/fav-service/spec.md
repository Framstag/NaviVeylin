# Spec Delta — fav-service

## ADDED Requirements

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
