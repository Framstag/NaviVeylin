# Spec Delta

## ADDED Requirements

### Requirement: A user change outranks an in-flight settings read

The dark-mode state SHALL carry a preference or an ambient-light sensitivity the user changed while the
persisted settings were still loading: when that read completes it SHALL NOT restore a persisted value over the
user's newer choice, in the resolver and in the UI state alike. A field the user did not change SHALL still be
restored from disk.

#### Scenario: A change made while the read runs survives it

- **WHEN** the persisted settings carry Automatic and the sensor off, and the user sets the preference to On and
  the sensitivity to High after the start-up read has begun but before it completes
- **THEN** the completed read leaves the preference On and the sensitivity High, resolved and in the UI state,
  instead of restoring Automatic and off
- **Case**: `DarkModeLateRestoreTest.aChangeThatLandsBeforeTheReadKeepsBothFields`

#### Scenario: An unchanged field is still restored

- **WHEN** the persisted settings carry Off and Medium and the user changes neither field while the start-up read
  runs
- **THEN** the completed read restores Off and Medium into the resolver and the UI state
- **Case**: `DarkModeLateRestoreTest.untouchedFieldsAreStillRestoredFromDisk`
