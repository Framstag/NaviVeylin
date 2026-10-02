# native-tile-data-cache Specification

## Purpose

Configure the capacity of libosmscout's per-database tile data caches (regional map and basemap) through the JNI bridge so render, pan, and zoom operations reuse previously loaded tile data across frames and zoom levels.

## Requirements

### Requirement: Native tile data cache capacity configured

The system SHALL set the capacity of libosmscout's per-database tile data cache on every open database — the regional map and, when present, the basemap — via the JNI bridge's cache-configuration API.

- The configured capacity SHALL be a tuned constant, not a user-facing setting
- The capacity SHALL be applied when a database is opened, before the first render of that database
- Each database SHALL receive an independent cache configuration (the basemap has its own `MapService` instance)
- The configured capacity SHALL exceed the library default (25 tiles) by a meaningful margin
- Every surface that opens databases SHALL configure the capacity itself: the phone map path and the car warmup path SHALL each apply their own tuned constant, so no surface inherits the library default or another surface's value by accident
- The capacity is a property of the native client, not of a database, and the native layer re-applies the current value to every open database on each render — so the effective capacity SHALL be client-global and **raise-only**: the highest value any surface has requested is the effective capacity; a request **above** the current value SHALL be applied, a request **below** it SHALL NOT lower it (it SHALL be reported as rejected in the diagnostics), and repeated configuration with the same value SHALL be idempotent. A surface therefore can never shrink another surface's working set mid-session
- Therefore the effective capacity SHALL NOT depend on which surface opened databases first
- The raise-only rule governs **configuration**. An explicit retention release MAY lower the capacity (see "Retention is released when the device is low on memory or the app stops using it"); that release is a deliberate, recorded operation, not a configuration request from a surface
- The car's tuned constant SHALL be smaller than the phone's, because the car surface's RAM budget is the smaller one

#### Scenario: Regional database cache is configured

- **WHEN** the app opens a regional map database
- **THEN** the native tile data cache for that database is configured with the tuned capacity before its first render

#### Scenario: Basemap gets its own cache configuration

- **WHEN** a basemap is loaded alongside a regional database
- **THEN** the basemap's native tile data cache is configured with the tuned capacity, independent of the regional database's cache

#### Scenario: Cache configuration failure is non-fatal

- **WHEN** configuring the cache capacity fails (e.g., no database service available)
- **THEN** rendering continues with the library default and no crash or error state is surfaced

#### Scenario: Car-only process does not run on the library default

- **WHEN** the app runs on an automotive head unit (or in a projection session where the phone map screen was never opened)
- **AND** the car warmup opens the installed map databases
- **THEN** the car's tuned capacity SHALL have been configured before those databases render
- **AND** the effective capacity SHALL exceed the library default

#### Scenario: Car-first ordering does not degrade the phone

- **WHEN** a car session opens databases first with the smaller car capacity
- **AND** the phone map surface then opens databases in the same process with the larger phone capacity
- **THEN** the effective capacity is raised to the phone capacity
- **AND** the phone does not render on the smaller car capacity

#### Scenario: Phone-first ordering does not silently change the car value

- **WHEN** the phone opened databases first with the larger capacity
- **AND** a car session then requests the smaller capacity
- **THEN** the effective capacity is not lowered
- **AND** the request is reported as not applied instead of flipping the capacity mid-session

#### Scenario: A car-only process keeps the car capacity

- **WHEN** no surface with the larger capacity exists in the process
- **THEN** the effective capacity is the car capacity

#### Scenario: Repeated configuration with the same value is idempotent

- **WHEN** a surface configures the capacity more than once with the same value
- **THEN** the effective capacity SHALL be unchanged and no error SHALL be surfaced

### Requirement: Retention is released when the device is low on memory or the app stops using it

The app SHALL release retained tile data instead of holding it until the system starts killing processes.
A release SHALL be confined: a fault raised while releasing SHALL NOT escape onto the process-wide
dispatcher the release runs on (it SHALL be recorded instead), because the same throwable would reach the
app's uncaught-exception handler — in a memory-pressure callback, where the app can least afford to die.
A release SHALL be triggered by (a) the device reporting low memory **while the app is running**, and
(b) the platform reporting that the app's UI is no longer visible or that its process has moved to the
background LRU list — case (b) **only while no car session is live**, because with a car session the car
surface keeps rendering from those caches, so releasing there would buy a refetch for nothing. A release
SHALL lower the configured capacity for the client's open databases, which evicts least-recently-used
tile data in the native cache, and it SHALL be recorded with the trigger and the capacity it released to.
The release SHALL be graduated: a first or moderate signal SHALL halve the capacity, and a severe or
further-along signal SHALL lower it to the library default. A release SHALL NOT change rendered output,
SHALL NOT fault, and SHALL degrade gracefully when no database or native service is available.

#### Scenario: Low memory while the app is running releases retention

- **WHEN** the device reports that it is low on memory while the app is running
- **THEN** the configured capacity for the open databases SHALL be lowered (halved, or to the library
  default in the severe band)
- **AND** the native cache SHALL have evicted least-recently-used tile data
- **AND** the release SHALL be recorded with its trigger and the capacity it released to

#### Scenario: A hidden UI releases only when no car session renders from the cache

- **WHEN** the platform reports the app's UI hidden or its process backgrounded and no car session is live
- **THEN** the release path SHALL be applied (halved for the hidden UI, the library default for the
  backgrounded process)

#### Scenario: A live car session keeps the cache

- **WHEN** the platform reports the app's UI hidden or its process backgrounded while a car session is live
- **THEN** no release SHALL take place

#### Scenario: A fault during a release is confined

- **WHEN** a release raises a throwable (a client-build failure, a broken native linkage, a torn-down context)
- **THEN** the throwable SHALL NOT escape onto the dispatcher the release runs on
- **AND** the confined fault SHALL be recorded, with the throwable's class and no message
- **AND** the retained capacity SHALL be unchanged
- **AND** the car surface SHALL keep rendering from the retained tile data without a refetch

#### Scenario: Severity and repetition converge at the library default

- **WHEN** the device stays low on memory across repeated signals
- **THEN** each release SHALL lower the capacity further until it reaches the library default
- **AND** a further signal at that floor SHALL be a no-op rather than an error

#### Scenario: Rendering is unchanged after a release

- **WHEN** the map is rendered after a retention release
- **THEN** the rendered content SHALL be identical to a render of the same viewport before the release
- **AND** only data reuse is affected, never the set or appearance of drawn objects

#### Scenario: A release never faults or surfaces an error

- **WHEN** a release runs with no open database, without a usable native service, or twice in a row
- **THEN** no crash, error state, or user-visible message SHALL result
- **AND** rendering SHALL continue

#### Scenario: A session end alone does not release retention

- **WHEN** a car session ends while the app keeps running and the phone map is still displayed
- **THEN** that boundary alone SHALL NOT lower the configured capacity
- **AND** the release SHALL be triggered only by the platform's memory-pressure signal (the session boundary was evaluated and rejected, with the reason recorded in the change design)

### Requirement: Retention stops at the configured capacity

The retained tile data of a process SHALL be bounded by the configured capacity: after the user has
walked an area larger than the configured capacity, the process footprint SHALL NOT continue to grow
with further walking, and the ceiling reached SHALL be reproducible by the documented walk protocol
(scripted zoom-out steps followed by scripted pan steps at a fixed zoom). The ceiling is a property to
be measured and quoted, not assumed.

#### Scenario: Walking beyond the capacity does not keep growing the footprint

- **WHEN** the documented walk protocol is run past the point where the configured capacity is reached
- **THEN** the process footprint (native heap and total PSS) SHALL stop growing
- **AND** repeated further walking SHALL NOT raise it beyond the measured ceiling

#### Scenario: The ceiling is reproducible

- **WHEN** the walk protocol is repeated on the same build and device state
- **THEN** the ceiling SHALL reproduce within a small tolerance
- **AND** the measurement SHALL be quotable as the build's retention ceiling for the change that tuned it

#### Scenario: Returning to the starting viewport retains the walked data

- **WHEN** the walk protocol ends by returning to its starting viewport
- **THEN** the retained footprint SHALL stay at the ceiling reached by the walk
- **AND** the app SHALL NOT report or display an error

### Requirement: Cache sizing must not change rendering output

Configuring the tile data cache capacity SHALL affect only data reuse and performance; the set and appearance of rendered objects SHALL be identical regardless of cache capacity.

#### Scenario: Same viewport renders identically under different capacities

- **WHEN** the same viewport is rendered with a small and with the tuned cache capacity
- **THEN** the resulting map content is identical (cache capacity is a performance knob only)
