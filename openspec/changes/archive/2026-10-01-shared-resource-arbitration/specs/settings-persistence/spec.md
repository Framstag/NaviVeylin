# Spec Delta

## Purpose

Defines how the shared settings file is written when more than one surface can change settings in the
same process: a serialized read-modify-write, per-writer field ownership, and preservation of fields a
writer does not own.

## ADDED Requirements

### Requirement: Settings writes are serialized read-modify-write transactions

A settings change SHALL read the current persisted settings, apply the change, and write the result as
one transaction that no other write can interleave with. A writer SHALL NOT be able to persist a
snapshot taken before another writer's completed update.

#### Scenario: Two writers in one process do not lose an update

- **WHEN** the phone changes one setting and the car changes another setting at the same time
- **THEN** the persisted settings contain both changes

#### Scenario: Repeated writes converge

- **WHEN** two settings writes complete
- **THEN** the persisted file reflects the second transaction applied to the first one's result
- **AND** no field silently reverts to a value from before the first write

### Requirement: A writer persists only the fields it owns

A settings writer SHALL apply its own fields onto the current persisted settings and SHALL leave fields
it does not own untouched.

#### Scenario: Car write preserves phone-only fields

- **WHEN** the car saves a preference while a phone-only field (e.g. keep-screen-on) has a non-default value
- **THEN** the phone-only field keeps its value after the write

#### Scenario: Car anchors are not frozen by an unrelated write

- **WHEN** the car saves an unrelated preference
- **THEN** a vehicle anchor the car has not chosen itself stays inherited from the phone instead of being persisted as the car's own value

#### Scenario: Unknown keys do not break a write

- **WHEN** a settings file contains keys the current schema does not know
- **THEN** the write still succeeds and the unknown keys do not cause a failure or an error state

### Requirement: Write failures are non-fatal and observed

A failed settings write SHALL NOT surface as a crash or an error state, and SHALL be recorded in the
diagnostics or log stream.

#### Scenario: Unwritable storage

- **WHEN** writing the settings file fails (I/O error)
- **THEN** the app keeps running with the in-memory values
- **AND** the failure is recorded in the log
