## Purpose

Configure the capacity of libosmscout's per-database tile data caches (regional map and basemap) through the JNI bridge so render, pan, and zoom operations reuse previously loaded tile data across frames and zoom levels.

## ADDED Requirements

### Requirement: Native tile data cache capacity configured

The system SHALL set the capacity of libosmscout's per-database tile data cache on every open database — the regional map and, when present, the basemap — via the JNI bridge's cache-configuration API.

- The configured capacity SHALL be a tuned constant, not a user-facing setting
- The capacity SHALL be applied when a database is opened, before the first render of that database
- Each database SHALL receive an independent cache configuration (the basemap has its own `MapService` instance)
- The configured capacity SHALL exceed the library default (25 tiles) by a meaningful margin

#### Scenario: Regional database cache is configured

- **WHEN** the app opens a regional map database
- **THEN** the native tile data cache for that database is configured with the tuned capacity before its first render

#### Scenario: Basemap gets its own cache configuration

- **WHEN** a basemap is loaded alongside a regional database
- **THEN** the basemap's native tile data cache is configured with the tuned capacity, independent of the regional database's cache

#### Scenario: Cache configuration failure is non-fatal

- **WHEN** configuring the cache capacity fails (e.g., no database service available)
- **THEN** rendering continues with the library default and no crash or error state is surfaced

### Requirement: Cache sizing must not change rendering output

Configuring the tile data cache capacity SHALL affect only data reuse and performance; the set and appearance of rendered objects SHALL be identical regardless of cache capacity.

#### Scenario: Same viewport renders identically under different capacities

- **WHEN** the same viewport is rendered with a small and with the tuned cache capacity
- **THEN** the resulting map content is identical (cache capacity is a performance knob only)
