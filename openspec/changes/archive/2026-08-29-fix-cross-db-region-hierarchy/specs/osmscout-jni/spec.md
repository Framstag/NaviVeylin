## MODIFIED Requirements

<!-- No changes to existing osmscout-jni requirements; added requirements below. -->

## ADDED Requirements

### Requirement: Search result region hierarchy scoped to originating database
When a search returns results while more than one map database is loaded, the admin region hierarchy of every result SHALL be resolved exclusively within the database that produced the result. Hierarchy levels SHALL NEVER be resolved from another loaded database, even when file offsets coincide between databases.

#### Scenario: Multi-map install resolves hierarchy from the correct map
- **WHEN** map databases for Iceland and Germany/Nordrhein-Westfalen are loaded
- **AND** a search returns an address in Bergkamen, Germany (e.g. "Rosenweg 20 Bergkamen")
- **THEN** the result's `adminRegionHierarchy` SHALL contain only German region names (e.g. "Bergkamen/Kreis Unna/Arnsberg/Nordrhein-Westfalen/Deutschland")
- **AND** SHALL NOT contain any region name from the Iceland database

#### Scenario: Coinciding file offsets do not poison the hierarchy
- **WHEN** the file offset of a region in database A numerically equals the file offset of a region in database B
- **AND** a search result from database A references its parent region at that offset
- **THEN** the resolved parent SHALL be database A's region
- **AND** the hierarchy path SHALL remain entirely consistent within database A

#### Scenario: Single-database behavior unchanged
- **WHEN** exactly one map database is loaded
- **AND** a search returns an address
- **THEN** the result's `adminRegionHierarchy` SHALL resolve exactly as before this change

#### Scenario: Hierarchy falls back when the chain cannot be resolved
- **WHEN** the originating database cannot resolve the full parent chain of a result's admin region
- **THEN** the hierarchy SHALL contain the resolvable prefix of the chain only
- **AND** the result SHALL still be returned with its remaining fields intact

### Requirement: Search result object identity scoped to originating database
When a search returns results while more than one map database is loaded, each result's object reference (coordinates, object name, object type) SHALL be resolved exclusively against the database that produced the result. Resolving an object reference from another loaded database SHALL NOT override the originating database's resolution, even when the object file offset coincides with an object in another database.

#### Scenario: Coordinates resolved from the originating database
- **WHEN** a search result's object file offset numerically coincides with an object in another loaded database
- **THEN** the result's coordinates and name SHALL come from the originating database's object
- **AND** the other database's coinciding object SHALL NOT be used

#### Scenario: Unreadable object still drops the entry
- **WHEN** the originating database cannot read the result's object reference (stale or corrupt index entry)
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining entries SHALL be returned normally (unchanged resilience behavior)

### Requirement: Free-text dedup scoped to originating database
Free-text search hit deduplication SHALL compare object file offsets only within the same database. Two hits from different databases SHALL NOT be treated as the same object merely because their file offsets coincide.

#### Scenario: Coinciding offsets across databases both returned
- **WHEN** a free-text search finds a hit in database A and a hit in database B at numerically equal file offsets
- **THEN** both hits SHALL be returned
- **AND** neither SHALL be dropped as a duplicate of the other

#### Scenario: Duplicate within one database still deduplicated
- **WHEN** the same object file offset appears twice in one database's free-text results
- **THEN** the duplicate SHALL be dropped and the object SHALL appear once
