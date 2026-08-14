# Free-Text Search (search-free-text)

## Purpose

Free-text search over the MARISA text index finds POIs, locations, regions and other named objects that structured location search misses, and merges them into the existing search results.

## Requirements

### Requirement: Free-text search over text index

The search API SHALL search the text search index (MARISA) for POIs, locations, regions and other objects in addition to the structured location search. The search SHALL use transliteration, matching the `TextSearchIndex::Search` behavior used by OSMScout2.

#### Scenario: Search finds POI by name

- **WHEN** the user searches for "cafe central"
- **AND** the database's text index contains a POI named "Café Central"
- **THEN** the result list contains the POI "Café Central" with its coordinates

#### Scenario: Search covers all object groups

- **WHEN** the user searches for a term
- **AND** the database has a text index
- **THEN** POIs, locations, regions and other objects are all searched

#### Scenario: Search falls back without text index

- **WHEN** the user searches for a location
- **AND** the database has no text index
- **THEN** the search still returns structured location results without failing

### Requirement: Free-text results merged with structured results

Free-text results and structured location results SHALL be merged into a single result list, deduplicated by object reference, and truncated to the requested limit.

#### Scenario: Same object found by both searches appears once

- **WHEN** an object matches both the structured search and the free-text search
- **AND** the user searches for the object's name
- **THEN** the object appears exactly once in the result list

#### Scenario: Result list respects limit

- **WHEN** a search would return more results than the requested limit
- **AND** the user searches with a limit of N
- **THEN** the result list contains at most N entries

### Requirement: Free-text search excludes basemap

The basemap database SHALL NOT be searched, because it is a low-zoom background map whose index files may be absent or incomplete.

#### Scenario: Basemap never searched

- **WHEN** the user searches while the basemap is loaded
- **THEN** no results are returned from the basemap database

### Requirement: Free-text results without garbage entries

Free-text search results SHALL NOT contain empty or invalid entries. Entries whose text index reference cannot be resolved to a real object, entries whose object resolves to invalid (0,0) coordinates, and entries introduced by result-list padding SHALL be omitted from the result list.

#### Scenario: Unresolvable index entry omitted

- **WHEN** the free-text search returns an entry whose object reference cannot be resolved (e.g. a stale or mismatched text index key)
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining valid results SHALL be returned normally

#### Scenario: Zero-coordinate entry omitted

- **WHEN** a free-text search entry resolves to an object with (0,0) coordinates
- **THEN** the entry SHALL be omitted from the results

#### Scenario: Invalid string data entry omitted

- **WHEN** a search entry carries string data that is not valid UTF-8 (e.g. garbage bytes from a corrupt text index or database entry)
- **THEN** the entry SHALL be omitted from the results
- **AND** the search SHALL complete without crashing

#### Scenario: No padding entries below limit

- **WHEN** the number of valid free-text results is smaller than the remaining space in the result list
- **THEN** the result list SHALL NOT be padded with empty entries
- **AND** the result list SHALL contain at most the requested limit of entries, all of which have a non-empty label and valid coordinates
