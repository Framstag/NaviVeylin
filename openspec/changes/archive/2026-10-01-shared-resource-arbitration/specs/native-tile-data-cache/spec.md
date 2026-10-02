# Spec Delta

## MODIFIED Requirements

### Requirement: Native tile data cache capacity configured

The system SHALL set the capacity of libosmscout's per-database tile data cache on every open database — the regional map and, when present, the basemap — via the JNI bridge's cache-configuration API.

- The configured capacity SHALL be a tuned constant, not a user-facing setting
- The capacity SHALL be applied when a database is opened, before the first render of that database
- Each database SHALL receive an independent cache configuration (the basemap has its own `MapService` instance)
- The configured capacity SHALL exceed the library default (25 tiles) by a meaningful margin
- The capacity is client-global: when several surfaces share one client in a process, the effective capacity SHALL be the highest capacity any surface requested, and a later request SHALL raise it but SHALL never lower it
- Therefore the effective capacity SHALL NOT depend on which surface opened databases first

#### Scenario: Regional database cache is configured

- **WHEN** the app opens a regional map database
- **THEN** the native tile data cache for that database is configured with the tuned capacity before its first render

#### Scenario: Basemap gets its own cache configuration

- **WHEN** a basemap is loaded alongside a regional database
- **THEN** the basemap's native tile data cache is configured with the tuned capacity, independent of the regional database's cache

#### Scenario: Cache configuration failure is non-fatal

- **WHEN** configuring the cache capacity fails (e.g., no database service available)
- **THEN** rendering continues with the library default and no crash or error state is surfaced

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

#### Scenario: Car-only process does not run on the library default

- **WHEN** the app runs on an automotive head unit (or in a projection session where the phone map screen was never opened)
- **AND** the car warmup opens the installed map databases
- **THEN** the car's tuned capacity SHALL have been configured before those databases render
- **AND** the effective capacity SHALL exceed the library default

#### Scenario: Repeated configuration with the same value is idempotent

- **WHEN** a surface configures the capacity more than once with the same value
- **THEN** the effective capacity SHALL be unchanged and no error SHALL be surfaced
