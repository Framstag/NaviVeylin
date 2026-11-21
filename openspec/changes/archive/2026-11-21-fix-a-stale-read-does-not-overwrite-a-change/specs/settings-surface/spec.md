# Spec Delta — settings-surface

## Purpose

Owns the phone's settings surfaces: the bounded quick sheet the map's gear opens, the full-height
settings surface behind it, how each kind of row behaves there, and the phone's access to the
settings Android Auto owns.

## ADDED Requirements

### Requirement: A superseded document is never published

The one writer publishes one document: a document the writer superseded is never published, and the
map's follower applies the writer's publications only after the start-up read completes.

#### Scenario: A change made before the read completes reaches the client once

- **WHEN** the user sets the icon-versus-symbol preference while the start-up settings read is still in
  flight
- **THEN** the native client receives that value once for the one change, not the replaced value before
  it
- **AND** the settings file holds the value the user chose
- **Case**: `SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted`

#### Scenario: A read that outlived a write publishes nothing

- **WHEN** a settings write begins after a read has started and finishes before that read returns
- **THEN** the read leaves the published document alone
- **AND** the document the write produced is the one the hosts see
- **Case**: `SettingsControllerTest#aLateLoadDoesNotPublishOverANewerWrite`
