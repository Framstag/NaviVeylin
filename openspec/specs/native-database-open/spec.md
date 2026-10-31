# native-database-open Specification

## Purpose
Registering map databases through the JNI bridge: several app paths open databases in one process
(phone map start, car session warmup, map scan, download completion), a whole discovered set can be
registered in one coordinated change, and concurrent registration cannot corrupt the client's
database set or take the process down.

## Requirements

### Requirement: Concurrent database registration is safe

The system SHALL support database-open calls issued concurrently from different app threads in one
process without a data race on the client's database set, without corrupting that set, and without a
process-level fault. After all concurrent calls return, the registered set SHALL contain every
directory whose open succeeded.

#### Scenario: Two threads open different directories

- **WHEN** two app threads open two different map database directories at the same time
- **THEN** both calls complete without a process fault
- **THEN** the registered database set contains both directories

#### Scenario: Two threads open the same directory

- **WHEN** two app threads open the same map database directory at the same time
- **THEN** both calls complete without a process fault
- **THEN** the directory appears exactly once in the registered set

#### Scenario: An opening in flight while the map renders

- **WHEN** a database open runs while a map render or a query is being served
- **THEN** the in-flight work completes against a consistent database set (never a partially updated one)
- **THEN** no process fault occurs

#### Scenario: Open storm under load

- **WHEN** concurrent openers repeatedly open the installed directories in a loop while the app renders
- **THEN** every iteration leaves a consistent set and the process stays alive

### Requirement: A whole database set is registered in one coordinated change

The system SHALL offer a batch entry point that registers a list of database directories in one call,
and SHALL apply that list to the client's database set as **one** coordinated update: the previously
open databases are closed and the new set is opened once for the batch, not once per directory. The
set a render, a search or a routing call sees SHALL be the same complete set after the batch returns.

#### Scenario: Batch of installed directories during startup

- **WHEN** the app registers the installed map databases as one batch of K directories
- **THEN** exactly one database-set update takes place
- **THEN** all K directories are available to the renderer afterwards

#### Scenario: Phone path and car warmup in one process

- **WHEN** the phone map path and the car session warmup each register their directories
- **THEN** neither call observes a set that the other left half-applied
- **THEN** the renderer sees every directory from both calls

#### Scenario: One directory cannot be opened

- **WHEN** a batch contains a directory that cannot be opened (missing, unreadable, invalid)
- **THEN** the remaining directories are still registered and usable
- **THEN** the unusable directory is still part of the registered set — registering a path does not inspect the filesystem — and the database layer reports its failure when the set is applied
- **THEN** no registration call fails because of one bad directory

### Requirement: Single-database open keeps its contract

The system SHALL keep the single-database open entry point available with unchanged result semantics:
it SHALL report success, SHALL register the directory so it is usable, and SHALL NOT duplicate an
already registered directory. Opening a single path SHALL have the same effect on the database set as
a batch containing just that path.

#### Scenario: Open a valid directory

- **WHEN** the app opens a valid map database directory
- **THEN** the call reports success
- **THEN** the directory is usable for rendering

#### Scenario: Open a directory that is already registered

- **WHEN** the app opens a directory that is already in the registered set
- **THEN** the call succeeds without adding a duplicate entry
- **THEN** previously registered directories stay registered

#### Scenario: Open an invalid or missing directory

- **WHEN** the app opens an invalid or missing directory
- **THEN** the call reports failure and the error is recoverable
- **THEN** the registered set is unchanged and the process stays alive

### Requirement: Registration does not stall the render path proportionally to the number of maps

Registering the installed databases SHALL NOT amount to one close-and-reopen cycle of the whole
database set per directory: the number of database-set updates SHALL equal the number of registration
calls (batches plus single opens), and startup registration time SHALL NOT grow quadratically with the
number of installed maps.

#### Scenario: Startup with many installed maps

- **WHEN** the app starts with K installed map databases registered as one batch
- **THEN** the number of database-set updates is one, independent of K
- **THEN** registration time grows at most linearly with K

#### Scenario: Render is not blocked across several set changes

- **WHEN** a map render is requested while the installed databases are being registered
- **THEN** the render waits for at most one coordinated set change rather than one per directory
- **THEN** the surface is updated without a multi-second stall caused by registration

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
