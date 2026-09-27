# Spec Delta — native-tile-data-cache

## MODIFIED Requirements

### Requirement: Native tile data cache capacity configured

The system SHALL set the capacity of libosmscout's per-database tile data cache on every open database — the regional map and, when present, the basemap — via the JNI bridge's cache-configuration API.

- The configured capacity SHALL be a tuned constant, not a user-facing setting
- The capacity SHALL be applied when a database is opened, before the first render of that database
- Each database SHALL receive an independent cache configuration (the basemap has its own `MapService` instance)
- The configured capacity SHALL exceed the library default (25 tiles) by a meaningful margin
- Every surface that opens databases SHALL configure the capacity itself: the phone map path and the car warmup path SHALL each apply their own tuned constant, so no surface inherits the library default or another surface's value by accident
- The capacity is a property of the native client, not of a database, and the native layer re-applies the current value to every open database on each render — so the effective capacity SHALL be decided once per client process: the first surface to configure it wins, a later configuration that requests a different value SHALL NOT change it (it SHALL be reported as a no-op in the diagnostics), and repeated configuration with the same value SHALL be idempotent
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

#### Scenario: A phone-started process keeps the phone capacity for the car session

- **WHEN** the phone map screen has configured the capacity in a process
- **AND** a car session in that same process later opens databases through the shared native client
- **THEN** the effective capacity SHALL remain the phone's value
- **AND** the car's different request SHALL NOT change it mid-session (no capacity flip between renders)

#### Scenario: Repeated configuration with the same value is idempotent

- **WHEN** a surface configures the capacity more than once with the same value
- **THEN** the effective capacity SHALL be unchanged and no error SHALL be surfaced
