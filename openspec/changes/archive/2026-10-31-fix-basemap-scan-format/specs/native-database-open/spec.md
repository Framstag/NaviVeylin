# Spec Delta

## ADDED Requirements

### Requirement: The map scan publishes only databases whose data format this build reads

The recursive scan that discovers map databases SHALL leave out a candidate directory whose `types.dat` names
a data format version other than the one this build reads, SHALL name that directory and both versions in one
client log line, and SHALL count it as invalid in the scan's own summary.

#### Scenario: A directory whose version this build does not read is skipped

- **WHEN** the scan meets a directory that holds every mandatory database file and whose `types.dat` names a
  version older or newer than this build's `FILE_FORMAT_VERSION`
- **THEN** the directory is absent from the published set
- **THEN** one warning line names the directory, the version its file names and the version this build reads
- **THEN** the scan's summary counts the directory as invalid
- **THEN** the case `MapManagerTest` — "A scan skips a database whose data format this build does not read" —
  exercises this

#### Scenario: A directory whose version matches is published unchanged

- **WHEN** the scan meets a directory whose `types.dat` names this build's `FILE_FORMAT_VERSION`
- **THEN** the directory is published exactly as before the version check existed
- **THEN** the scan's summary counts it as valid

### Requirement: A map directory whose data format cannot be read stays published

The scan SHALL NOT decide a candidate directory whose data format version cannot be read: a missing,
shorter-than-the-version-field or unreadable `types.dat` SHALL keep its directory in the published set, so the
native reader stays the authority on every directory this build does not refuse.

#### Scenario: An unreadable version is the reader's to decide

- **WHEN** the scan meets a directory whose `types.dat` is empty, shorter than the version field, or unreadable
- **THEN** the directory stays in the published set
- **THEN** the client's own log line for that directory is written at debug level
- **THEN** the case `MapManagerTest` — "A database whose data format version cannot be read stays published" —
  exercises this
